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
package graxxia

import com.xlson.groovycsv.CsvParser;
import com.xlson.groovycsv.CsvIterator;
import groovy.transform.CompileStatic;
import java.io.Reader;

/**
 * A convenience wrapper around Groovy-CSV (which is
 * itself a convenience wrapper around OpenCSV).
 * <p>
 * The main functionality added is auto-conversion of column types.
 * When the first line is read, the data is "sniffed" and the type is 
 * inferred in the order that each column can be parsed as:
 * <li>Double
 * <li>Integer
 * <li>Boolean
 * <li>String (default)
 * <p>
 * The parsed lines are then converted to these data types where possible
 * (where not possible, they just leave the value as Strings).
 * 
 * <p>Example</p>
 * <pre>
 * // File is : foo\tbar\t10\t4.2
 * for(line in new TSV("test.tsv")) {
 *    println line[2] + line[3] // Works as Integer and Float
 * }
 * </pre>
 * Passing column names is simple:
 * <pre>
 * for(line in new TSV("test.tsv", columnNames:['name','something','age','height'])) {
 *   println line.height + line.age
 * }
 * </pre>
 * 
 * @author Simon
 */
class TSV implements Iterable {
	
	CsvIterator parser
    
    Closure reader
    
    Map options
	
	boolean raw = false
    
    TSV(Map options=[:], String fileName) {
        this.reader =  { new File(fileName).newReader() }
        this.options = options
        if(this.options.containsKey('columnNames') && !this.options.containsKey('readFirstLine'))
            this.options.readFirstLine = true
    }
    
    TSV(Map options=[:], Reader r) {
        this.reader =  { return r }
        this.options = options
    }
     
    CsvIterator newIterator() {
        if(!options.containsKey("separator"))
            options.separator = "\t"
        CsvParser.parseCsv(options, reader())
    }
	
    /*
	TSV(String fileName, List columnNames = null) {
		
        // If the first line starts with # then we treat it as column headers
		if(!new File(fileName).withReader { r ->
			String firstLine = r.readLine()
			if(firstLine.startsWith('#')) {
				def cols = firstLine.substring(1).trim().split("\t")
				parser = CsvParser.parseCsv(columnNames: cols, separator: '\t', readFirstLine: true, r)
				return true
			}
			return false
		}) {
			new File(fileName).withReader { r ->
                if(columnNames)
    				parser = CsvParser.parseCsv(r, columns: columnNames, separator:'\t')
                else
    				parser = CsvParser.parseCsv(r, separator:'\t')
			}
		}
	}
    */
	
//	TSV(Reader reader) {
//		String line = reader.readLine()
//		parser = CsvParser.parseCsv(reader)
//	}	
//	
    
	TSV(Reader reader, List<String> columnNames) {
		parser = CsvParser.parseCsv(reader, columnNames: columnNames, readFirstLine: true, separator: '\t')
	}		
    
    Iterator iterator() {
        
        Iterator i = newIterator()
        
        List columnTypes = options.columnTypes
        
        new Iterator() {
            boolean hasNext() {
                i.hasNext()
            }
            
            Object next() {
                def line = i.next()
                
                if(!columnTypes) {
                    columnTypes = [String] * line.values.size()
                    line.values.eachWithIndex  { v, index ->
                        if(v.isInteger())
                            columnTypes[index] = Integer
                        else
                        if(v.isDouble())
                            columnTypes[index] = Double
                        else
                        if(v in ["true","false"])
                            columnTypes[index] = Boolean
                    }
                }
                
                line.values = TSV.this.convertColumns(line.values, columnTypes)
                
                return raw ? line.values : line
            }
            
            void remove() {
                throw new UnsupportedOperationException()
            }
        }
    }
    
    @CompileStatic
    List<Object> convertColumns(String [] values, List<Class> columnTypes) {
        List<Object> newValues = values as List
        final int numColumns = columnTypes.size()
        for(int index = 0; index<numColumns; ++index) {
            Class type = columnTypes[index]
            if(values.size()<=index)
                return
            try {
                newValues[index] = values[index].asType(type)
            }
            catch(NumberFormatException e) {
                // Ignore
                // This just leaves the unconverted (string) value in 
                // place
            }
        }
        return newValues
    }
	
	static Iterable parse(Closure c = null) {
		Reader r = new InputStreamReader(System.in)
		if(c != null) {
			for(line in CsvParser.parseCsv(r)) {
				c(line)
			}
		}
		else {
			return CsvParser.parseCsv(r)
		}
	}
    
    /**
     * Read the given file and then write it out to standard output
     * after passing to the given closure, allowing modification
     * @return
     */
    static filter(Closure c) {
        
    }
}

class CSV extends TSV {
    
    CSV(Map options=[:], String fileName) {
        super(options + [separator:','],fileName)
    }
    
    CSV(Map options=[:], Reader r) {
        super(options + [separator:','],r)
    }
 }
