import static org.junit.Assert.*;

import org.junit.Test;

import graxxia.*
import groovy.time.TimeCategory;

class MatrixTest {

    @Test
    public void test() {
        
        def m = new Matrix(3,4)
        
        m[2][3] = 9
        
        assert m[2][3] == 9
    }
    
    @Test
    void testColumn() {
        def m = new Matrix(4,2, [0d,1d,
                                 2d,3d,
                                 4d,5d,
                                 6d,6d])
        
        assert m.col(0)[0] == 0d
        assert m.col(0)[1] == 2d
        
        println m.col(0).grep { println(it); it > 0 }
        
        println m.col(0).find { it > 0 }
        
        println m.col(0).findIndexValues { it > 0 }
        
        println m.col(0)[0,2]
        
        
        println m.col(1).collect { it %2 }
    }
    
    @Test
    void testColumnMean() {
        def m = new Matrix(4,2, [0d,1d,
                                 2d,3d,
                                 4d,5d,
                                 6d,6d])
       
        assert Stats.mean(m.col(0)) == 3
        assert Stats.mean(m.col(1)) == 15 / 4
        
        assert Stats.mean(m[][0]) == 3
     }
    
    @Test
    void testColumnAccess() {
        def m = new Matrix(4,3, [0d,1d,3d,
                                 2d,3d,4d,
                                 4d,5d,6d,
                                 6d,6d,6d])
        
        assert m.columns.size() == 3
        
        println "m[][2] = " + m.columns[2]
        println "m[][2] = " + m[][2]
        println "m[][2][3] = " + m[][2][3]
        assert m[][2][3] == 6d
    }
    
    @Test
    void bigMatrix() {
        Matrix m = new Matrix(60,2)
        for(int i=0; i<m.rowDimension;++i) {
            m[i] = [i,i+1]
        }
        
        m.species = (["frog"] * 20 + ["cat"] * 20 + ["dog"] * 20)
        
        println "Matrix is $m"
        
        assert m.toString().contains("10 rows omitted")
    }
    
    @Test
    void testListAccess() {
        def m = new Matrix(4,3, [0d,1d,3d,
                                 2d,3d,4d,
                                 4d,5d,6d,
                                 6d,6d,6d])
        def sub = m[ [1,2]]         
        assert sub[0][0] == 2
        assert sub[1][2] == 6
        
        int [] indices = [1,2] as int[]
        sub = m[indices]
        assert sub[0][0] == 2
        assert sub[1][2] == 6
        
        long [] lndices = [1,2] as long[]
        sub = m[lndices]
        assert sub[0][0] == 2
        assert sub[1][2] == 6
         
        List lndices2 = [1L,2L]
        sub = m[lndices2]
        assert sub[0][0] == 2
        assert sub[1][2] == 6
    }
    
    @Test
    void testTypeConversion() {
        double [][] x = [
         [2d,3d,4d] as double[],
         [4d,5d,6d] as double[]
        ]
        
        Matrix m = x.toMatrix()
        assert m[0] == [2d,3d,4d]
        
        Matrix m2 = x as Matrix
        assert m2[0] == [2d,3d,4d]
        
        List l = x as List
        assert l[0] == [2d,3d,4d]
        
        def c = m[][1]
        
        assert c as List == [3d,5d]
    }
    
    @Test
    void testFromLists() {
        
        Matrix m = new Matrix([[2,5,3,3,4], [5,6,7,2,4]])
        assert m.rowDimension == 2
        
        m = [[2,5,3,3,4], [5,6,7,2,4]] as Matrix
        assert m.rowDimension == 2
    }
    
    @Test
    void testNamedColumns() {
        Matrix m = new Matrix([[2,5,3,3,4], [5,6,7,2,4]])
        m.names = ["foo","bar"]
        
        println "Second column is " + m.getColumns(["bar"]) 
        
        assert m.getColumns(["bar"])[0] == [5d,6d]
    }
    
    @Test
    void testLoadSave() {
        Matrix m = new Matrix([[2,5,3,3,4], [5,6,7,2,4]])
        m.save("test.tsv")
        
//        Matrix m2 = Matrix.load("test.tsv")
//        assert m2.columnDimension == 5
//        assert m2.rowDimension == 2
//        assert m2[1][2] == 7.0
        
        m.@names = ["foo","bar","cat","dog","tree"]
        m.save("test.tsv")
        
        Matrix m3 = Matrix.load("test.tsv")
        assert m3.rowDimension == 2
        assert m3.names == ["foo","bar","cat","dog","tree"]
        
    }
    
    @Test
    void testWhich() {
        Matrix m = new Matrix([[2,5,3,3,4], 
                               [5,6,7,2,4]])
        
        assert m.which { row ->
            row[0]>3 
        } == [1]
        
    }
    
//    @Test
    void testPerformance() {
        double [][] values 
        Random r = new Random()
        
        int size = 10000
        Matrix m 
        int count = 0
        // Recorded at about 651 ms 19/5/2014
        assert Utils.time("Initialize") {
            m = new Matrix(size,size).transform { ++count; r.nextGaussian() }
        } < 30000
    
        assert count == size*size
        
        // Recorded at about 263 ms 19/5/2014
        assert Utils.time("transform") {
            Matrix n = m.transform { x -> x * 2 }
        } < 20000
    
        assert Utils.time("mean by column") {
            int i = 0
            def means = m.columns.collect {
                Stats.mean(it)
            }
            println "First 10 means are: " + means[0..10]
        } < 50000
    }
    
    @Test
    void testNonNumericColumns() {
        Matrix m = new Matrix([[2,5,3,3,4], [5,6,7,2,4]])
        m.@names = ["legs","toes","feet","ears","eyes"]
        m.animal = ["frog","dog"]
        
//        m.grep { animal == "frog" && legs > 2 }
        assert m.grep { animal == "frog" }.rowDimension == 1
        
        Matrix n = m.transformRows {
            if(animal == "dog")
                [1d,1d]
            else 
                [2d,2d]
        }
        assert n[0][0] == 2
        assert n[0][1] == 2
        assert n[1][0] == 1
        assert n[1][1] == 1
        
        
        assert m.which { animal == "frog" } == [0]
        
        def animals = []
        m.eachRow { animals.add(animal) }
        
        assert animals == ["frog", "dog"]
		
		assert m.grep { legs > 3 }.animal == ["dog"]
		
    }
    
    @Test
    void testSaveNonNumeric() {
        Matrix m = new Matrix([[2,5,3,3,4], [5,6,7,2,4]])
        m.@names = ["legs","toes","feet","ears","eyes"]
        m.animal = ["frog","dog"]
        m.ages = [2,7]
        
        m.save("testSaveNonNumeric.tsv")
        
        println new File("testSaveNonNumeric.tsv").text
        
        Matrix m2 = Matrix.load("testSaveNonNumeric.tsv")
        
        println m2.toString()
        
        println m2.legs
        
        assert m2.legs.find { Math.abs(it - 2) < 0.01 }
        assert m2.legs.find { Math.abs(it - 5) < 0.01 }
        
        assert m2.animal == ["frog","dog"]
        assert "legs" in m2.@names
        assert "eyes" in m2.@names
        
        new File("testSaveNonNumeric.tsv").delete()
    }
    
    @Test
    void testSaveCustomType() {
        Matrix m = new Matrix([[2,5,3], [5,6,7]])
        m.@names = ["legs","toes","feet"]
        m.date = [new Date(),new Date(System.currentTimeMillis()-24*3600*1000)]
        
        m.save("testSaveDate.tsv")
        
        println new File("testSaveDate.tsv").text
        
        Matrix m2 = Matrix.load("testSaveDate.tsv")
        
        println m2
        
        assert m2.date[0] instanceof Date
         
    }
    
    @Test
    void testInitFromMap() {
        Matrix m = new Matrix(
            frog: [2,4,5],
            tree: [8,2,7]
            )
        
        assert m[2][1] == 7
        
        assert m.frog[1] == 4
    }
    
    @Test
    void testMarkdown() {
        Matrix m = new Matrix(
            frog: [2,4,5],
            tree: [8,2,7]
        )
        m.cow = ["maisy is a really good cow. I think she is great","daisy","doo"]
        
        StringWriter sw = new StringWriter()
        m.toMarkdown(sw)
        println sw.toString()
  
    }
    
    @Test
    void testSerialize() {
        Matrix m = new Matrix(
            frog: [2,4,5],
            tree: [8,2,7]
        )
        m.cow = ["maisy","daisy","doo"]
        
        println m[1][0] 
        
        ByteArrayOutputStream baos
        def oos = new ObjectOutputStream(baos=new ByteArrayOutputStream())
        oos.writeObject(m)
        oos.close()
        
        new ByteArrayInputStream(baos.toByteArray()).withObjectInputStream { ois ->
            Matrix m2 = ois.readObject()
            assert m2.cow[2] == "doo"
            assert m2[1][0] == 4
            
        }
    }
}
