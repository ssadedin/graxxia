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
 
import java.lang.reflect.Method
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import com.xlson.groovycsv.PropertyMapper
import groovy.lang.Closure;
import groovy.transform.CompileStatic;

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.SingularValueDecomposition
import org.codehaus.groovy.runtime.typehandling.GroovyCastException;

/**
 * Wraps an Apache-Commons-Math matrix of double values with a 
 * Groovy interface for convenient access. Because it wraps the
 * underlying matrix as a delegate, all the original methods of
 * the Commons-Math implementation are available directly, along with
 * Groovy-enhanced versions.
 * <p>
 * The most basic enhancements come in the form of random access operators 
 * that allowthe Matrix class to be referenced using square-bracket notation:
 * <pre>
 * Matrix m = new Matrix(2,2,[1,2,3,4])
 * assert m[0][0] == 2
 * assert m[1][1] == 4
 * </pre>
 * The rows of the Matrix are directly accessible simply by using
 * square-bracket indexing:
 * <pre>
 * assert m[0] == [1,2]
 * assert m[1] == [3,4]
 * </pre>
 * The columns are accessed by using an empty first index:
 * <pre>
 * assert m[][0] == [1,3]
 * assert m[][1] == [2,4]
 * </pre>
 * Rows and columns can both be treated as normal Groovy collections:
 * <pre>
 * assert m[0].collect { it.any { it > 2 }  } == [ false, true ]
 * assert m[][0].collect { it > 1 } == [ false, true ]
 * </pre>
 * Note that in the above code, both row-wise and column-wise access
 * occurs without copying any data.
 * <p>
 * Transforming the whole matrix can be done using <code>transform</code>:
 * <pre>
 * assert m.transform { it * 2 } == Matrix(2,2,[2,4,6,8])
 * </pre>
 * As an option, row and column indexes are available as well:
 * <pre>
 * assert m.transform { value, row, column -> value * 2 } == Matrix(2,2,[2,4,6,8])
 * </pre>
 * 
 * @author simon.sadedin@mcri.edu.au
 */ 
class Matrix extends Expando implements Iterable, Serializable {
    
    static final long serialVersionUID = 0
     
    static { 
        
//        println "Setting Matrix meta class properties ...."
        double[][].metaClass.toMatrix = { new Matrix(delegate) }
        
        def originalMethod = double[][].metaClass.getMetaMethod("asType", Class)
        double[][].metaClass.asType = { arg -> arg == Matrix.class ? delegate.toMatrix() : originalMethod(arg)}
        
        def original2 = Array2DRowRealMatrix.metaClass.getMetaMethod("asType", Class)
        Array2DRowRealMatrix.metaClass.asType = { arg -> arg == Matrix.class ? new Matrix(arg) : original2(arg)}
        
        def originalMultiply = Integer.metaClass.getMetaMethod("multiply", Class)
        Integer.metaClass.multiply = { arg -> arg instanceof Matrix ? arg.multiply(delegate) : originalMultiply(arg)}
        
//        Matrix.metaClass.max = { c -> delegate.iterateWithDelegate(org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod("max", Iterator,  Closure), c) }
        
    }
    
    /**
     * How many rows are displayed in toString() and other calls that format output
     */
    static final int DISPLAY_ROWS = 50
    
    @Delegate
    Array2DRowRealMatrix matrix
    
    List<String> names = []
    
    public Matrix(int rows, int columns) {
        matrix = new Array2DRowRealMatrix(rows, columns)
    }
    
    public Matrix(MatrixColumn... sourceColumns) {
        this.initFromColumns(sourceColumns)
    }
    
    @CompileStatic
    private void initFromColumns(MatrixColumn[] sourceColumns) {
        MatrixColumn c0 = sourceColumns[0]
        int rows = c0.size()
        final int cols = sourceColumns.size()
        double[][] newData =  new double[rows][]
        MatrixColumn [] columns = sourceColumns
        for(int i=0; i<rows;++i) {
            double [] row = newData[i]
            for(int j=0; j<cols;++j)
                row[j] = (double)(columns[j].getDoubleAt(i))
        }
        matrix = new Array2DRowRealMatrix(newData,false)
        this.names = columns.collect { MatrixColumn c -> c.name }
    }
    
    /**
     * Create a Matrix from a map of columns.
     * <p>
     * The keys in the map are treated as column names, while the values
     * are iterated to obtain values.
     * 
     * @param sourceColumns
     */
    Matrix(Map<String,Iterable> sourceColumns) {
        initFromMap(sourceColumns)
    }
    
    static Matrix fromMap(Map<String,Iterable> sourceColumns) {
        Matrix m = new Matrix([[0] as double[]] as double[][]);
        m.initFromMap(sourceColumns)
        return m
    }
    
    void initFromMap(Map<String,Iterable> sourceColumns) {
        int rows = sourceColumns.iterator().next().value.size()
        double[][] newData =  new double[rows][]
        List<String> numerics = sourceColumns.grep { 
            def result = this.sniffIsNumeric(it.value) 
            return result
        }*.key 
        
        final int cols = numerics.size()
        List<Iterator> iters = sourceColumns.grep { it.key in numerics }.collect { it.value.iterator() }
        
        for(int i=0; i<rows;++i) {
            newData[i] = iters.collect { (double)it.next() } as double[]
        }
        
        List<List> nonNumericValues = []
        sourceColumns.grep { !(it.key in numerics) }.each { e ->
            List listValue = e.value as List
            this[e.key] = listValue
            nonNumericValues << listValue
        }
        
        if(numerics.size()>0) {
            matrix = new Array2DRowRealMatrix(newData,false)
            this.@names = numerics
        }
        else {
            if(nonNumericValues.size()>0) {
                matrix = new ZeroColumnMatrix(nonNumericValues[0].size())
            }
        }
    }
    
    @CompileStatic
    boolean sniffIsNumeric(int [] values) {
        true
    } 
    
    @CompileStatic
    boolean sniffIsNumeric(double [] values) {
        true
    }
    
    @CompileStatic
    boolean sniffIsNumeric(Iterable iterable) {
        int i=0
        Iterator iter = iterable.iterator()
        while(i<5 && iter.hasNext()) {
            if(!(iter.next() instanceof Number))
                return false
        }
        return true
    }
    
    public Matrix(CSV csv) {
        Iterator i = csv.iterator()
        def r0 = i.next()
        initFromIterator(i, r0, r0.columns*.key)        
    }
    
    public Matrix(TSV tsv) {
        Iterator i = tsv.iterator()
        def r0 = i.next()
        initFromIterator(i, r0, r0.columns*.key)
    }
    
    public Matrix(Iterable<Iterable> rows, List<String> columnNames=null) {
        Iterator rowIterator = rows.iterator()
        if(!rowIterator.hasNext())
            this.matrix = new Array2DRowRealMatrix([[]] as double[][], false)
        def r0 = rowIterator.next()
        initFromIterator(rowIterator,r0,columnNames)
    }
    
    @CompileStatic
    public initFromIterator(Iterator<Iterable> rows, def r0, List<String> columnNames=null) {
        
        // new File("/Users/simon/test.txt").text = (new Date()).toString() + " : graxxia test"
        
        List data = new ArrayList(4096)
        int rowCount = 0
        List<Boolean> isNumerics
        if(r0 instanceof float[]) {
            boolean [] nums = new boolean[((float[])r0).size()]
            Arrays.fill(nums,true)
            isNumerics = nums as List
        }
        else 
        if(r0 instanceof int[]) {
            boolean [] nums = new boolean[((int[])r0).size()]
            Arrays.fill(nums,true)
            isNumerics = nums as List
        }
        else 
        if(r0 instanceof double[]) {
            boolean [] nums = new boolean[((double[])r0).size()]
            Arrays.fill(nums,true)
            isNumerics = nums as List
        }
        else
        if(r0 instanceof Iterable) {
            isNumerics = r0.collect { it instanceof Number }
        }
        else
        if(r0 instanceof PropertyMapper) {
            isNumerics = r0.values.collect { it instanceof Number }
        }
        else {
            boolean [] nums = new boolean[getRowSize(r0)]
            Arrays.fill(nums,true)
            isNumerics = nums as List
        }
        
        int matrixColumnCount = (int)isNumerics.count { it }
        
        // Initialise an empty list for each non-numeric column that
        // we are going to fill
        List<List> nonNumerics = isNumerics.grep { !it }.collect { [] }
        
        final int columnCount = isNumerics.size()
        
        int rowIndex = 0
        def row = r0
        while(true) {
            
            int colIndex = 0
            int numericColumnIndex = 0
            int nonNumericColumnIndex = 0
            double[] rowNumericValues = new double[matrixColumnCount]
            def rowValues = row instanceof PropertyMapper ? row.getValues() : row
            if(rowValues instanceof MatrixColumn)
                rowValues = rowValues.iterator()
            for(value in rowValues) {  
                if(colIndex >= columnCount) // ragged array!?
                    break
                if(isNumerics[colIndex]) {
                    rowNumericValues[numericColumnIndex++] = (double)value
                }
                else
                    nonNumerics[nonNumericColumnIndex++].add(value)
                ++colIndex
            }
            ++rowIndex
            data.add(rowNumericValues)
            
            if(!rows.hasNext())
                break
                
            row = rows.next()
        }
        
        matrix = new Array2DRowRealMatrix((double[][])data.toArray(), false)
        if(columnNames)
            this.@names = [columnNames,isNumerics].transpose().grep { List i -> i[1] }.collect { Object i -> ((List)i)[0] }
            
        int nonNumericIndex = 0
        isNumerics.eachWithIndex { isNumeric, index ->
            if(!isNumeric)
                this.setProperty(columnNames[index],nonNumerics[nonNumericIndex++])        
        }
    }
    
    int getRowSize(def row) {
        row.size()
    }
    
    public Matrix(double [][] values) {
        matrix = new Array2DRowRealMatrix(values, false)
    }
     
    public Matrix(int rows, int columns, List<Double> data) {
        this.initFromList(rows,columns,data)
    }
    
    @CompileStatic
    void initFromList(int rows, int columns, List<Double> data) {
        matrix = new Array2DRowRealMatrix(rows, columns)
        int i=0
        for(int r=0; r<rows; ++r) {
            for(int c=0; c<columns;++c) {
                matrix.dataRef[r][c] = (double)data[i++]
            }
        }
    }
    
    public Matrix(int rows, int columns, double[] matrixData) {
        this.initFromArray(rows, columns, matrixData)
    }
    
    @CompileStatic
    private void initFromArray(int rows, int columns, double[] matrixData) {
        matrix = new Array2DRowRealMatrix(rows, columns)
        int i=0
        for(int r=0; r<rows; ++r) {
            for(int c=0; c<columns;++c) {
                matrix.dataRef[r][c] = matrixData[++i]
            }
        }
        
    }
      
    public Matrix(Array2DRowRealMatrix m) {
        matrix = m
    }

     
    @CompileStatic
    MatrixColumn col(int n) {
        new MatrixColumn(columnIndex:n, sourceMatrix: this, name: names[n])
    }
    
    List<MatrixColumn> getColumns(List<String> names) {
        new MatrixColumnList(columns:names.collect { this.names.indexOf(it) }.collect { int index ->
             assert index >= 0; col(index) 
        })
    }
    
    MatrixColumnList getColumns() {
        new MatrixColumnList(columns:(0..<matrix.columnDimension).collect { col(it) })
    }
    
    @CompileStatic
    double [] row(int n) {
        matrix.getRow(n)
    }
    
    /**
     * Implementation of the [] operator. Adds several different behaviors:
     * <ul>
     *    <li>Plain old indexing returns a row: <code>m[4]</code> returns 5th row of matrix.
     *    <li>Double indexing returns a cell: <code>m[4][5]</code> returns 6th column of 4th row.
     *    <li>Empty index returns a column: <code>m[][6]</code> returns 7th column
     *    <li>List (or any iterable) index returns rows matching indices:
     *    </ul>
     * <pre>
     * Matrix m = new Matrix([1..80], 10, 8)
     * m[2..4] == [ [ 9..16 ], [17..24], [25..32] ]
     * @param n
     * @return
     */
    @CompileStatic
    Object getAt(Object n) {
        if(n == null) {
            return getColumns()
        }
        else
        if(n instanceof Number)
            return matrix.dataRef[(int)n]
        else
        if(n instanceof List) {
            List<Number> l = (List)n
            if(l.size() == 0) // Seems to happen with m[][2] type syntax
                return getColumns()
            else {
                double [][] submatrix = subsetRows(l)
                Matrix result = new Matrix(new Array2DRowRealMatrix(submatrix))
                result.@names = this.@names
                if(!this.properties.isEmpty()) 
                    this.transferPropertiesToRows(result, l)
                return result
            }
        }
        else
        if(n instanceof Iterable) {
            return subsetRows((n))
        }
        else
        if(n.class.isArray()) {
            return subsetRows(n as Collection<Number>)
        }
        else {
            throw new IllegalArgumentException("Cannot subset rows by type: " + n?.class?.name)
        }
    }
    
    @CompileStatic
    Iterator iterator() {
       new Iterator() {
           
           int i=0
           final int numRows = matrix.rowDimension
           
           boolean hasNext() {
               return i<numRows;
           }
           
           Object next() {
               matrix.dataRef[i++]
           }
           
           void remove() { 
               throw new UnsupportedOperationException() 
           }
       } 
    }
    
    /**
     * Return a subset of the rows indicated by the indices in the given iterable
     * (Note that the indices don't need to be consecutive or monotonic).
     * @param i
     * @return
     */
    @CompileStatic
    double[][] subsetRows(Iterable<Number> i) {
        List<Integer> indices = convertIndices(i)
//        i.each { Number n -> indices.add(n.toIn0eger()) }
        
        double [][] result = new double[indices.size()][this.matrix.columnDimension]
        if(this.matrix.columnDimension>0) {
            int destRowIndex = 0
            for(int srcRowIndex in indices) {
                System.arraycopy(this.matrix.dataRef[srcRowIndex], 0, result[destRowIndex++], 0, this.matrix.columnDimension)
            }
        }
        return result
    }

    @CompileStatic
    private List<Integer> convertIndices(Iterable i) {
        List<Integer> indices = new ArrayList(this.matrix.rowDimension)
        for(Number n : i) {
            indices.add(n.toInteger())
        }
        return indices
    }
    
    @CompileStatic
    void putAt(int n, Object values) {
       matrix.dataRef[n] = (values as double[])
    }
    
    /**
     * Iterates through a matrix row-wise, passing each row as an array of doubles
     * to the supplied closure.
     * <p>
     * If the passed closure acceptes 1 argument then just passes the row.
     * If 2 arguments, passes the row AND the index. 
     * <p>
     * In addition, this method enables accessing of columns and expando properties
     * by name for the current row.
     * <p>
     * Example:
     * <pre>
     *  def m = new Matrix(x1: [1,2,3,4,5], x2: [2,4,6,8,10], x3:[7,6,5,4,3])
     *  assert m.collect { x1 * x2 } == [2.0d,  8.0d, 18.0d, 32.0d, 50.0d]
     *  </pre>
     * 
     * <em>Note:</em> If you want to get a matrix back, see the #transformRows() method.
     * 
     * @param c    Closure to execute for each row in the matrix
     * @return    results collected
     */
    @CompileStatic
    List collect(Closure c) {
        List<Object> results = new ArrayList(matrix.dataRef.size())
        IterationDelegate delegate = new IterationDelegate(this)
        boolean withDelegate = !this.properties.isEmpty() || this.@names
        if(withDelegate) {
            c = (Closure)c.clone()
            c.setDelegate(delegate)
        }
        int rowIndex = 0;
        if(c.maximumNumberOfParameters == 1) {
            for(double [] row in matrix.dataRef) {
                if(withDelegate)
                    delegate.row = rowIndex++
                results.add(c(row))
            }
        }
        else 
        if(c.maximumNumberOfParameters == 2) {
            for(double [] row in matrix.dataRef) {
                if(withDelegate)
                    delegate.row = rowIndex
                results.add(c(row, rowIndex))
                ++rowIndex
            }
        }
        return results
    }    
   
    @CompileStatic
    public List<Number> findIndexValues(Closure<Boolean> c) {
        List<Integer> keepRows = []
        int rowIndex = 0;
        IterationDelegate delegate = new IterationDelegate(this)
        boolean withDelegate = !this.properties.isEmpty() || this.@names
        if(withDelegate) {
            c = (Closure)c.clone()
            c.setDelegate(delegate)
            c.setResolveStrategy(Closure.DELEGATE_FIRST)
        }
        if(c.maximumNumberOfParameters == 1) {
            for(double [] row in matrix.dataRef) {
                if(withDelegate)
                    delegate.row = rowIndex
                if(c(row) != false)
                    keepRows.add(rowIndex)
                ++rowIndex
            }
        }
        else 
        if(c.maximumNumberOfParameters == 2) {
            for(double [] row in matrix.dataRef) {
                if(withDelegate)
                    delegate.row = rowIndex 
                if(c(row, rowIndex) != false)
                    keepRows.add(rowIndex)
                ++rowIndex
            }
        }
        return (List<Number>) keepRows
    }
        
    /**
     * Filter the rows of this matrix and return 
     * a Matrix as a result
     * 
     * @param   c   a Closure to evaluate
     * 
     * @return  Matrix for which the closure c returns a non-false value
     */
    @CompileStatic
    Matrix grep(Closure c) {
        
        List<Number> keepRows = this.findIndexValues(c)

        double [][] submatrix = this.subsetRows((Iterable<Number>)keepRows)
        
        def result = new Matrix(newRealMatrix(submatrix))
        result.@names = this.@names
        if(!this.properties.isEmpty()) 
            this.transferPropertiesToRows(result, keepRows)
        return result
    }    
    
    Array2DRowRealMatrix  newRealMatrix(double [][] dMatrix) {
        if(dMatrix.size() == 0) {
            return new ZeroColumnMatrix(0)
        }
        else 
        if(dMatrix[0].size() == 0) {
            return new ZeroColumnMatrix(dMatrix.size())
        }
        else
            return new Array2DRowRealMatrix(dMatrix)        
    }
    
    private void transferPropertiesToRows(Matrix result, List<Number> indices = null) {
        if(indices != null) {
            this.properties.each {  String key, Iterable value ->
                result[key] = value[indices]
            }
        }
        else {
            this.properties.each {  String key, Iterable value ->
                result[key] = value as List
            }
        }
    }
    
    /**
     * Transforms a matrix by processing each element through the given
     * closure. The closure must take either one argument or three arguments.
     * The one argument version is only passed data values, while the 
     * three argument version is passed the data value and also the row and column
     * position.
     * 
     * @param c A closure taking 1 or 3 arguments (data value, or data value, row,
     *          column)
     * @return  A matrix reflecting the transformed data values
     */
    @CompileStatic
    Matrix transform(Closure c) {
        
        Matrix result
        if(c.maximumNumberOfParameters == 1) {
            result = transformWithoutIndices(c)
        }
        else 
        if(c.maximumNumberOfParameters == 3) {
            result = transformWithIndices(c)
        }
        if(names)
            result.names = this.names
            
        if(!this.properties.isEmpty()) 
            this.transferPropertiesToRows(result)
            
        return result
    }
    
    @CompileStatic
    private Matrix transformWithoutIndices(Closure c) {
        final int rows = matrix.rowDimension
        final int cols = matrix.columnDimension
        double[][] newData = new double[rows][cols]
        IterationDelegate delegate = new IterationDelegate(this)
        boolean withDelegate = !this.properties.isEmpty() || this.@names
        if(withDelegate) {
            c = (Closure)c.clone()
            c.delegate = delegate
        }
        for(int i=0; i<rows;++i) {
            double [] row = matrix.dataRef[i]
            if(withDelegate)
                delegate.row = i
            for(int j=0; j<cols;++j) {
                newData[i][j] = (double)c(row[j])
            }
        }                    
        return new Matrix(new Array2DRowRealMatrix(newData, false))
    }
    
    @CompileStatic
    private Matrix transformWithIndices(Closure c) {
        final int rows = matrix.rowDimension
        final int cols = matrix.columnDimension
        double[][] newData = new double[rows][cols]
        IterationDelegate delegate = new IterationDelegate(this)
        boolean withDelegate = !this.properties.isEmpty() || this.@names
        if(withDelegate) {
            c = (Closure)c.clone()
            c.delegate = delegate
        }
        for(int i=0; i<rows;++i) {
            double [] row = matrix.dataRef[i]
            double [] newRow = newData[i]
            if(withDelegate)
                delegate.row = i
            for(int j=0; j<cols;++j) {
                double value = row[j] // NOTE: embedding this direclty in call below causes VerifyError with CompileStatic
                newRow[j] = (double)c.call(value,i,j)
            }
        }                    
        return new Matrix(new Array2DRowRealMatrix(newData, false))
    }
    
    /**
     * Transform the given matrix by passing each row to the given
     * closure. If the closure accepts two arguments then the 
     * row index is passed as well. The closure must return a 
     * double array to replace the array passed in. If null
     * is returned then the data is left unchanged.
     * 
     * @param c Closure to process the data with.
     * @return  transformed Matrix
     */
    @CompileStatic
    Matrix transformRows(Closure c) {
        final int rows = matrix.rowDimension
        final int cols = matrix.columnDimension
        
        double[][] newData = new double[rows][cols]
        
        IterationDelegate delegate = new IterationDelegate(this)
        boolean withDelegate = !this.properties.isEmpty() || this.@names
        if(withDelegate) {
            c = (Closure)c.clone()
            c.delegate = delegate
        }
        if(c.maximumNumberOfParameters == 1) {
            for(int i=0; i<rows;++i) {
                if(withDelegate)
                    delegate.row = i
                newData[i] = (double[])c(matrix.dataRef[i])
            }
        }
        else 
        if(c.maximumNumberOfParameters == 2) {
            for(int i=0; i<rows;++i) {
                if(withDelegate)
                    delegate.row = i
                newData[i] = (double[])c(matrix.dataRef[i], i)
            }
        }
        else
            throw new IllegalArgumentException("Closure must accept 1 or two arguments")
        
        Matrix result = new Matrix(new Array2DRowRealMatrix(newData,false))
        if(names)
            result.names = names
        return result
    }
    
    @CompileStatic
    void eachRow(Closure c) {
        IterationDelegate delegate = new IterationDelegate(this)
        boolean withDelegate = !this.properties.isEmpty() || this.@names
        if(withDelegate) {
            c = (Closure)c.clone()
            c.delegate = delegate
        }
        if(c.maximumNumberOfParameters == 1) {
            int rowIndex = 0;
            for(double [] row in matrix.dataRef) {
                if(withDelegate)
                    delegate.row = rowIndex++
                c(row)    
            }
        }
        else 
        if(c.maximumNumberOfParameters == 2) {
            int rowIndex = 0;
            for(double [] row in matrix.dataRef) {
                if(withDelegate)
                    delegate.row = rowIndex
                c(row, rowIndex)
                ++rowIndex
            }
        }
    }
    
    /*
    Map<Object,Matrix> groupBy(Closure c) {
        IterationDelegate delegate = new IterationDelegate(this)
        boolean withDelegate = !this.properties.isEmpty()
        if(withDelegate) {
            c = (Closure)c.clone()
            c.setDelegate(delegate)
        }
        int rowIndex = 0;
        
        List myNames = this.@names
        
        matrix.dataRef.groupBy {
            if(withDelegate)
                delegate.row = rowIndex++
            c()
        }.collectEntries { e ->
            def value = e.value
            def m = new Matrix(e.value)
            m.@names = myNames;
            [ e.key, m ]
        }
    }
    */
    
    Map<Object,Integer> countBy(Closure c) {
        IterationDelegate delegate = new IterationDelegate(this)
        boolean withDelegate = !this.properties.isEmpty() || this.@names
        if(withDelegate) {
            c = (Closure)c.clone()
            c.setDelegate(delegate)
        }
        int rowIndex = 0;
        
        List myNames = this.@names
        
        matrix.dataRef.countBy {
            if(withDelegate)
                delegate.row = rowIndex++
            c(it)
        }
    }
  
    
    /**
     * Shorthand to give a familiar function to R users
     */
    @CompileStatic
    List<Number> which(Closure c) {
        this.findIndexValues(c)
    }
    
    @CompileStatic
    Matrix multiply(double d) {
        new Matrix((Array2DRowRealMatrix)this.matrix.scalarMultiply(d))
    }
    
    @CompileStatic
    Matrix multiply(Matrix m) {
        new Matrix((Array2DRowRealMatrix)this.matrix.preMultiply(m.matrix))
    }
    
    @CompileStatic
    Matrix plus(Matrix m) {
        new Matrix(this.matrix.add(m.matrix))
    }
      
    @CompileStatic
    Matrix minus(Matrix m) {
        new Matrix(this.matrix.subtract(m.matrix))
    }
    
    @CompileStatic
    Matrix divide(double d) {
        new Matrix((Array2DRowRealMatrix)this.matrix.scalarMultiply(1/d))
    }
    
    @CompileStatic
    Matrix plus(double x) {
        new Matrix((Array2DRowRealMatrix)((RealMatrix)this.matrix).scalarAdd(x))
    }
    
    @CompileStatic
    Matrix minus(double x) {
        new Matrix((Array2DRowRealMatrix)((RealMatrix)this.matrix).scalarAdd(-x))
    }
    
    @CompileStatic
    Matrix transpose() {
        new Matrix((Array2DRowRealMatrix)this.matrix.transpose())
    }
    
    Matrix div(Matrix m) {
        this.transform { value, i, j ->
            value / m[i][j]
        }
    }
    
    /**
     * Save the matrix to a file in tab separated format. 
     * <p>
     * If the matrix has column names set (either as expando properties or using the @names attribute,
     * then the column names will be printed as the first row.
     * <p>
     * Options may be passed as names parameters, or a map of parameters as the first argument. 
     * Valid options include:
     * <li>r    -   set to true to make the output format compatible with the default 
     *              format read by R
     * 
     * @param fileName
     */
    void save(Map options = [:], String fileName) {
        new File(fileName).withWriter { w ->
            save(options,w)
        }
    }
    
    void saveBinary(String fileName) {
        ObjectOutputStream oStream = new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(fileName), 1024*1024)))
        oStream.withStream { ObjectOutputStream o -> 
            o << this.matrix.dataRef
        }
    }
    
    static Matrix readBinary(String fileName) {
        ObjectInputStream iStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(fileName), 1024*1024)))
        double [][] data = null
        iStream.withStream { ObjectInputStream o ->
            data = iStream.readObject()
        }
        return new Matrix(data)
    } 
   
    @CompileStatic
    void save(Map options = [:], Writer w) {
        
        List nonMatrixCols = (List)this.properties*.key 
        
        List matrixCols = this.@names
        if(!matrixCols && this.properties.containsKey('names'))
            matrixCols = (List)this.properties.names
            
        
        List columnNames = (List)this.properties*.key 
        columnNames.addAll(matrixCols)
        
        // NOTE: the this.properties.names seems to be required because of a 
        // weird bug where groovy will prefer to set an expando property rather than
        // set the real property on this object
        if(columnNames) {
            if(!options.r)
            w.print "# "
            w.println columnNames.join("\t")   
        }
        
        if(this.rowDimension == 0)
            return
        
        List<MatrixValueAdapter> adapters = TSV.formats + (List<MatrixValueAdapter>) [
           new NumberMatrixValueAdapter(),
           new StringMatrixValueAdapter()
        ] 
        
        Matrix me = this
       
        List<MatrixValueAdapter> types = nonMatrixCols.collect { colName ->
            adapters.find { MatrixValueAdapter adapter -> 
                println "Find adapter for $colName"
                Iterable col = (Iterable)me.getProperty((String)colName)
                adapter.sniff(col.getAt(0)) 
            }
        }
        
        eachRow { row ->
            IterationDelegate d
            if(!delegate.is(null) && (delegate instanceof IterationDelegate))
                d = (IterationDelegate)delegate
                
             if(nonMatrixCols) {
                nonMatrixCols.eachWithIndex { colNameValue, colIndexValue -> 
                    String colName = (String)colNameValue
                    int colIndex = colIndexValue
                    MatrixValueAdapter adapter = types[colIndex]
                    Object value = d.propertyMissing(colName) 
                    w.print(adapter.serialize(value) + "\t") 
                }
            }
            w.println((row as List).join("\t"))
        }
    }
    
    static Matrix load(Map options = [:], String fileName) {
        List rows = new ArrayList(1024)
        
        Reader r = new FileReader(fileName)
        
        // Sniff the first line
        String firstLine = r.readLine()
        List names
        if(firstLine.startsWith('#')) {
            names = firstLine.substring(1).trim().split("\t")
        }
        else
        if(options.r) {
            names = firstLine.substring(1).trim().split("\t")
        }
        else {
            r.close()
            r = new FileReader(fileName)
        }
        def values = new TSV(readFirstLine:true, r)*.values
        Matrix m = new Matrix(values, names)
        return m
    }
    
    int displayPrecision = 6
    
    int displayColumns = 50
       
    String toString() {
        
        def headerCells = this.@names
        if(this.properties) {
            headerCells = this.properties.collect { it.key } + headerCells
        }
        
        int halfMaxCols = Math.floor(displayColumns/2)
        if(headerCells.size() > displayColumns) {
           headerCells = headerCells[0..halfMaxCols] + ['...'] + headerCells[(headerCells.size()-halfMaxCols)..<headerCells.size()]
        }
            
        
        int columnWidth = Math.max(10, headerCells ? headerCells*.size()?.max()?:0 : 0)
        int rowNumWidth = 6
        
        String headers = headerCells ? (" " * rowNumWidth) + headerCells*.padRight(columnWidth).join(" ") + "\n" : ""
        
        DecimalFormat format = new DecimalFormat()
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = displayPrecision
        
       
        int rowCount = 0
        def printRow = { row ->
            
           List cells
           if(row == null)
                cells = []
           else 
               cells = (row as List)
           if(this.properties) {
               cells = this.properties.collect { it.value?it.value[rowCount]:"null" } + cells
           }
           
           if(cells.size()>displayColumns) {
               cells = cells[0..halfMaxCols] + ['...'] + cells[(cells.size()-halfMaxCols)..<headerCells.size()]
           }
           
           List values = cells.collect { value ->
               if(!(value instanceof Double))
                   return String.valueOf(value).padRight(columnWidth)
                       
               ((value < 0.0001d && value !=0 && value > -0.0001d) ? String.format("%1.6e",value) : format.format(value)).padRight(columnWidth)
           }
           
           return ((rowCount++) + ":").padRight(rowNumWidth) + values.join(" ")  
        }
        
        if(matrix.rowDimension<DISPLAY_ROWS) {
            return "${matrix.rowDimension}x${matrix.columnDimension + this.properties.size()} Matrix:\n"+ 
                headers + 
                matrix.data.collect { row -> 
                    printRow(row)
            }.join("\n")
        }
        else {
            int omitted = matrix.rowDimension-DISPLAY_ROWS
            String value = "${matrix.rowDimension}x${matrix.columnDimension + this.properties.size()} Matrix:\n"+ 
                headers + 
                matrix.data[0..DISPLAY_ROWS/2].collect(printRow).join("\n")  
            rowCount += omitted -1    
            value +=
                "\n... ${omitted} rows omitted ...\n" + 
                matrix.data[-(DISPLAY_ROWS/2)..-1].collect(printRow).join("\n")
                
            return value
        }
    }
    
    Writer toMarkdown(Writer w = null) {
        
        if(w == null)
            w = new StringWriter()
        
        def propCells = this.properties.collect { it.key } 
        def headerCells = this.@names
        if(this.properties) {
            headerCells = propCells + headerCells
        }
        
        int columnWidth = Math.max(10, headerCells ? headerCells*.size().max() : 0)
        int rowNumWidth = 6
        
        List<Integer> columnWidths  
        if(this.rowDimension<100) {
            columnWidths = properties.collect { e -> e.value[0] instanceof Number ? columnWidth : e.value.collect { String.valueOf(it).size()}.max() } +
                           [columnWidth] * this.columnDimension
        }
        else {
            columnWidths = columnWidth * this.columnDimension
        }
        
        String headers = headerCells ? (" " * rowNumWidth) + headerCells*.padRight(columnWidth).join(" ") + "\n" : ""
        
        DecimalFormat format = new DecimalFormat()
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = 6
        
        int columnIndex = 0
        String header = "| " + headerCells.collect { it.padRight(columnWidths[columnIndex++]) }.join(" | ") + "|"
        w.println(header)
        columnIndex = 0
        w.println "|-" + headerCells.collect { "-" * Math.max(columnWidths[columnIndex++], it.size()) }.join("-|-") + "|"
        
        List<Iterator> props = properties.collect { it.value.iterator() }
        
        int index = 0
        this.eachRow { row ->
            
            columnIndex = 0
            w.print "| " +  props.collect { (it.hasNext() ? it.next() : " ").padRight(columnWidths[columnIndex++]) }.join(" | ") + " | "
           
            w.println row.collect { format.format(it).padRight(columnWidth) }.join(" | ") + "|"
            ++index
        }
        return w
    }
    
    @CompileStatic
    private void writeObject(ObjectOutputStream oos) throws IOException {
        // default serialization
        oos.defaultWriteObject();
        oos.writeObject(this.properties)
    }
    
    @CompileStatic
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        // default deserialization
        ois.defaultReadObject();
        
        Map props = (Map)ois.readObject()
        props.each { Map.Entry<String,Object> e ->
            setProperty((String)e.key, e.value)
        }
    }
    
    void setColumnNames(List<String> names) {
        this.names = names
    }
    
    void setNames(List<String> names) {
        this.names = names
    }
    
    Object getProperty(String name) {
        if(name in this.@names) {
            return this.col(this.@names.indexOf(name))
        }
        
       def result = super.getProperty(name)
       if(result == null) 
           throw new IllegalArgumentException("No column named $name in this Matrix")
           
       return result
    }
    
    static Matrix fromListMap(List<Map> valueList) {
        
        // Get the column names and types from the first row
        Map row0 = valueList[0]
        List<Integer> numerics = row0.findIndexValues { it.value instanceof Number }
        List<String> nonNumerics = row0.grep { !(it.value instanceof Number) }*.key
        
        final int numNumerics = numerics.size()
        
        double [][] data = new double[valueList.size()][]
        valueList.eachWithIndex { Map values, int index ->
            data[index] = values.collect {it}[numerics].collect { it.value.toDouble() }
        }
        
        Matrix result = new Matrix(data)
        nonNumerics.each { key ->
            result[key] = valueList.collect { it[key]}
        }
        result.@names = row0*.key[numerics]
        return result
    }
    
    static Method maxMethod = org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod("max", Collection,  Closure)
    Matrix max(Closure c) {
        applyViaIndices(maxMethod,c)
    }
    
    static Method findMethod = org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod("find", Object,  Closure)
    Matrix find(Closure c) {
        iterateWithDelegate(findMethod, c) 
    }    
    
    static Method findAllMethod = org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod("findAll", Object,  Closure)
    Matrix findAll(Closure c) {
        iterateWithDelegate(findAllMethod, c) 
    }    
    
    static Method uniqueMethod = org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod("unique", Collection,  Closure)
    Matrix unique(Closure c) {
        applyViaIndices(uniqueMethod, c)
    }
    
    static Method sortMethod = org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod("sort", Iterable,  Closure)
    Matrix sort(Closure c) {
        applyViaIndices(sortMethod, c)
    }
    
    static Method groupByMethod = org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod("groupBy", Iterable,  Closure)
    Matrix groupByTest(Closure c) {
        applyViaIndices(groupByMethod, c)
    }    
    
    Map<Object,Matrix> groupBy(Closure c) {
        List indices = (0..<this.rowDimension).collect { it }
        IterationDelegate delegate = new IterationDelegate(this)
        Closure cloned = (Closure)c.clone()
        cloned.delegate = delegate
        def result = indices.groupBy { i ->
            delegate.row = i
            return cloned(this.dataRef[i])
        }
        
        result.collectEntries { e ->
            double [][] submatrix = subsetRows(e.value)
            Matrix m = new Matrix(new Array2DRowRealMatrix(submatrix))
            m.@names = this.@names
            if(!this.properties.isEmpty()) 
                this.transferPropertiesToRows(m,e.value)        
            [
                e.key,
                m
            ]
        }
    }
    
    Matrix applyViaIndices(Method method, Closure c) {
        List indices = (0..<this.rowDimension).collect { it }
        IterationDelegate delegate = new IterationDelegate(this)
        Closure cloned = (Closure)c.clone()
        cloned.delegate = delegate
        def result = method.invoke(null, indices,{ i ->
            delegate.row = i
            return cloned(this.dataRef[i])
        })
        
        double [][] submatrix = result instanceof Integer ? subsetRows([result]) : subsetRows(result)
        Matrix m = new Matrix(new Array2DRowRealMatrix(submatrix))
        m.@names = this.@names
        if(!this.properties.isEmpty()) 
            this.transferPropertiesToRows(m)        
        
        return m
    }
    
    @CompileStatic
    Matrix iterateWithDelegate(String methodName, Closure c) {
        iterateWithDelegate(org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod(methodName, Iterator,  Closure), c) 
    }
    
    @CompileStatic
    Matrix iterateWithDelegate(Method m, Closure c) {
        IterationDelegate dg = new IterationDelegate(this)
        c = (Closure)c.clone()
        c.setDelegate(dg)
        int rowIndex = 0;
        Iterator i = this.iterator()
        def result = m.invoke(null, i, { values ->
            dg.row = rowIndex++
            c(values)
        })
        
        Matrix matrixResult
        if(result instanceof Matrix) {
            matrixResult = (Matrix)result
        }
        else 
        if(result instanceof double[][]) {
            matrixResult = new Matrix(result)
        }        
        else
        if(result instanceof double[]) {
            matrixResult = new Matrix((Iterable<Iterable>)[result])
        }
        else
        if(result instanceof Iterable) {
            matrixResult = new Matrix(result)
        }
        else 
            throw new Exception("Unexpected argument type from max: " + result.getClass().name)
        
        if(matrixResult.columnDimension == this.@names?.size())
            matrixResult.@names = this.@names
            
        return matrixResult
    }
    
    int size() {
        return matrix.rowDimension
    }
    
    Matrix aggregateBy(Object...args) {
        Matrix me = this
        List<String> names = args[0..-2]
        aggregateBy(names.collectEntries { name -> [ name , { return delegate.getProperty(name) }] }, (Closure)args[-1] )
    }
    
    Matrix aggregateBy(Map groups, Closure c) {
        
        Map<Object,Matrix> allGroups = [all:this]
        groups.each { group ->
            Map<Object,Matrix> reGrouped = [:]
            allGroups.each { prevKey, m ->
                Map<Object,Matrix> grouped = m.groupBy(group.value)
                grouped.each { key, mGrouped ->
                    if(prevKey == "all")
                        reGrouped[ [key] ] = mGrouped
                    else {
                        reGrouped[ prevKey+key ] = mGrouped
                    }
                }
            }
            allGroups = reGrouped
        }
        
        Map<Object,Matrix> aggregated = allGroups.collectEntries { g ->
            [g.key, g.value.aggregate(c)]
        }
  
           
        List<String> columnNames = aggregated.iterator().next().value.getAllColumnNames()
        Map<String,List> columns = [:] 
        
        int i=0
        groups.each { groupName, groupClosure ->
            columns[groupName] = aggregated*.key.collect { it[i] }
            ++i
        }
        
        for(columnName in columnNames) {
            columns[columnName] = aggregated.collect { e -> e.value[columnName][0] }
        }
        
        return new Matrix(columns)
    }
  
    
    /*
    Matrix aggregateBy(Map groups, Closure c) {
        
        def group0 = groups.iterator().next()
        Map<Object,Matrix> grouped = groupBy(group0.value)
        Map<Object,Matrix> aggregated = grouped.collectEntries { g ->
            [g.key, g.value.aggregate(c)]
        }
       
        List<String> columnNames = aggregated.iterator().next().value.getAllColumnNames()
        Map<List> columns = [:] 
        columns[group0.key] = aggregated*.key
        
        for(columnName in columnNames) {
            columns[columnName] = aggregated.collect { e -> e.value[columnName][0] }
        }
        
        return new Matrix(columns)
    }
    */
    
    Matrix aggregate(Closure c) {
        Closure cClone = c.clone()
        MatrixAggregator aggregator = new MatrixAggregator(matrix:this)
        cClone.delegate = aggregator
        cClone();
        
        return new Matrix(aggregator.columns.collectEntries { [it.key, [it.value]]})
    }
    
    List<String> getAllColumnNames() {
        getProperties()*.key + (this.@names?:[])
    }
    
    Map rowAsMap(int i) {
        getProperties().collectEntries {  e ->
            [e.key, e.value[i]]
        } + this.columns.collectEntries { MatrixColumn c -> [c.name, c[i] ] }
    }
    
    @CompileStatic
    Iterator<Map> listMapIterator() {
        new Iterator() {
            
            int i = -1;
            
            @CompileStatic
            boolean hasNext() {
                i < matrix.rowDimension-1
            }
            
            @CompileStatic
            Map next() {
                rowAsMap(++i)
            }
            
            @CompileStatic
            void remove() {
                throw new UnsupportedOperationException() 
            }
        }
    }
    
    /**
     * Return a virtual List of Maps that behaves as a real, read-only
     * list of rows inside this Matrix.
     * <p>
     * The rows are created and returned lazily as they are accessed. Therefore 
     * there is no issue with generating this for a large matrix. However operating on 
     * such a list-map has an overhead with each row accessed that makes it quite
     * inefficient compared to accessing rows on the raw Matrix, especially if you're
     * going to access the data multiple times.
     * <p>
     * Note: for successful use of this method, give your matrix columns names
     *       via the @names property.
     * 
     * @return  an object implmementing a List interface that reflects the contents of
     *          this Matrix as rows and named columns.
     */
    @CompileStatic
    List<Map> asListMap() {
        return [
            get : { int i -> rowAsMap(i) },
            size : { matrix.rowDimension },
            isEmpty : { matrix.rowDimension == 0 },
            iterator : { listMapIterator() }
        ] as List
    }
    
    @CompileStatic
    List<Map> toListMap() {
        asListMap()
    }
    
    SingularValueDecomposition svd() {
       new SingularValueDecomposition(this.matrix)
    }
}
