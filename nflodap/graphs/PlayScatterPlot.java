/* This file is part of NFLODAP, an On-Line Analytics Processing program for
   NFL plays. It creates various graphs of historic play data given the teams
   and the conditons of the wanted plays.

    Copyright (C) 2013   Ezra Erb

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License version 3 as published
    by the Free Software Foundation.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.

    I'd appreciate a note if you find this program useful or make
    updates. Please contact me through LinkedIn or github (my profile also has
    a link to the code depository)
*/
package nflodap.graphs;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import nflodap.datastore.*;

/* This class implements a simple scatter plot. It needs the dimensions of the
   X and Y axes, the size of the panel to generate, and a list of the points
   specifed in the units of the X and Y axes (NOT pixels!) It also needs the
   text for each axis. Successful plays are shown in black, and turnovers
   in red */
/* NOTE: All of this duplicates existing libraries. I wrote it to learn more
   about Java graphics */
public class PlayScatterPlot extends StatGraph
{
    // If the fields in this object change, increment this number by 1
    private static final long serialVersionUID = 2L;

    Point2D [] _points;
    Point2D [] _turnoverPoints;
    Dimension _size;

    String _xLabel;
    IntegerRange _xAxis;

    String _yLabel;
    IntegerRange _yAxis;

    public PlayScatterPlot(Point2D [] points, Point2D [] turnoverPoints,
                           Dimension size, String xLabel,
                           IntegerRange xAxisRange, String yLabel,
                           IntegerRange yAxisRange)

    {
        // Having no size or overall distances is a big error
        if ((size == null) || (xAxisRange == null) || (yAxisRange == null))
            throw new IllegalArgumentException();

        _points = points;
        _turnoverPoints = turnoverPoints;
        // If either set of points was passed null, replace with an empty array
        if (_points == null)
            _points = new Point2D[0];
        if (_turnoverPoints == null)
            _turnoverPoints = new Point2D[0];

        _size = size;
        _xLabel = xLabel;
        _xAxis = xAxisRange;
        /* If a range containing only one value was passed, extend it by 1 on
           either side to avoid a divide by zero later. */
        if (_xAxis.getLength() == 0) {
            _xAxis.extendRange(_xAxis.getLowerLimit() - 1);
            _xAxis.extendRange(_xAxis.getUpperLimit() + 1);
        }
        _yLabel = yLabel;
        _yAxis = yAxisRange;
        /* If a range containing only one value was passed, extend it by 1 on
           either side to avoid a divide by zero. */
        if (_yAxis.getLength() == 0) {
            _yAxis.extendRange(_yAxis.getLowerLimit() - 1);
            _yAxis.extendRange(_yAxis.getUpperLimit() + 1);
        }

        // Force the panel to display no smaller than wanted size
        setPreferredSize(size);
        setMinimumSize(size);
    }

    public void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D)g;

        // Draw and label axes. Get size of text to ensure no overlap
        Rectangle2D xLabelRect = g.getFontMetrics().getStringBounds(_xLabel, g);
        Rectangle2D yLabelRect = g.getFontMetrics().getStringBounds(_yLabel, g);
        Point2D origin = new Point2D.Double(yLabelRect.getHeight() * 2,
                                            getPreferredSize().getHeight() - (xLabelRect.getHeight() * 2));
        Point2D xEndPoint = new Point2D.Double(getPreferredSize().getWidth(), origin.getY());
        Point2D yEndPoint = new Point2D.Double(origin.getX(), 0.0);

        g2.draw(new Line2D.Double(origin, xEndPoint));
        
        /* The points are specified in terms of the units of the X and Y axis.
           Need to transform them into pixels for output. It can't be done
           before now, because the screen position depends on the axes
           position, which in turn depends on the label widths */
        double xRatio = (xEndPoint.getX() - origin.getX()) / _xAxis.getLength();
        double yRatio = (yEndPoint.getY() - origin.getY()) / _yAxis.getLength();
        
        /* To find the needed position, find the distance from the value in X
           and Y units that represents the origin, transform that, and then add
           the origin location. For each dimension, this is:
           ((point - axis lower) * ratio) + origin
           which becomes:
           (point * ratio) - (axis lower * ratio) + origin.
           All but the first are constants, so calculating it in advance will
           speed up the plot */
        double xAdjustment = origin.getX() - (_xAxis.getLowerLimit() * xRatio);
        double yAdjustment = origin.getY() - (_yAxis.getLowerLimit() * yRatio);

        // Want the label centered, which requires this
        g.drawString(_xLabel, (int)((getPreferredSize().getWidth() - xLabelRect.getWidth()) / 2),
                     (int)(getPreferredSize().getHeight()));
        
        // Label axis points
        int labelPoints = 10;
        double xValueIncrement = (double)_xAxis.getLength() / labelPoints;

        /* Java draws the label with the lower left hand corner at the specified
           position. They should be centered instead. The only way to do this
           is to find the width of each label ahead of time and adjust the
           position accordingly. It's lots of work. This code approximates it
           by finding the width of the center label and using it as an
           approximation of all of them. In practice this should be good
           enough */
        int middleLabel = _xAxis.getLowerLimit() + (_xAxis.getLength() / 2);
        double labelWidth = g.getFontMetrics().getStringBounds(Integer.toString(middleLabel), g).getWidth();
        
        int pointIndex;
        double currentLabel = (double)_xAxis.getLowerLimit();

        for (pointIndex = 0; pointIndex <= labelPoints; pointIndex++) {
            g.drawString(Integer.toString((int)currentLabel),
                         (int)(xAdjustment + ((int)currentLabel * xRatio) - (labelWidth / 2)),
                         (int)(getPreferredSize().getHeight() - xLabelRect.getHeight()));
            currentLabel += xValueIncrement;
        } // For each axis label to draw
            
        // Draw and label Y axis
        /* Java considers the origin to be in the upper left, while the graph
           is drawn with it in the lower right. Flip the two line ends */
        g2.draw(new Line2D.Double(yEndPoint, origin));
        /* Need to rotate how things are drawn within the panel in order to
           write vertically. This is done by changing the drawing transformation
           within the graphics object. Note that this transformation draws
           within the FRAME, not the PANEL, so it likely already contains data
           related to how the panel is positioned within the frame. Need to save
           this to handle drawing the points afterward, and then concatinate
           the needed transformations onto whatever is already there.

           Two tranformatons are needed. First, translate the origin from the
           upper left to the lower left, since it needs to be at the upper left
           corner of the rotated text to make it draw properly. Second, spin the
           drawing surface ninety degrees to the left to get vertical text. */
        AffineTransform saveAt = g2.getTransform();

        AffineTransform at = new AffineTransform();
        at.setToIdentity();
        at.translate(0.0, getPreferredSize().getHeight());
        at.rotate(-Math.PI / 2.0);
        g2.transform(at);
        
        /* At this point, the panel has been rotated such that the drawing
           origin is at the lower right. What it means is that the X coordinates
           above are now Y coordinates, and Y coordinates are now height - X
           coordinates. */
        g.drawString(_yLabel,
                     (int)((getPreferredSize().getHeight() - yLabelRect.getWidth()) / 2),
                     (int)yLabelRect.getHeight());
        
        // Label axis points
        double yValueIncrement = (double)_yAxis.getLength() / labelPoints;

        // Same position adjustment to handle the label width as the X axis
        middleLabel = _yAxis.getLowerLimit() + (_yAxis.getLength() / 2);
        labelWidth = g.getFontMetrics().getStringBounds(Integer.toString(middleLabel), g).getWidth();
        
        currentLabel = (double)_yAxis.getLowerLimit();
        /* See above for why the subtraction from _height takes place. Note
           carefully that the label ajustment takes place after the height
           subtraction, since its based on the position to put the label */
        for (pointIndex = 0; pointIndex <= labelPoints; pointIndex++) {
            g.drawString(Integer.toString((int)currentLabel), 
                         (int)(getPreferredSize().getHeight() - (yAdjustment + ((int)currentLabel * yRatio)) - (labelWidth / 2.0)),
                         (int)(yLabelRect.getHeight() * 2));
            currentLabel += yValueIncrement;
        } // For each label to generate
        g2.setTransform(saveAt);

        // Points are too small, draw little circles instead
        /* This looks nastier than it really is. Java doesn't have the concept
           of a circle, only an elipse. Furthermore, elipses must be specified
           by giving the bounding box. The net effect is that specifying what
           looks like a square actually produces a circle, the wanted shape. */
        for (Point2D index : _points)
            g2.fill(new Ellipse2D.Double(xAdjustment + (index.getX() * xRatio) - 2.0,
                                         yAdjustment + (index.getY() * yRatio) - 2.0,
                                         4.0, 4.0));
        Paint saveColor = g2.getPaint();
        g2.setPaint(Color.red); // Turnovers in red
        for (Point2D index : _turnoverPoints)
            g2.fill(new Ellipse2D.Double(xAdjustment + (index.getX() * xRatio) - 2.0,
                                         yAdjustment + (index.getY() * yRatio) - 2.0,
                                         4.0, 4.0));
        g2.setPaint(saveColor); // Restore previous color
    }

    // Version for common situation where turnovers are ignored
    public PlayScatterPlot(Point2D [] points, Dimension size, String xLabel,
                           IntegerRange xAxisRange, String yLabel,
                           IntegerRange yAxisRange)
    {
        this(points, null, size, xLabel, xAxisRange, yLabel, yAxisRange);
    }

    // Clone the object
    public Object clone() throws CloneNotSupportedException
    {
        return new PlayScatterPlot((Point2D[])_points.clone(),
                                   (Point2D[])_turnoverPoints.clone(),
                                   (Dimension)_size.clone(),
                                   _xLabel, (IntegerRange)_xAxis.clone(),
                                   _yLabel, (IntegerRange)_yAxis.clone());
    }

    public static void main(String[] args) throws CloneNotSupportedException
    {
        Point2D [] testPoints = new Point2D[5];
        testPoints[0] = new Point2D.Double(15, 35);
        testPoints[1] = new Point2D.Double(26, 15);
        testPoints[2] = new Point2D.Double(26, 55);
        testPoints[3] = new Point2D.Double(2, 15);
        testPoints[4] = new Point2D.Double(2, 55);

        Point2D [] testPoints2 = new Point2D[5];
        testPoints2[0] = new Point2D.Double(40, 75);
        testPoints2[1] = new Point2D.Double(40, 7);
        testPoints2[2] = new Point2D.Double(-2, 95);
        testPoints2[3] = new Point2D.Double(-2, 31);
        testPoints2[4] = new Point2D.Double(45, 95);

        PlayScatterPlot test = new PlayScatterPlot(testPoints, testPoints2,
                                                   new Dimension(300, 300),
                                                   new String("TEST X"),
                                                   new IntegerRange(-10, 50), 
                                                   new String("TEST Y"),
                                                   new IntegerRange(-5, 100));
        PlayGraphTest test2 = new PlayGraphTest((JPanel)test.clone(), 350, 350);
        test2.setVisible(true);
    }
}
