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
            return new Matrix(cols as MatrixColumn[])
        }
        else
        if(arg[0] instanceof String) {
            List<MatrixColumn> cols = arg.collect { columnName -> columns.find { it.name == columnName } }
            return new Matrix(cols as MatrixColumn[])
        }
        throw new IllegalArgumentException("Expected list of strings as column names or integers as column indices")
    }

    Object getAt(Range arg) {
        int colIndex = 0
        List<String> names = (List<String>)columns[arg]*.name.collect { n -> ++colIndex; n == null ? colIndex.toString() : n }
        List<MatrixColumn> cols = columns[arg]
        Map columnMap = [ names, cols ].transpose().collectEntries()
        def result =  new Matrix(columnMap)
        return result
    } 
}
