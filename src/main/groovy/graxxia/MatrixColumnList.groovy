package graxxia

import groovy.transform.CompileStatic

@CompileStatic
class MatrixColumnList {
    
    @Delegate
    ArrayList<MatrixColumn> columns
    
    Object getAt(String arg) {
        def col = columns.find { it.name == arg }
        if(col.is(null)) {
            def msg = "Column $arg was not found in matrix. Valid columns include: ${columns.take(20)*.name.join(', ')}"
            if(this.columns.size()>20) {
                msg = msg + " ... (${this.columns.size() - 20} more)"
            }
            throw new IllegalArgumentException(msg)
        }
        return new Matrix([col] as MatrixColumn[])
    }

    Object getAt(List arg) {
        if(arg[0] instanceof Number) {
            List<MatrixColumn> cols = columns[arg]
            final int oob = cols.findIndexOf { it.is(null) }
            if(oob>=0)
                throw new IllegalArgumentException("Column ${arg[oob]} is out of bounds. Matrix has ${columns.size()} columns")
            Matrix result = new Matrix(cols as MatrixColumn[])
            columns[0].sourceMatrix.transferPropertiesToRows(result)
            return result
        }
        else
        if(arg[0] instanceof String) {
            List<MatrixColumn> cols = arg.collect { columnName -> columns.find { it.name == columnName } }
            return new Matrix(cols as MatrixColumn[])
        }
        throw new IllegalArgumentException("Expected list of strings as column names or integers as column indices")
    }
    
    Object getAt(double [] indices) {
        List<MatrixColumn> cols = indices.collect { Double value ->
            int index = (int)value
            if(columns[index] == null)
                throw new IllegalArgumentException("Column ${index} is out of bounds. Matrix has only ${columns.size()} columns")
            return columns[index] 
        }
    }

    Object getAt(int [] indices) {
        List<MatrixColumn> cols = indices.collect { Integer index ->
            if(columns[index] == null)
                throw new IllegalArgumentException("Column ${index} is out of bounds. Matrix has only ${columns.size()} columns")
            return columns[index] 
        }
    }

    /**
     * Implement selection of a range of columns
     * <p>
     * note: this method preserves non-matrix columns since subsetting by range implicitly includes only the
     * matrix columns
     */
    Object getAt(Range arg) {
        int colIndex = 0
        List<String> names = (List<String>)columns[arg]*.name.collect { n -> ++colIndex; n == null ? colIndex.toString() : n }
        List<MatrixColumn> cols = columns[arg]
        Map<String,Iterable> columnMap = [ names, cols ].transpose().collectEntries()
        def result = new Matrix(columnMap)
        columns[0].sourceMatrix.transferPropertiesToRows(result)
        return result
    } 
}
