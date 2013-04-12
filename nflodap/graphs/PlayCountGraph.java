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

/* This class plots counts of plays: total, positive yardage, negative yardage,
   and turnovers */
/* NOTE: All of this duplicates existing libraries. I wrote it to learn more
   about Java graphics */
public class PlayCountGraph extends StatGraph
{
    // If the fields in this object change, increment this number by 1
    private static final long serialVersionUID = 2L;

    int _positivePlayCount; // Gained yardage
    int _negativePlayCount; // Lost yardage or zero
    int _turnoverCount;

    Dimension _size;
    IntegerRange _overallCounts;

    public PlayCountGraph(int positivePlayCount, int negativePlayCount,
                          int turnoverCount, Dimension size,
                          IntegerRange overallCounts)
    {
        // Having no size or overall distances is a big error
        if ((size == null) || (overallCounts == null))
            throw new IllegalArgumentException();

        /* Ensure all counts are non-negative, and the overall distances has
           zero as a lower limit (otherwise things are very wrong) */
        if ((positivePlayCount < 0) || (negativePlayCount < 0) ||
            (overallCounts.getLowerLimit() != 0))
            throw new IllegalArgumentException();

        _positivePlayCount = positivePlayCount; 
        _negativePlayCount = negativePlayCount; 
        _turnoverCount = turnoverCount;
        _size = size;
        _overallCounts = overallCounts;

        /* If a range containing only one value was passed, extend it by to
           avoid a divide by zero later. */
        if (_overallCounts.getLength() == 0)
            _overallCounts.extendRange(_overallCounts.getUpperLimit() + 1);
        // Force the panel to display no smaller than wanted size
        setPreferredSize(size);
        setMinimumSize(size);
    }

    public void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D)g;

        // Draw and label axes. Get size of labels to ensure no overlap
        String countLabel = new String("TOTAL POSYD NEGYD TRNOVR");
        String layoutAmt = new String("10"); // Common result value

        Rectangle2D xLabelRect = g.getFontMetrics().getStringBounds(countLabel, g);
        Rectangle2D yLabelRect = g.getFontMetrics().getStringBounds(layoutAmt, g);
        Point2D origin = new Point2D.Double(yLabelRect.getHeight(),
                                            getPreferredSize().getHeight() - xLabelRect.getHeight());
        Point2D xEndPoint = new Point2D.Double(getPreferredSize().getWidth(), origin.getY());
        Point2D yEndPoint = new Point2D.Double(origin.getX(), 0.0);

        /* Draw the count label centered in the space between the origin and the
           right of the window. This is:
           origin + ((window right - origin - label width) / 2) =
           origin + ((window right - label width) / 2) - origin / 2 =
           (window right + origin - label width) / 2
           Note that the origin has a positive sign while expectation is for it
           to be negative */
        double countLabelLocation = (getPreferredSize().getWidth() + origin.getX() - xLabelRect.getWidth()) / 2;
        
        g.drawString(countLabel, (int)countLabelLocation,
                     (int)(getPreferredSize().getHeight()));
                
        // Draw an axis above the label
        g2.draw(new Line2D.Double(origin, xEndPoint));

        /* The points are specified in terms of the units of distance on the
           Y axis. Need to transform them into pixels for output. It can't be
           done before now, because the screen position depends on the axes
           position, which in turn depends on the label widths */
        double yRatio = (yEndPoint.getY() - origin.getY()) / _overallCounts.getLength();
        
        /* To find the needed position, find the distance from the value in Y
           units that represents the origin, transform that, and then add the
           origin location. This is:
           ((point - axis lower) * ratio) + origin
           which becomes:
           (point * ratio) - (axis lower * ratio) + origin.
           All but the first are constants, so calculating it in advance will
           speed up the plot */
        double yAdjustment = origin.getY() - (_overallCounts.getLowerLimit() * yRatio);

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
        int labelPoints = 10;
        double yValueIncrement = (double)_overallCounts.getLength() / labelPoints;

        /* Java draws the label with the lower left hand corner at the specified
           position. They should be centered instead. The only way to do this
           is to find the width of each label ahead of time and adjust the
           position accordingly. It's lots of work. This code approximates it
           by finding the width of the center label and using it as an
           approximation of all of them. In practice this should be good
           enough */
        int middleLabel = _overallCounts.getLowerLimit() + (_overallCounts.getLength() / 2);
        double labelWidth = g.getFontMetrics().getStringBounds(Integer.toString(middleLabel), g).getWidth();
        
        double currentLabel = (double)_overallCounts.getLowerLimit();
        /* See above for why the subtraction from _height takes place. Note
           carefully that the label ajustment takes place after the height
           subtraction, since its based on the position to put the label */
        int pointIndex;
        for (pointIndex = 0; pointIndex <= labelPoints; pointIndex++) {
            g.drawString(Integer.toString((int)currentLabel), 
                         (int)(getPreferredSize().getHeight() - (yAdjustment + ((int)currentLabel * yRatio)) - (labelWidth / 2.0)),
                         (int)yLabelRect.getHeight());
            currentLabel += yValueIncrement;
        } // For each label to generate
        g2.setTransform(saveAt);

        /* Find the location for each bar, above the appropriate part of the
           labels. 
           TRICKY NOTE: Fonts often display space at variable width. Find the
           location of the wanted bar by finding the width of the label up to
           the wanted part */
        double totalLocation = countLabelLocation + 
            g.getFontMetrics().getStringBounds(countLabel.substring(0, 1), g).getWidth();
        double posYdLocation = countLabelLocation +
            g.getFontMetrics().getStringBounds(countLabel.substring(0, 7), g).getWidth();
        double negYdLocation = countLabelLocation +
            g.getFontMetrics().getStringBounds(countLabel.substring(0, 13), g).getWidth();
        double turnoverLocation = countLabelLocation +
            g.getFontMetrics().getStringBounds(countLabel.substring(0, 19), g).getWidth();
        
        // The width of the bar is the size of three letters
        /* WARNING: Letter width can vary in many fonts. This code assumes the
           variance is small, so one set can stand in for all */
        double barWidth = g.getFontMetrics().getStringBounds(countLabel.substring(0, 3), g).getWidth();

        // If object has no total count, stop now since all bars will be zero
        int totalCount = _positivePlayCount + _negativePlayCount + _turnoverCount;
        if (totalCount == 0)
            return;
        
        // Graph the bars
        double barTop = yAdjustment + (yRatio * totalCount);
        g2.fillRect((int)totalLocation, (int)barTop, (int)barWidth, 
                    (int)(origin.getY() - barTop));

        barTop = yAdjustment + (yRatio * _positivePlayCount);
        g2.fillRect((int)posYdLocation, (int)barTop, (int)barWidth, 
                    (int)(origin.getY() - barTop));

        barTop = yAdjustment + (yRatio * _negativePlayCount);
        g2.fillRect((int)negYdLocation, (int)barTop, (int)barWidth, 
                    (int)(origin.getY() - barTop));

        barTop = yAdjustment + (yRatio * _turnoverCount);
        g2.fillRect((int)turnoverLocation, (int)barTop, (int)barWidth, 
                    (int)(origin.getY() - barTop));
    }
        
    // Clone the object
    public Object clone() throws CloneNotSupportedException
    {
        return new PlayCountGraph(_positivePlayCount, _negativePlayCount,
                                  _turnoverCount, (Dimension)_size.clone(),
                                  (IntegerRange)_overallCounts.clone());
    }

    public static void main(String[] args) throws CloneNotSupportedException
    {
        PlayCountGraph test = new PlayCountGraph(10, 5, 7, StatGraph._minSize,
                                                 new IntegerRange(0, 25));
        PlayGraphTest test2 = new PlayGraphTest((JPanel)test.clone(), 250, 250);
        test2.setVisible(true);
    }
}
