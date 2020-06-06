package graxxia

import org.apache.commons.math3.analysis.interpolation.LinearInterpolator
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction

import groovy.transform.CompileStatic

/**
 * Facilitates easy binning of objects based on fixed bin sizes spanning an interval
 * 
 * @author Simon Sadedin
 */
class Binner {
    
    int binCount
    
    double min
    
    double max
    
    double binSize
    
    public Binner(int binCount, double min, double max) {
        super();
        this.binCount = binCount;
        this.min = min;
        this.max = max;
        this.binSize = (max - min) / binCount
    }
    
    @CompileStatic
    final int bin(final Number x) {
        (x - min) / binSize
    }

    @CompileStatic
    final int bin(final double x) {
        (x - min) / binSize
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
    List<Stats> stats(Iterable<Number> xs, Iterable<Number> ys) {
        
        List<Stats> result = (1..binCount).collect { new Stats() }
        
        final double binSize = (max - min) / binCount
        Iterator yi = ys.iterator()
        for(Number x in xs) {
            double y = yi.next() as double
            int bin = (int)((x - min) / binSize)
            if(bin < 0)
                continue
            if(bin > max)
                continue
            result[bin].addValue(y)
        }
        
        return result
    }
}
