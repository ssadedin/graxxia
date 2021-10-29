package graxxia

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction

import groovy.transform.CompileStatic

/**
 * Facilitates easy binning of objects based on fixed bin sizes spanning an interval
 * 
 * A binner is defined with a number of bins and a range of values the bins should span:
 * <p>
 * <pre>
 * binner = new Binner(20, 0, 1) // 20 bins from zero to 1
 * </pre>
 * Then you can feed a set of values to the binner and it will assign each one to a bin,
 * using several different functions. To just know which bin, use the {@link #bin} function:
 * <pre>
 * binIndex = binner.bin(0.63) // 12
 * </pre>
 * But more usefully, you can feed any iterables and calculate the statistics for values that
 * fall into bins:
 * <pre>
 * stats = binner.stats(
 *     [0,2,1,5,9,2,4,4],  // values for calculing bin
 *     [10,5,20,1,3,5,2]   // calculate statistics of these values
 * )
 * </pre>
 * If your goal is to do some kind of normalisation by bin, there is a convenient spline function
 * to create a linear spline to model the values:
 * <pre>
 * fn = binner.stats(
 *     [0,2,1,5,9,2,4,4],  // values for calculing bin
 *     [10,5,20,1,3,5,2]   // calculate statistics of these values
 * )
 * prediction = fn.value(5.3) // predict by interpolating b/w bins 
 * </pre>
 * For arbitrary binning by any attribute of any object, you can use the built 
 * in Groovy <code>groupBy</code> along with the {@link #bin} method:
 * <pre> 
 *      def objs = [
 *          [ foo: 1, bar: 'cat'],
 *          [ foo: 4, bar: 'tree'],
 *          [ foo: 5, bar: 'tree'],
 *          [ foo: 7, bar: 'house'],
 *      ]
 *      def binner = new Binner(3, 0, 10)
 *      def bins = objs.groupBy {
 *          binner.bin(it.foo)
 *      }
 *      assert bins[0].contains('cat')
 * </pre>
 * 
 * @author Simon Sadedin
 */
class Binner {
    
    int binCount
    
    double min
    
    double max
    
    double binSize
    
    private double halfBinSize
    
    public Binner(int binCount, double min, double max) {
        super();
        this.binCount = binCount;
        this.min = min;
        this.max = max;
        this.binSize = (max - min) / binCount
        this.halfBinSize = binSize/2
    }
    
    /**
     * @return the index of the bin for the value x
     */
    @CompileStatic
    final int bin(final Number x) {
        (x - min) / binSize
    }

    /**
     * @return the index of the bin for the value x
     */
    @CompileStatic
    final int bin(final double x) {
        (x - min) / binSize
    }
    
    /**
     * @return the midpoint value of the bin, specified by its index
     */
    @CompileStatic
    final double value(final int bin) {
        this.min + binSize*bin + halfBinSize
    }
    
    /**
     * @return a list of the bin midpoints (eg: for plotting)
     */
    @CompileStatic
    List<Double> getMidPoints() {
        (0..<binCount).collect { value(it) }
    }
    
    @CompileStatic
    PolynomialSplineFunction spline(Map options = [:], Iterable<Number> xs, Iterable<Number> ys) {

        def stats = stats(xs, ys)
        
        int splinePoints = binCount
        if(options.startPoint)
            ++splinePoints
        if(options.endPoint)    
            ++splinePoints
        
        final double [] bins = new double[splinePoints]
        
        double [] binValues = stats*.mean as double[]
        
        double [] values = new double[splinePoints]
        
        int binOffset = 0
        
        if(options.endPoint || options.startPoint) {
            System.arraycopy(binValues, 0, values, 1, binValues.size())
             
            if(options.startPoint) {
                bins[0] = ((List)options.startPoint)[0]
                values[0] = ((List)options.startPoint)[1]
                ++binOffset
            }
            
            if(options.endPoint) {
                bins[-1] = ((List)options.endPoint)[0]
                values[-1] = ((List)options.endPoint)[1]
            }
        }
        else
            values = binValues
       
        
        final double minStart = min + binSize / 2.0d
        for(int i=0; i<binCount; ++i) {
            bins[i+binOffset] = minStart + i*binSize
        }
        
        new LinearInterpolator().interpolate(bins, values)
    }
    
   @CompileStatic
   List<Stats> stats(final double [] xs, final double [] ys) {
        
        List<Stats> result = (1..binCount).collect { new Stats() }
        
        final double binSize = (max - min) / binCount
        for(int i=0; i < xs.length; ++i) {
            double y = ys[i]
            double x = xs[i]
            if(Double.isNaN((double)y))
                continue
            int bin = (int)((x - min) / binSize)
            if(bin < 0)
                continue
            if(bin >= binCount)
                continue
            result[bin].addValue(y)
        }
        return result
   }

   @CompileStatic
    List<Stats> stats(Iterable<Number> xs, Iterable<Number> ys) {
        
        List<Stats> result = (1..binCount).collect { new Stats() }
        
        final double binSize = (max - min) / binCount
        Iterator yi = ys.iterator()
        for(Number x in xs) {
            double y = yi.next() as double
            if(Double.isNaN((double)y))
                continue
            int bin = (int)((x - min) / binSize)
            if(bin < 0)
                continue
            if(bin >= binCount)
                continue
            result[bin].addValue(y)
        }
        
        return result
    }
}
