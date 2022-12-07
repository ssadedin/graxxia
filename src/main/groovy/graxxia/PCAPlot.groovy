package graxxia

import com.twosigma.beakerx.chart.Color
import com.twosigma.beakerx.chart.xychart.CombinedPlot
import com.twosigma.beakerx.chart.xychart.Plot
import com.twosigma.beakerx.chart.xychart.plotitem.Points
import com.twosigma.beakerx.chart.xychart.plotitem.Text
import com.twosigma.beakerx.jvm.object.GridOutputContainerLayoutManager
import com.twosigma.beakerx.jvm.object.OutputContainer

/**
 * Implements a PCA plot with coloring of points by class compatible with
 * BeakerX. To use this class, first use the {@link Matrix} class to perform
 * PCA using the {@link Matrix#reduce} method. Then you can pass the resulting
 * object to this the <code>from</code> method on this class to generate the plot.
 * <p> 
 * Note; the plot is designed to be displayed within a <a href='https://github.com/twosigma/beakerx'>BeakerX</a>
 * notebook (ie: Jupyter/JupyterLab).
 * 
 * Example:
 * <pre>
 * // Create some dummy data with an interesting outlier
 * def random = new Random()
 * def data = (1..6).collect {  int i=0; (1..10).collect { ++i; random.nextDouble() * (i == 4 ? 2 : 1) } }
 * 
 * // Reduce to 2 dimensions
 * def pca = new Matrix(data).reduce(2)
 * 
 * // Generate a plot
 * PCAPlot.from(pca)
 * </pre>
 * 
 * Some options are provided to allow customisation of the plot:
 * <ul>
 *   <li>toolTip : List of Strings to show when the user hovers mouse over or clicks on a point
 *   <li>color: List of Color objects to use to color the points
 * </ul>
 * 
 * @author simon.sadedin
 */
class PCAPlot extends Plot {
    
    static from(Map options = [:], Matrix reduced) {
        
        if(!reduced.@names || !reduced.@names.every { it.startsWith('PC') }) {
            throw new IllegalArgumentException("Please supply a matrix that was created using the Matrix.reduce function or has columns starting with PC<n>")
        }
        
        List<Integer> classes = options.classes ? options.classes : [0] * reduced.rowDimension
        
        List<String> toolTips

        List<String> colors = ['red','green','blue','orange','purple','gray', 'pink','magenta','maroon']
        
        CombinedPlot c = new CombinedPlot(initHeight:1600)
        
        def lg = new GridOutputContainerLayoutManager(2)
        OutputContainer og = new OutputContainer()
        og.layoutManager = lg
        
        (1..reduced.columnDimension).collate(2).collect {
            ['PC'+it[0], 'PC'+it[1]]
        }.each { pcs ->
            
            // If odd number then we can't plot both axes,
            // for now just return
            if(pcs[0] == null || pcs[1] == null)
                return
            
            def p = new Plot(title: "${pcs[0]} vs ${pcs[1]}", xLabel: pcs[0], yLabel: pcs[1])

            Points points = new Points(
                x:reduced[pcs[0]] as List, y: reduced[pcs[1]] as List,
                size: 7
            )
            
            if(options.color) {
                points.color = options.color
            }
            
            if(options.containsKey('toolTip')) {
                points.toolTip = options.toolTip
            }
            p << points

            if(options.containsKey('poi')) {
                options.poi.points.each { poi ->
                    p << new Text(x: delegate.getAt(pcs[0]), y:delegate.getAt(pcs[1]), text: delegate.getAt(poi.label) )
                }
            }

            og.addItem(p)
        }
        //c
        
        return og
    }
}
