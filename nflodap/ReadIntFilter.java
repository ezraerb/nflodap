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

/* Class to get integer range to filter plays. It can either be done through an
   internal menu, or specified
   NOTE: The menu for this class is so complicated that the class uses a box
   design pattern. The menu is designed as its own inner class that can modify
   the actual filter range in the outer class
   NOTE: This class has package visibility */
final class ReadIntFilter
{
    private SinglePlay.NumericFields _field; // Field being read
    private IntegerRange _specifiedRange; // Range for field, if any
    
    private GuiIntFilter _menu; // Menu to set range
    
    // Class to set integer based play filters from the GUI
    private class GuiIntFilter extends JPanel
    {
        private IntegerRange _range; // Last range read, may not match above
        JCheckBox _fieldBox; // Checkbox on whether field is used for filtering
        JTextField _lowerValue; // Lower value of range
        JTextField _upperValue; // Upper value of range
        JPanel _flipPanel; // Controls panels presented to user
        
        /* String constants, needed below */
        private final String _checkedString;
        private final String _uncheckedString;
        
        // Listener class on the text fields.
        private class RangeReader implements FocusListener
        {
            public void focusGained(FocusEvent e)
            {
                // Do nothing
            }
            
            public void focusLost(FocusEvent e)
            {
                /* Ignore temporary focus loss, such as a window change,
                   and focus change to the other field of the range */
                if ((!e.isTemporary()) &&
                    (e.getOppositeComponent() != _lowerValue) && 
                    (e.getOppositeComponent() != _upperValue)) {
                    /* NOTE: Officially, the validator will not allow the
                       cursor to leave a text field until the input is
                       valid. In reality, this can be bypassed. The code
                       below will only save the input AFTER it is
                       validated */
                    boolean validValue = true;
                    int upper = 0;
                    int lower = 0;
                    try {
                        upper = Integer.parseInt(_upperValue.getText().trim());
                        lower = Integer.parseInt(_lowerValue.getText().trim());
                    }
                    catch (NumberFormatException ex) {
                        validValue = false;
                    }
                    if (validValue) {
                        _range = new IntegerRange(upper, lower);
                        _specifiedRange = _range;
                    }
                } // Not temporary focus loss or move to other range field
            } // Method focusLost
        } // Inner class
        
        // Validator on text field
        private class TextValidator extends InputVerifier
        {
            public TextValidator()
            {
                // Nothing to do!
            }
            
            public boolean verify(JComponent component)
            {
                // The field is verified when it can be converted to an integer
                JTextField field = (JTextField)component;
                boolean valid = true;
                try {
                    Integer.parseInt(field.getText().trim());
                }
                catch (NumberFormatException e) {
                    valid = false;
                }
                return valid;
            }
        }
        
        /* Constructor for field. Thanks to the multiple components, the
           panel is rather complex */
        public GuiIntFilter()
        {
            super(new GridBagLayout());
            _range = new IntegerRange(-100, 100);
            
            /* Set up a panel as two layered panels that can be flipped. One
               is blank and the other contains input fields */
            _checkedString = new String("CHECKED");
            _uncheckedString = new String("UNCHECKED");
            
            RangeReader reader = new RangeReader();
            TextValidator validator = new TextValidator();

            _lowerValue = new JTextField(5);
            _lowerValue.addFocusListener(reader);
            _lowerValue.setInputVerifier(validator);
            
            _upperValue = new JTextField(5);
            _upperValue.addFocusListener(reader);
            _upperValue.setInputVerifier(validator);
            
            // Panel for when ranges should be added
            JPanel checkedPanel = new JPanel(new GridBagLayout());
            GridBagConstraints layout = new GridBagConstraints();
            layout.gridx = 0;
            layout.gridy = 0;
            checkedPanel.add(_lowerValue, layout);
            layout.gridx = 1;
            checkedPanel.add(new JLabel(" to ", SwingConstants.CENTER), layout);
            layout.gridx = 2;
            checkedPanel.add(_upperValue, layout);
            
            _flipPanel = new JPanel(new CardLayout());
            // Display default, must be added first
            _flipPanel.add(new JPanel(), _uncheckedString);
            _flipPanel.add(checkedPanel, _checkedString);
            
            // Checkbox that controls the panel above
            _fieldBox = new JCheckBox(_field.toString());
            _fieldBox.addActionListener(new ActionListener()
                {
                    public void actionPerformed(ActionEvent event)
                    {
                        if (_fieldBox.isSelected()) {
                            // Ensure last range, if any, is present
                            _lowerValue.setText(Integer.toString(_range.getLowerLimit()));
                            _upperValue.setText(Integer.toString(_range.getUpperLimit()));
                            _specifiedRange = _range;
                        }
                        else
                            // Menu deactivated, so clear value
                            _specifiedRange = null;
                        changePanel(_fieldBox.isSelected());
                    } // actionPerformed method
                } // Annonymous class
                );
            
            layout.gridx = 0;
            layout.gridy = 0;
            add(_flipPanel, layout);
            layout.gridx = 1;
            add(_fieldBox, layout);
        } // Constructor
        
        // Flips panel based on checkbox state
        void changePanel(boolean checked)
        {
            CardLayout cl = (CardLayout)(_flipPanel.getLayout());
            if (checked)
                cl.show(_flipPanel, _checkedString);
            else
                cl.show(_flipPanel, _uncheckedString);
        }
    } // Class GuiIntFilter
    
    // Constructor to create with a menu
    ReadIntFilter(SinglePlay.NumericFields field)
    {
        _field = field;
        // Set value first, since menu may override
        _specifiedRange = null;
        _menu = new GuiIntFilter();
    }
    
    // Constructor to create with a fixed value
    ReadIntFilter(SinglePlay.NumericFields field, int lowerLimit,
                  int upperLimit)
    {
        _field = field;
        _specifiedRange = new IntegerRange(lowerLimit, upperLimit);
        _menu = null;
    }
    
    // Get menu
    public JPanel getMenu()
    {
        return _menu;
    }
    
    // Get filter range
    public IntegerRange getRange()
    {
        return _specifiedRange;
    }
    
    public String toString()
    {
        return "ReadIntFilter " + _field + " value:" + _specifiedRange;
    }
}

