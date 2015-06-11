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

import groovy.transform.CompileStatic;

import java.awt.BasicStroke
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.apache.commons.math3.analysis.interpolation.*
import org.apache.commons.math3.analysis.polynomials.PolynomialSplineFunction;

import javax.imageio.*

/**
 * Drawing is a very simple graphics class that enables simple line and text drawings on a 
 * virtualised canvas that automatically maps mathematical coordinates to pixel based
 * coordinates.
 * 
 * @author simon
 */
class Drawing {
    
    static enum Coord {
        PIXELS,
        PLOT
    }
    
    BufferedImage  image = null;
    Graphics2D g = null
    String fileName = null
    
    List<String> operations = []
    
    boolean autoSave = true
    
    int xOffset = 0
    int yOffset = 0
    
    double minY = 0
    double maxY = 0
    
    List minX = []
    List maxX = []
    
    double xScale = 1.0d
    double yScale = 1.0d
    
    int height = -1
    int width = -1
    
    boolean log = false
    
    Font font = null
    
    FontMetrics fontMetrics = null
    
    /**
     * The default coordinate mode is to specify points in plot coordinates,
     * as opposed to pixels.
     */
    Coord coordType = Coord.PLOT
    
    static float [] dash1 = [5.0f] as float[];
    
    /**
     * The default line style used for dashed lines
     */
    BasicStroke DASHED =
        new BasicStroke(1.0f,
                        BasicStroke.CAP_BUTT,
                        BasicStroke.JOIN_MITER,
                        10.0f, dash1, 0.0f);
    
    /**
     * The default line style used for solid lines                    
     */
    BasicStroke SOLID = new BasicStroke(1.0f)                   
    
    /**
     * The default y position at which the next line of text will be drawn
     */
    double textY = 0
    
    /**
     * The default x position at which the next line of text will be drawn
     */
    double textX = 0
    
    /**
     * The height of the current font in drawing coordinates (not pixels)
     */
    double textHeight = 0
    
    int xMargin = 0
    
    int yMargin = 0
    
    public Drawing(String fileName, int width, int height, double minX, double minY, double maxX, double maxY) {
        this(fileName, width, height, [minX], minY, [maxX], maxY)
    }
    
    public Drawing(String fileName, int width, int height, List minX, double minY, List maxX, double maxY) {
        this.fileName = fileName
        this.minX = minX
        this.maxX = maxX
        this.minY = minY
        this.maxY = maxY
        this.height = height
        this.width = width
        this.init()
    }
    
    void setMargin(int x, int y) {
        this.xMargin = x
        this.yMargin = y
        this.init()
    }
    
    void init() {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.g = image.createGraphics()
        this.xScale = (width-2*xMargin) / ([maxX,minX].transpose().collect { it[0] - it[1] }.sum() )
        this.yScale = (height-2*yMargin) / (maxY - minY) // invert Y so it is like math plotting coordinates
        this.xOffset = -(minX.min()) 
        this.yOffset = -minY 

        g.setBackground(Color.WHITE)
        g.clearRect(0,0,width,height)
        color(0,0,0)
        
        g.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING,
            RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        
        setFont("SansSerif", Font.PLAIN, 12);
        textY = maxY 
    }
    
    public Drawing(String fileName, int width, int height) {
        this(fileName,width,height,0,0, 1.0d, 1.0d)
    }
    
    Drawing fontSize(float value) {
        this.setTextFont(this.font.deriveFont(value))
    }
    
    Drawing setFont(String name, int style, int size) {
        setTextFont(new Font(name, style,size))
    }
    
    Drawing setTextFont(Font f) {
        this.font = f
        g.setFont(this.font)
        this.fontMetrics = g.getFontMetrics()
        int fontHeight = this.fontMetrics.getHeight()
        
        this.textHeight = this.unTxY(fontHeight) - this.unTxY(0d)
        if(log)
            log.println "Text height = $textHeight"
        
        return this
    }
    
    /**
     * Adjust xScale and yScale to fit the given point inside 
     * the bounds
     */
    void rescale(double x1, double y1, double x2, double y2) {
        
    }
    
    Drawing bars(def x1, def x2, def y, List overText=null, List underText=null) {
        lines(x1,y,x2,y,bars:true,overText:overText, underText:underText)
    }
    
    Drawing bar(def x1, def x2, def y, String overText=null, String underText=null) {
        bars([x1], [x2], [y], [overText], [underText])
    }
    
    Drawing bar(IntRange range, def y, String overText=null, String underText=null) {
        bar(range.from, range.to, y, overText, underText)
    }
    
    
    PolynomialSplineFunction loess(Map options=[:], Iterable<Double> x, Iterable<Double> y) {
        LoessInterpolator interp = new LoessInterpolator()
        PolynomialSplineFunction fn = interp.interpolate(x as double[], y as double[])
        
        double minXValue = x.min()
        double maxXValue = x.max()
        
        // Interpolate to factor of 10
        double width = maxXValue - minXValue
        
        // A point every 5 pixels
        double xInterval = 5 / xScale
        double xInterp = minXValue
        List lineYValues = []
        List lineXValues = []
        List colors = []
        while(xInterp < maxXValue) {
            lineXValues << xInterp
            double yValue = fn.value(xInterp)
            lineYValues << yValue
            
            if(options.color != null)
                colors << options.color(xInterp, yValue)
            
            xInterp += xInterval
        }
        
        if(options.color != null) {
            options.color = colors;
        }
        
        lines(options, lineXValues[0..-2], lineYValues[0..-2], lineXValues[1..-1], lineYValues[1..-1])
        
        return fn
    }
      
    Drawing lines(Map options=[:], def x1, def y1, def x2, def y2) {
		if(x1 instanceof List ||x1 instanceof double[]) {
	        if(x1.size() != y1.size())
	            throw new IllegalArgumentException("Size of x1 (${x1.size()} different to size of y1 ${y1.size()}")
	            
	        if(x2.size() != y2.size())
	            throw new IllegalArgumentException("Size of x2 (${x2.size()} different to size of y2 ${y2.size()}")
		}
                
        x1.eachWithIndex { x, index -> 
            if(options.color) {
                if(options.color instanceof Closure)
                    color(options.color(x, y1[index], x2[index], y2[index]))
                else
                if(options.color instanceof List)
                    color(options.color[index])
                else
                    color(options.color)
            }
                
            def txed = txLine(x, y1[index], x2[index], y2[index]) 
            if(options.bars) {
                drawBar(txed, options.overText?options.overText[index]:null, options.underText?options.underText[index]:null)
                
                if(options.overText && options.overText[index]) {
                    // Draw text centered over bar with baseline 1 pixel above bar
                    int textWidth = fontMetrics.stringWidth(options.overText[index])
                    int middleX = txed[0] + (txed[2]-txed[0])/2
                    int textXPos = middleX - textWidth/2
                    g.drawString(options.overText[index],(float)textXPos, (float)(txed[1] - 4))
                }
                
                if(options.underText && options.underText[index]) {
                    // Draw text centered over bar with baseline 1 pixel above bar
                    int textWidth = fontMetrics.stringWidth(options.underText[index])
                    int middleX = txed[0] + (txed[2]-txed[0])/2
                    int textXPos = middleX - textWidth/2
                    g.drawString(options.underText[index],(float)textXPos, (float)(txed[1] + fontMetrics.height+1))
                } 
            }
        }
        if(autoSave)
            save()
        return this
    }
    
    int barHeightUp = 5
    
    int barHeightDown = 5
    
    private void drawBar(lineCoords,String overText=null, String underText=null) {
        g.drawLine((int)lineCoords[0],(int)lineCoords[1]-barHeightUp,(int)lineCoords[0],(int)lineCoords[1]+barHeightDown)
        g.drawLine((int)lineCoords[2],(int)lineCoords[3]-barHeightUp,(int)lineCoords[2],(int)lineCoords[3]+barHeightDown)
    }
    
    Drawing line(def x1, def y1, def x2, def y2) {
//        println "line ($x1,$y1) - ($x2,$y2)"
        txLine(x1,y1,x2,y2)
        if(autoSave)
            save()      
        return this
    }
    
    Drawing color(List rgb) {
        color(rgb[0],rgb[1], rgb[2])
    }
    
    Drawing color(String value) {
        Color colorVal = Color[value]
        g.setPaint(colorVal)
        return this
    }
    
    Drawing color(int red, int green, int blue) {
        g.setPaint(new Color(red,green,blue))
        return this
    }
    
    List<Double> txLine(double x1, double y1, double x2, double y2) {
        
        double x1Tx = txX(x1)
        double x2Tx = txX(x2)
        
        y1 = txY(y1)
        y2 = txY(y2)
        
        if(log)
            println "Drawing line ($x1-$x2) ($x1Tx,$y1) - ($x2Tx,$y2)"
        g.drawLine((int)x1Tx, (int)y1, (int)x2Tx, (int)y2)
        [x1Tx,y1,x2Tx,y2]
    }
    
    void drawRegions() {
        [minX,maxX].transpose().each { region ->
            line(region[0],minY,region[0],maxY)
            line(region[1],minY,region[1],maxY)
        }
    }
    
    void drawBorder() {
        g.drawRect(xMargin-1, yMargin-1, width-2*xMargin+2, height-2*yMargin+2)
    }
    
    Double txX(double x) {
        
        if(coordType == Coord.PIXELS)
            return x
        
        double startOffset = xMargin;
        int xInterval = 0
        while(x > maxX[xInterval]) {
            startOffset += (xScale * (maxX[xInterval] - minX[xInterval]))
            
            ++xInterval    
            
            if(xInterval >= maxX.size() -1)
                break
        }
        if(xInterval >= minX.size())
            xInterval = minX.size()-1
            
        Double result = startOffset + xScale *(Math.max(x - minX[xInterval],0))
        
//        println "$x => $result"
        return result
    }
    
    Double txY(double y) {
        if(coordType == Coord.PIXELS)
            return y
        (this.height - yMargin) - yScale * (yOffset + y) 
    }
    
    @CompileStatic
    double unTxY(double y) {
        (this.height - y) / yScale - yOffset
    }
    
    @CompileStatic
    double unTxX(double x) {
        x / xScale - xOffset
    }
    
    void save() {
        ImageIO.write(image, "png", new File(fileName));
    }
    
    void save(Closure c) {
        boolean oldAutoSave = this.autoSave
        this.autoSave = false
        c()
        save()
        this.autoSave = oldAutoSave
    }
    
    /**
     * Draws text at the given x,y position in graph coordinates (not pixels)
     * <p>
     * The text appears with its base line at the given y coordinate, ie: the
     * y position you specify is the bottom of the text. If you want to specify the
     * top of the text, use the #textHeight property to pass y+textHeight
     */
    Drawing text(def x, def y, def value) {
        x = x.toDouble()
        y = y.toDouble()
        textY = y + this.textHeight
        textX = x
        value = String.valueOf(value)
        x = txX(x)
        y = txY(y)
        if(log)
            println "Drawing $value at ($x,$y)"
        g.drawString(value, (float)x, (float)y)
        if(autoSave)
            save()
    }
    
    /**
     * Draw text at the default next position.
     * The default next position is adjusted each time new text is output so that
     * if you do nothing, the text appears as evenly spaced lines at the current
     * font height.
     * 
     * @param value
     * @return
     */
    Drawing text(def value) {
        double x = textX.toDouble()
        double y = textY.toDouble()
        text(x,y,value)
    }
    
    Drawing drawYAxis(List yAxis, boolean grid=false) {
                   
        def yTickPoints = yAxis.collect { txY(it) }
            
        save {
            g.setStroke(DASHED)
            lines([minX[0]] * (yAxis.size()-2), yAxis[1..-2], [maxX[-1]]*(yAxis.size()-2), yAxis[1..-2])
            g.setStroke(SOLID)
                
            color("black")
            coordType = Drawing.Coord.PIXELS
            [yAxis, yTickPoints].transpose().each {
                text(xMargin-40, it[1], it[0])
            }
        }
        coordType = Drawing.Coord.PLOT
        return this
    }
    
    static void main(String [] args) {
        Drawing d = new Drawing("/Users/simon/test.png", 800,600, 10,0, 20, 10)
        d.log = true
        
        Matrix m = new Matrix([
                               [2,0,0,3], 
                               [5,0,7,2]
                              ]) 
        
        println "Drawing $m"
        
        /*
        d.lines(m[][0], m[][1], m[][2], m[][3])
         .color(255,0,0)
         .line(5,3,2,4)
         */
         
         d.with {
             color(0,255,0)
             line(10,0,14,4)
             bars([12],[13.5],[1])
             text(12,1, "hello")
         }
         
         
    }
  
}
