package graxxia;
import java.util.Iterator;
import java.util.NoSuchElementException;


public class MatrixColumnIterator implements Iterator<Double> {

    private final double [][] values;
    private final int columnIndex;
    private int rowIndex = 0;
    private final int max;
    
    public MatrixColumnIterator(double [][] values, int columnIndex) {
        this(values, columnIndex, 0, values.length-1);
    }

    public MatrixColumnIterator(double [][] values, int columnIndex, int rowOffset, int rowLimit) {
        this.values = values;
        this.columnIndex = columnIndex;
        this.max = rowLimit < 0 ? values.length : rowLimit;
        this.rowIndex = rowOffset;
    }
    
    @Override
    public boolean hasNext() {
        return rowIndex < max;
    }

    @Override
    public Double next() {
        if(rowIndex>=max)
            throw new NoSuchElementException("Matrix column " + columnIndex + " does not have " + rowIndex + " rows within limit " + max);
        return values[rowIndex++][columnIndex];
    }

    @Override
    public void remove() {
        throw new UnsupportedOperationException();
    }

}
