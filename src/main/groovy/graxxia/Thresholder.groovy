package graxxia

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.apache.commons.math3.stat.descriptive.SummaryStatistics

@CompileStatic
enum ThresholdState {
    INACTIVE, ACTIVE
}

@CompileStatic
class ThresholdRange {
    IntRange range
    Object value
    Stats stats
    
    String toString() {
        if(value.is(null))
            range?.toString()
        else
            range?.toString() + "[$value]"
    }
}

interface ThresholdCondition {
    Object include(double value, Object additional)
}

/**
 * Finds runs of values in a stream of numbers that satisfy a condition, specified as a Closure
 * <p>
 * The closure can return a boolean, in which case the boolean result decides if each value is in or
 * out. If a non-boolean result is returned, the value is stored and is available on the resulting
 * {@link ThresholdRange} object.
 * <p>
 * Statistics are captured for values that are classified as in the desired set. This allows determination
 * of the number and range of values that triggered the condition, etc.
 * 
 * @author Simon Sadedin
 */
@CompileStatic
class Thresholder {
    
    ThresholdState state = ThresholdState.INACTIVE
    
    Closure<Object> condition
    
    ThresholdCondition conditionInterface
    
    private List<ThresholdRange> ranges = []
    
    int start = 0
    
    int index
    
    Object activeValue
    
    Stats stats = null
    
    Boolean twoArg = null
    
    Thresholder threshold(@ClosureParams(value=SimpleType,options='double') Closure<Object> c) {
        this.condition = c
        return this
    }
    
    Thresholder threshold(ThresholdCondition c) {
        this.conditionInterface = c
        return this
    }
    
    void leftShift(List<Double> values) {
        update(values)
    }
    
     void leftShift(double value) {
        update(value)
    }
    
    void update(Iterable<Double> values) {
        for(double d : values) {
            update(d)
        }
    }
    
    void update(double value) {
        
        def result
        
        if(this.conditionInterface != null) {
            result = this.conditionInterface.include(value, activeValue)
        }
        else {
            
            if(twoArg.is(null)) {
                twoArg = this.condition.maximumNumberOfParameters == 2
            }
            
            result = twoArg ? condition(value, activeValue) : condition(value)
        }
        
        if((!result.is(null) && (result != false))) { 
            if(state == ThresholdState.INACTIVE) {
                start = index
                state = ThresholdState.ACTIVE
                stats = new Stats()
            }
            if(result != true)
                activeValue = result
            stats << value
        }
        else
        if((result == false) && (state == ThresholdState.ACTIVE)) {
            endRange()
        }
        ++index
    }
    
    void endRange() {
        ranges.add(new ThresholdRange(range:start..index, value: activeValue, stats: stats))
        state = ThresholdState.INACTIVE
        start = -1
    }
    
    List<ThresholdRange> getRanges() {
        if(state == ThresholdState.ACTIVE) {
            endRange()
        }
        return this.@ranges
    }
}
