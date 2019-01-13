package graxxia

import static org.junit.Assert.*
import static Math.abs

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics
import org.junit.Test

class RollingStatsTest {

    @Test
    public void test() {
        DescriptiveStatistics s = new DescriptiveStatistics(10)
        RollingStats rs = new RollingStats(10)
        
        def values = [1,4,3,2,1,7,6,12,3] * 100
        values.each { 
            s.addValue(it)
            rs.addValue(it)
        }
        
        println "mean: $s.mean vs $rs.mean"
        println "var: $s.variance vs $rs.variance"
        println "sd: $s.standardDeviation vs $rs.standardDeviation"
        
        assert abs(s.mean - rs.mean) < 0.001
        assert abs(s.standardDeviation - rs.standardDeviation) < 0.001
    }

}
