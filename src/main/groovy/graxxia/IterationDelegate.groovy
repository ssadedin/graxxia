/*
 *  Graxxia - Groovy Maths Utililities
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

@CompileStatic
class IterationDelegate {
    
    int row = 0
    
	Matrix host
    
    IterationDelegate(Matrix host) {
        this.host = host
    }
    
    @CompileStatic
    Object getProperty(String name) {
        return resolve(name)
    }
    
    @CompileStatic
    Object resolve(String name) {
		def column = host.getProperty(name)
		if(column == null) {
			if(name in host.@names) {
				host.setProperty(name, host.col(host.@names.indexOf(name)))
//                this.metaClass.setProperty(name, column)
				column = host[name]
			}
		}
        
        if(column instanceof MatrixColumn)
    		return ((MatrixColumn)column).getDoubleAt(row)
        else
            return ((List)column).getAt(row)        
    }
    
	def propertyMissing(String name) {
        return resolve(name)
	}
}
