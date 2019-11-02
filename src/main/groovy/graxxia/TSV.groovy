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
import com.xlson.groovycsv.PropertyMapper;

import groovy.transform.CompileStatic;

import java.io.Reader;
import java.util.zip.GZIPInputStream

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
class TSV implements Iterable<PropertyMapper> {
	
	CsvIterator parser
    
    Closure reader
    
    Map options
    
    static List<MatrixValueAdapter> formats = [ new DateMatrixValueAdapter()]
    
    TSV(Map options=[:], String fileName) {
        this.reader =  { 
            getReader(fileName)
        }
        this.options = options
        checkFirstLine()
    }
    
    TSV(Map options=[:], Reader r) {
        this.reader =  { return r }
        this.options = options
        checkFirstLine()
    }
    
    void checkFirstLine() {
        if(this.options.containsKey('columnNames') && !this.options.containsKey('readFirstLine')) {
            this.options.readFirstLine = true
        }
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
    
    Iterator<PropertyMapper> iterator() {
        
        Iterator i = newIterator()
        
        // List or Map
        def customColumnTypes = options.columnTypes
        
        List columnTypes = null
        if(customColumnTypes instanceof List)
            columnTypes = customColumnTypes
        
        new Iterator() {
            @CompileStatic
            boolean hasNext() {
                i.hasNext()
            }
            
            @CompileStatic
            Object next() {
                PropertyMapper line = i.next()
                
                if(!columnTypes) {
                    columnTypes = inferColumnTypes(line)
                }
                
                line.values = TSV.this.convertColumns((String[])line.values, columnTypes)
                
                return line
            }

            private List inferColumnTypes(PropertyMapper line) {
                columnTypes = [String] * (int)((List)line.values).size()

                line.values.eachWithIndex  { Object v, int index ->
                    if(customColumnTypes instanceof Map && customColumnTypes.containsKey(index))
                        columnTypes[index] = customColumnTypes[index]
                    else {
                        if(v.isInteger())
                            columnTypes[index] = Integer
                        else
                        if(v.isDouble())
                            columnTypes[index] = Double
                        else
                        if(v in ["true","false"])
                            columnTypes[index] = Boolean
                        else {
                            for(f in formats) {
                                if(f.sniff(v)) {
                                    columnTypes[index] = f
                                }
                            }
                        }
                    }
                }
                return columnTypes
            }
            
            void remove() {
                throw new UnsupportedOperationException()
            }
        }
    }
    
    @CompileStatic
    List<Object> convertColumns(String [] values, List columnTypes) {
        List<Object> newValues = values as List
        final int numColumns = Math.min(columnTypes.size(), values.size())
        for(int index = 0; index<numColumns; ++index) {
            def type = columnTypes[index]
            try {
                if(type instanceof Class)
                    newValues[index] = values[index].asType((Class)type)
                else {
                    MatrixValueAdapter adapter = (MatrixValueAdapter)type
                    newValues[index] = adapter.deserialize(values[index])
                }
            }
            catch(NumberFormatException e) {
                if(type.is(Integer) && values[index].isDouble()) {
                    columnTypes[index] = Double
                    --index
                }
                else { 
                    // Ignore
                    // This just leaves the unconverted (string) value in 
                    // place
                }
            }
        }
        return newValues
    }
    
	static Iterator parse(Closure c = null) {
		Reader r = new InputStreamReader(System.in)
        parse(r,c)
	}
    
	static Iterator parse(Reader r, Closure c = null) {
		if(c != null) {
			for(line in CsvParser.parseCsv(r)) {
				c(line)
			}
		}
		else {
			return CsvParser.parseCsv(r)
		}
	}    
    
    void filter(Closure c) {
        filter(null, c)
    }
    
    /**
     * Invokes the given closure for each line in this file and then prints
     * only those lines for which it returns true to output.
     * If the 'quote' option is set, values are double quoted.
     * @return
     */
    void filter(Writer writer, Closure c) {
        if(writer==null)
            writer = new PrintWriter(System.out)
        def columns = null
        for(PropertyMapper line in this) {
            if(!columns) {
                columns = line.columns*.key
                def out = options.quote ? 
                        columns.collect { '"' + it + '"'}.join(options.separator) 
                    : 
                        columns.join(options.separator)
                writer.println(out)
            }
            if(c(line)==true) {
                if(options.quote)
                    writer.println columns.collect { (options.quoteall || (line[it] instanceof String)) ? ('"' + line[it] + '"') : line[it] }.join(options.separator)
                else
                    writer.println columns.collect { line[it] }.join(options.separator)
            }
        }
    }
    
    static int sniffColumnCount(String fileName, String separator = '\t') {
        getReader(fileName).withCloseable { r ->
            return r.readLine().split(separator).size()
        }
        
    }
    
    static getReader(String fileName) {
       fileName.endsWith(".gz") ? new GZIPInputStream(new FileInputStream(fileName)).newReader() : new File(fileName).newReader()  
    }
    
    /**
     * Consume all rows from this TSV and convert to ListMap object
     * 
     * @param normalizeColumns if true, columns will be lower cased and spaces replaced by underscores
     * @return
     */
    @CompileStatic
    List<Map> toListMap(boolean normalizeColumns=false) {
        List<String> columnNames = null
        this.collect { PropertyMapper row ->
            if(columnNames == null) {
                columnNames = ((Map<String,Object>)row.columns).collect { Map.Entry<String,Object> e -> e.key }
                if(normalizeColumns) {
                    columnNames = columnNames.collect { col -> col.toLowerCase().replaceAll(' {1,}','_') }
                }
            }
            
            
            [ columnNames, row.values ].transpose().collectEntries() 
        }
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
