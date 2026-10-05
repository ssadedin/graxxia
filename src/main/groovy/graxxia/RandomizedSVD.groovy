/*
 *  Graxxia - Groovy Maths Utilities
 *
 *  Copyright (C) 2014 Simon Sadedin, ssadedin<at>gmail.com and contributors.
 *
 *  This file is licensed under the Apache Software License Version 2.0.
 *  For the avoidance of doubt, it may be alternatively licensed under GPLv2.0
 *  and GPLv3.0. Please see LICENSE.txt in your distribution directory for
 *  further details.
 */
package graxxia

import groovy.transform.CompileStatic

/**
 * Truncated SVD of a matrix stored as a flat, row-major float32 array,
 * computed using the randomized (Halko et al., 2011) algorithm.
 * <p>
 * The point of this class is to make truncated SVD feasible on very wide
 * matrices (hundreds of thousands of columns) that cannot be decomposed by
 * classical dense SVD algorithms because those require working memory
 * proportional to p<sup>2</sup> (80 GB for p = 100,000 in double precision).
 * The randomized algorithm never forms such structures: its working memory
 * is O((n + p) * l) where l = k + oversampling.
 * <p>
 * The algorithm works by projecting the matrix through a random Gaussian
 * matrix Omega (p x l). The columns of the result Y = X*Omega are random
 * linear combinations of the columns of X, and therefore contain a strong
 * component in the direction of the dominant singular vectors. After
 * orthonormalisation (QR), Q spans (approximately) the leading singular
 * subspace; the small matrix B = Q<sup>T</sup>X can then be decomposed
 * cheaply, and its right singular vectors are the right singular vectors
 * of X itself.
 * <p>
 * Accuracy is controlled by the oversampling parameter and by power
 * iterations (each power iteration multiplies the failure probability
 * down for matrices with slowly decaying spectra). All arithmetic is done
 * in 32-bit floating point to halve the memory cost of the working copy;
 * the input matrix is <em>consumed</em> (it is centred in place).
 * <p>
 * Reference: N. Halko, P. G. Martinsson, J. A. Tropp,
 * "Finding Structure with Randomness: Probabilistic Algorithms for
 * Constructing Approximate Matrix Decompositions", SIAM Review 53(2), 2011.
 */
@CompileStatic
class RandomizedSVD {

    /**
     * Singular values of the (centred) input matrix, in descending order.
     * Length equals the number of components requested.
     */
    double[] singularValues

    /**
     * Right singular vectors as row vectors: vt[i] is the i-th singular
     * vector (length p). Shape is k x p, i.e. the transpose of the V matrix.
     */
    double[][] vt

    /**
     * Decompose the given flat, row-major float matrix.
     * <p>
     * Note: the input array is modified in place (columns are centred by
     * subtracting the column means).
     *
     * @param data            matrix values, row-major, length n*p
     * @param n               number of rows
     * @param p               number of columns
     * @param k               number of singular triplets to compute
     * @param oversampling    extra dimensions added to the random sketch
     *                        to improve accuracy (typically 5-20)
     * @param powerIterations number of power iterations applied to the sketch
     *                        to sharpen the subspace (0-3 is normally enough)
     * @param seed            seed for the random projection matrix; fix it
     *                        to make results reproducible
     */
    static RandomizedSVD decompose(float[] data, int n, int p, int k,
                                   int oversampling = 10, int powerIterations = 2, long seed = 42L) {

        if (k <= 0)
            throw new IllegalArgumentException("Number of components must be positive (got $k)")
        if (k > Math.min(n, p))
            throw new IllegalArgumentException("Cannot compute $k components of a $n x $p matrix")
        if (data.length != n * p)
            throw new IllegalArgumentException("Data array has ${data.length} elements but matrix is $n x $p = ${n * p}")

        int l = Math.min(k + oversampling, Math.min(n, p))

        centreColumns(data, n, p)

        // Random Gaussian projection matrix, p x l
        Random rnd = new Random(seed)
        float[] omega = new float[p * l]
        for (int i = 0; i < omega.length; ++i)
            omega[i] = (float) rnd.nextGaussian()

        // Y = X * Omega  (n x l)
        float[] y = gemmNN(data, n, p, omega, l)
        qrInPlace(y, n, l)

        // Power iterations: sharpen the subspace by re-multiplying through X.
        // Each iteration effectively adds an (X*X^T) factor, which suppresses
        // the tail of the spectrum relative to the leading components.
        for (int iter = 0; iter < powerIterations; ++iter) {
            float[] t = gemmTN(data, n, p, y, l)      // X^T * Y  (p x l)
            y = gemmNN(data, n, p, t, l)              // X * T    (n x l)
            qrInPlace(y, n, l)
        }

        // B = Q^T * X  (l x p) - the compressed matrix. From here on the
        // original data is not needed again.
        float[] b = gemmTN(y, n, l, data, p)

        // Small Gram matrix G = B * B^T (l x l). Its eigenvectors are the
        // left singular vectors of B and its eigenvalues are sigma^2.
        // Computing in double for accurate accumulation.
        double[][] gram = new double[l][l]
        for (int l1 = 0; l1 < l; ++l1) {
            int off1 = l1 * p
            for (int l2 = l1; l2 < l; ++l2) {
                int off2 = l2 * p
                double sum = 0d
                for (int c = 0; c < p; ++c)
                    sum += (double) b[off1 + c] * (double) b[off2 + c]
                gram[l1][l2] = sum
                gram[l2][l1] = sum
            }
        }

        JacobiEigen eigen = jacobiEigen(gram)

        RandomizedSVD result = new RandomizedSVD()

        // Sort eigenpairs in descending order of eigenvalue
        List<int[]> order = (0..<l).collect { int i -> [i] as int[] }
        order.sort { int[] a, int[] b2 -> (eigen.values[b2[0]] <=> eigen.values[a[0]]) }

        double sigmaMax = Math.sqrt(Math.max(eigen.values[order[0][0]], 0d))

        result.singularValues = new double[k]
        result.vt = new double[k][p]
        for (int c = 0; c < k; ++c) {
            int srcIndex = order[c][0]
            double sigma = Math.sqrt(Math.max(eigen.values[srcIndex], 0d))
            result.singularValues[c] = sigma

            if (sigma < 1e-9 * sigmaMax)
                throw new IllegalStateException(
                    "Requested $k components but rank of data appears to be $c (singular value $c is ~zero)")

            // v_c = B^T u_c / sigma_c, accumulated in double.
            // Eigenvectors are the COLUMNS of the Jacobi vector matrix.
            double[] u = new double[l]
            for (int row = 0; row < l; ++row)
                u[row] = eigen.vectors[row][srcIndex]
            double norm = 0d
            double[] row = new double[p]
            for (int col = 0; col < p; ++col) {
                double sum = 0d
                for (int l1 = 0; l1 < l; ++l1)
                    sum += (double) b[l1 * p + col] * u[l1]
                row[col] = sum / sigma
                norm += row[col] * row[col]
            }
            // Renormalise to iron out float roundoff in the division
            double invNorm = 1d / Math.sqrt(norm)
            for (int col = 0; col < p; ++col)
                result.vt[c][col] = row[col] * invNorm
        }

        return result
    }

    /**
     * Subtract the mean of each column from the matrix, in place.
     * Means are accumulated in double precision even though the data
     * is float32.
     */
    private static void centreColumns(float[] data, int n, int p) {
        double[] means = new double[p]
        for (int i = 0; i < n; ++i) {
            int off = i * p
            for (int j = 0; j < p; ++j)
                means[j] += data[off + j]
        }
        for (int j = 0; j < p; ++j)
            means[j] /= n
        for (int i = 0; i < n; ++i) {
            int off = i * p
            for (int j = 0; j < p; ++j)
                data[off + j] = (float) (data[off + j] - means[j])
        }
    }

    /**
     * C = A * B where A is n x a and B is a x b, both flat row-major.
     * Result is n x b, flat row-major.
     */
    private static float[] gemmNN(float[] a, int n, int aCols, float[] b, int bCols) {
        float[] c = new float[n * bCols]
        for (int i = 0; i < n; ++i) {
            int aOff = i * aCols
            int cOff = i * bCols
            for (int l = 0; l < aCols; ++l) {
                float av = a[aOff + l]
                if (av == 0f)
                    continue
                int bOff = l * bCols
                for (int j = 0; j < bCols; ++j)
                    c[cOff + j] += av * b[bOff + j]
            }
        }
        return c
    }

    /**
     * C = A^T * D where A is n x aCols and D is n x dCols, both flat
     * row-major. Result is aCols x dCols, flat row-major.
     */
    private static float[] gemmTN(float[] a, int n, int aCols, float[] d, int dCols) {
        float[] c = new float[aCols * dCols]
        for (int i = 0; i < n; ++i) {
            int aOff = i * aCols
            int dOff = i * dCols
            for (int l = 0; l < aCols; ++l) {
                float av = a[aOff + l]
                if (av == 0f)
                    continue
                for (int j = 0; j < dCols; ++j)
                    c[l * dCols + j] += av * d[dOff + j]
            }
        }
        return c
    }

    /**
     * Orthonormalise the columns of the given (n x l) row-major matrix in
     * place using modified Gram-Schmidt. l is small so the strided access
     * pattern is not a performance concern.
     */
    private static void qrInPlace(float[] y, int n, int l) {
        for (int j = 0; j < l; ++j) {
            double norm = 0d
            for (int i = 0; i < n; ++i) {
                double v = y[i * l + j]
                norm += v * v
            }
            norm = Math.sqrt(norm)
            if (norm < 1e-12d)
                throw new IllegalStateException("Randomized projection degenerated: zero column in QR at index $j")
            double inv = 1d / norm
            for (int i = 0; i < n; ++i)
                y[i * l + j] = (float) (y[i * l + j] * inv)

            for (int j2 = j + 1; j2 < l; ++j2) {
                double r = 0d
                for (int i = 0; i < n; ++i)
                    r += y[i * l + j] * y[i * l + j2]
                for (int i = 0; i < n; ++i)
                    y[i * l + j2] = (float) (y[i * l + j2] - r * y[i * l + j])
            }
        }
    }

    /**
     * Holder for the result of {@link #jacobiEigen(double[][]) }.
     */
    @CompileStatic
    static class JacobiEigen {
        double[] values
        double[][] vectors
    }

    /**
     * Eigendecomposition of a small symmetric matrix by the cyclic Jacobi
     * method. Intended for the tiny l x l Gram matrix produced by the
     * randomized sketch, where l is at most a few hundred: at that size
     * the O(l^3) cost per sweep is negligible and the method is simple,
     * robust and easily made accurate.
     * <p>
     * Returns eigenvalues in arbitrary order (the caller sorts them) and
     * the corresponding eigenvectors as the columns of the returned matrix,
     * normalised to unit length.
     */
    private static JacobiEigen jacobiEigen(double[][] a) {
        int m = a.length
        double[][] v = new double[m][m]
        for (int i = 0; i < m; ++i)
            v[i][i] = 1d

        // Scale of the matrix, for convergence thresholds
        double scale = 0d
        for (int i = 0; i < m; ++i)
            scale += Math.abs(a[i][i])
        if (scale == 0d)
            scale = 1d

        for (int sweep = 0; sweep < 100; ++sweep) {
            double offDiagonal = 0d
            for (int pp = 0; pp < m - 1; ++pp)
                for (int qq = pp + 1; qq < m; ++qq)
                    offDiagonal += a[pp][qq] * a[pp][qq]

            if (Math.sqrt(offDiagonal) < 1e-14 * scale)
                break

            for (int pp = 0; pp < m - 1; ++pp) {
                for (int qq = pp + 1; qq < m; ++qq) {
                    double apq = a[pp][qq]
                    if (Math.abs(apq) < 1e-18 * scale)
                        continue

                    double theta = (a[qq][qq] - a[pp][pp]) / (2d * apq)
                    double t = Math.abs(theta) < 1e-300 ? 1d :
                               Math.signum(theta) / (Math.abs(theta) + Math.sqrt(theta * theta + 1d))
                    double c = 1d / Math.sqrt(t * t + 1d)
                    double s = t * c

                    double app = a[pp][pp], aqq = a[qq][qq]
                    a[pp][pp] = app - t * apq
                    a[qq][qq] = aqq + t * apq
                    a[pp][qq] = 0d
                    a[qq][pp] = 0d

                    for (int kk = 0; kk < m; ++kk) {
                        // A accumulates J^T * A * J: rows/cols pp and qq are
                        // handled explicitly by the diagonal updates above,
                        // all other rows get the standard 2D rotation ...
                        if (kk != pp && kk != qq) {
                            double akp = a[kk][pp], akq = a[kk][qq]
                            a[kk][pp] = a[pp][kk] = c * akp - s * akq
                            a[kk][qq] = a[qq][kk] = s * akp + c * akq
                        }

                        // ... but V accumulates V * J, so EVERY row of V,
                        // including pp and qq, must be rotated
                        double vkp = v[kk][pp], vkq = v[kk][qq]
                        v[kk][pp] = c * vkp - s * vkq
                        v[kk][qq] = s * vkp + c * vkq
                    }
                }
            }
        }

        // Normalise eigenvector columns
        for (int j = 0; j < m; ++j) {
            double norm = 0d
            for (int i = 0; i < m; ++i)
                norm += v[i][j] * v[i][j]
            double inv = 1d / Math.sqrt(norm)
            for (int i = 0; i < m; ++i)
                v[i][j] *= inv
        }

        return new JacobiEigen(values: (0..<m).collect { a[it][it] } as double[], vectors: v)
    }
}
