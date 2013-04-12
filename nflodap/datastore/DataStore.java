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

/* This class defines the store for play data. ODAP data tends to be stored
   in one of two scemas, stars and cubes. In a star, the data is kept as an
   array or equivalent, with sets of pointers based on characteristics. In a
   cube, its kept as a multi-dimensional array indexed by characteristic values.
   Cubes have significantly better peformance when the number of dimensions is
   low, and most values of each dimension will have data. This is true in
   practice for NFL plays when play-calling characteristics are used as the
   dimensions. Dimension order affects performance, so characteristics should
   be specified in decreasing order of likelyhood of filtering or grouping.

   NOTE: To simplify the code, only category based characteristics can be used
   to group plays for output. ALL characterics can be used to filter.

   Since category characteristics are all specified as enums, the natural data
   design is a set of nested enummaps. Using them like this, though, is
   difficult, thanks to Java's strict typesafety and lack of typedefs. The same
   code must be implemented over and over, once for every level of nested maps.
   Such code is tedious to write and nearly impossible to update. Instead, this
   code uses the Composite pattern to implement the cube as a very wide tree.
   Every entry for a given enum value has pointers to the entries for the next
   layer of enum values. The memory usage is only slightly larger than the
   nested maps.

   Data is read out of the class using an iterator. It produces one list of
   plays for each set of category values with plays defined. The lists are
   copied from the data store to protect its contents. The category values
   are NOT reported; the caller should know the dimensions of the data store
   (which can change thanks to pivot calls) and read them directly from the
   plays. Any operation that modifies the data store will invalidate all
   iterators.

   This design contains lots of components that must be kept in sync. To protect
   them, they are all defined and accessed through a wrapper class. */
public class DataStore implements Iterable<ArrayList<SinglePlay>> {

    // Dummy enum created for test purposes
    private static enum TestEnum { VALUE_ONE, VALUE_TWO, VALUE_THREE };

    // Data stored within memory
    PlayStoreTree _data;

    /* Number of times iterators have become invalid due to modifications of
       the data store */
    int _iteratorInvalidCount;
    // True, at least one iterator generated since last modification
    boolean _generatedIterators; 
    
    // Constructor
    public DataStore()
    {
        _data = new PlayStoreNode<SinglePlay.DownNumber>(SinglePlay.DownNumber.class);
        _iteratorInvalidCount = 0;
        _generatedIterators = false;
    }

    // Clone method
    /* HACK/WORKAROUND: Easier to ignore the cast warning then to fully test
       the class cast in question. */
    @SuppressWarnings({"unchecked"})
    public Object clone() throws CloneNotSupportedException
    {
        DataStore newObject = new DataStore();
        newObject._data = (PlayStoreTree)_data.clone();
        return newObject;
    }

    // Returns true if this data store is empty
    public boolean empty()
    {
        if (_data == null)
            return true;
        else
            return _data.empty();
    }

    // Insert a new play into the data store
    public void insertPlay(SinglePlay play)
    {
        invalidateIterators();
        _data.insertPlay(play);
    }
        
    /* Remove all plays that do not have the passed enumerated value. If the
       value type does not exist in the play (possible thanks to the generic
       type) nothing happens. This implements the ODAP slice operation */
    public <P extends Enum<P>> void slice(P value, Class<P> valueType)
    {
        invalidateIterators();
        _data.slice(value, valueType);
    }
    
    /* Removes all plays whose integer field value is outside the specified
       range. This implements a version of slice for these fields */
    public void slice(SinglePlay.NumericFields field, IntegerRange wantRange)
    {
        invalidateIterators();
        _data.slice(field, wantRange);
    }

    // Converts a list of plays into a data store object
    private PlayStoreTree toDataStoreClass(ArrayList<SinglePlay> data)
    {
        return new PlayStoreList(data);
    }

    // Converts a map of play lists into a data store object
    private <P extends Enum<P>> PlayStoreTree toDataStoreClass(EnumMap<P, ArrayList<SinglePlay>> data,
                                                              Class<P> enumType)
    {
        /* Iterate through the data, convert each entry into a data store
           object, insert in new map, and then convert that map */
        EnumMap<P, PlayStoreTree> result = new EnumMap<P, PlayStoreTree>(enumType);
        Iterator<Map.Entry<P, ArrayList<SinglePlay>>> mapPtr = data.entrySet().iterator();
        while (mapPtr.hasNext()) {
            Map.Entry<P, ArrayList<SinglePlay>> mapEntry = mapPtr.next();
            result.put(mapEntry.getKey(), toDataStoreClass(mapEntry.getValue()));
        } // While map entries to convert
        return new PlayStoreNode<P>(enumType, result);
    }

    // Converts a map of maps of play lists into a data store object
    private <P extends Enum<P>, Q extends Enum<Q>> PlayStoreTree toDataStoreClass(EnumMap<P, EnumMap<Q, ArrayList<SinglePlay>>> data,
                                                                                 Class<P> firstEnumType,
                                                                                 Class<Q> secondEnumType)
    {
        /* Iterate through the data, convert each entry into a data store
           object, insert in new map, and then convert that map */
        EnumMap<P, PlayStoreTree> result = new EnumMap<P, PlayStoreTree>(firstEnumType);
        Iterator<Map.Entry<P, EnumMap<Q, ArrayList<SinglePlay>>>> mapPtr = data.entrySet().iterator();
        while (mapPtr.hasNext()) {
            Map.Entry<P, EnumMap<Q, ArrayList<SinglePlay>>> mapEntry = mapPtr.next();
            result.put(mapEntry.getKey(), toDataStoreClass(mapEntry.getValue(), secondEnumType));
        } // While map entries to convert
        return new PlayStoreNode<P>(firstEnumType, result);
    }

    /* Converts the data store into one with no indexes. This performs the
       OLAP rollup operation */
    public void rollup()
    {
        invalidateIterators();
        ArrayList<SinglePlay> result = new ArrayList<SinglePlay>();
        _data.rollup(result);
        _data = toDataStoreClass(result);
    }

    /* Converts the data store into one with a single enum index. This performs
       a version of the OLAP pivot operation */
    public <P extends Enum<P>> void pivot(Class<P> indexType)
    {
        invalidateIterators();
        EnumMap<P, ArrayList<SinglePlay>> result = new EnumMap<P, ArrayList<SinglePlay>>(indexType);
        _data.pivot(result, indexType);
        _data = toDataStoreClass(result, indexType);
    }

    /* Converts the data store into one with two enum indexes. Two was chosen
       as a practical limit because more than two dimensions on a screen gets
       hard to intepret. This performs a version of the OLAP pivot operation */
    public <P extends Enum<P>, Q extends Enum<Q>> void pivot(Class<P> firstIndexType,
                                                             Class<Q> secondIndexType)
    {
        invalidateIterators();
        EnumMap<P, EnumMap<Q, ArrayList<SinglePlay>>> result = new EnumMap<P, EnumMap<Q, ArrayList<SinglePlay>>>(firstIndexType);
        _data.pivot(result, firstIndexType, secondIndexType);
        _data = toDataStoreClass(result, firstIndexType, secondIndexType);
    }

    // Flags data store that all generated iterators are now invalid
    private void invalidateIterators()
    {
        if (_generatedIterators) {
            _iteratorInvalidCount++;
            _generatedIterators = false;
        }
        // Otherwise nothing needs to be done
    }
        
    // Iterator on the data store
    public class DataStoreIterator implements Iterator<ArrayList<SinglePlay>>
    {
        /* Iterators on the data store remain valid only as long as the
           datastore is not modified. This means that every call on the iterator
           needs to check validity. The key insight to doing this efficiently
           is that on modification, ALL existing iterators become invalid. This
           class tracks the number of times a modification invalidated
           iterators, and records this number in each iterator generated. Only
           when the number in the iterator matches the current value in the
           object will the iterator remain valid, otherwise the object has been
           modified since generation */
        private int _invalidationCount;
        private Iterator<ArrayList<SinglePlay>> _storeIterator;

        public DataStoreIterator()
        {
            if (_data != null)
                _storeIterator = _data.iterator();
            else
                _storeIterator = null;
            _invalidationCount = _iteratorInvalidCount; // Parent field
        }  // Constructor

        @Override
            public boolean hasNext()
            {
                /* Fail nicely: If iterator is invalid, return false so clients
                   can stop using it without getting an exception */
                if (_invalidationCount != _iteratorInvalidCount) // Parent field
                    return false;
                else if (_storeIterator == null)
                    return false;
                else {
                    return _storeIterator.hasNext();
                }
            }
        
        @Override
            public ArrayList<SinglePlay> next()
            {
                /* If iterator is invalid, throw exception. Required by Java
                   standard */
                if (_invalidationCount != _iteratorInvalidCount) // Parent field
                    throw new ConcurrentModificationException();
                else if (!hasNext())
                    throw new NoSuchElementException();
                else 
                    return _storeIterator.next();
            } // next method

        @Override
            public void remove()
            {
                // Not allowed!
                throw new UnsupportedOperationException();
            }
    }

    /* Iterator on the data store. It is actually implemented by each
       subclass using the factory pattern. */
    public Iterator<ArrayList<SinglePlay>> iterator()
    {
        _generatedIterators = true;
        return new DataStoreIterator();
    }

    public String toString()
    {
        if (_data == null)
            return "empty";
        else {
            StringBuffer output = new StringBuffer();
            StringBuffer indexes = new StringBuffer();
            _data.toOutput(indexes, output);
            return output.toString();
        } // Data store not empty
    }
        
    // Code to generate a hard-coded data store used for testing purposes.
    public static DataStore buildTestDataStore()
    {
        DataStore test = new DataStore();
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.FIRST_DOWN, 10, 50,
                                       45, 0, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.THIRD_DOWN, 10, 50,
                                   45, 0, 0, 8, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.FIRST_DOWN, 10, 50,
                                   45, 0, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.THIRD_DOWN, 10, 50,
                                   45, 0, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.THIRD_DOWN, 10, 50,
                                   45, 0, 0, 8, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.FIRST_DOWN, 10, 50,
                                   45, 0, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.FIRST_DOWN, 1, 50,
                                   45, 0, 0, 8, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.THIRD_DOWN, 1, 50,
                                   45, 0, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.FIRST_DOWN, 1, 50,
                                   45, 0, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.THIRD_DOWN, 1, 50,
                                   45, 0, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.THIRD_DOWN, 1, 50,
                                   45, 0, 0, 8, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.FIRST_DOWN, 1, 50,
                                   45, 0, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.FIRST_DOWN, 10, 50,
                                   45, 14, 0, 8, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.THIRD_DOWN, 10, 50,
                                   45, 14, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.FIRST_DOWN, 10, 50,
                                   45, 14, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.THIRD_DOWN, 10, 50,
                                   45, 14, 0, 8, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.THIRD_DOWN, 10, 50,
                                   45, 14, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.FIRST_DOWN, 10, 50,
                                   45, 14, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.FIRST_DOWN, 1, 50,
                                   45, 14, 0, 8, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.THIRD_DOWN, 1, 50,
                                   45, 14, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.FIRST_DOWN, 1, 50,
                                   45, 14, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.THIRD_DOWN, 1, 50,
                                   45, 14, 0, 11, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.PASS_SHORT_MIDDLE,
                                   SinglePlay.DownNumber.THIRD_DOWN, 1, 50,
                                   45, 14, 0, 8, false));
        test.insertPlay(new SinglePlay(1, SinglePlay.PlayType.RUN_LEFT,
                                   SinglePlay.DownNumber.FIRST_DOWN, 1, 50,
                                   45, 14, 0, 11, false));
        return test;
    }

    /* Code to test the class. Puts a bunch of plays in the data store,
       outputs it, and then slices it */
    public static void main(String[] args) throws CloneNotSupportedException
    {
        DataStore test = buildTestDataStore();
        Iterator<ArrayList<SinglePlay>> index = test.iterator();
        index.next();
        index.next();
        index.next();
        index.next();
        System.out.println("Iterator 1 valid:" + index.hasNext());
        test.slice(SinglePlay.DistanceNeeded.TEN_TO_FOUR,
                   SinglePlay.DistanceNeeded.class);
        test.slice(SinglePlay.NumericFields.DISTANCE_GAINED,
                   new IntegerRange(0, 10));
        System.out.println(test);
        Iterator<ArrayList<SinglePlay>> index2 = test.iterator();
        System.out.println("Iterator 1 valid:" + index.hasNext());
        System.out.println("Iterator 2 valid:"+ index2.hasNext());

        index2.next();
    }
};
