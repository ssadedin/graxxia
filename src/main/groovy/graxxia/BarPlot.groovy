package graxxia

import java.awt.Color;

class BarPlot extends Drawable {
    
    Iterable labels
    
    Iterable heights
    
    BarPlot(Map props=[:], Iterable labels, Iterable heights) {
        this.labels = labels
        this.heights = heights   
    }

    @Override
    public void draw(Drawing d) {
        
        int numLabels = labels.count { 1 }
        double barWidth = (d.maxX[-1] - d.minX[0])  / numLabels
        def lefts = (0..<numLabels).collect { it * barWidth }
        def rights = (1..numLabels).collect { it * barWidth }
        
        d.g.paint = Color.GREEN
        [lefts,rights,heights].transpose().each { bar ->
            d.rect(bar[0], 0, bar[2], bar[1])
        }
    }
}
