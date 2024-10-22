/*
 *  Graxxia - Groovy Maths Utililities
 *
 *  Copyright (C) 2014 Simon Sadedin, ssadedin<at>gmail.com and contributors.
 *
 *  This file is licensed under the Apache Software License Version 2.0.
 *  For the avoidance of doubt, it may be alternatively licensed under GPLv2.0
 *  and GPLv3.0. Please see LICENSE.txt in your distribution directory for
 *  further details.
 */
package graxxia

import com.twosigma.beakerx.chart.Color
import com.twosigma.beakerx.chart.xychart.Plot
import com.twosigma.beakerx.chart.xychart.plotitem.Bars
import com.twosigma.beakerx.chart.xychart.plotitem.Points
import com.twosigma.beakerx.chart.xychart.plotitem.Stems
import com.twosigma.beakerx.chart.xychart.plotitem.Text

/**
 * Implements a standard BoxPlot using BeakerX widget BarPlot features
 * 
 * To use, pass it a set of iterables containing numbers to plot:
 * 
 * <pre>
 * BoxPlot.of([
 *   [1,2,3,4,5,6],
 *   [2,3,4,5,6,7]
 * ])
 * </pre>
 * 
 * This returns you the {@link com.twosigma.beakerx.chart.xychart.Plot} object
 * that you can then configure. For convenience, attributes can be passed as named
 * args to the initial function, and an additional <code>labels</code> argument may 
 * be provided to name the categories:
 * 
 * <pre>
 * BoxPlot.of(
 *   title: 'Some simple numbers', xLabel: 'Type of Number', 
 *   labels: ['Smaller numbers', 'Bigger numbers']
 *   [
 *       [1,2,3,4,5,6],
 *       [3,4,5,6,7,8]
 *   ]
 * )
 * </pre>
 * 
 * @author simon.sadedin
 */
class BoxPlot {
    
    static by(Map props = [:], Matrix m, String key, String value) {
        Map<Object,Matrix> groups = m.groupBy {
            delegate[key]
        }
        return of(props, groups.collect { it.value[value] })
    }

    static of(Map props = [:], Iterable<Iterable<Number>> numbers) {
            List<Stats> allStats = numbers.collect { Stats.from(it)}
            
            List labels = props.remove('labels')
            
            List tooltips = props.remove('toolTip')
            
            Plot p = new Plot(xTickLabelsVisible: false, *:props)
            
            Color color = new Color(java.awt.Color.decode(BeakerX.plotColors[0]).RGB)

            def upper = allStats.collect { it.getPercentile(75) }
            def lower = allStats.collect { it.getPercentile(25) }
            def inter = [upper,lower].transpose().collect { it[1] - it[0]}
            def upperOutlierThresholds = [lower,upper].transpose().collect { it[1] + 1.5* (it[1] - it[0]) }
            def lowerOutlierThresholds = [lower,upper].transpose().collect { it[0] - 1.5* (it[1] - it[0]) }
            
            
            def upperOutliers = [numbers,upperOutlierThresholds].transpose().collect { values, outlierThreshold -> values.grep { it > outlierThreshold } }
            def lowerOutliers = [numbers,lowerOutlierThresholds].transpose().collect { values, outlierThreshold -> values.grep { it < outlierThreshold } }
            
            def max = [allStats, upperOutlierThresholds, upperOutliers].transpose().collect { stats, upperVal, outliers ->   if(outliers) upperVal else stats.max }
            def min = [allStats, lowerOutlierThresholds, lowerOutliers].transpose().collect { stats, lowerVal, outliers ->   if(outliers) lowerVal else stats.min }
             
            p.yLowerMargin = 0.2
            p.yUpperMargin = 0.2
            
            double barWidth = 0.5
           
            def s1 = new Stems(y:lower, color: color, width: 4, base: min)
            def b = new Bars(y: upper, color: color, width: barWidth, base: lower)
            def s2 = new Stems(y:max, color: color, width: 4, base: upper)
             
            // p << XYStacker.stack([s1,b,s2])
            
            p << s1 << b << s2
            
            Random rand = new Random(0)
            
            Color outlierColor = new Color(new java.awt.Color(color.RGB).brighter().RGB)
            
            // Draw outliers
            [max,upperOutliers].transpose().eachWithIndex { maxAndCol, i ->
                def (maxValue,col) = maxAndCol
                def outliers = col
                if(outliers)
                    p << new Points(x: outliers.collect { y -> i + (rand.nextDouble()-0.5)*0.1 }, y: outliers, color:outlierColor)
            }
            
            [min,lowerOutliers].transpose().eachWithIndex { minAndCol, i ->
                def (minValue,col) = minAndCol
                def outliers = col
                if(outliers)
                    p << new Points(x: outliers.collect { y -> i + (rand.nextDouble()-0.5)*0.1 }, y: outliers, color: outlierColor)
            }
            
            def range = max.max() - min.min()
            
            allStats.eachWithIndex { stats, i ->
                def medianBase = stats.getPercentile(48)
                Map medianProps = [x: [i], base: medianBase, y: [medianBase+range*0.03], width: barWidth, color: outlierColor]
                if(tooltips) {
                    medianProps.toolTip = [tooltips[i]]
                }
                p << new Bars(medianProps)
            }
            
            if(labels) {
                labels.eachWithIndex { label, i ->
                    p << new Text(x: i-0.1, y: max.max() + range*0.25, text: label, showPointer: false, size: 20)
                }
            }
            return p
    }
}
