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

/* The class selects which teams are wanted when loading plays. Statistical
   techniques are hard to apply to football, because the number of variables a
   team faces are so large. Modern NFL teams deal with the issue through their
   Quality Control departments. Any NFL team can request film on any game ever
   played back to the early 1960s (when NFL Films started). This department
   selects games that will be the most useful to study and breaks down the
   film into individual plays.
       
   Selection of games to study is one of the deepest arts of football coaching.
   Most select previous games against a given opponent, teams similiar to their
   their team against that opponent, and their team against teams similiar to
   their opponent. The class builds the database using this approach. The
   program caller specifies the similiar teams.
       
   WARNING: This class is still pretty simplistic compared to real Quality
   Control, which will lead to skewed results. Many go much futher that just
   teams, getting film containing key players or teams where a key coach has
   worked previously. They also select games from different team/season
   combinations while this class gets all seasons for a given matchup */
public class NFLqualityControl
{
    private String _ourTeam;
    private String _opposition;
    private String[] _ourSimiliar;
    private String[] _oppSimiliar;
    
    /* Constructor where team names are passed as strings. The input was chosen
       because it matches how they are specified in both the command line and
       the GUI */
    public NFLqualityControl(String ourTeam, String opposition,
                             String ourSimiliar, String oppSimiliar)
    {
        // Passing either required team NULL or empty is an error
        if ((ourTeam == null) || (opposition == null))
            throw new IllegalArgumentException();
        else if (ourTeam.isEmpty() || opposition.isEmpty())
            throw new IllegalArgumentException();

        _ourTeam = ourTeam;
        _opposition = opposition;

        /* If similiar teams are specified, break the strings into individual
           teams. If the result ends up empty, set to null instead
           NOTE: '\s' means 'any whitespace character. The second slash is
           needed to insert a litteral '\' into the output stream. The '+'
           means 'one or more', so multiple spaces get treated as a delimiter
           instead of wanted data */
        if (ourSimiliar != null) {
            _ourSimiliar = ourSimiliar.split("\\s+");
            if (_ourSimiliar.length == 0)
                _ourSimiliar = null;
            else
                Arrays.sort(_ourSimiliar);
        }
        if (oppSimiliar != null) {
            _oppSimiliar = oppSimiliar.split("\\s+");
            if (_oppSimiliar.length == 0)
                _oppSimiliar = null;
            else
                Arrays.sort(_oppSimiliar);
        }

        /* Specifying either ourTeam or opposition in the similarity list is
           a big problem */
        if (_ourSimiliar != null) {
            if ((Arrays.binarySearch(_ourSimiliar, _ourTeam) >= 0) ||
                (Arrays.binarySearch(_ourSimiliar, _opposition) >= 0))
                throw new IllegalArgumentException();
        } // Similiar teams specified
        if (_oppSimiliar != null) {
            if ((Arrays.binarySearch(_oppSimiliar, _ourTeam) >= 0) ||
                (Arrays.binarySearch(_oppSimiliar, _opposition) >= 0))
                throw new IllegalArgumentException();
        } // Similiar teams specified
    }

    // Determines whether a given play is wanted
    public boolean selectGame(String offense, String defense)
    {
        /* A play is wanted if it falls within one of three categories
           1. Offense matches our team and defense matches opposition
           2. Offense matches our team and defense falls in list of teams
              similiar to opposition
           3. Offense falls in list of teams similar to us and defense matches
              wanted oppostion
           WARNING: This process is sensitive to whitespace and capitalization,
           because its more efficient for the program caller to get this right
           in the arguments than for this code to deal with matching it. The
           current set of data files requires UPPERCASE and no whitespace
           SUBTLE NOTE: Java strings can in fact deal with capitalization
           differences. Doing so will degrade performance by a factor of
           roughly N/logN, where N is the number of similiar teams, thanks to
           standard array operators using compareTo() methods.
        */
        boolean haveMatch = false;
        if (_ourTeam.compareTo(offense) == 0) {
            if (_opposition.compareTo(defense) == 0)
                haveMatch = true;
            else if (_oppSimiliar == null)
                haveMatch = false;
            else
                haveMatch = (Arrays.binarySearch(_oppSimiliar, defense) >= 0);
        } // Offense matches the wanted team
        else if (_opposition.compareTo(defense) == 0) {
            if (_ourSimiliar == null)
                haveMatch = false;
            else
                haveMatch = (Arrays.binarySearch(_ourSimiliar, offense) >= 0);
        }
        else
            haveMatch = false;
        return haveMatch;
    } // Method selectGame

    public String toString()
    {
        StringBuffer output = new StringBuffer();
        output.append("Our Team:" + _ourTeam + " Opposition:"
                      + _opposition);
        if (_ourSimiliar != null) {
            output.append(" Our Similiar:");
            output.append(Arrays.toString(_ourSimiliar));
        }
        if (_oppSimiliar != null) {
            output.append(" Opposition Similiar:");
            output.append(Arrays.toString(_oppSimiliar));
        }
        return output.toString();
    }
} // class NFLqualityControl
