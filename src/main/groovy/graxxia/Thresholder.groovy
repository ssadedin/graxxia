package graxxia

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType



@CompileStatic
enum ThresholdState {
    INACTIVE, ACTIVE
}

@CompileStatic
class ThresholdRange extends IntRange {
    
    public ThresholdRange(int from, int to, Object value, Stats stats, int maxIndex, int minIndex) {
        super(from, to);
        this.value = value;
        this.stats = stats;
        this.maxIndex = maxIndex;
        this.minIndex = minIndex;
    }
    Object value
    Stats stats
    
    /**
     * The position of the maximum value during this range's activation
     */
    int maxIndex
    
    /**
     * The position of the minimum value during this range's activation
     */
    int minIndex
    
    @Override
    String toString() {
        if(value.is(null))
            "${from}..${to}"
        else
            "${from}..${to}($value)"
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
class Thresholder extends Expando {
    
    ThresholdState state = ThresholdState.INACTIVE
    
    Closure<Object> condition
    
    Closure<ThresholdRange> emit
    
    ThresholdCondition conditionInterface
    
    private List<ThresholdRange> ranges = []
    
    int start = 0
    
    int index
    
    Object activeValue
    
    /*
     * Running values updated within active regions
     */
    int maxIndex
    int minIndex
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
    
    Thresholder andThen(Closure<ThresholdRange> c) {
        this.emit = c
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
    
    void update(final int index, final double value) {
        if(index != this.index)
            checkEnd()
        this.index = index    
        update(value)
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
                minIndex = index
                maxIndex = index
            }
            if(result != true)
                activeValue = result
            if(value > stats.max)
                maxIndex = index
            if(value < stats.min)
                minIndex = index
                
            stats << value
        }
        else
        if(result == false) { 
            checkEnd()
        }
        ++index
    }

    private void checkEnd() {
        if(state == ThresholdState.ACTIVE) {
            endRange()
        }
    }
    
    void endRange() {
        ThresholdRange newRange = new ThresholdRange(start,index, activeValue, stats, maxIndex, minIndex)
        ranges.add(newRange)
        state = ThresholdState.INACTIVE
        start = -1
        if(this.emit != null) {
            this.emit.call(newRange)
        }
    }
    
    List<ThresholdRange> getRanges() {
        if(state == ThresholdState.ACTIVE) {
            endRange()
        }
        return this.@ranges
    }
}
