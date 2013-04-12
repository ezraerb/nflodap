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

/* Implements a menu of filter values for a given category based
   characterisric. Filtering on the same characteristics used to group plays is
   not allowed, so this class listens on the group menus and disables itself if
   the characteristic is selected.
   NOTE: This class has package visibility */
final class PlayCatFilter extends PlayCatMenuListener
{
    private NFLODAP.PlayCatCharacteristics _filterType; // Type to select on
    private JComboBox _list; // The actual menu
    private EnumValueWrapper<?> _value; // Selected value

    /* Menus that have prohibited this value from being used for
       filtering, because its used somewhere else */
    ArrayList<JComboBox> _prohibitMenus;
        
    // Constructor to create the dropdown
    PlayCatFilter(NFLODAP.PlayCatCharacteristics filterType)
    {
        _filterType = filterType;
        _prohibitMenus = new ArrayList<JComboBox>();
        _value = null;
            
        /* Create menu. Create listener class on menu that gets value,
           translates it, and inserts it in the parent object map
           NOTE: This listener is for its OWN menu, which is seperate from the
           method that listens on OTHER menus */
        _list = new JComboBox();

        // Add names. This is inelegant, but needed due to the extra value
        _list.addItem(new String("-none-"));
        for (Object e : _filterType.getEnumType().getEnum().getEnumConstants())
            // Deliberately adding the string, not the enum value!
            _list.addItem(e.toString()); 

        /* Annonymous class that listens on the menu and sets the value
           from whatever was selected */
        _list.addActionListener(new ActionListener()
            {
                public void actionPerformed(ActionEvent event)
                {
                    /* Convert selected name to a value. Failed conversion
                       means the value is '-none-' */
                    try {
                        _value = EnumValueWrapper.create(_filterType.getEnumType(),
                                                         (String)_list.getSelectedItem());
                    }
                    catch (IllegalArgumentException e) {
                        _value = null;
                    }
                }
            } // Annonymous class
            );
    }
    
    // Constructor to create the class with a specific value
    PlayCatFilter(NFLODAP.PlayCatCharacteristics filterType,
                  EnumValueWrapper<?> value)
    {
        _filterType = filterType;
        _value = value;
        _list = null;
        _prohibitMenus = null;
    }
    
    public JComboBox getDropdown()
    {
        return _list;
    }
    
    // Returns the value for the object. NULL implies none
    EnumValueWrapper<?> getValue()
    {
        if (_prohibitMenus == null) // Not menu driven
            return _value;
        else if (!_prohibitMenus.isEmpty())
            return null;
        else
            return _value;
    }
    
    // Handle a selection on a DIFFERENT category menu
    public void handleCatSelection(JComboBox menu,
                                   NFLODAP.PlayCatCharacteristics value)
    {
        // Record whether the list is empty before changing it
        boolean notProhibited = _prohibitMenus.isEmpty();
        if (value == _filterType) {
            // If menu not already on prohibit list, add it
            if (!_prohibitMenus.contains(menu))
                _prohibitMenus.add(menu);
        } // Newly prohibited
        else
            // If menu IS on the prohibit list, remove it
            _prohibitMenus.remove(menu);
        
        /* If list was empty before and now has an entry, or vice versa, update
           the status of the menu to match the status of the list */
        if (notProhibited != _prohibitMenus.isEmpty())
            _list.setEnabled(_prohibitMenus.isEmpty());
    }
        
    public String toString()
    {
        return "PlayCatFilter " + _filterType + " value:" + _value;
    }
}