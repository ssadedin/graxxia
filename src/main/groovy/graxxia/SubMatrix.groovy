/*
 *  Graxxia - Groovy Maths Utililities
 *
 *  Copyright (C) Simon Sadedin, ssadedin<at>gmail.com and contributors.
 *
 *  This file is licensed under the Apache Software License Version 2.0.
 *  For the avoidance of doubt, it may be alternatively licensed under GPLv2.0
 *  and GPLv3.0. Please see LICENSE.txt in your distribution directory for
 *  further details.
 */
package graxxia

import groovy.transform.CompileStatic

/**
 * Wraps a matrix to support copyless access to a submatrix of a 
 * {@link Matrix} class.
 * <p>
 * Only a subset of the overall features of the Matrix class are 
 * available, however basic row / column, named column and iterator 
 * access is supported.
 * <p>example:
 * <pre>
 * // Creates matrix like
 * // [ 
 * //  [1,2,3],
 * //  [2,3,4],
 * //  ...
 * // ]
 * Matrix m = new Matrix(
 *     (1..10).collect { [ it, it+1, it+2] as double[] },
 * ) 
 * s.@names = ['a','b','c']
 * SubMatrix s = new SubMatrix(m, 1, 3)
 * assert s[0][1] == 3
 * assert s.b[1] == 3
 * </pre>
 * 
 * @author Simon Sadedin
 */
class SubMatrix {
    
    Matrix data
    
    final int rowOffset
    
    final int numRows
    
    SubMatrix(Matrix m, int rowOffset, int numRows) {
        this.rowOffset = rowOffset
        this.numRows = numRows
        this.data = m
    }
    
    Object getAt(Object n) {
        if(n instanceof Integer) {
            if(n >= numRows)
                throw new IndexOutOfBoundsException("Index $n is greater than rows in submatrix which has ${numRows} from the original ${m.rowDimension}")
            return data.getAt(n+rowOffset)
        }
    }
    
    int getColumnDimension() {
        return data.getColumnDimension()
    }
    
    int getRowDimension() {
        return numRows
    }
    
    @CompileStatic
    class SubMatrixIterator implements Iterator {
        
           final int myRowOffset = rowOffset
           final int myNumRows = numRows
           final double[][] myData = data.matrix.dataRef
  
           int i = myRowOffset
           final int iNumRows = i + myNumRows
           
           boolean hasNext() {
               return i<iNumRows;
           }
           
           Object next() {
               myData[i++]
           }
           
           void remove() { 
               throw new UnsupportedOperationException() 
           }
    }
    
    @CompileStatic
    Iterator iterator() {
       return new SubMatrixIterator() 
    }
    
    @CompileStatic
    MatrixColumn col(int n) {
        new MatrixColumn(
            columnIndex:n, 
            sourceMatrix: data, 
            name: data.@names[n],
            rowOffset: rowOffset,
            rowLimit: rowOffset + numRows
            )
    }
    
    
    @CompileStatic
    Object getProperty(String name) {
        if(name in this.data.@names) {
            return this.col(this.data.@names.indexOf(name))
        }
        
       def result = ((GroovyObject)this).getProperty(name)
       if(result == null)
           throw new IllegalArgumentException("No column named $name in this Matrix")
           
       return result
    }
    
    int size() {
        return this.@numRows
    }
}
