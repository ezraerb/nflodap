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

/* This class implements a factory to generate count graphs of play data. It
   requires the maximim count of plays for any category, expressed as a range,
   otherwise every graph for a set of plays will have a different scale */
public class PlayCountFactory implements StatGraphFactory
{
    public PlayCountFactory()
    {
        // Nothing to do!
    }

    // This class requires a set of values from the overall plays. Here it is
    public SinglePlay.NumericFields getFirstOverallFieldNeeded()
    {
        return SinglePlay.NumericFields.PLAY_COUNT;
    }
    
    public SinglePlay.NumericFields getSecondOverallFieldNeeded()
    {
        return null;
    }

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. This method requires two ranges of some field in the overall
       plays, as described by the methods above */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size,
                              IntegerRange firstValueRange,
                              IntegerRange secondValueRange)
    {
        // Second range is ignored
        return getGraph(plays, size, firstValueRange);
    }

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. This method requires a range of some field in the overall
       plays, as described by the methods above. If called when two ranges
       are actually needed, the second is found from the plays being graphed */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size, 
                              IntegerRange firstValueRange)
    {
        /* If the value range was passed null, initialize it from the passed
           plays. This provides a dirtier graph than using the overall plays,
           but allows one to be produced */
        if (firstValueRange == null) {
            firstValueRange = PlayGraphGenerator.getPlayValueRange(plays,
                                                                   getFirstOverallFieldNeeded());
            /* If the plays to process are empty, the range will also be empty.
               default it */
            if (firstValueRange == null)
                firstValueRange = new IntegerRange(0, 0);
        }
        // Need to count the plays in different categories
        int positivePlayCount = 0;
        int negativePlayCount = 0;
        int turnoverCount = 0;
        if (plays != null) {
            int index;
            for (index = 0; index < plays.size(); index++) {
                if (plays.get(index).getTurnedOver())
                    turnoverCount++;
                else if (plays.get(index).getDistanceGained() <= 0)
                    negativePlayCount++;
                else
                    positivePlayCount++;
            } // For loop
        } // Plays exist
        return new PlayCountGraph(positivePlayCount, negativePlayCount,
                                  turnoverCount, size, firstValueRange);
    }

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. If called when ranges of values from the overall plays are
       needed, they are found from the plays being graphed */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size)
    {
        return getGraph(plays, size, null);
    }

    // Retrn type of graph to generate
    public String toString()
    {
        return "Play counts: positive and negative yardage, turnovers";
    }
        
    public static void main(String[] args)
    {
        StatGraphFactory testFactory = new PlayCountFactory();

        // Create a bunch of plays with interesting value distributions
        ArrayList<SinglePlay> testList = new ArrayList<SinglePlay>();
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 10, 50,
                                    45, 0, 0, 11, false));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 9, 50,
                                    45, 0, 0, -5, false));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 7, 50,
                                    45, 0, 0, -2, true));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 3, 50,
                                    45, 0, 0, 0, false));
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
                                    45, 0, 0, 11, false));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 20, 50,
                                    45, 0, 0, 30, true));
        testList.add(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                    SinglePlay.DownNumber.FIRST_DOWN, 14, 50,
                                    45, 0, 0, 10, false));
        JPanel test2 = testFactory.getGraph(testList, new Dimension (300, 300),
                                            new IntegerRange(0, 40),
                                            new IntegerRange(0, 10));
        new JFrameThreadWrapper(new PlayGraphTest(test2, 350, 350), 2);
        // Stress test: Confirm empty input produces an empty graph
        JPanel test3 = testFactory.getGraph(null, new Dimension (300, 300));
        new JFrameThreadWrapper(new PlayGraphTest(test3, 350, 350), 2);
    }
}