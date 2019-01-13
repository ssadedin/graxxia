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
    
    RollingStats(int length) {
        this.values = new RollingArray(length)
        this.length = length;
    }
    
    double variance = 0.0d
    double mean = 0.0d
    
    void addValue(double x) {
        double oldValue = this.values.getAt(0)
        double oldMean = this.mean
        double newMean = oldMean + (x - oldValue) / this.length 
        this.variance += (x - oldValue) * (x - newMean + oldValue - oldMean) / (this.length -1)
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
