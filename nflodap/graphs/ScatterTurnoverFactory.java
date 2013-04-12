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

import java.util.*;
import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import nflodap.datastore.*;

/* This class implements a factory to generate scatter plot graphs of play data,
   seperating successful plays from turnovers. Depending on how it is
   constructed it will show just one type of play or both (with turnovers in
   red). It is constructed with the two play fields to use for the scatterplots,
   which must be integer based (scatterplots of enumerated fields turn into
   lines or grids). To get the best results for grouped play data, the overall
   range of the two fields should be used; otherwise the graph for each group
   will use a different scale */
public class ScatterTurnoverFactory implements StatGraphFactory
{
    private SinglePlay.NumericFields _xField; // Play field for X axis
    private SinglePlay.NumericFields _yField; // Play field for Y axis
    private boolean _includeSuccessful;
    private boolean _includeTurnovers;
    
    public ScatterTurnoverFactory(SinglePlay.NumericFields xField,
                                  SinglePlay.NumericFields yField,
                                  boolean includeSuccessful,
                                  boolean includeTurnovers)
    {
        // Fields can not be null, and must be different
        if ((xField == null) || (yField == null) || (xField == yField))
            throw new IllegalArgumentException();
        _xField = xField;
        _yField = yField;
        _includeSuccessful = _includeSuccessful;
        _includeTurnovers = _includeTurnovers;
        // If neither specified, treat it as wanting both
        if ((!_includeSuccessful) && (!_includeTurnovers)) {
            _includeSuccessful = true;
            _includeTurnovers = true;
        }
    }

    /* This class requires two sets of values from the overall plays. Here they
       are */
    public SinglePlay.NumericFields getFirstOverallFieldNeeded()
    {
        return _xField;
    }
    
    public SinglePlay.NumericFields getSecondOverallFieldNeeded()
    {
        return _yField;
    }

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. This method requires two ranges of some field in the overall
       plays, as described by the methods above */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size,
                              IntegerRange firstValueRange,
                              IntegerRange secondValueRange)
    {
        /* If either value range was passed null, initialize it from the passed
           plays. This provides a dirtier graph than using the overall plays,
           but allows one to be produced */
        if (firstValueRange == null)
            firstValueRange = PlayGraphGenerator.getPlayValueRange(plays, _xField);
        if (secondValueRange == null)
            secondValueRange = PlayGraphGenerator.getPlayValueRange(plays, _yField);

        /* Need to make a list of the points for the scatterplot by extracting
           the wanted values from the plays. Note that an empty list still
           generates a graph */
        Point2D [] points = new Point2D[0];
        Point2D [] turnoverPoints = new Point2D[0];
        if (plays != null) {
            /* The array sizes depend on the number of plays of each type.
               Can either scan the list to find this, or use dymanically
               sized data structures and convert. The latter should be faster
               in practice */
            ArrayList<Point2D> successful = new ArrayList<Point2D>();
            ArrayList<Point2D> turnover = new ArrayList<Point2D>();
            
            int index;
            for (index = 0; index < plays.size(); index++) {
                Point2D newPoint = new Point2D.Double(plays.get(index).getIntValue(_xField),
                                                      plays.get(index).getIntValue(_yField));
                if (plays.get(index).getTurnedOver())
                    turnover.add(newPoint);
                else
                    successful.add(newPoint);
            } // Loop through play data

            if (_includeSuccessful)
                /* Yes, the empty array is passed in, to signal to issue
                   results in the type of the array instead of Object[] */
                points = successful.toArray(points);
            if (_includeTurnovers)
                turnoverPoints = turnover.toArray(turnoverPoints);
        } // Plays exist
        return new PlayScatterPlot(points, turnoverPoints, size,
                                   _xField.toString(), firstValueRange,
                                   _yField.toString(), secondValueRange);
    }

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. This method requires a range of some field in the overall
       plays, as described by the methods above. If called when two ranges
       are actually needed, the second is found from the plays being graphed */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size, 
                              IntegerRange firstValueRange)
    {
        return getGraph(plays, size, firstValueRange, null);
    }

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. If called when ranges of values from the overall plays are
       needed, they are found from the plays being graphed */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size)
    {
        return getGraph(plays, size, null, null);
    }

    // Report the type of graph to generate
    public String toString()
    {
        StringBuffer stream = new StringBuffer();
        stream.append("Scatter ");
        if (_includeSuccessful)
            stream.append("successful ");
        if (_includeSuccessful && _includeTurnovers)
            stream.append("and ");
        if (_includeTurnovers)
            stream.append("turnover ");
        stream.append("plot: " + _xField + " by " + _yField);
        return stream.toString();
    }

    public static void main(String[] args)
    {
        ScatterTurnoverFactory testFactory = new ScatterTurnoverFactory(SinglePlay.NumericFields.DISTANCE_NEEDED,
                                                                        SinglePlay.NumericFields.DISTANCE_GAINED,
                                                                        true, true);

        // Create a bunch of plays with interesting value distributions
        ArrayList<SinglePlay> testList = new ArrayList<SinglePlay>();
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 10, 50,
                                    45, 0, 0, 11, false));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 9, 50,
                                    45, 0, 0, -5, true));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 7, 50,
                                    45, 0, 0, -2, false));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 3, 50,
                                    45, 0, 0, 0, true));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 1, 50,
                                    45, 0, 0, 0, false));
        // Spawn a thread containing a window with the panel
        JPanel test = testFactory.getGraph(testList, new Dimension(300, 300));
        new JFrameThreadWrapper(new PlayGraphTest(test, 350, 350), 1);

        /* Add more data points and display it again. This tests that the
           class can handle calls for multiple different graphs */
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 12, 50,
                                    45, 0, 0, -2, true));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 15, 50,
                                    45, 0, 0, 5, false));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 17, 50,
                                    45, 0, 0, 11, true));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 20, 50,
                                    45, 0, 0, 30, false));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 14, 50,
                                    45, 0, 0, 11, true));
        JPanel test2 = testFactory.getGraph(testList, new Dimension (300, 300),
                                            new IntegerRange(0, 30),
                                            new IntegerRange(-10, 40));
        new JFrameThreadWrapper(new PlayGraphTest(test2, 350, 350), 2);
        // Stress test: Confirm empty input produces an empty graph
        JPanel test3 = testFactory.getGraph(null, new Dimension (300, 300));
        new JFrameThreadWrapper(new PlayGraphTest(test3, 350, 350), 2);
    }
}