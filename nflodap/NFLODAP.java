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
package nflodap;

import java.util.*;
import java.lang.*;
import java.io.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;
import javax.swing.event.*;
import nflodap.datastore.*;
import nflodap.graphs.*;

// The main driver for the NFL play ODAP system.
public final class NFLODAP {

    /* Enumerated type of SinglePlay enumerated types. It partly defines the
       play characteristics that can be used to manipulate play data.
       NOTE: Enum types are themselves objects, so they can have constructors
       and methods. This class stores the actual enumerated type class each
       entry of THIS enumerated type represents, so they can be extracted when
       doing method calls. This enumerated type can be manipulated like any
       other in the code, saving lots of work
       WARNING: Several categories also have corresponding characteristics
       based on integer values, defined by NumericFields in SinglePlay.java.
       The names must match, or serious user confusion will result! */
    public enum PlayCatCharacteristics
    {
        DOWN_NUMBER(EnumWrapper.create(SinglePlay.DownNumber.class)),
        DISTANCE_NEEDED(EnumWrapper.create(SinglePlay.DistanceNeeded.class)),
        FIELD_LOCATION(EnumWrapper.create(SinglePlay.FieldLocation.class)),
        TIME_REMAINING(EnumWrapper.create(SinglePlay.TimeRemaining.class)),
        SCORE_DIFFERENTIAL(EnumWrapper.create(SinglePlay.ScoreDifferential.class)),
        PLAY_TYPE(EnumWrapper.create(SinglePlay.PlayType.class));

        EnumWrapper<?> _type; // Type this enum value represents

        // Constructor
        PlayCatCharacteristics(EnumWrapper<?> type)
        {
            _type = type;
        }

        // Return the actual type represented by this class.
        /* WARNING: Its returned as a wildcard, which is normally not good
           Java practice. In this case it will work, since the value will
           almost always be passed into a method that needs a generic enum.
           Remember to extract the actual class from the wrapper first */
        public EnumWrapper<?> getEnumType()
        {
            return _type;
        }
    };

    // Types of data graphs available
    public enum PlayGraphTypes { COUNTS, DISTANCE_RESULTS, SCATTER_PLOT,
            TURNOVER_SCATTER_PLOT };

    // Teams to analyze plays for
    private GuiText _ourTeam;
    private GuiText _opposition;
    private GuiText _ourSimiliar;
    private GuiText _oppSimiliar;
    private String _graphTitle; // Depends on the teams, so need to cache
    
    PlayGraphGenerator _data; // The data analyzer
    
    // Characteristics to use to group plays, the pivot ODAP operation
    private PlayCatMenu _firstPivot;
    private PlayCatMenu _secondPivot;

    // Values to select plays to analyze, the slice ODAP operation
    private EnumMap<PlayCatCharacteristics, PlayCatFilter> _catSlice;
    private EnumMap<SinglePlay.NumericFields, ReadIntFilter> _intSlice;

    // Wanted graph for data analysis
    private GraphSelector _graph;

    // Error from argument parsing
    private String _parseError = null;

    // Constructor. 
    public NFLODAP()
    {
        _ourTeam = null;
        _opposition = null;
        _ourSimiliar = null;
        _oppSimiliar = null;
        _graphTitle = null;
        
        _data = null;
    
        _firstPivot = null;
        _secondPivot = null;

        _catSlice = new EnumMap<PlayCatCharacteristics, PlayCatFilter>(PlayCatCharacteristics.class);

        _intSlice = new EnumMap<SinglePlay.NumericFields, ReadIntFilter>(SinglePlay.NumericFields.class);

        _graph = null;

        _parseError = null;
    }

    // Convert a category play characteristic value to a name
    // NOTE: Has package visibility
    static PlayCatCharacteristics playCatNameToValue(String name)
    {
        /* Handle the conversion in a try...catch blocks, to handle the case
           where an unknown name is supplied. Return NULL for this case  */
        PlayCatCharacteristics value = null;
        try {
            value = Enum.valueOf(PlayCatCharacteristics.class, name);
        }
        catch (IllegalArgumentException e) {
            value = null;
        }
        return value;
    }

    // Runs graph generation through a GUI
    private void runByGUI() throws Exception
    {
        // Create GUI components
        _ourTeam = new GuiText(5, false);
        _opposition = new GuiText(5, false);
        _ourSimiliar = new GuiText(20, true);
        _oppSimiliar = new GuiText(20, true);
        _firstPivot = new PlayCatMenu();
        _secondPivot = new PlayCatMenu();
        // Item selected in first can't be used for second
        _firstPivot.addListener(_secondPivot);

        for (PlayCatCharacteristics tempIndex : PlayCatCharacteristics.values()) {
            PlayCatFilter newCatFilter = new PlayCatFilter(tempIndex);
            // Add the menus as listeners on both pivot menus
            _firstPivot.addListener(newCatFilter);
            _secondPivot.addListener(newCatFilter);
            _catSlice.put(tempIndex, newCatFilter);
        }
        _graph = new GraphSelector();
        
        // Create the GUI
        final JPanel guiPanel = new JPanel();
        guiPanel.setLayout(new BoxLayout(guiPanel, BoxLayout.Y_AXIS));

        // Add the first part, the teams to analyze.
        /* Need three components in a row, which stay there when resized. This
           requires a grid bag layout */
        JPanel teamPanel = new JPanel(new GridBagLayout());
        GridBagConstraints layout = new GridBagConstraints();
        layout.gridx = 0;
        layout.gridy = 0;
        layout.gridwidth = 3; // Force label to cover three items below
        teamPanel.add(new JLabel("Teams to analyze. Must be different",
                                 SwingConstants.CENTER),
                      layout);

        layout.gridy = 1;
        layout.gridwidth = 1;
        teamPanel.add(_ourTeam.getGui(), layout);
        layout.gridx = 1;
        teamPanel.add(new JLabel(" vs ", SwingConstants.CENTER), layout);
        layout.gridx = 2;
        teamPanel.add(_opposition.getGui(), layout);
        guiPanel.add(teamPanel);
        
        // Similiar teams. Another Grid Bag Layout. 
        JPanel similiarPanel = new JPanel(new GridBagLayout());
        layout.gridx = 0;
        layout.gridy = 0;
        layout.gridwidth = 2; // Force label to cover two items below
        similiarPanel.add(new JLabel("Similiar teams to include. Seprate multiple with spaces. No duplicates allowed",
                                     SwingConstants.CENTER),
                          layout);
        layout.gridwidth = 1;
        layout.gridy = 1;
        similiarPanel.add(new JLabel("Left team", SwingConstants.RIGHT),
                          layout);
        layout.gridx = 1;
        similiarPanel.add(_ourSimiliar.getGui(), layout);
        layout.gridy = 2;
        layout.gridx = 0;
        similiarPanel.add(new JLabel("Right team", SwingConstants.RIGHT),
                          layout);
        layout.gridx = 1;
        similiarPanel.add(_oppSimiliar.getGui(), layout);
        guiPanel.add(similiarPanel);
        
        // Add the second part, grouping characteristics. Another Grid Bag Layout
        JPanel pivotPanel = new JPanel(new GridBagLayout());
        layout.gridx = 0;
        layout.gridy = 0;
        layout.gridwidth = 2; // Force label to cover two items below
        pivotPanel.add(new JLabel("Characteristics to group plays for graphs",
                                  SwingConstants.CENTER),
                       layout);
        layout.gridwidth = 1;

        layout.gridy = 1;
        pivotPanel.add(new JLabel("First", SwingConstants.RIGHT), layout);
        layout.gridy = 2;
        pivotPanel.add(_firstPivot.getDropdown(), layout);
        layout.gridx = 1;
        layout.gridy = 1;
        pivotPanel.add(new JLabel("Second", SwingConstants.RIGHT), layout);
        layout.gridy = 2;
        pivotPanel.add(_secondPivot.getDropdown(), layout);
        guiPanel.add(pivotPanel);

        // Add the third part, category based filtering
        guiPanel.add(new JLabel("Filter plays by category values",
                                SwingConstants.CENTER));

        // Another Grid Bag Layout. Positions are based on enum order.
        JPanel catFilterPanel = new JPanel(new GridBagLayout());
        layout.gridx = 0;
        layout.gridy = 0;
        for (PlayCatCharacteristics tempIndex : PlayCatCharacteristics.values()) {
            catFilterPanel.add(new JLabel(tempIndex.toString(),
                                          SwingConstants.CENTER),
                               layout);
            layout.gridy = 1;
            catFilterPanel.add(_catSlice.get(tempIndex).getDropdown(), layout);
            layout.gridy = 0;
            layout.gridx++; // Move to next column
        } // Loop inserting menus
        guiPanel.add(catFilterPanel);
        
        /* Add the fourth part, range based filtering. Note that some of these
           filter the same things as categories, allowing much fined grain
           filtering. In theory, should disallow both, but not worth it */
        guiPanel.add(new JLabel("Filter plays by value ranges",
                                SwingConstants.CENTER));

        for (SinglePlay.NumericFields tempIndex : SinglePlay.NumericFields.values()) {
            // Filtering by play counts is not allowed
            if ((tempIndex != SinglePlay.NumericFields.PLAY_COUNT) &&
                (tempIndex != SinglePlay.NumericFields.TURNOVER_COUNT)) {
                _intSlice.put(tempIndex, new ReadIntFilter(tempIndex));
                guiPanel.add(_intSlice.get(tempIndex).getMenu());
            }
        } // For loop on field types
                
        // Add the fifth part, the graph to generate.
        guiPanel.add(_graph.getGui());

        /* Add the last part, a button to generate the graphs for the current
           inputs */
        JButton generateButton = new JButton("Generate Graphs");
        generateButton.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent event)
                {
                    try {
                        generateGraph();
                    }
                    catch (IllegalArgumentException e) {
                        // Assume due to bad input. Log it and show to user
                        System.out.println(NFLODAP.this);
                        System.out.println(e);
                        JOptionPane.showMessageDialog(guiPanel, e,
                                                      "Graph generation error", 
                                                      JOptionPane.ERROR_MESSAGE);
                    }
                    catch (Exception e) {
                        /* The method can't rethrow, so it needs to deal with
                           it. The options are to present it to the user, log
                           it, and kill the process. Since this type of
                           exception indicates something that will come up
                           repeatedly, this code logs and kills. This is
                           not nice, but forcing people to manually kill a
                           buggy program is even less nice
                           NOTE: If other types of exceptions appear for
                           configuration errors, they should be moved to the
                           clause above */
                        System.out.println(NFLODAP.this);
                        e.printStackTrace();
                        System.exit(-1); // As noted above, not very nice
                    }
                }
            } // Annonymous class
            );
        guiPanel.add(generateButton);

        /* Display it. Note that if its closed, the graphs will survive until
           killed themselves */
        JFrame guiFrame = new JFrame();
        guiFrame.setTitle("NFLODAP");
        guiFrame.getContentPane().add(guiPanel);
        guiFrame.pack();
        new JFrameThreadWrapper(guiFrame, 1);
    }

    // Returns true if the passed string represents a switch
    private boolean isSwitch(String arg)
    {
        // A switch is a dash, then a single letter
        if (arg.length() != 2)
            return false;
        else if (arg.charAt(0) != '-')
            return false;
        else
            /* Some consider this an anti-pattern, but it is the most
               efficient test */
            return ((arg.charAt(1) < '0') || (arg.charAt(1) > '9'));
    }

    /* Extracts similar teams from the output. Returns true if done
       successfully */
    public boolean getSimiliarTeams(String[] args, int switchArg,
                                    int nextSwitch, boolean ourSimiliar)
    {
        // Not supplying teams is an error
        if (switchArg == (nextSwitch - 1))
            _parseError = new String("Simmiliar team switch with no team names");
        /* If the variable to set already has a value, the switch was set
           twice. This is also an error */
        else if ((ourSimiliar && (_ourSimiliar.getValue() != null)) ||
                 ((!ourSimiliar) && (_oppSimiliar.getValue() != null)))
            _parseError = new String("Switch " + args[switchArg] + " specified twice");
        else {
            /* Consolidate the entries into one string. Its needed to match
               the GUI input */
            StringBuffer stream = new StringBuffer();
            int index;
            for (index = switchArg + 1; index < nextSwitch; index++) {
                // Seperate values with spaces
                if (index != (switchArg + 1))
                    stream.append(' ');
                stream.append(args[index]);
            } // For args to process
            if (ourSimiliar)
                _ourSimiliar = new GuiText(stream.toString());
            else
                _oppSimiliar = new GuiText(stream.toString());
        } // No obvious errors
        return (_parseError == null); // No error message means success
    }

    /* Extracts characteristics to group plays in the output, the ODAP pivot
       operation */
    public boolean getPivots(String[] args, int switchArg, int nextSwitch)
    {
        // Not supplying the group characteristic is an error
        if (switchArg == (nextSwitch - 1))
            _parseError = new String("Characteristic for grouping missing");
        // Supplying more than two is also an erorr
        else if (switchArg < (nextSwitch - 3))
            _parseError = new String("More than two grouping characteristics supplied");
        /* If the variables to set already have a value, the switch was set
           twice. This is also an error.
           NOTE: Only need to check the first */
        else if (_firstPivot != null)
            _parseError = new String("Switch " + args[switchArg] + " specified twice");
        else {
            PlayCatCharacteristics firstPivot = playCatNameToValue(args[switchArg + 1]);
            if (firstPivot == null) // Conversion failed
                _parseError = new String("Grouping type " + args[switchArg + 1]
                                         + " unknown");
            else
                _firstPivot = new PlayCatMenu(firstPivot);
            
            if ((switchArg == (nextSwitch - 3)) && (_parseError == null)) {
                PlayCatCharacteristics secondPivot = playCatNameToValue(args[switchArg + 2]);
                if (secondPivot == null) // Conversion failed
                    _parseError = new String("Grouping type " +
                                             args[switchArg + 2] + " unknown");
                else
                    _secondPivot = new PlayCatMenu(secondPivot);
            } // Second argument and no error with the first
        } // No obvious errors
        return (_parseError == null); // No error message means success
    }

    /* Extracts category based characteristics to select plays, the ODAP slice
       operation */
    public boolean getCatSlice(String[] args, int switchArg, int nextSwitch)
    {
        // This switch requires exactly two arguments
        if (switchArg > (nextSwitch - 3))
            _parseError = new String("Vales for characteristic play selection missing");
        else if (switchArg < (nextSwitch - 3))
            _parseError = new String("Extra values for characteristic play selection supplied");
        else {
            /* Extract the first argument, which is the characteristic. Use a
               try...catch block to handle an unknown name */
            PlayCatCharacteristics sliceCat = playCatNameToValue(args[switchArg + 1]);
            if (sliceCat == null) // Conversion failed
                _parseError = new String("Play selection characteristic "
                                         + args[switchArg + 1] + " unknown");
            else {
                /* If a value already exists for this characteristic, it was
                   specified twice, which is an error */
                if (_catSlice.containsKey(sliceCat))
                    _parseError = new String("Play selection characteristic "
                                             + sliceCat + " specified twice");
                else {
                    /* Get the value for play selection from the second
                       argument. This is also done in a try..catch block
                       TRICKY NOTE: Note very carefully how the class must be
                       extracted from the enum to do the conversion */
                    EnumValueWrapper<?> value = null;
                    try {
                        value = EnumValueWrapper.create(sliceCat.getEnumType(),
                                                        args[switchArg + 2]);
                    }
                    catch (IllegalArgumentException e) {
                        _parseError = new String("Characteristic selection value "
                                                 + args[switchArg + 2]
                                                 + " undefined for "
                                                 + sliceCat);
                    } // Catch block
                    if (value != null) // Extracted successfully
                        _catSlice.put(sliceCat,
                                      new PlayCatFilter(sliceCat, value));
                } // Characteristic not already specified
            } // Characteristic read
        } // Correct number of arguments
        return (_parseError == null); // No error message means success
    }

    /* Extracts numeric based characteristics to select plays, the ODAP slice
       operation */
    public boolean getIntSlice(String[] args, int switchArg, int nextSwitch)
    {
        // This switch requires exactly three arguments
        if (switchArg > (nextSwitch - 4))
            _parseError = new String("Vales for integer range play selection missing");
        else if (switchArg < (nextSwitch - 4))
            _parseError = new String("Extra values for interger range play selection supplied");
        else {
            /* Extract the first argument, which is the characteristic. Use a
               try...catch block to handle an unknown name */
            SinglePlay.NumericFields sliceCat = null;
            try {
                sliceCat = Enum.valueOf(SinglePlay.NumericFields.class, args[switchArg + 1]);
            }
            catch (IllegalArgumentException e) {
                _parseError = new String("Integer range play selection characteristic "
                                         + args[switchArg + 1] + " unknown");
            }
            if (sliceCat != null) { // Characteristic read succussfully
                /* If a value already exists for this characteristic, it was
                   specified twice, which is an error */
                if (_intSlice.containsKey(sliceCat))
                    _parseError = new String("Play selection characteristic "
                                             + sliceCat + " specified twice");
                else {
                    /* Get the range for the characteristic from the second
                       and third aguments. Use a try..catch block  to handle
                       non-numeric input */
                    ReadIntFilter value = null;
                    try {
                        value = new ReadIntFilter(sliceCat, 
                                                  Integer.parseInt(args[switchArg + 2]),
                                                  Integer.parseInt(args[switchArg + 3]));
                    }
                    catch (NumberFormatException e) {
                        _parseError = new String(args[switchArg + 1]
                                                 + " integer filter range "
                                                 + args[switchArg + 2] + " "
                                                 + args[switchArg + 3]
                                                 + " invalid");
                    } // Catch block
                    if (value != null) // Extracted successfully
                        _intSlice.put(sliceCat, value);
                } // Characteristic not already specified
            } // Characteristic read
        } // Correct number of arguments
        return (_parseError == null); // No error message means success
    }

    // Extracts the graph to generate from the arguments
    public boolean getGraphType(String[] args, int graphNamePos, int nextSwitch)
    {
        // Check the graph name is present
        if (graphNamePos == nextSwitch)
            _parseError = new String("Name of graph to generate missing");
        else {
            /* Extract the name of the graph. Use a try...catch block to
               handle an unknown name */
            PlayGraphTypes graph = null;
            try {
                graph = Enum.valueOf(PlayGraphTypes.class, args[graphNamePos]);
            }
            catch (IllegalArgumentException e) {
                _parseError = new String("Graph type " + args[graphNamePos]
                                         + " unknown");
            }
            if (graph != null) { // Characteristic read succussfully
                /* If graph needs no extra arguments, can generate it now,
                   otherwise call seperate routine to generate it */
                if ((graph == PlayGraphTypes.SCATTER_PLOT) ||
                    (graph == PlayGraphTypes.TURNOVER_SCATTER_PLOT))
                    _graph = getScatterPlot(args, graphNamePos + 1, nextSwitch, 
                                            graph);
                else
                    _graph = new GraphSelector(graph, null, null);
                if ((_parseError == null) && (_graph == null)) 
                    /* SERIOUS PROBLEM: Have a graph type without the factory
                       needed to generate it */
                    _parseError = new String(graph + " graph not defined currently");
            } // Type of graph read from input successfully
        } // Correct number of arguments
        return (_parseError == null); // No error message means success
    }

    // Extract a scatter plot graph configuration
    private GraphSelector getScatterPlot(String [] args, int firstArg,
                                         int firstSwitchPos,
                                         PlayGraphTypes graph)
    {
        // These graphs require two arguments, the play fields for the plot
        if (firstArg > (firstSwitchPos - 2)) {
            _parseError = new String(graph + " graph configuration invalid, missing paramters");
            return null;
        }
        else if (firstArg < (firstSwitchPos - 2)) {
            _parseError = new String(graph + " graph configuration invalid, extra paramters");
            return null;
        }

        /* Extract the two play characteristics for the graph. Do both in
           try..catch blocks to handle unknown types */
        SinglePlay.NumericFields firstField = null;
        SinglePlay.NumericFields secondField = null;
        try {
            firstField = Enum.valueOf(SinglePlay.NumericFields.class, args[firstArg]);
        }
        catch (IllegalArgumentException e) {
            _parseError = new String(graph + " graph first play field unknown");
        }
        if (firstField != null) { // Read first successfully
            try {
                secondField = Enum.valueOf(SinglePlay.NumericFields.class, args[firstArg + 1]);
            }
            catch (IllegalArgumentException e) {
                _parseError = new String(graph + " graph second play field unknown");
            }
        } // First field read successfully
        if (secondField != null) {
            // NOTE: Operator == works for enums even when different objects
            if (firstField == secondField) {
                _parseError = new String("Play fields for " + graph + " must be different");
                return null;
            } // Same field passed twice
            else
                return new GraphSelector(graph, firstField, secondField);
        } // Second field read successfully
        else
            // Error reading data
            return null;
    }

    // Helper method for converting enum values into data slices on that value
    private <P extends Enum<P>> PlaySlice getSliceHelper(EnumValueWrapper<P> value,
                                                         PlaySlice chain)
    {
        return PlaySliceFactory.getSlice(value.getValue(), value.getEnum(),
                                         chain);
    }

    // Generates the graph
    public void generateGraph() throws Exception, CloneNotSupportedException
    {
        // Reload data as needed
        if ((_data == null) || _ourTeam.getChanged() ||
            _opposition.getChanged() || _ourSimiliar.getChanged() ||
            _oppSimiliar.getChanged()) {
            if ((_ourTeam.getValue() == null) || (_opposition.getValue() == null))
                throw new IllegalArgumentException("Teams to analyze must be set");
            else if (_ourTeam.getValue().equals(_opposition.getValue()))
                throw new IllegalArgumentException("Team " + _ourTeam.getValue() + " specified twice");
            NFLqualityControl teamFilter = new NFLqualityControl(_ourTeam.getValue(),
                                                                 _opposition.getValue(),
                                                                 _ourSimiliar.getValue(),
                                                                 _oppSimiliar.getValue());
            _graphTitle = teamFilter.toString();
            // For now, hard code file path
            PlayLoader playLoader = new PlayLoader("Data");
            // For now, always graph a single season of plays
            _data = new PlayGraphGenerator(playLoader.loadPlays(teamFilter, 1));
            // Clear changed statuses
            _ourTeam.resetChangedStatus();
            _opposition.resetChangedStatus();
            _ourSimiliar.resetChangedStatus();
            _oppSimiliar.resetChangedStatus();
        }

        /* Assemble the filter object. Iterate through the value range based
           filters first and build the filter */
        PlaySlice filter = null;
        Iterator<Map.Entry<SinglePlay.NumericFields, ReadIntFilter>> intData = _intSlice.entrySet().iterator();
        while (intData.hasNext()) {
            Map.Entry<SinglePlay.NumericFields, ReadIntFilter> intTemp = intData.next();
            if (intTemp.getValue().getRange() != null)
                filter = PlaySliceFactory.getSlice(intTemp.getKey(),
                                                   intTemp.getValue().getRange(),
                                                   filter);
        } // While loop through map
            
        /* Efficiency for category slices greatly depends on the order, which
           is the REVERSE of how they appear in the map. Convert it to a
           collection, then an array, and iterate through in reverse.
           SUBTLE NOTE: Why not reverse the enum values? They are also used for
           menu output, and users expect them in a certain order
           TRICKY NOTE: Processing of the EnumValueWrapper class requires a
           helper method. The reason is that two pieces of data are read from it
           (the enum class and the value). If both are wildcarded, the compiler
           can't enforce that they are for the same enum. The helper method has
           only one wildcard, which fixes the issue */
        PlayCatFilter[] tempCatValues = new PlayCatFilter[0];
        tempCatValues = _catSlice.values().toArray(tempCatValues);
        int index;
        for (index = tempCatValues.length - 1; index >= 0; index--)
            if (tempCatValues[index].getValue() != null)
                filter = getSliceHelper(tempCatValues[index].getValue(),
                                        filter);

        // Extract pivot values from the relevant menus
        PlayCatCharacteristics firstPivot = _firstPivot.getValue();
        PlayCatCharacteristics secondPivot = _secondPivot.getValue();

        // If have a second pivot but no first, move it over
        if ((firstPivot == null) && (secondPivot != null)) {
            firstPivot = secondPivot;
            secondPivot = null;
        }

        if (firstPivot == null)
            _data.generateGraph(_graph.getGraph(), filter, _graphTitle);
        else if (secondPivot == null) // Single dimensional grouping
            _data.generateGraph(_graph.getGraph(),
                                firstPivot.getEnumType().getEnum(),
                                filter, _graphTitle);
        else // Two dimensional grouping
            _data.generateGraph(_graph.getGraph(),
                                firstPivot.getEnumType().getEnum(),
                                secondPivot.getEnumType().getEnum(), filter,
                                _graphTitle);
    }

    private void runByCommandLine(String[] args) throws Exception
    {                                                        
        Boolean valid = true;
        int switchArg = -1;
        if (args.length < 3) {
            _parseError = new String("Teams to analyze and graph type required");
            valid = false;
        }
        else { 
            /* First three arguments are the two teams to analyze, and the type
               of graph to generate. The graph may need more arguments, so find
               them by looking for the first switch. */
            _ourTeam = new GuiText(args[0]);
            _opposition = new GuiText(args[1]);
            switchArg = 3;
            while ((switchArg < args.length) &&
                   (!isSwitch(args[switchArg])))
                switchArg++;
            valid = getGraphType(args, 2, switchArg);
        }
        while ((switchArg < args.length) && valid) {
            /* Find the position of the next switch, which gives the
               values (if any) for THIS switch */
            int nextSwitch = switchArg + 1;
            /* NOTE: The test below always works, because && short circuits.
               If the first part fails, the second never gets executed,
               avoiding the exception that would otherwise happen for array out
               of bounds */
            while ((nextSwitch < args.length) && (!isSwitch(args[nextSwitch])))
                nextSwitch++;

            if (args[switchArg].charAt(1) == 'u')
                valid = getSimiliarTeams(args, switchArg, nextSwitch, true);
            else if (args[switchArg].charAt(1) == 'o')
                valid = getSimiliarTeams(args, switchArg, nextSwitch,
                                         false);
            else if (args[switchArg].charAt(1) == 'p')
                valid = getPivots(args, switchArg, nextSwitch);
            else if (args[switchArg].charAt(1) == 'c')
                valid = getCatSlice(args, switchArg, nextSwitch);
            else if (args[switchArg].charAt(1) == 'i')
                valid = getIntSlice(args, switchArg, nextSwitch);
            else {
                _parseError = new String("Switch " + args[switchArg] + " unknown");
                valid = false;
            }
            switchArg = nextSwitch;
        } // While arguments to parse and no errors

        if (valid) {
            /* Confirm argument consistency. Pivot categories must be different,
               and can't be used to slice data */
            if ((_firstPivot != null) && (_secondPivot != null) &&
                (_firstPivot.getValue() == _secondPivot.getValue())) {
                _parseError = new String("Grouping specification invalid, same characteristic given twice");
                valid = false;
            }
            /* NOTE: && does short circuit evaluation, so if the first part
               below is false, the second part is never called. This avoids the
               null pointer exception that would otherwise be thrown */
            else if ((_firstPivot != null) &&
                     _catSlice.containsKey(_firstPivot.getValue())) {
                _parseError = new String("Filter value supplied for grouping category " + _firstPivot.getValue());
                valid = false;
            }
            else if ((_secondPivot != null) &&
                     _catSlice.containsKey(_secondPivot.getValue())) {
                _parseError = new String("Filter value supplied for grouping category " + _secondPivot);
                valid = false;
            }
        } // Arguments processed successfully

        if (!valid)
            throw new IllegalArgumentException(_parseError);
        else {
            /* The graph generation routine expects these classes to exist,
               even if they have no data in them. Default them here */
            if (_ourSimiliar == null)
                _ourSimiliar = new GuiText(null);
            if (_oppSimiliar == null)
                _oppSimiliar = new GuiText(null);
            if (_firstPivot == null)
                _firstPivot = new PlayCatMenu(null);
            if (_secondPivot == null)
                _secondPivot = new PlayCatMenu(null);
            System.out.println(this);
            generateGraph();
        } // No errors parsing program arguments
    }
        
    // Executes the class on the passed arguments
    public void run(String[] args) throws Exception
    {
        if (args.length == 0)
            // If no paramters, use the GUI version
            runByGUI();
        else
            runByCommandLine(args);
    }

    // Output the contents of the class as a string, not including play data
    public String toString() {
        StringBuffer buff = new StringBuffer();
        buff.append("_ourTeam:" + _ourTeam + " _opposition:" + _opposition + "\n");
        buff.append("_ourSimiliar:" + _ourSimiliar + "\n");
        buff.append("_oppSimiliar:" + _oppSimiliar + "\n");
        buff.append("_firstPivot:" + _firstPivot + " _secondPivot:"
                    + _secondPivot + "\n");

        buff.append("Category based filters\n");
        if (_catSlice.size() == 0)
            buff.append("none\n");
        else {
            Iterator<Map.Entry<PlayCatCharacteristics, PlayCatFilter>> catData = _catSlice.entrySet().iterator();
            while (catData.hasNext()) {
                Map.Entry<PlayCatCharacteristics, PlayCatFilter> catTemp = catData.next();
                buff.append(catTemp.getKey() + ":" + catTemp.getValue() + "\n");
            } // While loop through map
        } // Category slice map has entries

        buff.append("Integer range based filters\n");
        if (_intSlice.size() == 0)
            buff.append("none\n");
        else {
            Iterator<Map.Entry<SinglePlay.NumericFields, ReadIntFilter>> intData = _intSlice.entrySet().iterator();
            while (intData.hasNext()) {
                Map.Entry<SinglePlay.NumericFields, ReadIntFilter> intTemp = intData.next();
                buff.append(intTemp.getKey() + ":" + intTemp.getValue() + "\n");
            } // While loop through map
        } // Integer range slice map has entries
        buff.append("Graph:" + _graph + "\n");
        return buff.toString();
    }

    // Mainline wrapper around class
    public static void main(String[] args) throws Throwable
    {
        try {
            NFLODAP worker = new NFLODAP();
            worker.run(args);
        }
        catch (Throwable e) {
            System.out.println("Exeption " + e + " caught");
            throw e; // Force improper termination, so error is obvious
        }
    }
}
