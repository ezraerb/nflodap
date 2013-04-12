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
/* Class to slice a data store based on an enumerated type. They are chained
   together to create the overall slice of the datastore */
public class PlaySliceEnum<P extends Enum<P>> implements PlaySlice
{
    private Class<P> _sliceClass;
    private P _sliceValue;
    private PlaySlice _nextSlice;
    
    /* Constructor. Needs value for the slice and the slice to call afterward,
       if any */
    public PlaySliceEnum(P sliceValue, Class<P> sliceClass,
                         PlaySlice nextSlice)
    {
        _sliceValue = sliceValue;
        _sliceClass = sliceClass;
        _nextSlice = nextSlice;
    }

    // Constructor for when there is no next slice
    public PlaySliceEnum(P sliceValue, Class<P> sliceClass)
    {
        this(sliceValue, sliceClass, null);
    }

    public void slice(DataStore data)
    {
        data.slice(_sliceValue, _sliceClass);
        if (_nextSlice != null)
            _nextSlice.slice(data);
    }

    public String toString()
    {
        if (_nextSlice != null)
            return "Slice by " + _sliceValue + ", " + _nextSlice;
        else
            return "Slice by " + _sliceValue;
    }
}