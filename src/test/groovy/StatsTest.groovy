import static org.junit.Assert.*;

import org.junit.Test;

import graxxia.*

class StatsTest {

    def x = new Stats()
    
    @Test
    public void testConversion() {
        
        x << 4
        x << 7
        x << 8
        
        println x.mean
        
        assertEquals(6.333,x.mean, 0.05)
        
        def s = Stats.from(["cat","dog","treehouse","farm"]) { it.size() }
        println s
        assertEquals 4.75, s.mean, 0.05
        assertEquals 2.87, s.standardDeviation, 0.05
         
        println Stats.from([5,4,2,9])
        assertEquals 5.0, Stats.from([5,4,2,9]).mean, 0.05
    }
    
    @Test
    void testMean() {
        
        def values = [1, 5, 7, 8]
        Iterator i = values.iterator()
        
        def m = Stats.mean { 
           i.next() 
        }
        println m
        assertEquals 5.25, m, 0.05
        
        println Stats.mean(values)
        println Stats.mean(values.iterator())
        assertEquals 5.25d, Stats.mean(values.iterator()), 0.05d
    }
    
    @Test
    void testFromMatrix() {
        double [][] x = [
            [0, 2, 4],
            [1, 3, 5],
        ] as double[][]
        
        assert Stats.from(x).mean == 1.5
    }
    
    @Test
    void testIterable() {
        Iterable x = new Iterable() {
            int count = 0
            
            def values = [5,4,3,2] as double[]
            
            Iterator iterator() {
                return [ hasNext : { count < values.size() },
                         next : { values[count++] }
                       ] as Iterator
            }
        }
        
//        def s = Stats.from(x)
//        assert s.mean == 3.5
        
        // Filter the values
        assert Stats.from(x) { it > 3 }.mean == 4.5
        
    }
    
    @Test
    void testMidPercentile() {
        
        def values = [1, 5, 7, 8]
        Iterator i = values.iterator()
        def p50 = Stats.percentile(200) {
             i.next()
        }.getPercentile(50)
        
        assertEquals 6f, p50, 0.05
    }
}
