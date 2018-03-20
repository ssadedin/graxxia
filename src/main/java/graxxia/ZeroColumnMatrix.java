package graxxia;

import org.apache.commons.math3.linear.Array2DRowRealMatrix;

public class ZeroColumnMatrix extends Array2DRowRealMatrix {

    private static final long serialVersionUID = 1L;
    
    private int numberOfRows;
    
    double [][] zeroData;

    public ZeroColumnMatrix(int rows) {
        super();
        this.numberOfRows = rows;
        this.zeroData = new double[rows][];
    }

    @Override
    public int getColumnDimension() {
        return super.getColumnDimension();
    }

    @Override
    public int getRowDimension() {
        return this.numberOfRows;
    }

    @Override
    public double[][] getData() {
        return zeroData;
    }

    @Override
    public double[][] getDataRef() {
        return zeroData;
    }
    
}
