package graxxia

import org.apache.commons.math3.stat.descriptive.SummaryStatistics

import groovy.transform.CompileStatic
import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType



@CompileStatic
enum ThresholdState {
    INACTIVE, ACTIVE
}

@CompileStatic
class ThresholdRange extends IntRange {
    
    public ThresholdRange(int from, int to, Object value, SummaryStatistics stats, int maxIndex, int minIndex) {
        super(from, to);
        this.value = value;
        this.stats = stats;
        this.maxIndex = maxIndex;
        this.minIndex = minIndex;
    }
    Object value
    SummaryStatistics stats
    
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

interface ThresholdCondition<T> {
    Object include(final T value, final Object additional)
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
 * <p>Example:
 * <pre>
 *    Thresholder t = new Thresholder().threshold {
 *        it > 5
 *    }
 *    t << [1,1,1,2,2,6,7,9,4,2,1,9,8]
 * </pre>
 *
 * 
 * @author Simon Sadedin
 */
@CompileStatic
class Thresholder<T> extends Expando {
    
    ThresholdState state = ThresholdState.INACTIVE
    
    Closure<Object> condition
    
    Closure<ThresholdRange> emit
    
    Closure updater
    
    Closure<ThresholdRange> init
    
    ThresholdCondition<T> conditionInterface
    
    private List<ThresholdRange> ranges = []
    
    boolean trackStatistics = true
    
    int start = 0
    
    int index
    
    Object activeValue
    
    /*
     * Running values updated within active regions
     */
    int maxIndex
    int minIndex
    double currentMax = Double.MIN_VALUE
    double currentMin = Double.MAX_VALUE
    
    SummaryStatistics stats = null
    
    Boolean twoArg = null
    
    Thresholder threshold(@ClosureParams(value=SimpleType,options='double') Closure c) {
        this.condition = c
        return this
    }
    
    Thresholder threshold(ThresholdCondition<T> c) {
        this.conditionInterface = c
        return this
    }
    
    Thresholder initWith(@ClosureParams(value=SimpleType, options=['double','Object']) Closure c) {
        this.init = c
        return this
    }
    
    Thresholder updateWith(Closure c) {
        this.updater = c
        return this
    }
     
    Thresholder andThen(@ClosureParams(value=SimpleType, options=['graxxia.ThresholdRange']) Closure c) {
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
    
    void update(final int index, final T value) {
        if(index != this.index)
            checkEnd()
        this.index = index    
        update(value)
    }
    
    void update(final T value) {
        
        def result
        
        if(this.conditionInterface != null) {
            result = this.conditionInterface.include(value, activeValue)
        }
        else {
            
            if(twoArg.is(null)) {
                twoArg = this.condition.maximumNumberOfParameters == 2
            }
            
            result = twoArg ? condition.call(value, activeValue) : condition.call(value)
        }
        
        if((!result.is(null) && (result != false))) { 
            if(state == ThresholdState.INACTIVE) {
                start = index
                state = ThresholdState.ACTIVE
                stats = trackStatistics ? new SummaryStatistics() : null
                
                currentMax = Double.MIN_VALUE
                currentMin = Double.MAX_VALUE
                
                minIndex = index
                maxIndex = index
                if(!init.is(null)) {
                    this.activeValue = init.call(value, activeValue)
                }
            }
            
            if(result != true)
                activeValue = result
                
            if(!this.updater.is(null)) {
                this.updater.call(this.activeValue, value)
            }
                
            if(trackStatistics && value instanceof Number) {
                Number numericValue = (Number) value
                if(numericValue > currentMax) {
                    maxIndex = index
                    currentMax = (double)numericValue
                }
                if(numericValue < currentMin) {
                    minIndex = index
                    currentMin = (double)numericValue
                }

                if(trackStatistics)
                    stats.addValue((double)value)
            }
                
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
        this.activeValue = null
    }
    
    List<ThresholdRange> getRanges() {
        if(state == ThresholdState.ACTIVE) {
            endRange()
        }
        return this.@ranges
    }
}
