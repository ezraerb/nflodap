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

import javax.swing.JPanel;
import java.awt.Dimension;

/* This class defines classes that graph NFL play statistics. Its sole reason
   for existence is to create a clonable graphics panel. Implement the
   clone method by constructing a new subclass with the data for the
   graph, copied right out of the existing graph */
public abstract class StatGraph extends JPanel implements Cloneable
{
    // The minimum size all graphs must display at
    // NOTE: Always test new graphs at this size to ensure they are readable!
    public static Dimension _minSize = new Dimension(200, 200);

    /* Override protected method in Object, and force subclasses to implement
       the clone method */
    public abstract Object clone() throws CloneNotSupportedException;
}