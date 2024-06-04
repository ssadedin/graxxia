package graxxia

import static org.junit.Assert.*

import org.junit.Test

class BinnerTest {

    @Test
    public void testCreateBins() {
        
        def values =    [ 2,5,3,9,5,3,2,1,6,8,4,2,1,6,2,3,5 ]
        def responses = [ 1,8,0,7,7,2,0,3,9,7,2,1,0,8,1,2,9 ]
        
//        println values.sort()
        
        def binner = new Binner(3, 0, 10)
        
        def bins = binner.stats(values, responses)
        
        assert bins.size() == 3
        double bin0Mean = Stats.mean([values,responses].transpose().grep { it[0] <= 3 }*.getAt(1))
        assert Math.abs(bins[0].mean - bin0Mean) < 0.01
        assert bins[1].mean > 5 && bins[1].mean < 9
    }
    
    
    @Test
    void testInterpolator() {
        def values =    [ 2,5,3,9,5,3,2,1,6,8,4,2,1,6,2,3,5 ]
        def responses = [ 1,8,0,7,7,2,0,3,9,7,2,1,0,8,1,2,9 ]
        def binner = new Binner(3, 0, 10)
        
        def fn = binner.spline(values, responses)
        
        println fn.value(3.5)
 
    }
    
    @Test
    void testInterpolatorOutofRange() {
        def values =    [ 2,5,3,9,5,3,2,1,6,8,4,2,1,6,2,3,5 ]
        def responses = [ 1,8,0,7,7,2,0,3,9,7,2,1,0,8,1,2,9 ]
        def binner = new Binner(3, 0, 10)
        
        def fn = binner.spline(values, responses, startPoint:[0,0], endPoint:[20,20])
        
        assert fn.value(1.0) > 0.0d
        assert fn.value(19) > 15.0d
 
    }
    
    @Test
    void testGroupBy() {
        
        def objs = [
            [ foo: 1, bar: 'cat'],
            [ foo: 4, bar: 'tree'],
            [ foo: 2, bar: 'cat'],
            [ foo: 9, bar: 'house'],
            [ foo: 5, bar: 'tree'],
            [ foo: 7, bar: 'house'],
        ]

        def binner = new Binner(3, 0, 10)
        
        def groups = objs.groupBy {
            binner.bin(it.foo)
        }

        assert groups.size() == 3
        assert groups[0].size() == 2
        assert groups[1].size() == 2
        assert groups[2].size() == 2
        assert groups[0]*.bar.every { it == 'cat' }
    }
    
    @Test
    void testBinBy() {
        List<Map> objs = [
            [ foo: 1, bar: 'cat'],
            [ foo: 4, bar: 'tree'],
            [ foo: 2, bar: 'cat'],
            [ foo: 9, bar: 'house'],
            [ foo: 5, bar: 'tree'],
            [ foo: 7, bar: 'house'],
        ]

        def binner = new Binner(3, 0, 10)
         
        def binned = binner.binBy(objs, objs*.foo) {
            
            Stats.from(it*.foo).mean
        }
        
        println("Binned foos are: " + binned + "based on $binner")
    }
}
