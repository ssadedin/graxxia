package graxxia

class MatrixColumnList {
    
    @Delegate
    ArrayList<MatrixColumn> columns
    
    Object getAt(Range arg) {
        Map columnMap = [columns[arg]*.name, columns[arg]].transpose().collectEntries()
        def result =  new Matrix(columnMap)
        return result
    } 
}
