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
package nflodap.datastore;

/* Java has no standard class for a range of numbers. The one below provides the
   functionality this program needs and little else. In particular, it has no
   concept of an unlimited range, and can be updated. A more general purpose one
   should probably be found from a package somewhere at some point */
public final class IntegerRange implements Cloneable
{
    int _minimum;
    int _maximum;

    // Constructor for an actual range
    public IntegerRange(int minimum, int maximum)
    {
        // Silently handle a common calling error
        if (minimum > maximum) {
            _minimum = maximum;
            _maximum = minimum;
        }
        else {
            _minimum = minimum;
            _maximum = maximum;
        }
    }

    // Constructor for a range containing one number
    public IntegerRange(int value)
    {
        _minimum = value;
        _maximum = value;
    }

    /* Creates a union of two ranges.
       WARNING: If they are disjoint, every value between the two ranges will
       ALSO be included */
    public void union(IntegerRange other)
    {
        if (other._minimum < _minimum)
            _minimum = other._minimum;
        if (other._maximum > _maximum)
            _maximum = other._maximum;
    }

    /* Extends the range to include the passed value. Most common use will be
       building a range that contains a set of values. This effectively does
       union(new IntegerRange(newValue) */
    public void extendRange(int newValue)
    {
        if (newValue < _minimum)
            _minimum = newValue;
        else if (newValue > _maximum)
            _maximum = newValue;
        // else nothing needs to be done
    }

    // Returns true if the value is within the range
    public boolean contains(int value)
    {
        return ((value >= _minimum) && (value <= _maximum));
    }

    // Return the amount of values covered by the range
    public int getLength()
    {
        return _maximum - _minimum;
    }

    // Returns the range limits
    public int getLowerLimit()
    {
        return _minimum;
    }

    public int getUpperLimit()
    {
        return _maximum;
    }

    // Clone object
    public Object clone() throws CloneNotSupportedException
    {
        return new IntegerRange(_minimum, _maximum);
    }

    public String toString()
    {
        return "Range (" + _minimum + " to " + _maximum + ")";
    }
}