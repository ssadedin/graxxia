import static org.junit.Assert.assertThrows

import org.junit.Test;

import graxxia.*
import smile.classification.RandomForest
import smile.data.DataFrame
import smile.data.formula.Formula
import smile.data.type.StructField
import smile.data.type.StructType

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
        def c = m[][2]
        
        assert m[][2][3] == 6d
    }
    
//    @Test
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
        Matrix m = new Matrix([
            [2,5,3,3,4], 
            [5,6,7,2,4]
        ])
        m.names = ["foo","bar"]
        
        println "Second column is " + m.getColumns(["bar"]) 
        
        assert m.getColumns(["bar"])[0] == [5d,6d]
        
        assert m.bar == [5d,6d]
        
        m.names = ((1..2).collect { "S$it" })
        
        println(m.S1)

        assert m.S1 == [2d,5d]

        Matrix m2 = new Matrix([
            [2,5,3,3,4], 
            [5,6,7,2,4]
        ], ["foo","bar"])
        
        assert m2.foo == [2d,5d]
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
        
        Matrix m4 = new Matrix([[2,5,3,3,4], [5,6,7,2,4]])
        m4.save("test2.tsv")
        
        Matrix m5 = Matrix.load('test2.tsv', columnNames:(1..5).collect { "S$it" }, readFirstLine: true)
        assert m5.S1 == [2d,5d]
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
        
        m = Matrix.fromMap([hello:[1,2,3], world:[3,4,5]])
        
        assert m[0][0] == 1
        
    }
    
    @Test
    void testInitFromMapNonNumeric() {
        
        Matrix gapMetrics = new Matrix(
            metric: ["NewGapSize","RemovedGapSize","CaptureSize","PercTargetDiff","NetGapChange","NetGapChangePerc"],
            value: [1,2,3,4,5,6]
        )
       
        assert gapMetrics[0][0] == 1
        assert gapMetrics.metric[0] == "NewGapSize"
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
    
    @Test
    void testGroupBy() {
       Matrix m = new Matrix(
            [
             [2,4], // brown
             [8,2], // white
             [4,5], // white
             [3,6], // brown
             [7,2]  // black
            ]
        )
        m.@names = ["age","weight"]
        m.color = ["brown","white","white","brown", "black"]
          
        def grouped = m.groupBy { color }
        
        assert grouped.brown[0][0] == 2
        assert grouped.brown[1][0] == 3
        assert grouped.brown[1][1] == 6
        assert grouped.black.rowDimension == 1
        
        assert grouped.black.age == [7]
        assert grouped.black.color[0] == "black"
    }
    
    @Test
    void testCountBy() {
       Matrix m = new Matrix(
            [
             [2,4], // brown
             [8,2], // white
             [4,5], // white
             [3,6], // brown
             [7,2]  // black
            ]
        )
        m.@names = ["age","weight"]
        m.color = ["brown","white","white","brown", "black"]
          
        def counts = m.countBy { color }
       
        assert counts.white == 2
        assert counts.brown == 2
        assert counts.black == 1
    } 
    
    @Test
    void testColumnSubset() {
        def m = new Matrix(x1: [1,2,3,4,5], x2: [2,4,6,8,10], x3:[7,6,5,4,3])
        
        def m2c = m[]
        
        assert m2c.getClass().name == "graxxia.MatrixColumnList"
       
        def m2 = m[][1..-1]
        
        assert m2[0][0] == 2
        
        assert m2.@names == ["x2","x3"]
        
        println "Matrix after columns subset = " + m2
    }
    
    @Test
    void testCollect() {
        def m = new Matrix(x1: [1,2,3,4,5], x2: [2,4,6,8,10], x3:[7,6,5,4,3])
        
        assert m.collect { x1 * x2 } == [2.0d,  8.0d, 18.0d, 32.0d, 50.0d]
        
    }
    
    @Test
    void testPartition() {
        
        def lm = [
               [foo: 1, bar:2], // row 0
               [foo: 4, bar:2], // row 1
               [foo: 8, bar:1], // row 2 // transition to true
               [foo: 3, bar:9], // row 3 // transition to false
               [foo: 3, bar:9], // row 4
               [foo: 3, bar:9], // row 5
               [foo: 9, bar:9], // row 6
               [foo: 7, bar:9], // row 7
        ]

        Matrix m = Matrix.fromListMap(lm)
        
        def result = m.partition {
            foo > 5
        }
        
        assert result.size() == 2
        
        assert result[true].size() == 2
        assert result[false].size() == 2
        
        assert result[false][1].from == 3
        assert result[false][1].to == 5
        
        assert result[true][0].from == 2
        assert result[true][0].to == 2
 
        
    }
    
    
    @Test
    void testPartitionMultiKey() {
        
        def lm = [
               [chr: 'chr1', pos: 0, cov: 1, bar:2], // row 0
               [chr: 'chr1', pos: 1, cov: 4, bar:2], // row 1
               [chr: 'chr1', pos: 2, cov: 8, bar:1], // row 2 // transition to true
               [chr: 'chr1', pos: 3, cov: 3, bar:9], // row 3 // transition to false
               [chr: 'chr1', pos: 4, cov: 3, bar:9], // row 4
               [chr: 'chr2', pos: 0, cov: 3, bar:9], // row 5 // transition to [chr2,false]
               [chr: 'chr2', pos: 1, cov: 3, bar:9], // row 6
               [chr: 'chr2', pos: 2, cov: 9, bar:9], // row 7
               [chr: 'chr2', pos: 3, cov: 7, bar:9], // row 8
        ]

        Matrix m = Matrix.fromListMap(lm)
        
        def result = m.partition {
            [chr, cov > 5]
        }
        
        assert result[['chr1',true]].size() == 1
        assert result[['chr1',false]].size() == 2
        assert result[['chr1',false]][1].to == 4

        assert result[['chr2',false]].size() == 1
        assert result[['chr2',false]][0].from == 5
    }
    
    @Test
    void testPartitionExample() {
        Matrix m = Matrix.fromListMap([
                [sun: 'sunny', temp: 20],
                [sun: 'sunny', temp: 20],
                [sun: 'cloudy', temp: 15],
                [sun: 'cloudy', temp: 15],
                [sun: 'sunny', temp: 14],
                [sun: 'sunny', temp: 12],
                [sun: 'sunny', temp: 20],
        ])
        
        Map result = m.partition { [sun, temp>15] }
        
        println "The times when it was sunny but temperature <= 15 were: " + result[['sunny',false]]

    }
    
    @Test
    void testFromListMap() {
        
        def lm = [
               [foo: 1, bar:2, dog: "fido"],
               [foo: 8, bar:1, dog: "biffo"],
               [foo: 3, bar:9, dog: "pup"],
            ]
        
        Matrix m = Matrix.fromListMap(lm)
        
        println m
        
        assert m.foo[0] == 1
        assert m.bar[1] == 1
        assert m.dog[2] == "pup"
    }
    
    @Test
    void testMax() {
        def m = new Matrix(x1: [1,2,3,4,5], x2: [2,4,6,8,10], x3:[7,6,5,4,3])
        
        def mMax =  m.max { x1 + x2 }
        assert mMax.rowDimension == 1
        assert mMax[0] == [5,10,3]
    }
    
    @Test
    void testFind() {
        def m = new Matrix(x1: [1,2,3,4,5], x2: [2,4,6,8,10], x3:[7,6,5,4,3])
        
        def mFound =  m.find { x1 + x2 > 5 }
        assert mFound.rowDimension == 1
        assert mFound[0] == [2,4,6]
        
        println mFound.x1
    } 
    
    @Test
    void testFindAll() {
        def m = new Matrix(x1: [1,2,3,4,5], x2: [2,4,6,8,10], x3:[7,6,5,4,3])
        
        def mFound =  m.findAll { x1 + x2 > 7 }
        assert mFound.rowDimension == 3
        assert mFound[0] == [3,6,5]
        
        println mFound.x1
    }  
    
    @Test
    void testUnique() {
        def m = new Matrix(x1: [1,2,1,4,5], 
                           x2: [2,4,2,8,10], 
                           x3: [7,6,5,4,3])
        
        def u = m.unique { x1 + x2 }
        
        println "Result = " + u
        
        assert u.rowDimension == 4
        assert u[2] == [4,8,4]
    }
    
    @Test
    void testSort() {
        def m = new Matrix(x1: [1,2,1,4,5], x2: [2,4,2,8,10], x3:[7,6,5,4,3])
        
        def u = m.sort { x1 + x2 }
        
        assert u.rowDimension == 5
        assert u[0] == [1,2,7]
        assert u[1] == [1,2,5]
        assert u[2] == [2,4,6]
    } 
    
    /*
     * what is the right form for aggregate?
     */
    @Test
    void testAggregateBy() {
        def m = new Matrix(x1: [1,2,1,4,5,7], 
                           x2: [2,4,2,8,10,8], 
                           x3: [7,6,5,4,3,5])
        
        m.foo = ["joe","chris","bob", "chris","ted","chris"]
        m.pet = ["cat","dog","dog", "giraffe","dog","dog"]
       
        Matrix m4 = m.aggregateBy(person: { foo }, pet: { pet }) {
            mean Stats.mean(x1)
        }
        println "Aggregated by foo AND pet: " + m4
        
        assert m4.grep { person == 'chris' && pet == 'dog' }.mean == 4.5
        
        Matrix m5 = m.aggregateBy('foo','pet') {
            mean Stats.mean(x1)
        }
        println "Aggregated by foo AND pet: " + m5 
        
        assert m5.grep { foo == 'chris' && pet == 'dog' }.mean == 4.5
        
        Matrix m6 = m.aggregateBy('foo') {
            pets pet.unique().join(",")
            sum x1.sum()
        }
        
        println "Non-numeric aggregation: " + m6
        
        assert m6.grep { foo == 'chris' }.pets[0] == "dog,giraffe"
    }
    
     
    @Test
    void testAggregate() {
        def m = new Matrix(x1: [1,2,1,4,5], 
                           x2: [2,4,2,8,10], 
                           x3: [7,6,5,4,3])
        
        m.foo = ["joe","chris","bob", "chris","ted"]
        m.pet = ["cat","dog","dog", "giraffe","dog"]
        
        Matrix m2 = m.aggregate { 
            mean Stats.mean(x1)
            sd   Stats.from((x2)).standardDeviation
        }
        
        println "Aggregated matrix = " + m2
        
        Matrix m3 = m.aggregateBy(person:{ foo }) {
            mean Stats.mean(x1)
            sd   Stats.from(x2).standardDeviation
        }
    }
    
    @Test
    void testAsListMap() {
        def m = new Matrix(x1: [1,2,1,4,5], x2: [2,4,2,8,10], x3:[7,6,5,4,3])
        m.foo = ["joe","fred","bob", "chris","ted"]
        
        def map = m.rowAsMap(2)
        assert map.foo == "bob"
        assert map.x1 == 1
        assert map.x2 == 2
        assert map.x3 == 5
        
        map = m.listMapIterator()[2]
        assert map.foo == "bob"
        assert map.x1 == 1
        assert map.x2 == 2
        assert map.x3 == 5
  
    }
    
    @Test
    void testFromTSV() {
        
        new File('test.tsv').text = [
            ['name','bar','cat'],
            ['fred',1.0, 'megsy'],
            ['joe',2.0, 'abigail']
        ]*.join('\t').join('\n')
        
        TSV tsv = new TSV("test.tsv")
        Matrix m = new Matrix(tsv)
        
        assert m.rowDimension == 2
        assert m.name[1] == 'joe'
        assert m.bar[1] == 2.0
    }
    
//    @Test 
    void testRecycling() {
        def raw = new URL("https://data.cityofnewyork.us/api/views/ebb7-mvp5/rows.csv?accessType=DOWNLOAD").openStream().text
        def csv = { new CSV(new StringReader(raw)) }
        Matrix m = new Matrix(csv())
    }
    
    /*
     * Sadly, count using a closure is not implemented for arrays in groovy
    @Test
    void testCount() {
        Matrix m = new Matrix([[0,2,3],[4,0,0]])
        assert m.collect { counts -> println(counts); counts.count { it < 1 } } == [1,2]
    }
    */

    
    @Test
    void testGrepNumericWithNames() {
        Matrix m = new Matrix([
            [0,1,2,3],
            [4,5,6,7],
            [8,9,10,11]
        ])
        
        m.@names = ["frog","cat","dog","person"]
        
        println m
        
        println m.grep { cat > 1 }
    }
    
    @Test
    void testNoNumeric() {
        
        Matrix m = new Matrix([ 
            foo: ["cat","dog","tree","bird","orange"],
            type: ["animal","animal", "plant","animal","plant"]
        ])
        
        
        assert m.foo == ["cat","dog","tree","bird","orange"]
        
        
       def x = m.grep { type == "animal" }
       
       println "After grep for animal: " + x
        
        assert m.grep { type == "animal" }.foo == ["cat","dog","bird"]
        
        assert m.countBy { type } == [ "animal" : 3, "plant" : 2]
        
    }
    
    @Test
    void displayWide() {
        Matrix m = new Matrix([
            1..100,
            200..300
        ])
        
        m.@displayColumns = 10
        
        println m.toString()
    }
    
    @Test
    void subsetColumns() {
        Matrix m = new Matrix([
            1..100,
            201..300
        ])
        
       Matrix small = m[][1..5]
       
       assert small.columnDimension == 5
       assert small.rowDimension == 2
       
       println small
    }
    
    @Test
    void subsetColumnsNonContig() {
        Matrix m = new Matrix([
            1..100,
            201..300
        ])
        
       Matrix small = m[][[3,5,8,57]]
       
       assert small.columnDimension == 4
       assert small.rowDimension == 2
       
       println small
    }
    
    @Test
    void subsetColumnsOOB() {
        Matrix m = new Matrix([
            1..100,
            201..300
        ])
        
       assertThrows(IllegalArgumentException) {
           Matrix small = m[][[3,604,8,10]]
        }
    }
     
    @Test
    void initFromColumnArray() {
        Matrix foo = new Matrix(
            [
                [1,2,3,4],
                [5,6,7,8]
            ]
        )
        
        foo.@names = ['tim','fred','bob','paul']
        
        // Pass as array
        Matrix subset = new Matrix(foo.getColumns(['tim','paul']) as MatrixColumn[])
        println subset
        
        assert subset.@names == ['tim','paul']
        assert subset.getColumnDimension() == 2
        assert subset.getRowDimension() == 2
        assert subset[1][1] == 8.0d
    }
    
    @Test
    void initFromColumnIterable() {
        Matrix foo = new Matrix(
            [
                [1,2,3,4],
                [5,6,7,8]
            ]
        )
        
        foo.@names = ['tim','fred','bob','paul']
        
        // Pass as array
        Matrix subset = new Matrix(foo.getColumns(['tim','paul']))
        println subset
        
        assert subset.@names == ['tim','paul']
        assert subset.getColumnDimension() == 2
        assert subset.getRowDimension() == 2
        assert subset[1][1] == 8.0d
    }    
    
    @Test
    void initFromMixedRows() {
        
        List data = [
            ['chr1', 979993, 0, 114, 113],
            ['chr1', 979994, 0, 114, 113],
            ['chr1', 979995, 0, 113, 112],
            ['chr1', 979996, 0, 101, 100],
            ['chr1', 979997, 0, 99, 98],
            ['chr1', 979998, 0, 97, 96],
            ['chr1', 979999, 0, 97, 96],
            ['chr1', 980000, 0, 103, 102]
        ]
    
        Matrix m = new Matrix(data, ['chr', 'pos','coeffv', 'ref', 'eval'])
        println m
        
    }
    
    @Test
    void readFromMixed() {
        Matrix m = Matrix.load('src/test/data/test.cov.tsv', columnNames: ['chr','pos','s1','s2'])
        println m
    }
    
    @Test
    void readFromReader() {
        Matrix m = Matrix.load(new File('src/test/data/test.cov.tsv').newReader(), 
            columnNames: ['chr','pos','s1','s2'],
            columnTypes: [0:String]
            )
        println m
        assert m.pos[0] == 2160180
    } 
    
    @Test
    void readFromReaderWithHeaders() {
        File f = new File('src/test/data/test_headers.cov.tsv')
        
        f.withWriter { w ->
            w.println(['chr','pos','s1','s2'].join('\t'))
            w.println(new File('src/test/data/test.cov.tsv').text)
        }
        
        Matrix m = Matrix.load(f.newReader())
        println m
        assert Math.abs(m.pos[0] - 2160180) < 0.001
    }  
    
    @Test
    void readInconsitentNumericTypes() {
       Matrix m = Matrix.load('src/test/data/test.inconsistent.tsv', r:true) 
    }
    
    @Test
    void testGetRowCorrelations() {
        Matrix m = new Matrix([
            [2,3,4],
            [7,8,9],
            [6,5,4]
        ])
        
        Matrix c = m.rowCorrelations
        println c
        
        assert c[0][0] == 1
        assert c[2][0] == -1
        
        m
    }
    
    @Test
    void testConcat() {
        Matrix m1 = new Matrix([
            [2,3,4],
            [7,8,9],
            [6,5,4]
        ])
        
        m1.foo = ['a','b','c']
        
        Matrix m2 = new Matrix([
            [9,9,9],
            [7,7,7],
            [6,6,6]
        ])
        m2.foo = ['d','e','f']
        
        Matrix result = Matrix.concat([m1, m2])
        
        println result
        
        assert result.foo == ['a','b','c','d','e','f']
  }
  
  @Test
  void testReduce() {
      
      Matrix m = createRandomCorrelatedTestMatrix()
      
      Matrix r = m.reduce(2)
      
      println r
      
      Matrix b = m.getReducedBasis(2)
      println b.metadata.loadings
  }

  
//  @Test
//  void testSmile() {
//      Matrix m = createRandomCorrelatedTestMatrix(500)
//      m.@names = ['Foo','Bar','Cat','Dog']
//      
//      double [] response = m.collect { it[0] > 0.5 ? 1d : 0d }
//      
//      println response
//      
//      DataFrame df = DataFrame.of(m.dataRef, *m.@names)
//      
//      DataFrame responseDf = DataFrame.of([response] as double[][], "y")
//
//      Formula f = Formula.of("y", m.@names)
//      
////      RandomForest r = new RandomForest(m.attributes, m.data, response, 5, 1)
//
//      RandomForest r = RandomForest.fit(f, df)
//      
//      println "Error is: " + r.error()
//  }
  
  @Test
  void testDisplayPrecision() {
        Matrix m1 = new Matrix([
            [1/6,1/3,4],
            [7,8,9],
        ])      
        
        m1.@displayPrecision = 2
        
        println m1
  }
  
  @Test
  void testSaveBinary() {
      Matrix m = [
          foo: [1,2,3,4,5],
          bar: [6,7,8,9,10]
      ]
      
      m.stuff = ["cat","dog","tree","house","camel","dolphin"]
      
      println "Saving: \n" + m
      
      m.saveBinary("test.bin.gz")
      
      Matrix m2 = m.readBinary("test.bin.gz")
      
      println m2
  }
  
  @Test
  void testWindow() {
      
      Matrix m = new Matrix(
          (1..10).collect { [ it, it+1, it+2] as double[] },
          
      )
      m.@names = ['a','b','c']
      
      println(m)
      
      List results = m.window(0,3).collect { it }
      
      println "window 0 = " + results[0]
      
      assert results[0][0] == [1,2,3]
      assert results[0][1] == [2,3,4]
      assert results[0][2] == [3,4,5]
      
      assert results[0].size() == 3
      
      List means =    m.window(0,3).collect { 
              List values = it.col(1).collect { it }
              def mean = Stats.mean( it.col(1).collect { it })
              println mean
              return mean
          }
          
      println "Windowed mean of column 1 is: " + means
      
      assert (int)means[0] == (int)3
      assert (int)means[3] == (int)6
      
      means = m.window(0,3).collect { 
          def mean = Stats.mean( it.collect { sm ->
              sm[1] 
          })
          return mean
      } 
      println "Windowed mean of column 1 via direct access is: " + means
     
      assert (int)means[0] == (int)3
      assert (int)means[3] == (int)6
  }
  
  @Test
  void testWindowNamedColumnAccess() {
      
      Matrix m = new Matrix(
          (1..10).collect { [ it, it+1, it+2] as double[] },
          
      )
      m.@names = ['a','b','c']
      
      println(m)
      
      def means = m.window(0,3).collect { 
//          println(it.b.join(','))
          def mean = Stats.mean( it.b)
          return mean
     }       
     
     assert (int)means[0] == (int)3
     assert (int)means[3] == (int)6     
  }
  
  @Test
  void testReshape() {
        def m = new Matrix(4,2, [0d,1d,
                                 2d,3d,
                                 4d,5d,
                                 6d,7d])
       
       def m2 = m.reshape(1,8)
       
       assert m2.rowDimension == 1
       assert m2.columnDimension == 8
       
       assert m2[0][2] == 2d
       assert m2[0][6] == 6d
  }
  
  @Test
  void testNormaliseRows() {
        def m = new Matrix(4,2, [0d,1d,
                                 2d,3d,
                                 4d,5d,
                                 6d,7d])
      
        def n = m.normaliseRows()
        
        assert n[0][0]  == 0d
        assert n[3][0]  == (6d / (6.5d))

  }
  
  @Test
  void testNormaliseColumns() {
        def m = new Matrix(4,2, [0d,1d,
                                 2d,3d,
                                 4d,5d,
                                 6d,7d])
      
        def n = m.normaliseColumns()
        
        assert n[0][0]  == 0d
        assert n[3][0]  == 6d / 3d
        assert n[3][1]  == 7d / 4d // mean of second column
  }
  
  @Test
  void testMultipleColumnsByName() {
      
      Matrix m = new Matrix([
          foo: 1..20,
          bar: (2..40).step(2)
      ])
      
      List cols = ['foo','bar']
      
      Matrix m2 = m[][cols]
      
      assert m2[0] == m[0]
      m2.@names[1] == 'bar'
      
      Matrix m3 = m[]['foo','bar']
      
      assert m3[1] == m[1]
      
      Matrix m4 = m[]['foo']
      
      assert m4[1][0] == m[1][0]
      
      Matrix m5 = new Matrix(foo: [1,2,3,4], bar: [5,6,7,8])
      assert m5[]['foo'][0] == [1]
      assert m5[]['foo','bar'][0] == [1,5]
      assert m5[]['bar','foo'][0] == [5,1]

  }
  
  @Test
  void testCollectWithIndex() {
      def m = new Matrix(x1: [1,2,3,4,5], x2: [2,4,6,8,10], x3:[7,6,5,4,3])
        
//      def result = m.collect { x1 * x2 } 
//      
//      assert result == [2.0d,  8.0d, 18.0d, 32.0d, 50.0d]
//      
      int cat = 3
      
      println m.@names
      
      def index_result = m.collect { index * x1  * cat }
  }
  
  @Test
  void testVector() {
      Matrix m = new Matrix([a: [1,2,3,4], b:[5,6,7,8]])
      m.person = ['fred','sam','john','sue']
      m.age = [ 30i, 20i, 46i, 92i]
      
      println m
      
      def v1 = m.vector('a')
      
      assert v1.name() == 'a'
      assert v1.size() == 4
      assert v1.get(0) == 1d
      
      def v2 = m.vector('person')
      assert v2.name() == 'person'
      
      def v3 = m.vector('age')
      assert v3.name() == 'age'
      assert v3.getInt(3) == 92
      
//      DataFrame df = DataFrame.of(m.vector('a'), m.vector('person'))
//      
//      println df
  }
  
  @Test
  void testDataFrame() {
      Matrix m = new Matrix([a: [1,2,3,4], b:[5,6,7,8]])
      
      DataFrame df = m as DataFrame
      
      println df
      
      assert df.size() == 4
      assert df.ncols() == 2
      
      
      m.person = ['fred','sam','john','sue']
      m.age = [ 30i, 20i, 46i, 92i]
       
      df = m as DataFrame
      
      println df
      assert df.size() == 4
      assert df.ncols() == 4
      assert df.getString(2, 0) == "john"
   }
   
   @Test
   void testRandomForest() {
       
       Matrix m = createRandomCorrelatedTestMatrix(50)
       m.@names = ['A','B','C','D']
       m.Y = m.collect { A <  0.3 ? 1 : 0 }

       RandomForest rf = m.forest('Y', max_depth:2)
       
       println "Error = " + rf.error()
   }
   
   @Test
   void testRangeToEnd() {
      Matrix m = new Matrix(a: [1,2,3,4], b:[5,6,7,8])
      
      Matrix sub = m[1..-1]
      
      assert sub.a[0] == 2
      assert sub.rowDimension == 3
   }
   
   @Test
   void testFindIndexOf() {
      Matrix m = new Matrix(a: [1,2,3,4], b:[5,6,7,8])
      
      int result = m.findIndexOf { a == 3 }
      
      assert result == 2
      
      assert m.findIndexOf { b == 5 } == 0

      assert m.findIndexOf { b == 8 } == 3
      
   }
  
    
  private Matrix createRandomCorrelatedTestMatrix(int rows=10) {
        double[][] data = new double[rows][4]
        for(int i=0; i<rows; ++i) {
            double x = Math.random()
            double y = Math.random()
            data[i][0] = x
            data[i][1] = y
            data[i][2] = x*2 + Math.random()*0.01
            data[i][3] = y*2 + Math.random()*0.01
        }

        Matrix m = new Matrix(data)
        return m
  }
  
  
    
    
//  @Test
//  void testLoadBug() {
//     Matrix m = Matrix.load('src/test/data/test.headers_with_numeric.tsv')
//     println(m)
//  }
//  
////  @Test
//  void testLoadPerformance() {
//      Matrix m = Matrix.load('/Volumes/Space2/cnv/coeffv/covs.chr4.tsv.gz', columnNames: ['chr','pos','coeffv'] + (1..34)*.toString())
//      println m.toString()
//  }
//  
  
}
