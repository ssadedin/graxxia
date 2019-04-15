package graxxia

import groovy.transform.CompileStatic

@CompileStatic
class MatrixColumnList {
    
    @Delegate
    ArrayList<MatrixColumn> columns
    
    Object getAt(Range arg) {
        
        int colIndex = 0
        List<String> names = (List<String>)columns[arg]*.name.collect { n -> ++colIndex; n == null ? colIndex.toString() : n }
        List<MatrixColumn> cols = columns[arg]
        Map columnMap = [ names, cols ].transpose().collectEntries()
        def result =  new Matrix(columnMap)
        return result
    } 
}
