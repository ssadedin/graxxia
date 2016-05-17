package graxxia

class MatrixAggregator {
    
    Matrix matrix
    
    Map columns = [:]

    def methodMissing(String name, Object args) {
        columns[name] = args[0]
    }
    
    def propertyMissing(String name) {
        return matrix.getProperty(name)
    }
}
