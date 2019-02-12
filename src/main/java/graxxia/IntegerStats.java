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
package graxxia;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.io.Writer;
import java.util.Arrays;

import org.apache.commons.math3.exception.MathIllegalArgumentException;
import org.apache.commons.math3.stat.descriptive.StorelessUnivariateStatistic;
import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import groovy.transform.CompileStatic;

/**
 * An efficient method to calculate percentiles of integer values
 * that doesn't require holding them all in memory or sorting them.
 * <p>
 * It relies on some limitations regarding coverage values:
 * <li>They are integers
 * <li>We assume bounds on the range, and don't care about it
 *     above a certain value. For example, we are unlikely to observe
 *     values above 1000 and those that do are unlikely to affect the
 *     50'th percentile.
 * 
 * @author simon.sadedin@mcri.edu.au
 */
public class IntegerStats extends SummaryStatistics implements Serializable {
    
    private static final long serialVersionUID = 1L;

    public int [] values = null;
    
    int total = 0;
    
    static class SerializableStorelessUnivariateStatistic implements StorelessUnivariateStatistic, Serializable {
        
            private static final long serialVersionUID = 1L;

            @Override
            public double evaluate(double[] values, int begin, int length) throws MathIllegalArgumentException {
                return 0;
            }
            
            @Override
            public double evaluate(double[] values) throws MathIllegalArgumentException {
                return 0;
            }
            
            @Override
            public void incrementAll(double[] values, int start, int length) throws MathIllegalArgumentException {
            }
            
            @Override
            public void incrementAll(double[] values) throws MathIllegalArgumentException {
            }
            
            @Override
            public void increment(double d) {
            }
            
            @Override
            public double getResult() {
                return 0;
            }
            
            @Override
            public long getN() {
                return 0;
            }
            
            @Override
            public StorelessUnivariateStatistic copy() {
                return null;
            }
            
            @Override
            public void clear() {
            }
    };
    
    /**
     * 
     * @param maxPercentileValue
     */
    public IntegerStats(int maxPercentileValue) {
        values = new int[maxPercentileValue];
        Arrays.fill(values, 0);
        
        // This is because in benchmarking I found this statistic that I never use
        // is very computationally intensive to compute. So I instead make a dummy that
        // bypasses computing it.
        this.setSumLogImpl(new SerializableStorelessUnivariateStatistic());
    }
    
    public IntegerStats(int maxPercentileValue, InputStream inStream) throws IOException {
        values = new int[maxPercentileValue];
        BufferedReader r = new BufferedReader(new InputStreamReader(inStream));
        String line = null;
        while((line = r.readLine()) != null) {
            leftShift(line);
        }
    }
     
    public IntegerStats(int maxPercentileValue, Iterable sourceValues) {
        values = new int[maxPercentileValue];
        for(Object obj : sourceValues) {
            this.leftShift(obj);
        }
    }
    
    void leftShift(Object obj) {
        if(obj instanceof Integer) {
            addValue((Integer)obj);
        }
        else 
        if(obj instanceof String) {
            addValue(Integer.parseInt(String.valueOf(obj).trim()));
        }
        else 
        if(obj instanceof Number) {
            addValue(((Number)obj).intValue());
        }    
     }
    
    /**
     * Count the given coverage value in calculating the median
     * @param coverage
     */
    public void addValue(int coverage) {
        if(coverage>=values.length)
            ++values[values.length-1];
        else
            ++values[coverage];
        
        super.addValue(coverage);
        ++total;
    }
    
    public void addIntValue(int coverage) {
        if(coverage>=values.length)
            ++values[values.length-1];
        else
            ++values[coverage];
        
        super.addValue(coverage);
        ++total;
    }
  
    
    /**
     * Return the specified percentile from the observed coverage counts
     * eg: for median, getPercentile(50).
     * 
     * @return the specified percentile, if it is smaller than the max value passed in the
     *         constructor.
     */
    public int getPercentile(int percentile) {
        int observationsPassed = 0;
        int lowerValue = -1;
        final int percentileIndex = (int)((float)total / (100f/(float)percentile));
        for(int i=0; i<values.length; ++i) {
            observationsPassed += values[i];
            if(observationsPassed >= percentileIndex) {
                if(total%2 == 0) {
                    // Find the next value and return average of this one and that
                    lowerValue = i;
                    for(int k=i+1; k<values.length; ++k) {
                        if(values[k]>0) {
                            return (lowerValue + k) / 2;
                        }
                    }
                    // Now larger value to average - just return what we had
                    return lowerValue;
                }
                else
                    return i;
            }
        }
        return -1;
    }
    
    /**
     * Read values from standard input, converting them to integers and compiling
     * an IntegerStats object from them.
     * 
     * @param values
     * @return
     * @throws IOException
     */
    static IntegerStats read(InputStream values) throws IOException {
        return new IntegerStats(1000, System.in);
    }
    
    /**
     * @param   threshold
     * @return  the fraction of values above given threshold
     */
    public double fractionAbove(int threshold) {
        final int numValues = values.length;
        int above = 0;
        for(int i=threshold; i<numValues; ++i) {
            above += values[i];
        }
        return ((double)above) / getN();
    }
     
    /**
     * @param coverageDepth
     * @return  the percentage of values above given threshold
     */
    public double percentageAbove(int coverageDepth) {
        return 100d*fractionAbove(coverageDepth);
    }
    
    public int getMedian() {
        return getPercentile(50);
    }
    
    public int getAt(int percentile) {
        return getPercentile(percentile);
    }
    
    public String toString() {
        return super.toString() + "Median: " + getMedian() + "\n";
    }
    
    void save(Writer w) throws IOException {
        for(int i=0; i<values.length; ++i) {
            w.write(i + "\t" + values[i] + "\t" + (1 - fractionAbove(i)));
            w.write('\n');
        }
    }
}
