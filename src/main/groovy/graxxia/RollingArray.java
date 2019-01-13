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
package graxxia;

/**
 * A rolling buffer of double values - suitable for maintaining 
 * a history fo values over a stream, eg: for computing the
 * moving average or similar.
 * <p>
 * The value at index 0 is always the oldest value, and the value at windowSize-1
 * is the value you just put in most recently.
 * 
 * @author Simon Sadedin
 */
public class RollingArray {
    
    private double [] values;
    
    private final int maxPosition;
    
    int offset = 0;
    
    final int windowSize;
    
    public RollingArray(int windowSize) {
        this.windowSize = windowSize;
        this.maxPosition = windowSize-1;
        this.values = new double[windowSize];
    }
    
    /**
     * Adds a value to the window, shifting it to the right, and updating all the totals.
     * 
     * @param value
     */
    public void add(double value) {
        ++offset;
        setAt(maxPosition, value);
    }
    
    public void setAt(int position, double value) {
        if(position<0 || position>maxPosition)
            throw new IndexOutOfBoundsException(String.format("Index %d is outside the bounds of window of size %d", position, windowSize));
        values[(offset + position) % windowSize] = (short) value;
    }
    
    public double getAt(int position) {
        if(position<0 || position>maxPosition)
            throw new IndexOutOfBoundsException(String.format("Index %d is outside the bounds of window of size %d", position, windowSize));
        return values[(offset + position) % windowSize];
    }
}
