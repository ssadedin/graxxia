import static org.junit.Assert.*;

import org.junit.Test;

import graxxia.*

class StatsTest {

    def x = new Stats()
    
    @Test
    public void test() {
        
        x << 4
        x << 7
        x << 8
        
        println x.mean
        
        println Stats.from(["cat","dog","treehouse","farm"]) { it.size() }
        
        println Stats.from([5,4,2,9])
    }
    
    @Test
    void testMean() {
        
        def values = [1, 5, 7, 8]
        Iterator i = values.iterator()
        
        println Stats.mean { 
           i.next() 
        }
        
        println Stats.mean(values)
        println Stats.mean(values.iterator())
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
    void testPercentile() {
        
        def values = [1, 5, 7, 8]
        Iterator i = values.iterator()
         println Stats.percentile(200) {
             i.next()
         }.getPercentile(50)
    }

}
