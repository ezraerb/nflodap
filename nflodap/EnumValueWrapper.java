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

/* This class allows values of enumerated types to be manipulated without
   specifying what type it is. Several ODAP methods take play categories and
   values specified by enumerated types and manipulate the data to analyze.
   These classes are specified by configuration, so a way is needed to pass
   and store them. The usual solution is using generic wildcards.
   Unfortunately, they don't work directly with enumerated types. The problem
   is that a generic enum must be specified as <P extends Enum<P>>. Note that
   it takes TWO generics, and they must be the same. When two wildcards are
   used, they are not guarenteed to match, which means that capture of
   <? extends Enum<?>> will not work.

   The solution is a design pattern called an EnumWrapper. It is a generic
   class whose sole purpose is to wrap an Enum value. The thing that makes it
   work is that it has only one generic in its definition, and thus only one
   '?' wildcarded, so capture works. Even better, this wildcarded class can be
   used to define variable types, allowing the types to be passed around like
   any other value! The price is that the client must extract the actual enum
   value from the wrapper before passing it into whatever method needs it.
   Note that if both the class AND value are needed, the call into the method
   must be done by a helper that takes this object, otherwise the call will
   again have two wildcards, preventing capture */
public final class EnumValueWrapper<P extends Enum<P>>
{
    private Class<P> _class;
    
    private P _value;

    // Straightforward constructor
    private EnumValueWrapper(Class<P> type, P value)
    {
        /* Extracting the class from a generic variable is difficult; better
           to include it here */
        _class = type;
        _value = value;
    }
    
    /* Construct it using the class and the string for the value. This is
       a wrapper around the Enum.valueOf() method. Throws
       IllegalArgumentException for an unknown name */
    private EnumValueWrapper(Class<P> type, String value)
    {
        this(type, Enum.valueOf(type, value));
    }
    
    /* Factory method to generate enum value wrappers, avoids need to write
       the names of enums twice when creating them, a royal pain */
    static public <P extends Enum<P>> EnumValueWrapper<P> create(Class<P> type,
                                                                 P value)
    {
        return new EnumValueWrapper<P>(type, value);
    }

    // Factory method to generate enum value wrappers given an enum wrapper
    static public <P extends Enum<P>> EnumValueWrapper<P> create(EnumWrapper<P> type,
                                                                 P value)
    {
        return new EnumValueWrapper<P>(type.getEnum(), value);
    }

    /* Factory method to generate enum value wrappers given an enum wrapper and
       the string name of the enum value. Throws IllegalArgumentException for
       an unknown name */
    static public <P extends Enum<P>> EnumValueWrapper<P> create(EnumWrapper<P> type,
                                                                 String value)
    {
        return new EnumValueWrapper<P>(type.getEnum(), value);
    }

    public Class<P> getEnum()
    {
        return _class;
    }
    
    public P getValue()
    {
        return _value;
    }

    public String toString()
    {
        // Want the value, not the class
        return _value.toString();
    }
}