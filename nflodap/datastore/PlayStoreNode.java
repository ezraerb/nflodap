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

/* Map entities by the values of a play enum. As the name should imply, this
   is implemented as a wrapper around an EnumMap */
final class PlayStoreNode<K extends Enum<K>> implements PlayStoreTree
{
    private Class<K> _enumClass; // Need this for class operations
    private EnumMap<K, PlayStoreTree> _playMap;
    
    /* Constructor. Needs the class because deriving it from K is incredibly
       difficult */
    PlayStoreNode(Class<K> keyClass)
    {
        _enumClass = keyClass;
        _playMap = new EnumMap<K,PlayStoreTree>(keyClass);
    }
    
    // Construct around an existing map
    /* WARNING: This DOES NOT encapsulate the map; it is shared with
       whatever called the constructor */
    PlayStoreNode(Class<K> keyClass, EnumMap<K, PlayStoreTree> map)
    {
        _enumClass = keyClass;
        _playMap = map;
    }
            
    // Clone method
    public Object clone() throws CloneNotSupportedException
    {
        PlayStoreNode<K> newMap = new PlayStoreNode<K>(_enumClass);
        /* Iterate through the current map and clone everything in it.
           This produces a deep copy of the map contents */
        Iterator<Map.Entry<K, PlayStoreTree>> mapPtr = _playMap.entrySet().iterator();
        while (mapPtr.hasNext()) {
            Map.Entry<K, PlayStoreTree> mapEntry = mapPtr.next();
            newMap._playMap.put(mapEntry.getKey(),
                                (PlayStoreTree)mapEntry.getValue().clone());
        } // While entries to duplicate
        return newMap;
    }
    
    // Returns true if this data store is empty
    public boolean empty()
    {
        if (_playMap == null)
            return true;
        else
            return (_playMap.size() == 0);
    }
    
    // Insert play into the datastore
    public void insertPlay(SinglePlay play)
    {
        /* Extract needed value from play and find matching data store.
           If not found, create it. Then insert the play in it */
        K playValue = play.getValue(_enumClass);
        if (playValue == null)
            // Major, unrecoverable problem that indicates a coding error
            throw new IllegalArgumentException();
        PlayStoreTree wantDataStore = _playMap.get(playValue);
        if (wantDataStore == null) {
            wantDataStore = newMapEntry();
            _playMap.put(playValue, wantDataStore);
        }
        wantDataStore.insertPlay(play);
    }
    
    /* Remove all plays that do not have the passed enumerated value. If the
       value type does not exist in the play (possible thanks to the generic
       type) nothing happens. This implements the ODAP slice operation */
    public <P extends Enum<P>> void slice(P value, Class<P> valueType)
    {
        /* If the passed class is the same as the enum class of this
           object, remove all map entries except the wanted entry.
           Otherwise, do a slice on every map entry */
        if (empty())
            return; // Nothing to do!
        if (_enumClass.isInstance(value)) {
            PlayStoreTree wantedEntry = _playMap.get(_enumClass.cast(value));
            _playMap.clear();
            if (wantedEntry != null)
                _playMap.put(_enumClass.cast(value), wantedEntry);
            else
                // Everything is filtered
                _playMap = null;
        } // Class to filter is class for object
        else {
            Iterator<Map.Entry<K, PlayStoreTree>> mapPtr = _playMap.entrySet().iterator();
            while (mapPtr.hasNext()) {
                Map.Entry<K, PlayStoreTree> mapEntry = mapPtr.next();
                mapEntry.getValue().slice(value, valueType);
                if (mapEntry.getValue().empty()) // Entry entry filtered out
                    mapPtr.remove();
            } // While entries to test
        } // Filter value not for enum type of this object
    } // Method slice()
    
    /* Removes all plays whose integer field value is outside the specified
       range. This implements a version of slice for these fields */
    public void slice(SinglePlay.NumericFields field,
                      IntegerRange wantRange)
    {
        if (empty())
            return; // Nothing to do!
        
        // Slice every entry below this one
        Iterator<Map.Entry<K, PlayStoreTree>> mapPtr = _playMap.entrySet().iterator();
        while (mapPtr.hasNext()) {
            Map.Entry<K, PlayStoreTree> mapEntry = mapPtr.next();
            mapEntry.getValue().slice(field, wantRange);
            if (mapEntry.getValue().empty()) // Every entry filtered out
                mapPtr.remove();
        } // While entries to test
    }
    
    /* Generates entries for the map within this object. The type of entry
       depends on the enum type of the current map
       WARNING: This routine ensures that maps are generated in a hierarchy
       of enum types. Other routines must respect this hierarchy or bad
       things will result
       WARNING: Every enum type must appear in SinglePlay, or the code will
       ultimately fail on play insert */
    private PlayStoreTree newMapEntry()
    {
        if (SinglePlay.DownNumber.class.isAssignableFrom(_enumClass))
            return newDataStore(SinglePlay.DistanceNeeded.class);
        else if (SinglePlay.DistanceNeeded.class.isAssignableFrom(_enumClass))
            return newDataStore(SinglePlay.TimeRemaining.class);
        else if (SinglePlay.TimeRemaining.class.isAssignableFrom(_enumClass))
            return newDataStore(SinglePlay.FieldLocation.class);
        else if (SinglePlay.FieldLocation.class.isAssignableFrom(_enumClass))
            return newDataStore(SinglePlay.ScoreDifferential.class);
        else
            return new PlayStoreList();
    }
    
    /* Utility to generate a new PlayStoreNode without specifying the enum
       type twice (once in the type paramter and once as the argument). */
    private <K extends Enum<K>> PlayStoreTree newDataStore(Class<K> type)
    {
        return new PlayStoreNode<K>(type);
    }
    
    // Rolls up the entire contents into the passed play list
    public void rollup(ArrayList<SinglePlay> plays)
    {
        /* Iterate through the entries in this object, and roll up each
           one */
        if (!empty()) {
            Iterator<Map.Entry<K, PlayStoreTree>> entryPtr = _playMap.entrySet().iterator();
            while (entryPtr.hasNext())
                entryPtr.next().getValue().rollup(plays);
        } // Current map not empty
    }
    
    /* Rolls the contents into the passed enumerated map, splitting
       plays by the category of the enumerated type */
    // NOTE: K is used by DataStoreInterface, end up with L
    public <L extends Enum<L>> void pivot(EnumMap<L, ArrayList<SinglePlay>> plays, Class<L> enumType)
    {
        /* If the passed class is the same as the enum class of this
           object, roll each entry it its matching entry from the passed
           map (which may need to be created first). Otherwise, roll
           every entry into the existing map */
        if (empty())
            return; // Nothing to do!
        boolean haveMatchClass = _enumClass.isAssignableFrom(enumType);
        Iterator<Map.Entry<K, PlayStoreTree>> mapPtr = _playMap.entrySet().iterator();
        while (mapPtr.hasNext()) {
            Map.Entry<K, PlayStoreTree> mapEntry = mapPtr.next();
            if (haveMatchClass) {
                ArrayList<SinglePlay> wantedEntry = plays.get(enumType.cast(mapEntry.getKey()));
                if (wantedEntry == null) {
                    wantedEntry = new ArrayList<SinglePlay>();
                    plays.put(enumType.cast(mapEntry.getKey()), wantedEntry);
                }
                mapEntry.getValue().rollup(wantedEntry);
            } // Class to filter is class for object
            else
                mapEntry.getValue().pivot(plays, enumType);
        } // While entries to test
    } // Pivot method
    
    /* Rolls the contents into the passed double layered enumerated map,
       splitting plays by the category of the enumerated types */
    // NOTE: K is used by DataStoreInterface, end up with L and M
    public <L extends Enum<L>, M extends Enum<M>> void pivot(EnumMap<L, EnumMap<M, ArrayList<SinglePlay>>> plays, Class<L> firstEnumType, Class<M> secondEnumType)
    {
        /* If the first passed class is the same as the enum class of this
           object, roll each entry it its matching entry from the passed
           map (which may need to be created first). Otherwise, roll
           every entry into the existing map
           TRICKY NOTE: What if it matches the second entry? Do nothing. The
           situation means that the enum index order for the result is
           opposite to their order in the current data store. Trying to
           split by the second index now will lead to bad results. Let it
           fall through to the last level, which knows how to handle it
           properly */
        if (empty())
            return; // Nothing to do!
        boolean haveMatchClass = _enumClass.isAssignableFrom(firstEnumType);
        Iterator<Map.Entry<K, PlayStoreTree>> mapPtr = _playMap.entrySet().iterator();
        while (mapPtr.hasNext()) {
            Map.Entry<K, PlayStoreTree> mapEntry = mapPtr.next();
            if (haveMatchClass) {
                EnumMap<M, ArrayList<SinglePlay>> wantedEntry = plays.get(firstEnumType.cast(mapEntry.getKey()));
                if (wantedEntry == null) {
                    /* Note about to create a M EnumMap here, so need the
                       second type, not the first */
                    wantedEntry = new EnumMap<M, ArrayList<SinglePlay>>(secondEnumType);
                    plays.put(firstEnumType.cast(mapEntry.getKey()),
                              wantedEntry);
                }
                mapEntry.getValue().pivot(wantedEntry, secondEnumType);
            } // Class to filter is class for object
            else
                mapEntry.getValue().pivot(plays, firstEnumType,
                                          secondEnumType);
        } // While entries to test
    } // Pivot method
    
    /* Custom iterator for this data store, based on the usual design
       pattern. The iterator is an inner class so it has access to
       the private members of the object that generates it. Note that
       it implements an interface, so this is also the Factory pattern */
    public class PlayStoreIterator implements Iterator<ArrayList<SinglePlay>>
    {
        /* This class iterates through the map within the class and calls
           their iterators in order. When the last one has no more entries,
           this object doesn't either.
           WARNING: Changing the map in any way will invalidate the
           iterator! This code DOES NOT check for the situation. This
           iterator should only be used privately within the data store,
           and the overall data store handles it for its own iterators,
           ensuring this one is only called when it is still valid */
        
        // Map entry being iterated
        Iterator<Map.Entry<K, PlayStoreTree>> _entryPtr; 
        // Iterator within map entry
        Iterator<ArrayList<SinglePlay>> _entryIterator;
        
        public PlayStoreIterator()
        {
            _entryPtr = null;
            _entryIterator = null;
            if (_playMap != null) {
                _entryPtr = _playMap.entrySet().iterator(); // Parent struct
                findNextEntry();
            }
        }  // Constructor
        
        @Override
            public boolean hasNext()
            {
                if (_entryIterator == null)
                    return false;
                else
                    return _entryIterator.hasNext();
            }
        
        @Override
            public ArrayList<SinglePlay> next()
            {
                if (!hasNext())
                    throw new NoSuchElementException();
                else {
                    ArrayList<SinglePlay> result = _entryIterator.next();
                    findNextEntry();
                    return result;
                }
            } // next method
        
        @Override
            public void remove()
            {
                // Not allowed!
                throw new UnsupportedOperationException();
            }
        // Find the next valid entry, which may be the current one 
        private void findNextEntry()
        {
            /* Reading an iterator autmatically advances it. Thanks to the
               multiple tree layers, it may not advance to a valid entry. As
               long as map entries exist, need to check them until a valid
               entry is fouund */
            while ((!hasNext()) && _entryPtr.hasNext())
                _entryIterator = _entryPtr.next().getValue().iterator();
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
        if (empty()) {
            output.append("MapValues ");
            output.append(indexes);
            output.append("\n");
            output.append("empty\n");
        }
        else {
            /* The calls below add the current enum value in the map to
               the index list. Need to clear them before the next call,
               which requires trimming the index buffer to its current
               size */
            int indexSize = indexes.length();
            Iterator<Map.Entry<K, PlayStoreTree>> entryPtr = _playMap.entrySet().iterator();
            while (entryPtr.hasNext()) {
                Map.Entry<K, PlayStoreTree> mapEntry = entryPtr.next();
                indexes.append(" " + mapEntry.getKey());
                mapEntry.getValue().toOutput(indexes, output);
                indexes.setLength(indexSize); 
            } // While map entries to process
        } // Object has a map set
    } // toOutput method
} // Class PlayStoreNode

