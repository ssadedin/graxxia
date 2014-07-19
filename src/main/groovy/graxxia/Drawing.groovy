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

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Color;
import java.awt.image.BufferedImage;
import javax.imageio.*
/**
 * A very simple class to support a simple drawing canvas and make it easy to map
 * a mathematical space on to the drawing canvas.
 * 
 * @author Simon
 */
class Drawing {
    
    BufferedImage  image = null;
    Graphics2D g = null
    String fileName = null
    
    List<String> operations = []
    
    int xOffset = 0
    int yOffset = 0
    
    double maxX = 0
    double maxY = 0
    double minX = 0
    double minY = 0
    
    double xScale = 1.0d
    double yScale = 1.0d
    
    int height = -1
    int width = -1
    
    boolean log = false
    
    public Drawing(String fileName, int width, int height, double minX, double minY, double maxX, double maxY) {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        this.g = image.createGraphics()
        this.fileName = fileName
        this.xScale = width / (maxX - minX)
        this.yScale = width / (maxY - minY) // invert Y so it is like math plotting coordinates
        this.xOffset = -minX 
        this.yOffset = -minY 
        this.height = height
        this.width = width
        g.setBackground(Color.WHITE)
        g.clearRect(0,0,width,height)
        color(0,0,0)
    }
    
    public Drawing(String fileName, int width, int height) {
        this(fileName,width,height,0,0, 1.0d, 1.0d)
    }
    
    /**
     * Adjust xScale and yScale to fit the given point inside 
     * the bounds
     */
    void rescale(double x1, double y1, double x2, double y2) {
        
    }
    
    Drawing bars(def x1, def x2, def y) {
        lines(x1,y,x2,y,bars:true)
    }
    
    Drawing bars(IntRange range, def y) {
        lines(range.from, range.to,y,bars:true)
    }
      
    Drawing lines(Map options=[:], def x1, def y1, def x2, def y2) {
        if(x1.size() != y1.size())
            throw new IllegalArgumentException("Size of x1 (${x1.size()} different to size of y1 ${y1.size()}")
            
        if(x2.size() != y2.size())
            throw new IllegalArgumentException("Size of x2 (${x2.size()} different to size of y2 ${y2.size()}")
                
        x1.eachWithIndex { x, index -> 
            if(options.color)
                color(options.color)
                
            def txed = txLine(x, y1[index], x2[index], y2[index]) 
            if(options.bars) {
                drawBar(txed)
            }
        }
        save()
        return this
    }
    
    private void drawBar(lineCoords) {
        g.drawLine((int)lineCoords[0],(int)lineCoords[1]-5,(int)lineCoords[0],(int)lineCoords[1]+5)
        g.drawLine((int)lineCoords[2],(int)lineCoords[3]-5,(int)lineCoords[2],(int)lineCoords[3]+5)
    }
    
    Drawing line(def x1, def y1, def x2, def y2) {
        txLine(x1,y1,x2,y2)
        save()      
        return this
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
        x1 = xScale * (xOffset + x1)
        x2 = xScale * (xOffset + x2)
        y1 = this.height - yScale * (yOffset + y1)
        y2 = this.height - yScale * (yOffset + y2)
        
        if(log)
            println "Drawing line ($x1,$y1) - ($x2,$y2)"
        g.drawLine((int)x1, (int)y1, (int)x2, (int)y2)
        [x1,y1,x2,y2]
    }
    
    void save() {
        ImageIO.write(image, "png", new File(fileName));
    }
    
    Drawing text(def x, def double y, def value) {
        x = x.toDouble()
        y = y.toDouble()
        value = String.valueOf(value)
        x = xScale * (xOffset + x)
        y = this.height - yScale * (yOffset + y)
        if(log)
            println "Drawing $value at ($x,$y)"
        g.drawString(value, (float)x, (float)y)
        save()
    }
    
	/**
	 * A very simple test function
	 * 
	 * @param args
	 */
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
