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

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;
import java.util.*;
import nflodap.datastore.*;
import nflodap.graphs.*;

/* Class to find the graph to generate. It can either be done through the
   internal menu or specified, in which case a menu is not generated 
   NOTE: This class can be designed one of two ways: either regenerate the
   graph factory every time a relavent input changes, or only generate it when
   needed based on current input. Generating a factory is somewhat expensive,
   so this class takes the latter approach
   NOTE: The menu for this class is so complicated that the class uses a box
   design pattern. The menu is designed as its own inner class that can modify
   the graph generator parameters in the other class
   NOTE: This class has package visibility */
final class GraphSelector
{
    private NFLODAP.PlayGraphTypes _graphType; // Graph to generate

    // Numeric play fields used by graphs. Not required by all
    private SinglePlay.NumericFields _firstGraphField;
    private SinglePlay.NumericFields _secondGraphField;
    
    GuiGraphFactory _graphGui; // Gui to generate graph types

    // Inner class to implement the actual graph selection panel
    private class GuiGraphFactory extends JPanel
    {
        JPanel _flipPanel; // Panel used to show inputs for different graphs
        // Labels for different data entry panels to display
        EnumMap<NFLODAP.PlayGraphTypes, String> _graphPanelTypes;

        JComboBox _graphSelector; // Selector on graph to generate

        // Play fields used for plots
        JComboBox _firstFieldMenu;
        JComboBox _secondFieldMenu;

        /* Constructor for the graph selection panel. Thanks to its multiple
           components, the panel is rather complex */
        public GuiGraphFactory()
        {
            super(new GridBagLayout());

            // The graph needs to have default values. Here they are
            // NOTE: This modifies the outer class
            _graphType = NFLODAP.PlayGraphTypes.COUNTS;
            _firstGraphField = SinglePlay.NumericFields.DISTANCE_NEEDED;
            _secondGraphField = SinglePlay.NumericFields.DISTANCE_GAINED;
            
            /* Set up panels to allow entry of data needed for different types
               of graphs. Some graphs need the same type of data, so they
               should all use the same labels. */
            // Currently, only panel neaded takes two play type fields
            String fieldPanelName = new String("PlayFields");

            /* Second menu for entering play fields. Affected by the first, so
               need to do first */
            _secondFieldMenu = new JComboBox();
            for (Object e : SinglePlay.NumericFields.class.getEnumConstants())
                _secondFieldMenu.addItem(e); 
            // Second menu can't contain item selected on the first
            _secondFieldMenu.removeItem(_firstGraphField);
            // Overide the menu default to the actual default value
            _secondFieldMenu.setSelectedItem(SinglePlay.NumericFields.DISTANCE_GAINED);
            
            // The menu needs an action listener, and here it is
            _secondFieldMenu.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent event)
                    {
                        _secondGraphField = (SinglePlay.NumericFields)_secondFieldMenu.getSelectedItem();
                    }
                } // Annonymous class
                );
                
            /* First field menu. Item selected on this menu automatically
               removed from the other menu */
            _firstFieldMenu = new JComboBox();
            for (Object e : SinglePlay.NumericFields.class.getEnumConstants())
                _firstFieldMenu.addItem(e); 
            // The menu needs an action listener, and here it is
            _firstFieldMenu.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent event)
                    {
                        SinglePlay.NumericFields newValue = (SinglePlay.NumericFields)_firstFieldMenu.getSelectedItem();
                        if (newValue != _firstGraphField) { // Value changed
                            /* Add the existing value to the second menu, in
                               its position within the enum order */
                            _secondFieldMenu.insertItemAt(_firstGraphField,
                                                          _firstGraphField.ordinal());
                            /* Remove the newly selected item. Note that if
                               it is currently selected, this will trigger
                               an automatic change to another item, and an
                               action event on the second menu */
                            _secondFieldMenu.removeItem(newValue);
                            _firstGraphField = newValue;
                        } // Value for menu changed
                    }
                } // Annonymous class
                );
            
            JPanel fieldPanel = new JPanel(new GridBagLayout());
            GridBagConstraints layout = new GridBagConstraints();
            layout.gridx = 0;
            layout.gridy = 0;
            fieldPanel.add(new JLabel("First Play Field",
                                      SwingConstants.CENTER),
                           layout);
            layout.gridy = 1;
            fieldPanel.add(_firstFieldMenu, layout);
            
            layout.gridx = 1;
            layout.gridy = 0;
            fieldPanel.add(new JLabel("Second Play Field",
                                      SwingConstants.CENTER),
                           layout);
            layout.gridy = 1;
            fieldPanel.add(_secondFieldMenu, layout);
            
            // String for a panel with no inputs
            String noInputs = new String("NoInputs");
            
            /* Create a panel consiting of a layered stack of the panels above
               that can be flipped, plus one empty */
            _flipPanel = new JPanel(new CardLayout());
            // Display default, must be added first
            _flipPanel.add(new JPanel(), noInputs);
            _flipPanel.add(fieldPanel, fieldPanelName);

            /* Map the panel names to the graph types. Fill the entire map with
               default values and then overwrite, to ensure every graph type
               always has a panel */
            _graphPanelTypes = new EnumMap<NFLODAP.PlayGraphTypes, String>(NFLODAP.PlayGraphTypes.class);
            for (NFLODAP.PlayGraphTypes tempIndex : NFLODAP.PlayGraphTypes.values())
                _graphPanelTypes.put(tempIndex, noInputs);
            _graphPanelTypes.put(NFLODAP.PlayGraphTypes.SCATTER_PLOT,
                                 fieldPanelName);
            _graphPanelTypes.put(NFLODAP.PlayGraphTypes.TURNOVER_SCATTER_PLOT,
                                 fieldPanelName);

            // Create a graph type selector that flips the panels
            _graphSelector = new JComboBox(); 
            for (Object e : NFLODAP.PlayGraphTypes.class.getEnumConstants())
                _graphSelector.addItem(e); 
            // The menu needs an action listener, and here it is
            _graphSelector.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent event)
                    {
                        _graphType = (NFLODAP.PlayGraphTypes)_graphSelector.getSelectedItem();
                        changePanel(_graphType);
                    }
                } // Anonymous class
                );
            
            // Finally, assemble it all into the main panel
            // First part of panel is label and dropdown
            layout.gridx = 0;
            layout.gridy = 0;
            add(new JLabel("Graph Type to Generate", SwingConstants.CENTER),
                layout);
            layout.gridy = 1;
            add(_graphSelector, layout);
            
            layout.gridy = 2;
            add(_flipPanel, layout);
        } // Constructor
        
        // Flips panel based on type of graph
        void changePanel(NFLODAP.PlayGraphTypes graph)
        {
            /* NOTE: In many cases, this will result in trying to show the
               panel that is already displayed. This is not an error */
            CardLayout cl = (CardLayout)(_flipPanel.getLayout());
            cl.show(_flipPanel, _graphPanelTypes.get(graph));
        }
    } // Inner class
    
    // Constructor for generating graphs types via the GUI.
    public GraphSelector()
    {
        // Set these before constructing the menu, since it may override
        _graphType = null;
        _firstGraphField = null;
        _secondGraphField = null;
        _graphGui = new GuiGraphFactory();
    }

    /* Constructor for a fixed type of graph.
       WARNING: Input consistency is NOT validated, caller is expected to
       handle it */
    GraphSelector(NFLODAP.PlayGraphTypes graphType,
                  SinglePlay.NumericFields firstGraphField,
                  SinglePlay.NumericFields secondGraphField)
    {
        _graphType = graphType;
        _firstGraphField = firstGraphField;
        _secondGraphField = secondGraphField;
        
        _graphGui = null;
    }
    
    StatGraphFactory getGraph()
    {
        StatGraphFactory graph = null;
        switch (_graphType) {
        case COUNTS:
            graph = new PlayCountFactory();
            break;

        case DISTANCE_RESULTS:
            graph = new PlayResultsFactory();
            break;
            
        case SCATTER_PLOT:
            graph = new ScatterPlotFactory(_firstGraphField, _secondGraphField);
            break;

        case TURNOVER_SCATTER_PLOT:
            graph = new ScatterTurnoverFactory(_firstGraphField,
                                               _secondGraphField, true, true);
            break;

        default:
            /* SERIOUS PROBLEM: Have a graph type without the factory needed to
               generate it */
            graph = null;
        } // Switch on graph type
        return graph;
    }

    // Get panel to select graphs
    public JPanel getGui()
    {
        return _graphGui;
    }

    // String is the graph the object generates
    public String toString()
    {
        // Output the raw object data too, which aids analysis
        return "_graphType:" + _graphType + " _firstGraphField:" +
            _firstGraphField + " _secondGraphField:" + _secondGraphField +
            " " + getGraph().toString();
    }
}
