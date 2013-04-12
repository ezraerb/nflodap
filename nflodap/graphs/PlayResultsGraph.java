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

/* This class plots the results of a group of plays: Distances as a bar chart,
   Average distance, variance, turnover percentage. The more plays in the group,
   the less readable it gets */
/* NOTE: All of this duplicates existing libraries. I wrote it to learn more
   about Java graphics */
public class PlayResultsGraph extends StatGraph
{
    // If the fields in this object change, increment this number by 1
    private static final long serialVersionUID = 2L;

    int [] _distances;
    int _turnoverCount; // Needed for the clone

    // Cache these results so drawing is faster
    double _distanceAverage;
    double _distanceVariance;
    double _turnoverPercentage;
    
    Dimension _size;
    IntegerRange _overallDistances;

    public PlayResultsGraph(int [] distances, int turnoverCount,
                            Dimension size, IntegerRange overallDistances)
    {
        // Having no size or overall distances is a big error
        if ((size == null) || (overallDistances == null))
            throw new IllegalArgumentException();

        _distances = distances;
        // If distances passed null, replace with an empty array
        if (_distances == null)
            _distances = new int[0];

        _turnoverCount = turnoverCount; 
        _size = size;
        _overallDistances = overallDistances;
        /* If a range containing only one value was passed, extend it by 1 on
           either side to avoid a divide by zero later. */
        if (_overallDistances.getLength() == 0) {
            _overallDistances.extendRange(_overallDistances.getLowerLimit() - 1);
            _overallDistances.extendRange(_overallDistances.getUpperLimit() + 1);
        }

        // Compute and cache result statistics
        _distanceAverage = 0.0;
        _distanceVariance = 0.0;
        _turnoverPercentage = 0.0;

        if (_distances.length > 0) {
            int index;
            for (index = 0; index < _distances.length; index++)
                _distanceAverage += (double)_distances[index];
            _distanceAverage /= _distances.length;

            double itemVariance;
            for (index = 0; index < _distances.length; index++) {
                itemVariance = _distanceAverage - (double)_distances[index];
                _distanceVariance += (itemVariance * itemVariance);
            }
            _distanceVariance /= _distances.length;
            _distanceVariance = Math.sqrt(_distanceVariance);

            _turnoverPercentage = (double)_turnoverCount / _distances.length;
        } // At least one distance to graph

        // Force the panel to display no smaller than wanted size
        setPreferredSize(size);
        setMinimumSize(size);
    }

    public void paintComponent(Graphics g)
    {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D)g;

        // Draw and label axes. Get size of labels to ensure no overlap
        String distLabel = new String("DIST");
        String statLabel = new String("AV VA TN");
        String layoutAmt = new String("10"); // Common result value

        Rectangle2D xDistRect = g.getFontMetrics().getStringBounds(distLabel, g);
        Rectangle2D xStatRect = g.getFontMetrics().getStringBounds(statLabel, g);
        Rectangle2D yLabelRect = g.getFontMetrics().getStringBounds(layoutAmt, g);
        Point2D origin = new Point2D.Double(yLabelRect.getHeight(),
                                            getPreferredSize().getHeight() - xDistRect.getHeight());
        Point2D xEndPoint = new Point2D.Double(getPreferredSize().getWidth(), origin.getY());
        Point2D yEndPoint = new Point2D.Double(origin.getX(), 0.0);

        /* Draw the stat label on the far right. Draw the distance label
           centered in the remaining space */
        double statLabelLocation = getPreferredSize().getWidth() - xStatRect.getWidth();
        
        g.drawString(statLabel, (int)statLabelLocation,
                     (int)(getPreferredSize().getHeight()));
        // Want the label centered, which requires this
        g.drawString(distLabel, (int)((statLabelLocation - xDistRect.getWidth()) / 2),
                     (int)(getPreferredSize().getHeight()));
                
        /* The points are specified in terms of the units of distance on the
           Y axis. Need to transform them into pixels for output. It can't be
           done before now, because the screen position depends on the axes
           position, which in turn depends on the label widths */
        double yRatio = (yEndPoint.getY() - origin.getY()) / _overallDistances.getLength();
        
        /* To find the needed position, find the distance from the value in Y
           units that represents the origin, transform that, and then add the
           origin location. This is:
           ((point - axis lower) * ratio) + origin
           which becomes:
           (point * ratio) - (axis lower * ratio) + origin.
           All but the first are constants, so calculating it in advance will
           speed up the plot */
        double yAdjustment = origin.getY() - (_overallDistances.getLowerLimit() * yRatio);

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
        double yValueIncrement = (double)_overallDistances.getLength() / labelPoints;

        /* Java draws the label with the lower left hand corner at the specified
           position. They should be centered instead. The only way to do this
           is to find the width of each label ahead of time and adjust the
           position accordingly. It's lots of work. This code approximates it
           by finding the width of the center label and using it as an
           approximation of all of them. In practice this should be good
           enough */
        int middleLabel = _overallDistances.getLowerLimit() + (_overallDistances.getLength() / 2);
        double labelWidth = g.getFontMetrics().getStringBounds(Integer.toString(middleLabel), g).getWidth();
        
        double currentLabel = (double)_overallDistances.getLowerLimit();
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

        /* Find the location to display each of the summary bars, which depends
           on the location of the label below them
           TRICKY NOTE: Fonts often display space at variable width. Find the
           location of the wanted bar by finding the width of the label up to
           the wanted part */
        double varianceLocation = statLabelLocation + 
            g.getFontMetrics().getStringBounds(statLabel.substring(0, 3), g).getWidth();
        double turnoverLocation = statLabelLocation +
            g.getFontMetrics().getStringBounds(statLabel.substring(0, 6), g).getWidth();
        
        /* The width of a summary bar is the space available for the turnover
           bar, since the label has no space on the right */
        double summaryBarWidth = getPreferredSize().getWidth() - turnoverLocation;

        /* Want the bar to be narrower than the label, so subtract a fudge
           factor. This also affects location, so track it seperately */
        double summaryBarSeperation = summaryBarWidth * 0.1;
        summaryBarWidth -= summaryBarSeperation;

        /* Find the height of zero in the graph, needed to draw the graph bars.
           If the yAdjustment found above is within the drawing area, it is the
           needed value, because it gives the location on the graph to draw a
           value of zero. Unfortunately, if the range of values does not contain
           zero, it won't be in the drawing area and needs to be adjusted to
           the limit of the area
           SUBTLE NOTE: Remember that the drawing origin is in the upper left,
           requiring the seeming swap of the comparison operators below */
        double heightOfZero = yAdjustment;
        if (heightOfZero > origin.getY())
            heightOfZero = origin.getY();
        else if (heightOfZero < yEndPoint.getY())
            heightOfZero = yEndPoint.getY();

        /* Draw the X axis at this height from the origin to the turnover label
           location. It doesn't cross the turnover bar because that bar uses a
           different scale */
        g2.draw(new Line2D.Double(origin.getX(), heightOfZero, turnoverLocation,
                                  heightOfZero));

        /* If object has no distance data, stop now since all bars will be
           zero */
        if (_distances.length == 0)
            return;

        // Draw bars for statistics
        drawBar(g2, yAdjustment + (_distanceAverage * yRatio),
                statLabelLocation + (summaryBarSeperation / 2),
                summaryBarWidth, heightOfZero);
        drawBar(g2, yAdjustment + (_distanceVariance * yRatio),
                varianceLocation + (summaryBarSeperation / 2),
                summaryBarWidth, heightOfZero);

        /* Turnover percentage gets graphed from the lower description, because
           it is on a different scale. Scale the available space by the
           percentage to find the bar top, and draw it */
        double barTop = origin.getY() + (_turnoverPercentage * (yEndPoint.getY() - origin.getY()));
        g2.fillRect((int)(turnoverLocation + (summaryBarSeperation / 2)),
                    (int)barTop, (int)summaryBarWidth, 
                    (int)(origin.getY() - barTop));


        // Find the amount of space for each play result
        double resultStart = origin.getX();
        double resultBarSeperation = 0.0;
        double resultBarWidth = (statLabelLocation - resultStart) / _distances.length;
        /* If the result bars are wider than the summary bars, shrink the
           result bars to their size. This will generate extra space, which is
           split on either side of the bars to center them */
        if (resultBarWidth > (summaryBarWidth + summaryBarSeperation)) {
            resultStart += (statLabelLocation - origin.getX()
                            - ((summaryBarWidth + summaryBarSeperation)
                               * _distances.length)) / 2;
            resultBarWidth = summaryBarWidth;
        }

        // Shrink the bar width to leave space between them.
        resultBarSeperation = resultBarWidth * 0.1;
        resultBarWidth -= resultBarSeperation;
        
        /* Add half the adjustment to the start location, again to center the
           bars */
        resultStart += (resultBarSeperation / 2);

        // Draw the bars
        int index;
        for (index = 0; index < _distances.length; index++) {
            drawBar(g2, yAdjustment + (_distances[index] * yRatio),
                    resultStart, resultBarWidth, heightOfZero);
            resultStart += (resultBarWidth + resultBarSeperation);
        }
    }
        
    // Draws a statistics bar. Values are in screen coordinates
    private void drawBar(Graphics2D g2, double value, double location,
                         double barWidth, double heightOfZero)
    {
        /* Java requires a positive width and height when drawing rectangles,
           so pass the lower height as the location, and find the height from
           the other */
        if (value < heightOfZero)
            g2.fillRect((int)location, (int)value, (int)barWidth,
                        (int)(heightOfZero - value));
        else
            g2.fillRect((int)location, (int)heightOfZero, (int)barWidth,
                        (int)(value - heightOfZero));
    }

    // Clone the object
    public Object clone() throws CloneNotSupportedException
    {
        return new PlayResultsGraph((int [])_distances.clone(), _turnoverCount,
                                    (Dimension)_size.clone(),
                                    (IntegerRange)_overallDistances.clone());
    }

    public static void main(String[] args) throws CloneNotSupportedException
    {
        int [] testDistances = new int[7];
        testDistances[0] = -3;
        testDistances[1] = 10;
        testDistances[2] = 12;
        testDistances[3] = 4;
        testDistances[4] = 6;
        testDistances[5] = 8;
        testDistances[6] = -1;

        PlayResultsGraph test = new PlayResultsGraph(testDistances, 2,
                                                     StatGraph._minSize,
                                                     new IntegerRange(-10, 20));
        PlayGraphTest test2 = new PlayGraphTest((JPanel)test.clone(), 250, 250);
        test2.setVisible(true);
    }
}
