import static org.junit.Assert.*;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.junit.Test;

import graxxia.*

class IntegerStatsTest {

    @Test
    public void testCoveragePercentile() {
        
        def tests = [
            [2,5,2,3,2,1,4,3,2,2,2],
            [20] * 10 + [30]*15,
            [20] * 15 + [30]*10,
            [20] * 10 + [30]*10
        ]
        
        for(values in tests) {
        
          def c = new IntegerStats(1000)
          def d = new DescriptiveStatistics()
          
          values.each { c.addValue(it); d.addValue(it) }
          
          println c.getPercentile(50)
          println d.getPercentile(50).toInteger()
          
         assert c.getPercentile(50) == d.getPercentile(50).toInteger()
              
        }
    }
    
    @Test
    void testBreakTie() {
        def values = [20] * 10 + [30]*10
        def c = new IntegerStats(1000)
        def d = new DescriptiveStatistics()
        values.each { c.addValue(it); d.addValue(it) }
        assert c.getPercentile(50) == d.getPercentile(50).toInteger()
    }
    
    @Test
    void testBreakTieNoLarger() {
        def values = [30]*10
        def c = new IntegerStats(100)
        def d = new DescriptiveStatistics()
        values.each { c.addValue(it); d.addValue(it) }
        assert c.getPercentile(50) == 30
    }
    
    @Test
    void testSave() {
        def values = [30]*10 + [20]*5 + [5] * 10
        def c = new IntegerStats(100)
        values.each { c.addValue(it) }
        
        StringWriter sw = new StringWriter()
        c.save(sw)
        
        println "Output:\n" + sw.toString()
        println "*" * 40
    } 
}
