import static org.junit.Assert.*;

import graxxia.BeakerRunner
import org.junit.Test

class BeakerRunnerTest {
    @Test
    void testGroovyWithImports() {
        
        def bkr = new BeakerRunner("/Users/simon/work/groovy-ngs-utils/test.bkr", foo:"bar") 
        
        bkr.includeBeakerImports = true
        
        def result = bkr.run()
        
        println "Result is: " + result
    }
    
    @org.junit.Test
    void testJavaScript() {
        def bkr = new BeakerRunner("/Users/simon/work/graxxia/test_js.bkr", foo:"bar") 
        bkr.verbose = true
        
        def result = bkr.run()
        
        println "Result = " + result
    }
}
