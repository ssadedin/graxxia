package graxxia
import static org.junit.Assert.*;

import org.junit.Test;


class TSVTest {

    String testTsv = 
        [ 
           ["foo","cat","5", "10.1"],
           ["bar","dog","9", "4.2"]
        ]*.join("\t").join("\n").trim().stripIndent()
            
    String testCsv = 
        [ 
           ["foo","cat","5", "10.4"],
           ["bar","dog","9", "4.2"]
        ]*.join(",").join("\n").trim().stripIndent()
        
     @Test
    public void test() {
        println "Parsing: $testTsv"
        for(line in new TSV(new StringReader(testTsv), columnNames: ["name","species", "age","weight"])) {
            println "Line = $line"
            println "$line.name is a $line.species and is $line.age years old, weighing $line.weight"
            
            println line.age.class.name
            
            assert line.age instanceof Integer
            assert line.weight instanceof Double
        }
    }
    
    @Test
    public void testGZipInput() {
        new TSV("tests/test.gz").each {
            println "Line: " + it
        }
    }
    
    @Test
    void testCSVFilter() {
        StringWriter s = new StringWriter()
        new CSV(new StringReader(testCsv), columnNames: ["name","species", "age","weight"], quote:true).filter(s) { line ->
            line.age > 5
        }
        println s.toString()
        
        String csv = s.toString();
        
        assert csv.contains(/"dog"/) : "CSV does not contain expected quoted string"
        assert !csv.contains(/cat/) : "CSV contains unexpected string"
    }
    
    
    
    @Test
    void testRaggedToListMap() {
        
        Reader ragged = toTsv([
            ["animal","legs","colour"],
            ["dog",4,"brown"],
            ["snake",0],
            ["duck",2,"white"]
        ])
        
        TSV tsv = new TSV(ragged)
        
        List<Map> result = tsv.toListMap()
        assert result[0].animal == "dog"
        assert result[1].colour == null
    }
    
    @Test
    void testNormalizeToListMap() {
        
        Reader ragged = toTsv([
            ["Animal","How Many  legs","colour"],
            ["dog",4,"brown"],
            ["snake",0],
            ["duck",2,"white"]
        ])
        
        TSV tsv = new TSV(ragged)
        
        def result = tsv.toListMap(true)
        println "=" * 100
        println result
        println "=" * 100
        
        assert result[0].animal == "dog"
        assert result[1].colour == null
        assert result.find { it.animal == "dog" }.how_many_legs == 4
        
    }
    
    
    Reader toTsv(List values) {
        new StringReader(values*.join("\t").join("\n").trim().stripIndent())
    }
}
