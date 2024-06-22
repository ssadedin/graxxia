/*
 *  Graxxia - Groovy Maths Utilities
 *
 *  Copyright (C) 2024 Simon Sadedin, ssadedin<at>gmail.com and contributors.
 *
 *  This file is licensed under the Apache Software License Version 2.0.
 *  For the avoidance of doubt, it may be alternatively licensed under GPLv2.0
 *  and GPLv3.0. Please see LICENSE.txt in your distribution directory for
 *  further details.
 */    
package graxxia;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.groovy.runtime.StringGroovyMethods;

/**
 * Java implementation of TSV column format converter to save time
 * 
 * This was identified as a hotspot in profiling, partly due to Groovy method
 * call indirection.
 */
public class TSVColumnConverter 
{
    final static List<Object> convertColumns( final String [] values, final Object [] columnTypes) throws ParseException {

        final List<Object> newValues = new ArrayList(values.length);

        final int numColumns = Math.min(columnTypes.length, values.length);

        for(int index = 0; index<numColumns; ++index) {
            final var type = columnTypes[index];
            try {
                Object convertedValue;

                if(type instanceof Class) {
                    if(type == Integer.class) {
                        convertedValue = Integer.parseInt(values[index]);
                    }
                    else {
                        convertedValue = StringGroovyMethods.asType(values[index], (Class)type);
                    }
                }
                else {
                    MatrixValueAdapter adapter = (MatrixValueAdapter)type;
                    convertedValue = adapter.deserialize(values[index]);
                }
                
                newValues.add(convertedValue);
            }
            catch(NumberFormatException e) {
                if((type == Integer.class) && StringGroovyMethods.isDouble(values[index])) {
                    columnTypes[index] = Double.class;
                    --index;
                }
                else { 
                    // Ignore
                    // This just leaves the unconverted (string) value in 
                    // place
                }
            }
        }
        return newValues;
    }
 

}
