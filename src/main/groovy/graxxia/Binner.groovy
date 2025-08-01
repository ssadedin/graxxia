package graxxia

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.FromString
import groovy.transform.stc.SimpleType

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
 * There is also a built in <code>binBy</code> method that allows you to provide the object
 * and the values to bin by, and it will call an aggregator function with the list of
 * objects in each bin, to perform both binning and aggregation of data in each bin
 * in the same call:
 * 
 * <pre> 
 *      def objs = [
 *          [ foo: 1, bar: 'cat'],
 *          [ foo: 4, bar: 'tree'],
 *          [ foo: 5, bar: 'tree'],
 *          [ foo: 7, bar: 'house'],
 *      ]
 *      def binner = new Binner(3, 0, 10)
 *      def binned = binner.binBy(objs, obs*.foo) { binnedValues ->
 *          binnedValues*.foo.average(0)
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
     * Bin the given objects based on a closure that returns the value. The map returns
     * contains midpoints of the bins as keys and lists of objects as values
     * 
     * @param objs  objects to bin
     * @param valueFn   function to compute binned value from object
     * @return  a sparse Map with keys representing the midpoint of each bin
     */
    @CompileStatic
    <T> Map<Number, List> binBy(Iterable<T> objs, @ClosureParams(value=FromString, options=["T"]) Closure<Number> valueFn = null) {
        
        Iterator<T> iObj = objs.iterator()
        
        Map<Integer, List> grouped = [:]
        
        while(iObj.hasNext()) {
            
            T obj = iObj.next()
            Number val = valueFn(obj)
            
            int binIndex = this.bin(val)
            
            List grp = grouped[binIndex]
            if(!grp) {
                grp = grouped[binIndex] = []
            }
            grp.add(obj)
        }
        
        return (Map<Number,List>)grouped.collectEntries {
            [
                this.value(it.key),
                it.value
            ]
        }
    }
    
    /**
     * Count the number of objects mapping to each bin, based on return values from the given closure
     * 
     * @param objs  objects to bin
     * @param valueFn   function to compute binned value from object
     * @return  a sparse Map with keys representing the midpoint of each bin (note: values never observed will not have an entry)
     */
    @CompileStatic
    <T> Map<Number, Integer> countBy(Iterable<T> objs, @ClosureParams(value=FromString, options=["T"]) Closure<Number> valueFn = null) {
        
        Iterator<T> iObj = objs.iterator()
        
        Map<Integer, Integer> grouped = [:]
        
        while(iObj.hasNext()) {
            
            T obj = iObj.next()
            Number val = valueFn(obj)
            
            int binIndex = this.bin(val)
            if(grouped.containsKey(binIndex)) {
                ++grouped[binIndex]
            }
            else {
                grouped[binIndex] = 0
            }
        }
        
        return  (Map<Number,Integer>)grouped.collectEntries {
            [
                this.value(it.key),
                it.value
            ]
        }
    }
    
    
    /**
     * Bin the given objects based on the given list of corresponding values
     * 
     * @param objs
     * @param values
     * @param aggregator
     * @return
     */
    @CompileStatic
    <T> List<Object> binBy(Iterable<T> objs, Iterable<Number> values, @ClosureParams(value=FromString, options=["List<T>", "Integer"]) Closure aggregator = null) {
        
        Iterator iObj = objs.iterator()
        Iterator iVal = values.iterator()
        
        Map<Integer, List> grouped = [:]
        
        while(iVal.hasNext()) {
            
            Object obj = iObj.next()
            Number val = iVal.next()
            
            int binIndex = this.bin(val)
            
            List grp = grouped[binIndex]
            if(!grp) {
                grp = grouped[binIndex] = []
            }
            grp.add(obj)
        }
        
        if(aggregator == null) {
            return (List<Object>)grouped*.value
        }
        
        return grouped.collect { Integer index, List binValues ->
            if(aggregator.maximumNumberOfParameters == 1)
                return aggregator(binValues)
            else
                return aggregator(binValues, index)
        }
    }
    
    /**
     * @return a list of the bin midpoints (eg: for plotting)
     */
    @CompileStatic
    List<Double> getMidPoints() {
        (0..<binCount).collect { value(it) }
    }
    
    @CompileStatic
    PolynomialSplineFunction spline(Map options = [:], double [] xs, double [] ys) {

        def stats = stats(xs, ys)
        
        return splineFromStats(options, stats)
    }

    @CompileStatic
    PolynomialSplineFunction spline(Map options = [:], Iterable<Number> xs, Iterable<Number> ys) {

        def stats = stats(xs, ys)
        
        return splineFromStats(options, stats)
    }

    @CompileStatic
    PolynomialSplineFunction splineFromStats(Map options=[:], List<Stats> stats) {
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
        
        double [] goodBins = bins
        double [] goodValues = values
        
        final List<Number> goodValueIndices = values.findIndexValues { !Double.isNaN((double)it) }
        if(goodValueIndices.size() != values.size()) {
            goodValues = values[goodValueIndices] as double[]
            goodBins = bins[goodValueIndices] as double[]
        }

        new LinearInterpolator().interpolate(goodBins, goodValues)
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
    
    String toString() {
        "${binCount} bins from $min to $max"
    }
}
