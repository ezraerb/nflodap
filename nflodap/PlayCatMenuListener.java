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

/* Abstract class to modify menus related to play category selection if the
   same category is selected elsewhere. Consistency requirements force
   categories used to group plays to not be used for other purposes. Its
   abstract because HOW the menu is modified depends on the specific menu */
// NOTE: Visibility is deliberately restricted to the package
abstract class PlayCatMenuListener implements ActionListener
{
    PlayCatMenuListener()
    {
        // Does nothing
    }

    // React to an action event
    public void actionPerformed(ActionEvent event)
    {
        /* Extract the menu that was selected. This had better be the
           JComboBox object inside a PlayCatMenu object or bad things
           will happen */
        JComboBox menu = (JComboBox)event.getSource();
        // Now get the selected value, also a cast
        String menuValue = (String)menu.getSelectedItem();
        // Convert it to the actual enum value
        NFLODAP.PlayCatCharacteristics value = NFLODAP.playCatNameToValue(menuValue);
        handleCatSelection(menu, value);
    }

    /* How the class reacts to the selection of the category on ANOTHER
       menu (not its own!) */
    public abstract void handleCatSelection(JComboBox menu,
                                            NFLODAP.PlayCatCharacteristics value);
};
