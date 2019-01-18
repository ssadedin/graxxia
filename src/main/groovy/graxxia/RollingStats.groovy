/*
 *  Graxxia - Groovy Maths Utilities
 *
 *  Copyright (C) 2019 Simon Sadedin, ssadedin<at>gmail.com and contributors.
 *
 *  This file is licensed under the Apache Software License Version 2.0.
 *  For the avoidance of doubt, it may be alternatively licensed under GPLv2.0
 *  and GPLv3.0. Please see LICENSE.txt in your distribution directory for
 *  further details.
 */
package graxxia

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics

import groovy.transform.CompileStatic

/**
 * An implementation of an adaptation of Wellfords method for 
 * computing running mean and standard deviation without numerical 
 * stability issues.
 * 
 * @author Simon Sadedin
 */
@CompileStatic
class RollingStats {
    
    RollingArray values
    
    final int length;
    
    static boolean debug = false
    
    /**
     * Used only for debugging! If debug turned on, rolling stats also tracked in this commons-math object
     * to compare with results from this class
     */
    DescriptiveStatistics desc 
    
    RollingStats(int length) {
        this.values = new RollingArray(length)
        this.length = length;
        if(debug) {
            initDebug()
        }
    }
    
    void initDebug() {
        this.desc = new DescriptiveStatistics(length)
        for(i in 1..length)
            desc.addValue(0.0d)
    }
    
    double variance = 0.0d
    double mean = 0.0d
    int n = 0
    
    void addValue(double x) {
        double oldValue = this.values.getAt(0)
        double oldMean = this.mean
        double newMean = oldMean + (x - oldValue) / this.length 
        double oldVariance = variance
        this.variance += (x - oldValue) * (x - newMean + oldValue - oldMean) / (this.length -1)
        
        if(debug) {
            this.desc.addValue(x)
            assert Math.abs(this.variance - desc.variance) < 0.001 : \
                "Variance update produced incorrect value: old var = $oldVariance new var: $variance, true var: $desc.variance, old mean $oldMean, new mean $newMean, length = $length"
            assert variance >= 0 : "Variance update produced negative value: old mean $oldMean, new mean $newMean, length = $length"
        }
        
        // This seems to occur sporadically due to numerical issues when mean / sd is very close to zero
        if(this.variance<0)
            this.variance = 0.0d
        
        this.mean = newMean
        this.values.add(x)
    }
    
    void leftShift(double x) {
        this.addValue(x)
    }
    
    double getStandardDeviation() {
        Math.sqrt(this.getVariance())
    }
}
