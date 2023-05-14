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
import java.nio.file.Files
import java.nio.file.Path
import java.text.DecimalFormat;
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector
import org.apache.commons.math3.linear.SingularValueDecomposition
import org.apache.commons.math3.stat.correlation.PearsonsCorrelation

import com.twosigma.beakerx.jvm.object.OutputCell
import com.twosigma.beakerx.table.TableDisplay
import com.twosigma.beakerx.table.highlight.TableDisplayCellHighlighter
import com.xlson.groovycsv.PropertyMapper

import groovy.transform.CompileStatic;
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import jupyter.Displayer
import jupyter.Displayers
import smile.classification.RandomForest
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.data.type.DataTypes
import smile.data.vector.BaseVector
import smile.data.vector.BooleanVector
import smile.data.vector.DoubleVector
import smile.data.vector.IntVector
import smile.data.vector.StringVector

//import smile.data.Attribute
//import smile.data.NumericAttribute

/**
 * Wraps an Apache-Commons-Math matrix of double values and enhances
 * it with support for non-matrix columns to create an experience 
 * similar to R or Pandas DataFrames.
 * <p>
 * This class takes a specific approach to emulating the DataFrame experience, in that
 * it models the matrix as two distinct sets of columns, these being the "matrix columns"
 * and the "non-matrix" columns. Matrix columns can only hold doubles and are stored as a dense
 * double array internally (wrapped by Apache-commons-math RealMatrix). The non-matrix columns
 * are stored as expando properties on the Matrix object itself and can be of any (even heterogenous) 
 * data types. Where sensible, matrix operations
 * such as subsetting rows or columns preserve the corresponding matrix column values. Conceptually
 * you can think of the design as breaking data into numeric data to be manipulated and metadata
 * about that numeric data. 
 * <p>
 * Matrix wraps the underlying matrix as a delegate, so all the original methods of
 * the Commons-Math implementation are available directly, along with
 * Groovy-enhanced methods and properties.
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
 * You can access contiguous subsets of columns by using the groovy range operator:
 * <pre>
 * m[][0..1] // submatrix with all the rows but onlyu column 0 and 1
 * </pre>
 * Non-contiguous subsets of columns can be extracted using comma separated values in the brackets:
 * <pre>
 * m[][0,3] // submatrix with all the rows but onlyu column 0 and 3
 * </pre>
 * 
 * If the columns have names, they can be accessed that way too (with order
 * respected if you provide a list - which is a way to re-order columns):
 * <pre>
 * Matrix m = new Matrix(foo: [1,2,3,4], bar: [5,6,7,8])
 * assert m[]['foo'][0] == [1]
 * assert m[]['foo','bar'][0] == [1,5]
   assert m5[]['bar','foo'][0] == [5,1]
 * </pre>
 * 
 * Rows and columns can both be treated as normal Groovy collections:
 * <pre>
 * assert m[0].collect { it.any { it > 2 }  } == [ false, true ]
 * assert m[][0].collect { it > 1 } == [ false, true ]
 * </pre>
 * Note that in the above code, both row-wise and column-wise access
 * occurs without copying any data.
 * <p>
 * Column names for the Matrix data can be assigned by setting the <code>@names</property>,
 * which causes them to be displayed when the Matrix is printed and saved and
 * restored by the save/load methods.
 * <p>
 * Arbitrary non-Matrix columns can be added. These columns can be numeric or non-numeric, but 
 * in both cases they are treated separately and do not interact with the pure matrix 
 * operations (eg: matrix multiplication, transpose, etc). To add a non-Matrix column, just assign a
 * property to the Matrix with the name of the column. Eg: to create a Foo column:
 * <pre>
 * m.Foo = ["cat","tree","dog","house"]
 * </pre>
 * The non-Matrix columns will be preserved for a subset of the operations which do not change 
 * the row/column associations of the matrix data elements. In general, however,
 * if you perform a matrix operation you need to re-associate the custom columns 
 * yourself.
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
    static final int DISPLAY_ROWS = 20
    
    @Delegate
    Array2DRowRealMatrix matrix
    
    List<String> names = []
    
    /**
     * Optional information about the matrix. 
     * <p>
     * Useful to carry information about how a matrix was created.
     */
    Map<String,Object> metadata = [:]
    
    public Matrix(int rows, int columns) {
        matrix = new Array2DRowRealMatrix(rows, columns)
    }
    
    public Matrix(MatrixColumn... sourceColumns) {
        this.initFromColumns(sourceColumns)
    }
    
    @CompileStatic
    private void initFromColumns(final MatrixColumn[] sourceColumns) {
        final MatrixColumn c0 = sourceColumns[0]
        final int rows = c0.size()
        final int cols = sourceColumns.size()
        final double[][] newData =  new double[rows][cols]
        final MatrixColumn [] columns = sourceColumns
        for(int i=0; i<rows;++i) {
            double [] row = newData[i]
            for(int j=0; j<cols;++j)
                row[j] = (double)(columns[j].getDoubleAt(i))
        }
        this.matrix = new Array2DRowRealMatrix(newData,false)
        
        if(columns.any { it.name != null }) {
            this.names = columns.collect { MatrixColumn c -> c.name }
        }
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
            def result = sniffIsNumeric(it.value) 
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
    boolean sniffIsNumeric(String value) {
        false
    } 
 
    
    @CompileStatic
    boolean sniffIsNumeric(int [] values) {
        true
    } 
    
    @CompileStatic
    boolean sniffIsNumeric(short [] values) {
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
        if(r0 instanceof MatrixColumn) {
            this.initFromColumns(rows as MatrixColumn[])
        }
        else {
            initFromIterator(rowIterator,r0,columnNames)
        }
    }
    
    @CompileStatic
    public initFromIterator(Iterator<Iterable> rows, def r0, List<String> columnNames=null) {
        
        List data = new ArrayList(4096)
        int rowCount = 0
        List<Boolean> isNumerics = sniffNumericColumns(r0)
        
        int matrixColumnCount = (int)isNumerics.count { it }
        
        // Initialise an empty list for each non-numeric column that
        // we are going to fill
        List<List> nonNumerics = isNumerics.grep { !it }.collect { [] }
        if(!nonNumerics.isEmpty() && columnNames == null)
            throw new IllegalArgumentException("Column names must be provided as second argument when non-numeric columns are used")
        
        if(isNumerics.every { !it }) 
            throw new IllegalArgumentException("No numeric columns were detected. Check if column headers were read as data and apply readFirstLine:false to ignore them.")
        
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
            this.setNames((List<String>)([columnNames,isNumerics].transpose().grep { List i -> i[1] }.collect { Object i -> ((List)i)[0] }))
            
        int nonNumericIndex = 0
        isNumerics.eachWithIndex { isNumeric, index ->
            if(!isNumeric)
                this.setProperty(columnNames[index],nonNumerics[nonNumericIndex++])        
        }
    }

    /**
     * Probes the given row to see which columns in the row appear to be 
     * numeric types.
     * 
     * @param r0
     * @return  a list of booleans, true for numeric columns, false for non-numeric
     */
    @CompileStatic
    private List<Boolean> sniffNumericColumns(Object r0) {
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
        return isNumerics
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
    
    /**
     * Cooerces a vector into a Matrix by reshaping it. The vector must have 
     * at least rows x columns elements.
     * 
     * @param rows
     * @param columns
     * @param data
     */
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
    
    @CompileStatic
    MatrixColumn col(String columnName) {
        int n = this.@names.indexOf(columnName)
        if(n<0)
            throw new IllegalArgumentException("Column $columnName not found in this matrix")
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
    
    @CompileStatic
    RealVector rowVector(int n) {
        matrix.getRowVector(n)
    }
    
    @CompileStatic
    RealVector columnVector(int n) {
        matrix.getColumnVector(n)
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
        if(n instanceof IntRange) {
            int from = n.from
            int to = n.to
            
            if(n.reverse && n.from<0) {
                from = n.to
                to = n.from
            }
            
            if(from<0)
                from = this.rowDimension + from 
            if(to<0)
                to = this.rowDimension + to 
                
            List l = from..to

            double [][] submatrix = subsetRows((List<Number>)l)
            Matrix result = new Matrix(new Array2DRowRealMatrix(submatrix))
            result.inheritSettings(this)
            if(!this.properties.isEmpty()) 
                this.transferPropertiesToRows(result, (List<Number>)l)
            return result
        }
        else
        if(n instanceof List) {
            List<Number> l = (List)n
            if(l.size() == 0) // Seems to happen with m[][2] type syntax
                return getColumns()
            else {
                double [][] submatrix = subsetRows(l)
                Matrix result = new Matrix(new Array2DRowRealMatrix(submatrix))
                result.inheritSettings(this)
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
    void inheritSettings(final Matrix other) {
        this.@names = other.@names
        this.@displayColumns = other.@displayColumns
        this.@displayRows = other.@displayRows
        this.@displayPrecision = other.@displayPrecision
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
    private List<Integer> convertIndices(Iterable<Number> i) {
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
     * Execute the given closure <code>c</code> for each row in the Matrix
     * and then pass the result to the given operation to transform the result.
     * If the operation returns an instance of {@link StopIteration} the 
     * iteration is aborted.
     * 
     * @param c
     * @param operation
     */
    @CompileStatic
    private void iterateRowsWithDelegate(Closure c, Closure operation) {
        IterationDelegate delegate = new IterationDelegate(this, c)
        boolean withDelegate = !this.properties.isEmpty() || this.@names
        if(withDelegate) {
            c = (Closure)c.clone()
            c.setDelegate(delegate)
            c.setResolveStrategy(Closure.DELEGATE_FIRST)
        }
        int rowIndex = 0;
        if(c.maximumNumberOfParameters == 1) {
            for(double [] row in matrix.dataRef) {
                if(withDelegate)
                    delegate.row = rowIndex
                Object rowResult = c(row)
                def result = operation(rowIndex, row, rowResult)
                if(result instanceof StopIteration)
                    break
                ++rowIndex
            }
        }
        else 
        if(c.maximumNumberOfParameters == 2) {
            for(double [] row in matrix.dataRef) {
                if(withDelegate)
                    delegate.row = rowIndex
                Object rowResult = c(row)
                def result = operation(rowIndex, row, rowResult)
                if(result instanceof StopIteration)
                    break
                ++rowIndex
            }
        }
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
        this.iterateRowsWithDelegate(c) { int index, double [] row, Object rowResult ->
            results.add(rowResult)
        }
        return results
    }    
    
    /**
     * Find the index of the first row in the matrix satisfying the given closure
     * 
     * @param c
     * @return
     */
    @CompileStatic
    int findIndexOf(Closure<Boolean> c) {
        int result = -1
        this.iterateRowsWithDelegate(c) { int index, double [] row, Object rowResult ->
            if(rowResult) {
                result = index
                return StopIteration.instance
            }
        }
        return result
    }
   
    @CompileStatic
    public List<Number> findIndexValues(Closure<Boolean> c) {
        List<Integer> keepRows = []
        int rowIndex = 0;
        IterationDelegate delegate = new IterationDelegate(this, c)
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
        result.copyDisplaySettingsFrom(this)
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
    
    protected void transferPropertiesToRows(Matrix result, List<Number> indices = null) {
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
    
    @CompileStatic
    void copyDisplaySettingsFrom(final Matrix other) {
        this.@displayColumns = other.@displayColumns
        this.@displayRows = other.@displayRows 
        this.@displayPrecision = other.@displayPrecision
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
        if(!this.properties.isEmpty()) 
            this.transferPropertiesToRows(result)
            
        result.inheritSettings(this)

        return result
    }
    
    @CompileStatic
    private Matrix transformWithoutIndices(Closure c) {
        final int rows = matrix.rowDimension
        final int cols = matrix.columnDimension
        double[][] newData = new double[rows][cols]
        IterationDelegate delegate = new IterationDelegate(this, c)
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
        IterationDelegate delegate = new IterationDelegate(this, c)
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
     * @return a {@link Matrix} where each row has been divided by its mean value
     */
    @CompileStatic
    Matrix normaliseRows() {
        
        final int cols = this.matrix.getColumnDimension()
        
        final Matrix result = this.transformRows { double [] row ->
            
            double mean = Stats.mean(row)
            
            double [] rowResult = new double[row.size()]
            if(mean == 0d)
                return rowResult

            for(int i=0; i<cols; ++i) {
                rowResult[i] = row[i] / mean
                if(!Double.isFinite(rowResult[i])) {
                    rowResult[i] = 0.0d
                }
            }
            return rowResult
        }
        
        result.inheritSettings(this)

        if(this.@names)
            this.transferPropertiesToRows(result)
 
        return result
    }
    
    /**
     * @return a {@link Matrix} where each column has been divided by its mean value
     */
    @CompileStatic
    Matrix normaliseColumns() {
        
        final int rows = matrix.rowDimension
        final int cols = matrix.columnDimension
        final double[][] raw = this.dataRef
        
        double [][] normalised = new double[rows][cols]

        for(int i=0; i<cols;++i) {
            MatrixColumn column = col(i)
            final double mean = Stats.mean(column)
            for(int j=0; j<rows; ++j) {
                normalised[j][i] = raw[j][i] / mean
            }
        }
        Matrix result = new Matrix(normalised)
        result.inheritSettings(this)
        if(this.@names)
            this.transferPropertiesToRows(result)
            
        return result
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
        
        IterationDelegate delegate = new IterationDelegate(this, c)
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
        if(names && result.columnDimension == cols)
            result.names = names
        result.@displayColumns = this.@displayColumns
        result.@displayRows = this.@displayRows
        return result
    }
    
    @CompileStatic
    void eachRow(Closure c) {
        IterationDelegate delegate = new IterationDelegate(this,c)
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
    
    @CompileStatic
    Map<Object,Integer> countBy(Closure c) {
       
        Map<Object, Integer> result = [:]
        
        this.iterateRowsWithDelegate(c) { int index, double [] row, rowResult ->
            Integer value = result.get(rowResult)
            if(value.is(null)) {
                result.put(rowResult, 1)
            }
            else {
                result.put(rowResult, value+1)
            }
        }
        return result
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
//        new Matrix((Array2DRowRealMatrix)this.matrix.preMultiply(m.matrix))
        new Matrix((Array2DRowRealMatrix)m.matrix.preMultiply(this.matrix))
    }
    
    @CompileStatic
    Matrix plus(Matrix m) {
        new Matrix(this.matrix.add(m.matrix))
    }
      
    @CompileStatic
    Matrix minus(Matrix m) {
        Matrix result = new Matrix(this.matrix.subtract(m.matrix))
        this.transferPropertiesToRows(result)
        result.copyDisplaySettingsFrom(this)
        return result
    }
    
    @CompileStatic
    Matrix divide(double d) {
        Matrix result = new Matrix((Array2DRowRealMatrix)this.matrix.scalarMultiply(1/d))
        this.transferPropertiesToRows(result)
        result.copyDisplaySettingsFrom(this)
        return result
    }
    
    @CompileStatic
    Matrix plus(double x) {
        def result = new Matrix((Array2DRowRealMatrix)((RealMatrix)this.matrix).scalarAdd(x))
        this.transferPropertiesToRows(result)
        result.copyDisplaySettingsFrom(this)
        return result
    }
    
    @CompileStatic Matrix minus(final Number x) { return this.minus(x.toDouble() as double) }
    @CompileStatic Matrix plus(final Number x) { return this.plus(x.toDouble() as double) }
    @CompileStatic Matrix multipy(final Number x) { return this.multiply(x.toDouble() as double) }
    @CompileStatic Matrix divide(final Number x) { return this.divide(x.toDouble() as double) }
    
    @CompileStatic
    Matrix minus(double x) {
        def result = new Matrix((Array2DRowRealMatrix)((RealMatrix)this.matrix).scalarAdd(-x))
        this.transferPropertiesToRows(result)
        result.copyDisplaySettingsFrom(this)
        return result
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
    
    /**
     * Save the matrix in gzip compressed native memory format
     * <p>
     * This format is efficient but java-specific since it encodes the values as their native underlying memory format.
     * 
     * @param fileName  The file name to save as
     */
    void saveBinary(String fileName) {
        ObjectOutputStream oStream = new ObjectOutputStream(new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(fileName), 1024*1024)))
        List<Serializable[]> userColumns  = this.properties.grep { it.value instanceof Iterable }.collect { [it.key, it.value] as Serializable[] }
        oStream.withStream { ObjectOutputStream o -> 
            oStream << this.@names
            oStream << userColumns
            o << this.matrix.dataRef
        }
    }
    
    /**
     * Read a matrix saved using {@link #saveBinary}
     * @param fileName
     * @return  Matrix object read from file
     */
    static Matrix readBinary(String fileName) {
        ObjectInputStream iStream = new ObjectInputStream(new BufferedInputStream(new GZIPInputStream(new FileInputStream(fileName), 1024*1024)))
        double [][] data = null
        List<Serializable[]> userColumns  = null
        List<String> columnNames = null
        iStream.withStream { ObjectInputStream o ->
            columnNames = iStream.readObject()
            userColumns = iStream.readObject()
            data = iStream.readObject()
        }
        Matrix result = new Matrix(data)
        result.@names = columnNames
        userColumns.each { obj ->
            result.setProperty(obj[0], obj[1])
        }
        return result
    } 
    
    static Reader createReader(fileLike) {
        
        if(fileLike instanceof Reader)
            return fileLike
        
        boolean gzip = false
        boolean bgzip = false
        
        if(fileLike instanceof String) {
            fileLike = new File(fileLike)
        }
        
        if(fileLike instanceof File) {
            fileLike = fileLike.toPath()
        }
        
        if(fileLike instanceof Path) {
            Path path = fileLike
            if(path.toString().endsWith('.gz'))
                gzip = true
            else
            if(path.toString().endsWith('.bgz'))
                bgzip = true
            fileLike = Files.newInputStream(fileLike)
        }
        
        if(!(fileLike instanceof InputStream))
            throw new IllegalArgumentException("Expected object of type String, File, Path or InputStream, but was passed " + fileLike.class.name)
        
        if(gzip || bgzip) {
            fileLike = new GZIPInputStream(fileLike, 128*1024)
        }

        return fileLike.newReader()
    }
    
   
    @CompileStatic
    void save(Map options = [:], Writer w) {
        
        List nonMatrixCols = (List)this.properties*.key 
        
        // NOTE: the this.properties.names seems to be required because of a 
        // weird bug where groovy will prefer to set an expando property rather than
        // set the real property on this object
        List matrixCols = this.@names
        if(!matrixCols && this.properties.containsKey('names'))
            matrixCols = (List)this.properties.names
            
        if(!nonMatrixCols.isEmpty() && matrixCols.isEmpty())
            matrixCols = (1..this.columnDimension).collect { 'C' + it }

        List columnNames = (List)this.properties*.key 
        columnNames.addAll(matrixCols)
        
        if(columnNames) {
            if(!options.r) {
                w.write "# "
            }
            w.write columnNames.join("\t")   
            w.write('\n')
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
    
    static Matrix load(Map options = [:], Reader r) {
        List rows = new ArrayList(1024)
        
        
        boolean rfl = false
        
        List names
        if(options.columnNames) {
            names = options.columnNames
            rfl = true
        }
        
        Map tsvOptions = [readFirstLine:rfl]
        if('columnTypes' in options) {
            tsvOptions.columnTypes = options.columnTypes
        }
        
        List<PropertyMapper> values = new TSV(tsvOptions, r).collect { it }
        
        if(!names && values) {
            PropertyMapper firstRow = values[0]
            names = firstRow.columns*.key 
        }
        
        Matrix m = new Matrix(values*.values, names)
        return m
    } 
    
    static Matrix load(Map options = [:], String fileName) {
        List rows = new ArrayList(1024)
        
        Reader r = createReader(fileName)
        
        // Sniff the first line
        String firstLine = r.readLine()
        List names
        
        boolean rfl = true
        
        Closure reRead = {
            r.close()
            r = createReader(fileName)
        }

        if(options.columnNames) {
            names = options.columnNames
            rfl = true
            reRead()
        }
        else
        if(firstLine.startsWith('#')) {
            names = firstLine.substring(1).trim().split("\t")
            rfl = true // since we do not reRead(), we already read the column names,
                       // data should start reading from the current "first line"
        }
        else
        if(firstLine.tokenize('\t').every { !it.isNumber() }) { // none numeric
            names = firstLine.trim().split("\t")
            rfl = true
        }
        else
        if(options.r) {
            names = firstLine.substring(1).trim().split("\t")
            rfl = true
        }

        if(options.containsKey('readFirstLine'))
            rfl = options.readFirstLine

      
        Map tsvOptions = [readFirstLine:rfl]
        if('columnTypes' in options) {
            tsvOptions.columnTypes = options.columnTypes
        }

        List values = new TSV(tsvOptions, r)*.values
        Matrix m = new Matrix(values, names)
        return m
    }
    
    int displayPrecision = 6
    
    int displayColumns = 50
    int displayRows = DISPLAY_ROWS
    int defaultColumnWidth = 10
    int maxUserColumnWidth = 20
        
    String toString() {
       
        List<Map.Entry> userColumns = getUserColumns()
        
        List<String> headerCells = this.@names ? this.@names : (1..this.columnDimension).collect { ' ' }

        if(this.properties) {
            headerCells = userColumns*.key + headerCells
        }
        
        int halfMaxCols = Math.floor(displayColumns/2)
        List<Integer> headerCellIndexes = (0..<headerCells.size())
        List<Integer> leftIndexes = 0..<headerCells.size()
        List<Integer> rightIndexes = []
        if(headerCells.size() > displayColumns) {
            
           leftIndexes = 0..halfMaxCols
           rightIndexes = (headerCells.size()-halfMaxCols)..<headerCells.size()
            
           headerCellIndexes = leftIndexes + rightIndexes
           headerCells = headerCells[leftIndexes] + ['...'] + headerCells[rightIndexes]
        }
        
        int defaultColumnWidth = Math.max(this.@defaultColumnWidth, headerCells*.size()?.max()?: 0)
        int rowNumWidth = 6
        
       
        DecimalFormat format = new DecimalFormat()
        format.minimumFractionDigits = 0
        format.maximumFractionDigits = displayPrecision
       
        List<Integer> columnWidths = [defaultColumnWidth] * (headerCells.size())
        
        int rowCount = 0
        def printRow = { row ->
            
          List cells
           if(row == null)
                cells = []
           else 
               cells = (row as List)
           if(this.properties) {
               cells = userColumns.collect { it.value?it.value[rowCount]:"null" } + cells
           }
           
           if(cells.size()>displayColumns) {
               cells = cells[leftIndexes] + ['...'] + cells[rightIndexes]
           }
           
           int columnIndex = 0
           List values = cells.collect { value ->
               int columnWidth = columnWidths[columnIndex]?:defaultColumnWidth
               
               ++columnIndex;

               if(!(value instanceof Double)) {
                   String renderedValue = String.valueOf(value)
                   if(renderedValue.size()>columnWidth) {
                       renderedValue = renderedValue.substring(0, columnWidth-4) + '...'
                   }
                   
                   return renderedValue.padRight(columnWidth)
               }
                       
               String result
               if(value < 0.0001d && value !=0 && value > -0.0001d)  {
                   result = String.format("%1.6e",value)
               }
               else {
                   result = format.format(value)
               }
               return result.padRight(columnWidth)
           }
           
           return ((rowCount++) + ":").padRight(rowNumWidth) + values.join(" ")  
        }
        
        // Adjust width based on user columns
        for(int i=0; i<userColumns.size(); ++i) {
            Map.Entry<String,List> e = userColumns[i]
            List widthBasis = [e.key, *e.value]
            if(matrix.rowDimension>=displayRows) {
                widthBasis = [
                    e.key,
                    *e.value[0..displayRows/2],
                    *e.value[-(displayRows/2)..-1]    
                ]
            }
            if(e.value != null) {
                columnWidths[i] = Math.min(maxUserColumnWidth,widthBasis*.toString()*.size().max())
            }
        }
       
        String headers = ""
        if(headerCells) {
            int headerIndex = 0
            headers =  (" " * rowNumWidth) + 
               headerCells.collect { it.padRight(columnWidths[headerIndex++]?:defaultColumnWidth) }
               .join(" ") + "\n"
        }

        if(matrix.rowDimension<displayRows) {
            return "${matrix.rowDimension}x${matrix.columnDimension + this.properties.size()} Matrix:\n"+ 
                headers + 
                matrix.data.collect { row -> 
                    printRow(row)
            }.join("\n")
        }
        else {
            int omitted = matrix.rowDimension-displayRows
            String value = "${matrix.rowDimension}x${matrix.columnDimension + this.properties.size()} Matrix:\n"+ 
                headers + 
                matrix.data[0..displayRows/2].collect(printRow).join("\n")  
            rowCount += omitted -1    
            value +=
                "\n... ${omitted} rows omitted ...\n" + 
                matrix.data[-(displayRows/2)..-1].collect(printRow).join("\n")
                
            return value
        }
    }

    private List getUserColumns() {
        List<Map.Entry<String,Iterable>> userColumns = this.properties.grep { it.value instanceof Iterable }
        return userColumns
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
        String header = "| " + headerCells.collect { String.valueOf(it).padRight(columnWidths[columnIndex++]) }.join(" | ") + "|"
        w.println(header)
        columnIndex = 0
        w.println "|-" + headerCells.collect { "-" * Math.max(columnWidths[columnIndex++], it.size()) }.join("-|-") + "|"
        
        List<Iterator> props = properties.collect { it.value.iterator() }
        
        int index = 0
        this.eachRow { row ->
            
            columnIndex = 0
            w.print "| " +  props.collect { (String.valueOf(it.hasNext()) ? it.next() : " ").padRight(columnWidths[columnIndex++]) }.join(" | ") + " | "
           
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
        setNames(names)
    }
    
    void setNames(List<String> names) {
        this.names = names*.toString()
    }
    
    @CompileStatic
    Object getProperty(String name) {
        getProperty(name,false)
    }

    @CompileStatic
    Object getProperty(String name, boolean safe) {
        if(this.@names.contains(name)) {
            return this.col(this.@names.indexOf(name))
        }
        
       def result = super.getProperty(name)
       if(result == null) {
           
           if(safe) {
               return null
           }

           String suggestedColumns = ""
           if(this.@names) {
               suggestedColumns = ". Valid columns include: " + this.@names.take(20).join(', ')
           }
           throw new IllegalArgumentException("No column named $name or matching property in this Matrix$suggestedColumns")
       }
           
       return result
    }
    
    void setProperty(String name, Object value) {
        if(name == "names" || name == "columnNames") {
            this.setNames(value)
        }
        else {
            // In some other places we rely on custom values that are
            // set being Iterables, so for now convert them here
            if(value instanceof double[]) {
                super.setProperty(name,value as List)
            }
            else
            if(value instanceof String[]) {
                super.setProperty(name,value as List)
            }
            else
            if(value instanceof int[]) {
                super.setProperty(name,value as List)
            }
            else
            if(value instanceof long[]) {
                super.setProperty(name,value as List)
            }
            else
            if(value instanceof float[]) {
                super.setProperty(name,value as List)
            }
            else
                super.setProperty(name,value)
        }
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
    
    @CompileStatic
    List<Integer> order(@ClosureParams(value=SimpleType.class,options="double[]") Closure c) {
        List indices = (0..<this.rowDimension).collect { it }
        IterationDelegate delegate = new IterationDelegate(this, c)
        Closure cloned = (Closure)c.clone()
        cloned.delegate = delegate

        return indices.sort { i ->
            ((IterationDelegate)delegate).row = i
            return cloned(this.dataRef[i])
        }
    }
    
    Map<Object,Matrix> groupBy(Closure c) {
        List indices = (0..<this.rowDimension) as List
        IterationDelegate delegate = new IterationDelegate(this, c)
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
    
    @CompileStatic
    Matrix applyViaIndices(Method method, Closure c) {
        List indices = (0..<this.rowDimension).collect { it }
        IterationDelegate delegate = new IterationDelegate(this, c)
        Closure cloned = (Closure)c.clone()
        cloned.delegate = delegate
        def result = method.invoke(null, indices,{ int i ->
            ((IterationDelegate)delegate).row = i
            return cloned(this.dataRef[i])
        })
        
        double [][] submatrix = 
            (double[][])(result instanceof Integer ? subsetRows(((List<Number>)[result])) : subsetRows((List<Number>)result))

        Matrix m = new Matrix(new Array2DRowRealMatrix(submatrix))
        m.@names = this.@names
        if(!this.properties.isEmpty()) 
            this.transferPropertiesToRows(m, (List<Number>)indices)        
        
        return m
    }
    
    @CompileStatic
    Matrix iterateWithDelegate(String methodName, Closure c) {
        iterateWithDelegate(org.codehaus.groovy.runtime.DefaultGroovyMethods.getDeclaredMethod(methodName, Iterator,  Closure), c) 
    }
    
    @CompileStatic
    Matrix iterateWithDelegate(Method m, Closure c) {
        IterationDelegate dg = new IterationDelegate(this,c)
        c = (Closure)c.clone()
        c.setDelegate(dg)
        int rowIndex = 0;
        Iterator i = this.iterator()
        def result = m.invoke(null, i, { values ->
            dg.row = rowIndex++
            c(values)
        })
        
        Matrix matrixResult
        if(result.is(null)) {
            return null
        }
        else
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
            throw new Exception("Unexpected argument result type from Matrix iteration: " + result?.getClass()?.name)
        
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
        new Iterator<Map>() {
            
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
    
    List<Map> toListMap() {
        asListMap()
    }
    
    SingularValueDecomposition svd() {
       new SingularValueDecomposition(this.matrix)
    }
    
    double[][] getTruncatedSVD(final int k) {
        SingularValueDecomposition svd = new SingularValueDecomposition(this.matrix);
    
        double[][] truncatedU = new double[svd.getU().getRowDimension()][k];
        svd.getU().copySubMatrix(0, truncatedU.length - 1, 0, k - 1, truncatedU);
    
        double[][] truncatedS = new double[k][k];
        svd.getS().copySubMatrix(0, k - 1, 0, k - 1, truncatedS);
    
        double[][] truncatedVT = new double[k][svd.getVT().getColumnDimension()];
        svd.getVT().copySubMatrix(0, k - 1, 0, truncatedVT[0].length - 1, truncatedVT);
    
        RealMatrix approximatedSvdMatrix = (new Array2DRowRealMatrix(truncatedU)).multiply(new Array2DRowRealMatrix(truncatedS)).multiply(new Array2DRowRealMatrix(truncatedVT));
    
        return approximatedSvdMatrix.getData();
    }

    
    /**
     * Calculate pairwise correlation between all rows and columns in the matrix
     * 
     * @return
     */
    @CompileStatic
    Matrix getRowCorrelations() {
        
        final PearsonsCorrelation p = new PearsonsCorrelation()
        
        final double [][] subset = this.matrix.data
        
        Matrix subset_cov = (0..<rowDimension).collect { int i ->
            double [] vals = new double[rowDimension]
            for(int j=0; j<i; ++j) {
                vals[j] = p.correlation(subset[i],subset[j])
            }
            return vals
        } as Matrix
        
        Matrix result = subset_cov.transform { double x, int i, int j ->
           if(j>i) {
               ((double[])(subset_cov[j]))[i]
           }
           else
           if(i == j) {
               1.0d
           }
           else {
               x
           }
       }
       this.transferPropertiesToRows(result)
       result.copyDisplaySettingsFrom(this)
       return result
    }
    
    /**
     * Concat the given matrices "vertically" (ie: stacking their rows)
     * 
     * @param matrices
     * @return
     */
    @CompileStatic
    static Matrix concat(Matrix... matrices) {
        concat(matrices as List)
    }

    /**
     * Concat the given matrices "vertically" (ie: stacking their rows)
     * 
     * @param matrixIter
     * @return
     */
    @CompileStatic
    static Matrix concat(Iterable<Matrix> matrixIter) {
        List<Matrix> matrices = matrixIter.asList()
        Array2DRowRealMatrix data = 
            new Array2DRowRealMatrix((int)matrices*.rowDimension.sum(), matrices[0].columnDimension)
        int rowIndex = 0
        for(int i=0; i<matrices.size(); ++i) {
            data.setSubMatrix(matrices[i].data, rowIndex, 0)
            rowIndex += matrices[i].rowDimension
        }
        
        Matrix result = new Matrix(data)
        matrices[0].properties.each {  key, value ->
            result.setProperty((String)key, matrices*.getProperty((String)key).sum())
        }
        
        result.copyDisplaySettingsFrom(matrices[0])
        if(matrices[0].@names)
            result.@names = matrices[0].@names
        return result
    }
    
    @CompileStatic
    Iterator<SubMatrix> window(int offset, int rows) {
       new Iterator<SubMatrix>() {
           
           int windowStart = offset
           int i=offset + rows
           
           final int numRows = matrix.rowDimension
          
           @CompileStatic
           boolean hasNext() {
               return i<numRows;
           }
           
           @CompileStatic
           SubMatrix next() {
               ++i
               new SubMatrix(Matrix.this, windowStart++, rows)
           }
           
           @CompileStatic
           void remove() { 
               throw new UnsupportedOperationException() 
           }
       }         
    }
    
    @CompileStatic
    Matrix getReducedBasis(int numComponents) {
        EJMLPCA pca = new EJMLPCA(this)
        pca.computeBasis(numComponents)
        Matrix result = new Matrix((0..<numComponents).collect { 
            pca.getBasisVector(it)
        } as double[][])
        
        List<Double> loading = (0..<numComponents).collect { 
            Math.sqrt(pca.W.get(it,it))
        } 
        
        result.@metadata.loadings = loading
        return result
    }
    
    /**
     * Perform dimensionality reduction to reduce the dimensions of the matrix columns
     * down to the sepecified number of components.
     * 
     * @param numComponents
     * @return a n x numComponents Matrix object where n is the original row dimension of this
     *         matrix, with metadata carrying the loadings and basis of the PCA outputs
     */
    @CompileStatic
    Matrix reduce(int numComponents) {
        EJMLPCA pca = new EJMLPCA(this)
        pca.computeBasis(numComponents)
        Matrix basis = new Matrix((0..<numComponents).collect { 
            pca.getBasisVector(it)
        } as double[][]) 
        
        List<Double> loading = (0..<numComponents).collect { 
            Math.sqrt(pca.W.get(it,it))
        } 
  
        Matrix result = this.multiply(basis.transpose())
        result.@metadata.loadings = loading
        result.@metadata.basis =  basis
        result.@names = (1..numComponents).collect { 'PC' + it }
        
        // The result will have rows corresponding to the origional matrix, so we 
        // can transfer metadata across
        this.transferPropertiesToRows(this)
        return result
    }
    
    @CompileStatic
    Matrix reshape(final int rows, final int outputCols = -1) {
        
        final int cols = outputCols > 0 ? outputCols : (int)((columnDimension * rowDimension) / rows)
        
        final double [][] newData = new double[rows][cols]
        final int oldCols = this.getColumnDimension()
        int newRow = -1
        int valueCount = 0
        for(int i=0; i<this.size(); ++i) {
            for(int j=0; j<oldCols; ++j) {
                int newCol = valueCount % cols
                if(newCol==0)
                    ++newRow
                newData[newRow][newCol] = this.dataRef[i][j]
                ++valueCount
            }
        }
        return new Matrix(newData)
    }
    
    /**
     * Partition this matrix based on the value returned by the given closure.
     * <p>
     * This method allows you to easily identify ranges of rows that satisfy a particular
     * condition, or fall into any range of different classes. This method is a good substitute
     * for using {@link #groupBy} when you only need to know the indexes of the rows that 
     * satisfy the conditions, and when those indexes occur in runs, as it returns the indexes
     * in ranges rather than the full list of values.
     * <p>
     * The closure is executed for each row, passing the, the double array for the row
     * to the closure. Columns can be accessed by name using implicit variables for the 
     * column names.
     * <p>
     * The returned result is used as a state classifier similar to usage with {@link #groupBy}.
     * Whenever the returned state changes, the range of indices for which that state was constant
     * will be captured and added as an IntRange to the result for that state value.
     * <p>
     * <pre>
     *  Matrix m = Matrix.fromListMap([
     *          [sun: 'sunny', temp: 20],
     *          [sun: 'sunny', temp: 20],
     *          [sun: 'cloudy', temp: 15],
     *          [sun: 'cloudy', temp: 15],
     *          [sun: 'sunny', temp: 14],
     *          [sun: 'sunny', temp: 12],
     *          [sun: 'sunny', temp: 20],
     *  ])
     *  
     *  Map result = m.partition { [sun, temp>15] }
     *  
     *  println "The times when it was sunny but temperature <= 15 were: " + result[['sunny',false]]
     * </pre>
     * 
     * @param c
     * @return
     */
    @CompileStatic
    Map<Object,List<IntRange>> partition(@ClosureParams(value=SimpleType, options=['double[]']) Closure c) {
        
        Map<Object,List<IntRange>> partitions = [:]
        
        Object state = null
        int start = 0
        
        this.iterateRowsWithDelegate(c) { int index, double [] row, rowResult ->
            
            if(state.is(null))
                state = rowResult
            
            if(state != rowResult) {
                if(start != index) {
                    List<IntRange> resultPartitions = partitions[state]
                    if(resultPartitions.is(null)) {
                        resultPartitions = []
                        partitions.put(state, resultPartitions)
                    }
                    resultPartitions.add(start..<index)
                    start = index
                    state = rowResult
                }
            }
        }
        
        List<IntRange> resultPartitions = partitions[state]
        if(resultPartitions.is(null)) {
            resultPartitions = []
            partitions.put(state, resultPartitions)
        }
        resultPartitions.add(start..<rowDimension)
        
        return partitions
    }
    
    /**
     * Return the given column as a Smile Vector
     * 
     * @param columnName    Column to convert to vector
     * @return  Smile Vector 
     */
    BaseVector vector(String columnName) {
        if(this.@names && (columnName in this.@names)) {
            return DoubleVector.of(columnName, this.getAt(columnName) as double[])
        }
        else
        if(columnName in this.userColumns*.key) {
            
            def values = this[columnName]
            if(values[0] instanceof Double) 
                return DoubleVector.of(columnName, this.getAt(columnName) as double[])
            else
            if(values[0] instanceof String) 
                return StringVector.of(columnName, *values)
            else
            if(values[0] instanceof Integer)  
                return IntVector.of(columnName, *values)
            else
            if(values[0] instanceof Boolean)  
                return BooleanVector.of(columnName, *values)
            else
                throw new IllegalArgumentException("Column $columnName of type ${values[0].class} is of an unsupported type to convert to vector")
        }
        else
            throw new IllegalArgumentException("Column $columnName is not known in this matrix (candidate columns are: ${this.@names}, ${this.properties*.key.join(',')}")
    }
    
    /**
     * Fit a random forest on the the data in this matrix wiht the given column name 
     * as the response and all other columns as predictors.
     * 
     * @param params    named parameter arguments, including 'predictors', or any of the
     *                  documented Smile parameters, eg: max_depth, max_nodes (see Smile 
     *                  <a href='https://github.com/haifengl/smile/blob/master/core/src/main/java/smile/classification/RandomForest.java#L182'>RandomForest docs</a> 
     *                  for valid values)
     * @param response  String value specifying the name of the column to treat as the response
     * @return  RandomForest, fitted to data
     */
    RandomForest forest(Map params = [:], String response) {
       DataFrame df = this as DataFrame 
       
       List allColumns = [] 
       if(params.containsKey('predictors')) {
           allColumns.addAll(params.predictors)
           assert params.predictors.every { it in this.@names }
           
           // Don't let it get passed through to smile
           params.remove('predictors')
       }
       else {
           if(this.getUserColumns())
               allColumns.addAll(getUserColumns()*.key)
           if(this.@names)
               allColumns.addAll(this.@names)
       }
           
       List predictors = allColumns.grep { it != response }
       
       Formula formula = Formula.of(response, *predictors)
       
       Map convertedParams = params.collect{ String k, Object v ->
           ['smile.random.forest.' + k.replaceAll('_','.'), String.valueOf(v)]
       }.collectEntries()

       return RandomForest.fit(formula, df, new Properties(convertedParams))
    }
    
    /**
     * Same as dataRef property but works around some contexts where that property
     * is not visible because it is implicitly resolved as groovy delegate
     */
    @CompileStatic
    double [][] getRawData() {
        return this.matrix.dataRef
    }
    
    /**
     * Return a matrix where the values that are NaN have been replaced with
     * the specified value (default: 0)
     * 
     * @param value value to replace with
     * @return  result matrix
     */
    @CompileStatic
    Matrix fillna(double value = 0) {
        this.transform { Double x ->
            x.isNaN() ? value : x
        }
    }
  
//    @CompileStatic
    Object asType(Class clazz) {
        if(clazz == DataFrame) {
            List userVectors = this.getUserColumns()*.key.collect { String colName -> vector(colName) } 
            List colNames = this.@names ? this.@names : (1..this.columnDimension).collect { "C$it" }
            DataFrame matrixDataFrame = DataFrame.of(this.getDataRef(), *colNames)
            if(userVectors) {
                return DataFrame.of(*userVectors).merge(matrixDataFrame)
            }
            else {
                return matrixDataFrame
            }
        }
    }
    
    /**
     * @return a matrix whose columns are zero-centered and scaled to have standard deviation
     *         of 1
     */
    @CompileStatic
    Matrix standardise() {
        List<Stats> col_stats = (List<Stats>)this.columns.collect { MatrixColumn c -> Stats.from((Iterable)c) }
        return this.transform { double x, int i, int j ->
            (x - col_stats[j].mean) / col_stats[j].standardDeviation
        }.fillna(0)
    }
    
    /**
     * Convenience method to return summary statistics of all the values in this Matrix
     */
    @CompileStatic 
    Stats summarize() {
        return Stats.from(this)
    }
    
    TableDisplay display(Map attributes = [:]) {
        
        TableDisplay display = new TableDisplay(this.toListMap())
        for(Map.Entry e : attributes) {
            if(display.hasProperty(e.key))
                display[e.key] = e.value
        }
        
        if(attributes.heatmap == true) {
            for(String colName in this.@names) {
                display.addCellHighlighter(TableDisplayCellHighlighter.getHeatmapHighlighter(colName, TableDisplayCellHighlighter.SINGLE_COLUMN))
            }
        }
        return display
    }
    
    @CompileStatic
    static void registerBeakerX() {
        Displayers.register(
            Matrix.class,
            new Displayer<Matrix>() {
                @Override
                public Map<String, String> display(Matrix matrix) {
                    new TableDisplay(matrix.toListMap()).display();
                    return OutputCell.DISPLAYER_HIDDEN;
                }
            });
    }
   
    /**
     * Return smile attributes for this matrix
     * @return
     */
//    @CompileStatic
//    Attribute[] getAttributes() {
//        if(this.@names)
//            return this.@names.collect { new NumericAttribute(it) } as Attribute[]
//        else
//            return this.columnDimension.collect { 'V' + it }.collect { new NumericAttribute(it) } as Attribute[]
//    }
}
