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

/* This class allows the classes of enumerated types to be manipulated without
   specifying what class it is. Several ODAP methods take play categories
   specified as enumerated types and manipulate the data to analyze. These
   classes are specified by configuration, so a way is needed to pass and store
   them. The usual solution is using generic wildcards. Unfortunately, they
   don't work directly with enumerated types. The problem is that a generic
   enum must be specified as <P extends Enum<P>>. Note that it takes TWO
   generics, and they must be the same. When two wildcards are used, they are
   not guarenteed to match, which means that capture of <? extends Enum<?>>
   will not work.

   The solution is a design pattern called an EnumWrapper. It is a generic
   class whose sole purpose is to wrap an Enum class. The thing that makes it
   work is that it has only one generic in its definition, and thus only one
   '?' wildcarded, so capture works. Even better, this wildcarded class can be
   used to define variable types, allowing the types to be passed around like
   any other value! The price is that the client must extract the actual enum
   class from the wrapper before passing it into whatever method needs it */
public final class EnumWrapper<P extends Enum<P>>
{
    private Class<P> _class;
    
    private EnumWrapper(Class<P> type)
    {
        _class = type;
    }
    
    /* Factory method to generate enum wrappers, avoids need to write the names
       of enums twice when creating them, a royal pain */
    static public <P extends Enum<P>> EnumWrapper<P> create(Class<P> type)
    {
        return new EnumWrapper<P>(type);
    }

    public Class<P> getEnum()
    {
        return _class;
    }
    
    public String toString()
    {
        return _class.toString();
    }
}