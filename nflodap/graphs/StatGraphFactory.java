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
import nflodap.datastore.*;

/* This interface defines a factory that generates graphs about NFL play
   statistics. The graph is returned as a panel. The panels were chosen over
   generating the graph directly so the graph can outlive the database that
   was used to generate it. This interface, of course, implements the Factory
   design pattern */
public interface StatGraphFactory
{
    /* If this factory requires an overall value range from all plays (not just
       those used to generate the graph), these methods will return the type
       needed. */
    public SinglePlay.NumericFields getFirstOverallFieldNeeded();
    public SinglePlay.NumericFields getSecondOverallFieldNeeded();

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. This method requires two ranges of some field in the overall
       plays, as described by the methods above */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size,
                              IntegerRange firstValueRange,
                              IntegerRange secondValueRange);

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. This method requires a range of some field in the overall
       plays, as described by the methods above. If called when two ranges
       are actually needed, the second is found from the plays being graphed */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size,
                              IntegerRange firstValueRange);

    /* Generates a graph for a group of plays. The size of the graph must be
       specified in pixels so the layout looks correct when tiled with other
       graphs. If called when ranges of values from the overall plays are
       needed, they are found from the plays being graphed */
    public StatGraph getGraph(ArrayList<SinglePlay> plays, Dimension size);
}