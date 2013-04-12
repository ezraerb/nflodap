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

/* Implements a menu of category based play caracteristics. Its an (optional)
   listener on other menus of the same type; if a category is selected it is
   removed from this objects menu. The class can also be constructed with a
   play category, in which case it fixes its value to the supplied value and
   doesn't generate a menu
   WARNING: This class is designed to listen on only one other menu
   WARNING: Be sure to check for circular references on listeners, or nasty
   things will happen
   NOTE: Class has package visibility */
final class PlayCatMenu extends PlayCatMenuListener
{
    private NFLODAP.PlayCatCharacteristics _value; // Selected value
    private JComboBox _list; // The actual menu

    // Value not allowed to be selected, because its used somewhere else
    private NFLODAP.PlayCatCharacteristics _forbidValue;
    
    // Constructor to create the dropdown
    PlayCatMenu()
    {
        /* Create menu. Create listener class on menu that gets value,
           translates it, and stores it in the class
           NOTE: This listener is for its OWN menu, which is seperate from the
           method that listens on OTHER menus */
        _list = new JComboBox();

        // Add names. This is inelegant, but needed due to the extra value
        _list.addItem(new String("-none-"));
        for (Object e : NFLODAP.PlayCatCharacteristics.class.getEnumConstants())
            // Deliberately adding the string, not the enum value!
            _list.addItem(e.toString()); 
        
        /* Annonymous class that listens on the menu and sets the value
           from whatever was selected */
        _list.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent event)
                {
                    // NOTE: method converts "-none-" to NULL value
                    _value = NFLODAP.playCatNameToValue((String)_list.getSelectedItem());
                }
            } // Annonymous class
            );
        
        // Default the value to 'none'
        _value = null;
        _forbidValue = null;
    }
    
    // Constructor to fix the value, used to skip the menu
    PlayCatMenu(NFLODAP.PlayCatCharacteristics value)
    {
        _list = null;
        _value = value;
    }
    
    // Add the passed object as a listener on this category menu
    public void addListener(PlayCatMenuListener listener)
    {
        /* If listener is this class, ignore it. Also check for case where this
           class has a fixed value so no menu */
        if ((listener == this) || (_list == null))
            return; // Ignore the reqest
        else
            _list.addActionListener(listener);
    }
    
    // Returns the last value selected
    public NFLODAP.PlayCatCharacteristics getValue()
    {
        return _value;
    }
    
    public JComboBox getDropdown()
    {
        return _list;
    }
    
    // Handle a selection on a DIFFERENT category menu
    public void handleCatSelection(JComboBox menu,
                                   NFLODAP.PlayCatCharacteristics value)
    {
        /* A value selected on some other menu is not allowed on this one. If
           some other value was already forbidden, reactive it, then remove
           the new value */
        /* NOTE: Ideally, should track WHICH menu forbid each value, and
           reactivate only if that menu then changes. The class could then
           handle multiple forbid values simultaneously. Currently, only one
           other menu can forbid values, so this functionality is not worth
           much */
        if (value != _forbidValue) { // Value changed
            if (_forbidValue != null)
                /* Add it back to the menu. Its position is the ordinal
                   position in the enum plus 1 (to account for the value
                   "-none-" at the start) */
                _list.insertItemAt(_forbidValue.toString(),
                                   _forbidValue.ordinal() + 1);
            if (value != null) {
                /* Need to remove it from the menu. If the entry was selected,
                   the menu will automatically switch to the previous entry,
                   and trigger an action event accordingly. Want to override
                   this behavior, so test for it */
                boolean prohibitSelected = (_value == value);

                // Remove it from the menu
                _list.removeItem(value.toString());
                if (prohibitSelected) {
                    /* Wait for the resulting action to be processed,  if any.
                       Remember that the value will change to something else */
                    try {
                        while (_value == value)
                            Thread.sleep(500);
                    }
                    catch (InterruptedException e)
                    {
                        // Just continue and live with the inconsistency
                    }
                    /* Change the automatic selection (which is pretty random)
                       to "-none-" */
                    _list.setSelectedIndex(0);
                    _value = null;
                } // Forbidden vale was selected
            } // Value to forbid is not "-none-" (which is always allowed)
            _forbidValue = value;
        } // Value to forbid has changed
    }
    
    public String toString()
    {
        return "PlayCatMenu: " + _value;
    }
}