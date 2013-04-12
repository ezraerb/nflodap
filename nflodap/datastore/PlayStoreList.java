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

import java.util.*;

/* This class is part of the internal storage for NFL play data. Since
   category characteristics are all specified as enums, the natural data design
   is a set of nested enummaps. Using them like this, though, is difficult,
   thanks to Java's strict typesafety and lack of typedefs. The same code must
   be implemented over and over, once for every level of nested maps. Such code
   is tedious to write and nearly impossible to update. An alternative is
   using wildcards, but they are difficult to set up correctly with enums.
   Instead, this code uses the Composite pattern to implement the cube as a
   very wide tree. Every entry for a given enum value has pointers to the
   entries for the next layer of enum values. The memory usage is only slightly
   larger than the nested maps.

   Data is read out of the class using an iterator. It produces one list of
   plays for each set of category values with plays defined. The lists are
   copied from the data store to protect its contents. The category values
   are NOT reported; the caller should know the dimensions of the data store
   (which can change thanks to pivot calls) and read them directly from the
   plays. Any operation that modifies the data store will invalidate all
   iterators. */
/* NOTE: To protect the consistency of the data, all public access should be
   done through a single class. This class is deliberately restricted to the
   package */

// The leaf of the tree containing plays that meet the wanted index criteria */
final class PlayStoreList implements PlayStoreTree
{
    private ArrayList<SinglePlay> _plays;
    
    // Constructor, create an empty list
    PlayStoreList()
    {
        _plays = new ArrayList<SinglePlay>();
    }
    
    // Construct the object around a pre-existing list
    /* WARNING: This DOES NOT encapsulate the list, it is shared with
       whatever called the constructor */
    PlayStoreList(ArrayList<SinglePlay> list)
    {
        _plays = list;
    }
    
    // Clone method
    public Object clone() throws CloneNotSupportedException
    {
        PlayStoreList newList = new PlayStoreList();
        newList._plays.addAll(_plays); // Shallow copy of the list
        return newList;
    }
    
    // Returns true if this data store is empty
    public boolean empty()
    {
        if (_plays == null)
            return true;
        else
            return _plays.isEmpty();
    }
    
    // Insert play into the datastore
    public void insertPlay(SinglePlay play)
    {
        _plays.add(play);
    }
    
    /* Remove all plays that do not have the passed enumerated value. If the
       value type does not exist in the play (possible thanks to the generic
       type) nothing happens. This implements the ODAP slice operation */
    public <P extends Enum<P>> void slice(P value, Class<P> valueType)
    {
        if (empty())
            return; // Nothing to do!
        
        Iterator<SinglePlay> playPtr = _plays.iterator();
        while (playPtr.hasNext()) {
            P testValue = playPtr.next().getValue(valueType);
            if (testValue != null) // Play has value in wanted type
                if (testValue != value)
                    playPtr.remove();
        } // While list entries to test
    } // Method slice()
    
    /* Removes all plays whose integer field value is outside the specified
       range. This implements a version of slice for these fields */
    public void slice(SinglePlay.NumericFields field,
                      IntegerRange wantRange)
    {
        if (empty())
            return; // Nothing to do!
        
        Iterator<SinglePlay> playPtr = _plays.iterator();
        while (playPtr.hasNext()) {
            int testValue = playPtr.next().getIntValue(field);
            if (!wantRange.contains(testValue))
                playPtr.remove();
        } // While list entries to test
    } // Method slice()
    
    // Rolls up the entire contents into the passed play list
    public void rollup(ArrayList<SinglePlay> plays)
    {
        plays.addAll(_plays); // Shallow copy of the list
    }
    
    /* Rolls the contents into the passed enumerated map, splitting
       plays by the category of the enumerated type */
    // NOTE: K is used by DataStoreInterface, end up with L
    public <L extends Enum<L>> void pivot(EnumMap<L, ArrayList<SinglePlay>> plays, Class<L> enumType)
    {
        /* Need to iterate through the map and extract the wanted value
           from each play. If an entry does not already exist in the
           result map, have to add it, and then insert the play */
        Iterator<SinglePlay> index = _plays.iterator();
        while (index.hasNext()) {
            SinglePlay testPlay = index.next();
            L wantValue = testPlay.getValue(enumType);
            if (wantValue != null) { // Play has value in wanted type
                ArrayList<SinglePlay> wantList = plays.get(wantValue);
                if (wantList == null) {
                    wantList = new ArrayList<SinglePlay>();
                    plays.put(wantValue, wantList);
                }
                wantList.add(testPlay);
            } // Play has value in wanted type
        } // While plays to process
    }
    
    /* Rolls the contents into the passed double layered enumerated map,
       splitting plays by the category of the enumerated types */
    // NOTE: K is used by DataStoreInterface, end up with L and M
    public <L extends Enum<L>, M extends Enum<M>> void pivot(EnumMap<L, EnumMap<M, ArrayList<SinglePlay>>> plays, Class<L> firstEnumType, Class<M> secondEnumType)
    {
        /* Need to iterate through the map and extract the wanted value
           from each play. If an entry does not already exist in the
           result map, have to add it, and then insert the play */
        Iterator<SinglePlay> index = _plays.iterator();
        while (index.hasNext()) {
            SinglePlay testPlay = index.next();
            L firstWantValue = testPlay.getValue(firstEnumType);
            M secondWantValue = testPlay.getValue(secondEnumType);
            if ((firstWantValue != null) &&
                (secondWantValue != null)) { // Play has value in wanted types
                EnumMap<M, ArrayList<SinglePlay>> wantMap = plays.get(firstWantValue);
                if (wantMap == null) {
                    /* NOTE: creating an enum map of M here, so need the
                       second enumerated type, not the first */
                    wantMap = new EnumMap<M, ArrayList<SinglePlay>>(secondEnumType);
                    plays.put(firstWantValue, wantMap);
                }
                ArrayList<SinglePlay> wantList = wantMap.get(secondWantValue);
                if (wantList == null) {
                    wantList = new ArrayList<SinglePlay>();
                    wantMap.put(secondWantValue, wantList);
                }
                wantList.add(testPlay);
            } // Play has value in wanted type
        } // While plays to process
    } // Rollup method
    
    /* Custom iterator for this data store, based on the usual design
       pattern. The iterator is an inner class so it has access to
       the private members of the object that generates it. Note that
       it implements an interface, so this is also the Factory pattern */
    public class PlayStoreIterator implements Iterator<ArrayList<SinglePlay>>
    {
        /* Since the outer class can only contain a single list of plays,
           this class looks quite silly. It exists to fulfull the Factory
           pattern used to generate iterators for data store. If the
           iterator hasn't been read it returns a copy of the list,
           otherwise it throws an exception. Note that this means that if
           the list is updated between generating the iterator and reading
           it, it remains valid */
        boolean _read;
        
        public PlayStoreIterator()
            {
                if (_plays == null) // Parent
                    _read = true; // Force empty list to be ignored
                else
                    _read = false;
            }    
        
        @Override
            public boolean hasNext()
            {
                return (!_read);
            }
        
        @Override
            public ArrayList<SinglePlay> next()
            {
                if (_read)
                    throw new NoSuchElementException();
                else {
                    _read = true;
                    ArrayList<SinglePlay> result = new ArrayList<SinglePlay>();
                    result.addAll(_plays); // Copy list from parent
                    return result;
                }
            } // next method
        
        @Override
            public void remove()
            {
                // Not allowed!
                throw new UnsupportedOperationException();
            }
    } // class PlayStoreIterator
    
    /* Iterator on the data store. It is actually implemented by each
       subclass using the factory pattern. */
    public Iterator<ArrayList<SinglePlay>> iterator()
    {
        return new PlayStoreIterator();
    }
    
    /* Dump the contents of this play list to the output, preceeded by
       the passed index values */
    public void toOutput(StringBuffer indexes, StringBuffer output)
    {
        // Generate a header of the index values for this set of plays
        if (indexes != null) {
            output.append("MapValues ");
            output.append(indexes);
            output.append("\n");
        } // Indexes specified
        if (_plays == null)
            output.append("empty\n");
        else {
            Iterator<SinglePlay> playPtr = _plays.iterator();
            while (playPtr.hasNext())
                // Output the play. Note that it takes two lines
                output.append(playPtr.next() + "\n");
            output.append("\n");
        } // Have array entries
    } // toOutput method
} 
