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
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.text.*;

/* Class to hold a text string from the GUI, with automatic listener and
   updating. It can also be constructed with a string value, in which case
   the value is fixed. It exists so the menu can be passed around like any
   other string, eliminating the need for seperate listener logic on every
   string field in the GUI */
/* NOTE: This class is private to the package, since it should only be used
   through the main menu driver */
final class GuiText
{
    private String _value;
    private JTextField _gui;
    private boolean _allowSpaces;
    // If set, field has been updated since last tested
    private boolean _changed;
    
    // Listener class on the text field.
    private class TextUpdater implements FocusListener
    {
        public void focusGained(FocusEvent e)
        {
            // Do nothing
        }
        
        public void focusLost(FocusEvent e)
        {
            // Ignore temporary focus loss, such as a window change
            if (!e.isTemporary()) {
                String value = getValue(_gui);
                /* NOTE: Officially, the validator will not allow the
                   cursor to leave a text field until the input is valid.
                   In reality, this can be bypassed. The code below will
                   only save the input AFTER it is validated */
                if (isValid(value)) {
                    if (value.isEmpty())
                        _value = null; // Entry cleared
                    else {
                        _value = value;
                        _changed = true;
                    } // Non-empty entry
                } // Valid entry
            } // Not temporary focus loss
        } // Method focusLost
    } // Inner class
    
    // Validator on the text field
    private class TextValidator extends InputVerifier
    {
        public TextValidator()
        {
            // Nothing to do!
        }
        
        public boolean verify(JComponent component)
        {
            return isValid(getValue((JTextField)component));
        }
    }
    
    // Method to extract the value from the text field and process it
    private String getValue(JTextField input)
    {
        // Convert all commas to spaces, then remove spaces on ends
        return input.getText().replace(',', ' ').trim();
    }
    
    // Returns true if a given string is valid input
    private boolean isValid(String value)
    {
        /* If the value does not allow spaces, it must have a positive
           length and no spaces. Entries that do allow spaces must allow
           a length of zero to handle the case of clearing the value */
        return (_allowSpaces ||
                ((value.length() > 0) && (value.indexOf(' ') < 0)));
    }
    
    // Constructor to use as a GUI component
    public GuiText(int columns, boolean allowSpaces)
    {
        _value = null;
        _allowSpaces = allowSpaces;
        _changed = false;
        _gui = new JTextField(columns);
        _gui.addFocusListener(new TextUpdater());
        _gui.setInputVerifier(new TextValidator());
    }
    
    // Constructor to use as a fixed value
    public GuiText(String value)
    {
        /* WARNING: Input is not validated. This method depends on the
           caller handling it. Since the data had to come from somewhere
           (usually command line arguments) this should be sufficient in
           practice */
        _value = value;
        _changed = false;
        _allowSpaces = true;
        _gui = null;
    }
    
    // Returns true if value changed since flag last reset
    public boolean getChanged()
    {
        return _changed;
    }
    
    // Resets any value changed status
    public void resetChangedStatus()
    {
        _changed = false;
    }
    
    public String getValue()
    {
        return _value;
    }
    
    public JTextField getGui()
    {
        return _gui;
    }
    
    public String toString()
    {
        return "GuiText: " + _value;
    }
}

