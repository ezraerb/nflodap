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

import javax.swing.*;

/* Wrapper class to display a JFrame in a seperate thread so it doesn't block
   execution. This allows multiple play graphs to exist at the same time */
public final class JFrameThreadWrapper implements Runnable
{
    private JFrame _window;
    private Thread _thread;

    // Construct it with the window to display
    public JFrameThreadWrapper(JFrame window, int windowCount)
    {
        _window = window;
        /* Ensure program doesn't die when window closed, unless its the last
           one */
        _window.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        _thread = new Thread(this, new String("Window" + windowCount));
        _thread.start();
    }

    public void run()
    {
        _window.setVisible(true); // Display window
    }
}
