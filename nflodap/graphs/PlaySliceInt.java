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

import nflodap.datastore.*;
/* Class to slice a datastore based on an integer field. They are chained
   together to create the overall slice of the datastore */
public class PlaySliceInt<P extends Enum<P>> implements PlaySlice
{
    private SinglePlay.NumericFields _field;
    IntegerRange _wantRange;
    private PlaySlice _nextSlice;
    
    /* Constructor. Needs value for the slice and the slice to call afterward,
       if any */
    public PlaySliceInt(SinglePlay.NumericFields field, IntegerRange wantRange,                        PlaySlice nextSlice)
    {
        _field = field;
        _wantRange = wantRange;
        _nextSlice = nextSlice;
    }

    // Constructor for when there is no next slice
    public PlaySliceInt(SinglePlay.NumericFields field, IntegerRange wantRange)
    {
        this(field, wantRange, null);
    }

    public void slice(DataStore data)
    {
        data.slice(_field, _wantRange);
        if (_nextSlice != null)
            _nextSlice.slice(data);
    }

    public String toString()
    {
        String temp = new String("Slice by " + _field + " " + _wantRange);
        if (_nextSlice != null)
            return temp + ", " + _nextSlice;
        else
            return temp;
    }
}