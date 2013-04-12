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
/* This class implements a factory for generating data store slice objects.
   The class itself is generic, so a factory makes generating them much
   easier. They must be generated in REVERSE order of the wanted slice
   operations, with each object passed back to the factory when generating
   the next.

   The order of slices has a big impact on performance. Slicing earlier
   dimensions in the data store first reduces the amount of data processed
   in later slices. Slicing enumerated type values is much faster than slicing
   integer values, so integer field slices should be done last. The end result
   does not depend on order however, only the speed.

   As of the time this comment was last updated, slice objects should be
   generated in this order (remember that this is the reverse of how they will
   actually be called):
   Integer based fields
   SinglePlay.PlayType.class
   SinglePlay.ScoreDifferential.class
   SinglePlay.FieldLocation.class
   SinglePlay.TimeRemaining.class
   SinglePlay.DistanceNeeded.class
   SinglePlay.DownNumber.class
*/
public class PlaySliceFactory
{
    // Adds a slice of the wanted type to an existing chain
    public static <P extends Enum<P>> PlaySlice getSlice(P sliceValue,
                                                         Class<P> sliceClass,
                                                         PlaySlice chain)
    {
        return new PlaySliceEnum<P>(sliceValue, sliceClass, chain);
    }

    // Adds an integer field slice of the wanted type to an existing chain
    public static PlaySlice getSlice(SinglePlay.NumericFields field,
                                     IntegerRange wantRange,
                                     PlaySlice chain)
    {
        return new PlaySliceInt(field, wantRange, chain);
    }

    // Generates a slice of the wanted type, at the end of the chain
    public static <P extends Enum<P>> PlaySlice getSlice(P sliceValue, Class<P> sliceClass)
    {
        return new PlaySliceEnum<P>(sliceValue, sliceClass);
    }

    // Generates an interger field slice of the wanted type at the end of the chain
    public static PlaySlice getSlice(SinglePlay.NumericFields field,
                                     IntegerRange wantRange)
    {
        return new PlaySliceInt(field, wantRange);
    }

    // Test program. Generate a chain and apply it
    public static void main(String[] args)
    {
        PlaySlice test = getSlice(SinglePlay.NumericFields.DISTANCE_GAINED,
                                  new IntegerRange(0, 10));
        test = getSlice(SinglePlay.PlayType.RUN_LEFT,
                        SinglePlay.PlayType.class, test);
        test = getSlice(SinglePlay.DownNumber.FIRST_DOWN,
                         SinglePlay.DownNumber.class, test);
        System.out.println(test + "\n");
        DataStore testData = DataStore.buildTestDataStore();
        test.slice(testData);
        System.out.println(testData);
    }
}