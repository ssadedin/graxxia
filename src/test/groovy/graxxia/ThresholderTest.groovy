package graxxia

import static org.junit.Assert.*

import org.junit.Test

class ThresholderTest {

    @Test
    public void test() {
        Thresholder t = new Thresholder().threshold {
            it > 5
        }
        
        
        t << [1,1,1,2,2,6,7,9,4,2,1,9,8]
        
        println t.ranges
        assert t.ranges.size() == 2
        
        assert t.ranges[0].stats.max == 9
    }
    
    @Test
    void 'test threshold with value captures value'() {
        Thresholder t = new Thresholder().threshold { x, value ->
            x > 5 ? (value?:"") + "x" : false
        }
        t << [1,1,1,2,2,6,7,9,4,2,1,9,8]
        assert t.ranges.size() == 2
        
        assert t.ranges[0].value == "xxx"
        
    }
    
    @Test
    void 'test maxIndex and minIndex correctly set'() {
        Thresholder t = new Thresholder().threshold {
            it > 5
        }
        
        t << [1,1,1,2,2,6,7,9,4,2,1,9,8]
            //0 1 2 3 4 5 6 7 8 9 0 1 2 
        
        List<ThresholdRange> ranges = t.ranges
        assert ranges[0].maxIndex == 7 
        assert ranges[0].minIndex == 5
        assert ranges[1].maxIndex == 11
        assert ranges[1].minIndex == 12
    }
}
