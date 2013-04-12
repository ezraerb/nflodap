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
import javax.swing.text.*;
import java.awt.*;
import nflodap.datastore.*;

/* This class generates a wanted data graph from a play database. Most utility
   routines a private. Some are public because they are shared with classes
   that actually do the graphs */
public final class PlayGraphGenerator
{
    // Size of overall window
    private static Dimension _size = new Dimension(700, 700);

    // Singleton to hold count of windows generated
    private static Integer _windowCount = null;

    private DataStore _db;

    // Number of rows of graphs on screen
    private int _totalGraphRows;

    // Number of columns of graphs on screen
    private int _totalGraphColumns;

    // True, layout has a set of labels on the rows as well as the columns
    private boolean _haveRowLabels;

    // Row of last graph inserted into drawing pane
    private int _lastRow;

    // Column of last graph inserted into drawing pane
    private int _lastColumn;

    // Graph to use when no data exists
    private StatGraph _emptyGraph;

    // Overall window around graphs
    private JFrame _window;
    
    // Actual pane used to hold the graphs
    private JPanel _graphPane;

    // Cache of layout data, prevents needed to constantly generate it
    private GridBagConstraints _cachedConstraints;

    // Private class to return a pair of integer ranges
    private class RangePair
    {
        private IntegerRange _first;
        private IntegerRange _second;

        public RangePair(IntegerRange first, IntegerRange second)
        {
            _first = first;
            _second = second;
        }

        public IntegerRange getFirst()
        {
            return _first;
        }

        public IntegerRange getSecond()
        {
            return _second;
        }
    }

    // Constructor. Needs the DB to graph
    public PlayGraphGenerator(DataStore db)
    {
        if (db == null)
            throw new IllegalArgumentException();
        // NOTE: An empty database is technically legal
        _db = db;
        clearGraphWindowData();
    }

    // Clears all data about the graph window
    private void clearGraphWindowData()
    {
        _totalGraphRows = 0;
        _totalGraphColumns = 0;
        _lastRow = 0;
        _lastColumn = -1; // 0 is a valid value
        _haveRowLabels = false;
        _emptyGraph = null;
        _window = null;
        _graphPane = null;
        _cachedConstraints = null;
    }

    /* Generate the wanted graph from the play database. Last paramter is how
       to slice the DB before graphing it. */
    public void generateGraph(StatGraphFactory graph, PlaySlice slices,
                              String graphTitle) throws CloneNotSupportedException
    {
        ArrayList<SinglePlay> plays = null;
        if (graph == null) // Graph to generate must be passed
            throw new IllegalArgumentException();

        DataStore data = getWantedData(slices);
        // Since there are no grouping, everything is combined into one graph
        data.rollup();
        // Only one list of plays, this is how to extract it
        Iterator<ArrayList<SinglePlay>> index = data.iterator();
        if (index.hasNext())
            plays = index.next();
        
        initGraphWindow(graphTitle);

        /* Since all plays are shown on a single graph, don't need to find the
           overall play values; they are the same as the values for the set of
           plays. Insert the result directly into the frame */
        // If graph size is 1 by 1, insert directly into frame
        _window.getContentPane().add(graph.getGraph(plays, _size),
                                     BorderLayout.CENTER);
        displayWindow();
    }

    /* Generate the wanted graph from the play database, grouping plays by
       the wanted characteristic. Last paramter is how to slice the DB before
       graphing it */
    public <P extends Enum<P>> void generateGraph(StatGraphFactory graph,
                                                  Class<P> groupType,
                                                  PlaySlice slices,
                                                  String graphTitle) throws CloneNotSupportedException
    {
        if (graph == null) // Graph to generate must be passed
            throw new IllegalArgumentException();

        // Get the wanted plays
        DataStore data = getWantedData(slices);

        // Pivot the datastore to the wanted groupings
        data.pivot(groupType);

        // If the graph requires overall data about the plays, extract it now
        /* SUBTLE NOTE: Don't bother checking whether the graph needs the
           values before doing the call. The method itself handles the case
           where none (or only one) value is wanted */
        RangePair rangeResult = getDbValuesRange(data,
                                                 graph.getFirstOverallFieldNeeded(),
                                                 graph.getSecondOverallFieldNeeded());

        /* Find the size of each graph. Its equal to the overall wanted window
           size divided by the number of grouped entries. Divide both height
           and width so the graphs come out proportional */
        int categoryCount = groupType.getEnumConstants().length;
        Dimension graphSize = new Dimension((int)(_size.getWidth() / categoryCount),
                                            (int)(_size.getHeight() / categoryCount));
        /* If the resulting size is under the minimum size, set to the minimum
           graph size. This is a compromise between having graphs to small to
           read and a window that can't fit on the screen */
        if (graphSize.getWidth() < StatGraph._minSize.getWidth())
            graphSize.setSize(StatGraph._minSize.getWidth(), graphSize.getHeight());
        if (graphSize.getHeight() < StatGraph._minSize.getHeight())
            graphSize.setSize(graphSize.getWidth(), StatGraph._minSize.getHeight());
        
        // Create the graph for when a given category has no data
        StatGraph emptyGraph = graph.getGraph(null, graphSize,
                                              rangeResult.getFirst(),
                                              rangeResult.getSecond());

        // Generate the window to lay out the graphs
        initGraphWindow(graphTitle, groupType, emptyGraph);

        Iterator<ArrayList<SinglePlay>> playIndex = data.iterator();
        while (playIndex.hasNext()) {
            ArrayList<SinglePlay> plays = playIndex.next();
            if (!plays.isEmpty())
                layoutGraph(graph.getGraph(plays, graphSize,
                                           rangeResult.getFirst(),
                                           rangeResult.getSecond()),
                            plays.get(0).getValue(groupType));
        } // While plays left to graph

        // Fill out the remaing groups with empty graphs, if needed
        /* SUBTLE NOTE: If an empty DB was passed, no graphs have been generated
           yet and this code will generate all graphs empty. This is the wanted
           result */
        fillRemainderWindow();
        displayWindow();
    }

    /* Generate the wanted graph from the play database, grouping plays by
       the wanted characteristic. Last paramter is how to slice the DB before
       graphing it */
    public <P extends Enum<P>, Q extends Enum<Q>> void generateGraph(StatGraphFactory graph,
                                                                     Class<P> xAxisGroup,
                                                                     Class<Q> yAxisGroup,
                                                                     PlaySlice slices,
                                                                     String graphTitle) throws CloneNotSupportedException
    {
        if (graph == null) // Graph to generate must be passed
            throw new IllegalArgumentException();

        // Get the wanted plays
        DataStore data = getWantedData(slices);

        // Pivot the datastore to the wanted groupings
        /* NOTE: The way the pivot works, the first group becomes the Y axis
           in the final graph layout, and the second the X axis */
        data.pivot(yAxisGroup, xAxisGroup);

        // If the graph requires overall data about the plays, extract it now
        /* SUBTLE NOTE: Don't bother checking whether the graph needs the
           values before doing the call. The method itself handles the case
           where none (or only one) value is wanted */
        RangePair rangeResult = getDbValuesRange(data,
                                                 graph.getFirstOverallFieldNeeded(),
                                                 graph.getSecondOverallFieldNeeded());

        /* Find the size of each graph. Its equal to the overall wanted window
           size divided by the number of entries of each grouping characteristic
           WARNING: If the number of characteristics is widely different, this
           can result in distorted graphs. The minimim size check below will
           ensure this does not get too extreme */
        Dimension graphSize = new Dimension((int)(_size.getWidth() / xAxisGroup.getEnumConstants().length),
                                            (int)(_size.getHeight() / yAxisGroup.getEnumConstants().length));

        /* If the resulting size is under the minimum size, set to the minimum
           graph size. This is a compromise between having graphs to small to
           read and a window that can't fit on the screen */
        if (graphSize.getWidth() < StatGraph._minSize.getWidth())
            graphSize.setSize(StatGraph._minSize.getWidth(), graphSize.getHeight());
        if (graphSize.getHeight() < StatGraph._minSize.getHeight())
            graphSize.setSize(graphSize.getWidth(), StatGraph._minSize.getHeight());
        
        // Create the graph for when a given category has no data
        StatGraph emptyGraph = graph.getGraph(null, graphSize,
                                              rangeResult.getFirst(),
                                              rangeResult.getSecond());

        // Generate the window to lay out the graphs
        initGraphWindow(graphTitle, xAxisGroup, yAxisGroup, emptyGraph);

        Iterator<ArrayList<SinglePlay>> playIndex = data.iterator();
        while (playIndex.hasNext()) {
            ArrayList<SinglePlay> plays = playIndex.next();
            if (!plays.isEmpty())
                /* NOTE: The Y axis determines the row and the X axis the
                   column, leading to the seeming switch of values below */
                layoutGraph(graph.getGraph(plays, graphSize,
                                           rangeResult.getFirst(),
                                           rangeResult.getSecond()),
                            plays.get(0).getValue(yAxisGroup),
                            plays.get(0).getValue(xAxisGroup));
        } // While plays left to graph

        // Fill out the remaing groups with empty graphs, if needed
        /* SUBTLE NOTE: If an empty DB was passed, no graphs have been generated
           yet and this code will generate all graphs empty. This is the wanted
           result */
        fillRemainderWindow();
        displayWindow();
    }

    // Gets a datastore with the wanted data in it
    /* HACK/WORKAROUND: Easier to ignore the cast warning then to fully test
       the class cast in question. */
    @SuppressWarnings({"unchecked"})
    private DataStore getWantedData(PlaySlice slices) throws CloneNotSupportedException
    {
        // Copy the data before slicing it to preserve the original
        DataStore result = (DataStore)_db.clone();
        if (slices != null)
            slices.slice(result);
        return result;
    }

    // Find the range of some play value in a set of plays
    public static IntegerRange getPlayValueRange(ArrayList<SinglePlay> plays,
                                                 SinglePlay.NumericFields field)
    {
        if ((plays == null) || (field == null))
            return new IntegerRange(0, 0); // Default value
        Iterator<SinglePlay> index = plays.iterator();
        if (!index.hasNext())
            return null; // Empty list

        /* If the field is either the play count or turnover count, accumulate
           the count over the plays, and return as a range from zero to the
           final count */
        IntegerRange result = null;
        if ((field == SinglePlay.NumericFields.PLAY_COUNT) ||
            (field == SinglePlay.NumericFields.TURNOVER_COUNT)) {
            int total = 0;
            while (index.hasNext())
                total += index.next().getIntValue(field);
            result = new IntegerRange(0, total);
        } // Want a total
        else {
            result = new IntegerRange(index.next().getIntValue(field));
            while (index.hasNext())
                result.extendRange(index.next().getIntValue(field));
        } // Want a range of play values
        return result;
    }

    // Find the wanted range within the given database of plays
    private RangePair getDbValuesRange(DataStore data,
                                       SinglePlay.NumericFields firstField,
                                       SinglePlay.NumericFields secondField)
    {
        IntegerRange first = null;
        IntegerRange second = null;

        // If neither field set, nothing to do!
        if ((firstField != null) || (secondField != null)) {
            Iterator<ArrayList<SinglePlay>> index = data.iterator();
            while (index.hasNext()) {
                ArrayList<SinglePlay> plays = index.next();
                if (firstField != null) {
                    IntegerRange newRange = getPlayValueRange(plays, firstField);
                    if (first == null)
                        first = newRange;
                    else
                        first.union(newRange);
                } // First field passed to fetch
                if (secondField != null) {
                    IntegerRange newRange = getPlayValueRange(plays, secondField);
                    if (second == null)
                        second = newRange;
                    else
                        second.union(newRange);
                } // First field passed to fetch
            } // While lists of plays in the datastore to process
        } // If at least one field specified to fetch
        return new RangePair(first, second);
    }

    // Initializes a new graph window for a single graph
    private void initGraphWindow(String graphTitle)
    {
        newWindow(graphTitle);
        // Window will contain only one graph; set the size accordingly
        _totalGraphRows = 1;
        _totalGraphColumns = 1;
    }

    /* Initializes a new graph window for graphs indexed by one play
       characteristic */
    private <P extends Enum<P>> void initGraphWindow(String graphTitle,
                                                     Class<P> indexType,
                                                     StatGraph emptyGraph)
    {
        newWindow(graphTitle);

        /* Create the panel to hold the graphs and labels. Use a gridbag
           layout so everything lines up correctly */
        _graphPane = new JPanel(new GridBagLayout());

        /* Add labels for the graphs. Grids are indexed from the upper left
           corner, starting to zero. First row is the graphs, so the labels
           are in row 2, starting at column zero */
        setColumnLabels(indexType, 1, 0);

        // Set up layouts for the graphs. This is cached
        _cachedConstraints = new GridBagConstraints();
        _cachedConstraints.ipadx = 5; // Ensure space between graphs
        _cachedConstraints.ipady = 5; // Ensure space above labels

        // Compute the size. Rows is 1. Columns is number of labels
        _totalGraphRows = 1;
        _totalGraphColumns = indexType.getEnumConstants().length;

        // Initialize the graph to use for empty graphs.
        _emptyGraph = emptyGraph;
    }

    /* Initializes a new graph window for graphs indexed by two play
       characteristics */
    private <P extends Enum<P>, Q extends Enum<Q>> void initGraphWindow(String graphTitle,
                                                                        Class<P> xIndexType,
                                                                        Class<Q> yIndexType,
                                                                        StatGraph emptyGraph)
    {
        newWindow(graphTitle);

        /* Create the panel to hold the graphs and labels. Use a gridbag
           layout so everything lines up correctly */
        _graphPane = new JPanel(new GridBagLayout());

        // Extract the labels for rows from the yIndexType
        Q[] groupValues = yIndexType.getEnumConstants();

        // Label the rows. All of these go in the first column
        GridBagConstraints labelLayout = new GridBagConstraints();
        labelLayout.gridx = 0;
        labelLayout.ipadx = 5; // Ensure space between labels and graphs

        int labelIndex;
        for (labelIndex = 0; labelIndex < groupValues.length; labelIndex++) {
            labelLayout.gridy = labelIndex;
            _graphPane.add(new JLabel(groupValues[labelIndex].name(),
                                      SwingConstants.CENTER),
                           labelLayout);
        } // For loop
        _haveRowLabels = true;
        
        /* Add labels for the columns. Grids are indexed from the upper left
           corner, starting to zero. The row for the labels is one after the
           rows indexed above. First column is 1, since column zero holds the
           row labels */
        setColumnLabels(xIndexType, groupValues.length, 1);

        // Set up layouts for the graphs. This is cached
        _cachedConstraints = new GridBagConstraints();
        _cachedConstraints.ipadx = 5; // Ensure space between graphs
        _cachedConstraints.ipady = 5; // Ensure space above labels

        // Compute the size. Number of labels gives the columns
        _totalGraphRows = groupValues.length;
        _totalGraphColumns = xIndexType.getEnumConstants().length;

        // Initialize the graph to use for empty graphs.
        _emptyGraph = emptyGraph;
    }
    
    // Returns a frame with the passed text as a header
    private void newWindow(String text)
    {
        /* WARNING: If an existing window has not been displayed, it will
           be overwritten and lost! */
        _window = new JFrame();
        _window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        _window.setTitle("Play ODAP results");
        _window.getContentPane().add(new JLabel(text, SwingConstants.CENTER),
                                    BorderLayout.PAGE_START);
    }

    /* Lays out a set of labels of characteristics in a row, starting at
       the wanted position in the grid */
    private <P extends Enum<P>>void setColumnLabels(Class<P> type, int row,
                                                    int startColumn)
    {
        // Extract the values of the group
        P[] groupValues = type.getEnumConstants();

        /* Create a layout description for the labels under the graphs, and
           use it to insert the labels */
        GridBagConstraints labelLayout = new GridBagConstraints();
        labelLayout.gridy = row;
        labelLayout.ipadx = 5; // Ensure space between labels

        int labelIndex;
        for (labelIndex = 0; labelIndex < groupValues.length; labelIndex++) {
            labelLayout.gridx = labelIndex + startColumn;
            _graphPane.add(new JLabel(groupValues[labelIndex].name(),
                                      SwingConstants.CENTER),
                           labelLayout);
        } // For loop
    }

    // Displays the current window and clears it from the class
    /* Clearing it is a compromise which prevents data from old graphs
       carrying over to later ones. Adding to existing graphs should never
       happen in practice, so this is acceptable */
    private void displayWindow()
    {
        // If a graph pane exists, insert it into the window before display
        if (_graphPane != null)
            _window.getContentPane().add(_graphPane, BorderLayout.CENTER);
        _window.pack();
        new JFrameThreadWrapper(_window, getWindowCount());
        clearGraphWindowData();
    }
    
    // Get the count of windows generated from a singleton field
    private static int getWindowCount()
    {
        if (_windowCount == null)
            _windowCount = new Integer(0);
        _windowCount++; // Only works in newer Java
        return _windowCount.intValue();
    }
    
    /* Inserts the passed graph at the position implied by the passed
       charactersitic value, laying out other graphs around it as needed */
    private <P extends Enum<P>> void layoutGraph(JPanel graph, P value) throws CloneNotSupportedException
    {
        /* This can only be called for a graph with one row. More than
           one implies a coding error */
        if (_totalGraphRows != 1)
            throw new IllegalArgumentException();
        else
            /* Columns are labeled in order of the enumeration values, so
               the ordinal value gives the insert column */
            layoutGraph(graph, 0, value.ordinal());
    }

    /* Inserts the passed graph at the position implied by the passed
       charactersitic value, laying out other graphs around it as needed */
    private <P extends Enum<P>, Q extends Enum<Q>> void layoutGraph(JPanel graph,
                                                                    P rowValue,
                                                                    Q columnValue) throws CloneNotSupportedException
    {
        /* Rows and columns are labeled in order of the enumeration values, so
           the ordinal value gives the insert position. */
        layoutGraph(graph, rowValue.ordinal(), columnValue.ordinal());
    }

    /* Inserts the passed graph in the wanted position, laying out other
       graphs around it as needed */
    private void layoutGraph(JPanel graph, int row, int column) throws CloneNotSupportedException
    {
        // If graph not passed or positions outside limits, have a big problem
        if (graph == null)
            throw new IllegalArgumentException("Graph to layout not specified");
        if ((row < 0) || (row >= _totalGraphRows))
            throw new IllegalArgumentException("Invalid layout row " + row +
                                               " range(0 - " +
                                               _totalGraphRows + ")");
        if ((column < 0) || (column >= _totalGraphColumns))
            throw new IllegalArgumentException("Invalid layout column " +
                                               column + " range(0 - " +
                                               _totalGraphColumns + ")");

        /* Need to fill in any unused positions with empty graphs. Assume
           calls are in order of the enumerated types, so any gaps since
           the last insert should be filled. Can partially protect against
           overwrite by NOT filling in other cases, although it is far
           from perfect (first fill column 4, then column 2, then column 5 will
           wipe out the graph for column 2)
           NOTE: Could get the same effect by filling the entire window with
           empty graphs and then inserting over them, but this will do lots of
           inserts that end up not being needed in practice */
        if (isAfterLastPos(row, column)) {
            incrementLastPos(); // Move off last graph inserted
            // Insert empty graphs until wanted position is reached
            /* NOTE: Java renderer can't handle multiple references to a single
               panel in a grid; only the last one is rendered. Empty graphs
               must be cloned on insert so each one is unique */
            while (isAfterLastPos(row, column)) {
                insertGraph((JPanel)_emptyGraph.clone(), _lastRow, _lastColumn);
                incrementLastPos();
            } // While holes in the layout to fill
        } // Wanted position after last graph inserted
        insertGraph(graph, row, column);
    }
    
    // Fills the remainder of the window with empty graphs
    private void fillRemainderWindow() throws CloneNotSupportedException
    {
        /* While the last position is before the last position in the graph,
           increment by 1 and fill in that spot.
           NOTE: Incrementing when on the last spot will cause an error, hence
           the need to increment before insertion and test */
        while (isAfterLastPos(_totalGraphRows - 1, _totalGraphColumns - 1)) {
            incrementLastPos();
            /* NOTE: Java renderer can't handle multiple references to a single
               panel in a grid; only the last one is rendered. Empty graphs
               must be cloned on insert so each one is unique */
            insertGraph((JPanel)_emptyGraph.clone(), _lastRow, _lastColumn);
        } // While holes in the layout to fill
    }

    /* Returns true if the passed position is beyond the last position a
       graph was inserted */
    private boolean isAfterLastPos(int row, int column)
    {
        return ((row > _lastRow) ||
                ((row == _lastRow) && (column > _lastColumn)));
    }
    
    // Increments the last position by 1
    /* NOTE: To ensure the graph plot has no holes, an insert after the last
       position is always required, so this is the only method to change the
       last graph inserted */
    private void incrementLastPos()
    {
        _lastColumn++;
        if (_lastColumn >= _totalGraphColumns) {
            _lastRow++;
            _lastColumn = 0;
        }
        if (_lastRow >= _totalGraphRows)
            // Called improperly, big problem
            throw new IndexOutOfBoundsException();
    }

    // Inserts a graph at the wanted position.
    /* WARNING: Since this is a utility method, it does not check that the
       wanted position is valid! Callers need to handle it. */
    private void insertGraph(JPanel graph, int row, int column)
    {
        _cachedConstraints.gridx = column;
        _cachedConstraints.gridy = row;
        // Include space for the row label, if needed
        if (_haveRowLabels)
            _cachedConstraints.gridx++;
        _graphPane.add(graph, _cachedConstraints);
    }

    // Test code
    public static void main(String[] args) throws CloneNotSupportedException
    {
        DataStore testPlays = new DataStore();
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                            SinglePlay.DownNumber.FIRST_DOWN,
                                            10, 50, 45, 0, 0, 11, false));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_RIGHT,
                                            SinglePlay.DownNumber.THIRD_DOWN,
                                            9, 50, 45, 0, 0, -5, false));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_MIDDLE,
                                            SinglePlay.DownNumber.FIRST_DOWN,
                                            7, 9, 45, 0, 0, -2, true));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                            SinglePlay.DownNumber.THIRD_DOWN,
                                            3, 9, 45, 0, 0, 0, true));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_RIGHT,
                                            SinglePlay.DownNumber.FIRST_DOWN,
                                            1, 95, 45, 0, 0, 0, false));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_MIDDLE,
                                            SinglePlay.DownNumber.THIRD_DOWN,
                                            12, 95, 45, 0, 0, -2, false));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                            SinglePlay.DownNumber.FIRST_DOWN,
                                            15, 50, 45, 0, 0, 5, true));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_RIGHT,
                                            SinglePlay.DownNumber.THIRD_DOWN,
                                            17, 50, 45, 0, 0, 11, true));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_MIDDLE,
                                            SinglePlay.DownNumber.FIRST_DOWN,
                                            20, 95, 45, 0, 0, 30, false));
        testPlays.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                            SinglePlay.DownNumber.THIRD_DOWN,
                                            14, 5, 45, 0, 0, 11, true));

        PlayGraphGenerator test = new PlayGraphGenerator(testPlays);

        test.generateGraph(new ScatterPlotFactory(SinglePlay.NumericFields.DISTANCE_NEEDED,
                                                  SinglePlay.NumericFields.DISTANCE_GAINED),
                           null,
                           new String("Very long test string to make the code scream"));
        test.generateGraph(new ScatterTurnoverFactory(SinglePlay.NumericFields.DISTANCE_NEEDED,
                                                      SinglePlay.NumericFields.DISTANCE_GAINED,
                                                      true, true),
                           SinglePlay.DownNumber.class, null,
                           new String("Very long test string to make the code scream 2"));
        test.generateGraph(new PlayResultsFactory(),
                           SinglePlay.DownNumber.class,
                           SinglePlay.FieldLocation.class, null,
                           new String("Very long test string to make the code scream 3"));
    }
}