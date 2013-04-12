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

import java.io.*;
import java.util.*;
import java.lang.*;

/* This class loads plays from .csv files into memory. Statistical techniques
   are hard to apply to football, because the number of variables a team faces
   are so large. Modern NFL teams deal with the issue through their Quality
   Control departments. Any NFL team can request film on any game ever played
   back to the early 1960s (when NFL Films started). This department selects
   games that will be the most useful to study and breaks down the film into
   individual plays.

   Selection of games to study is one of the blackest arts of football
   coaching. Most select previous games against a given opponent, teams
   similiar to their their team against that opponent, and their team
   against teams similiar to their opponent. The class builds the database
   using this approach. The caller specifies the similiar teams.
   
   WARNING: This class is still pretty simplistic compared to real Quality
   Control, which will lead to skewed results. Many go much futher that
   just teams, getting film containing key players or teams where a key
   choach has worked previously. They also select games from different
   team/season combinations while this routine gets all seasons for a given
   matchup */

public final class PlayLoader {
    
    // File to load plays from. Inside class to ensure always released
    private BufferedReader _file;
    private String _directory; // Where to file data files
    private String _name; // Name of file currently processed
    private String _buffer; // Line from current file being processed
    private int _position; // Position processed within current line of file

    private int _playCount; // Number of plays processed
    private int _sackCount; // Number of sacks in input, turned into pass plays
    

    /* Private class to hold the results of a play parsed from a description */
    private class PlayResults
    {
        private int _distance;
        private boolean _turnedOver;

        public PlayResults(int distance, boolean turnedOver)
        {
            _distance = distance;
            _turnedOver = turnedOver;
        }

        public int getDistance()
        {
            return _distance;
        }

        public boolean getTurnedOver()
        {
            return _turnedOver;
        }
    }

    /* Private class whose sole reason for existence is to pass back the results
       of parsing the description of a play */
    private class ParsePlayData
    {
        SinglePlay.PlayType _playType;
        private int _distance;
        private boolean _turnedOver;

        public ParsePlayData(SinglePlay.PlayType playType, PlayResults results)
        {
            _playType = playType;
            _distance = results.getDistance();
            _turnedOver = results.getTurnedOver();
        }

        public ParsePlayData(SinglePlay.PlayType playType, int distance,
                             boolean turnedOver)
        {
            _playType = playType;
            _distance = distance;
            _turnedOver = turnedOver;
        }

        public boolean havePlayData()
        {
            return (_playType != null);
        }

        public SinglePlay.PlayType getPlayType()
        {
            return _playType;
        }

        public int getDistance()
        {
            return _distance;
        }

        public boolean getTurnedOver()
        {
            return _turnedOver;
        }
    }

    // Constructor. Takes the path to the directory with data files
    public PlayLoader(String filePath)
    {
        _directory = filePath;
        _position = -1; // Token, invalid
        _playCount = 0;
        _sackCount = 0;
    }

    /** This method ensures the file is always closed before the object dies.
        In general, if the file gets to here, something has gone wrong and
        resources have been held far longer than needed. A warning is issued
        to handle this case */
    public void finailize()
    {
        if (_file != null) {
            System.out.println("WARNING: File " + _name + " not properly closed");
            try {
                _file.close();
            }
            /* Catch and dispose of any IO exception, since the object is going
               away soon anyway. This is normally a hack, but needed in this
               case */
            catch (IOException e) {}
            _file = null;
        }
    }

    /* Closes the file open in the object */
    private void closeFile() throws Exception
    {
        if (_file != null)
            _file.close();
        _file = null;
    }

    // Returns true if the current buffer contains valid data
    private boolean bufferValid()
    {
        return (_position >= 0);
    }
    
    // Sets a buffer to be invalid
    private void setBufferInvalid()
    {
        _position = -1; // Token value to indicate invalid buffer
    }
    
    /** Load plays into a data store and return it */
    public DataStore loadPlays(NFLqualityControl wantedMatchups,
                               int yearRange) throws Exception
    {
        /* FUTURE DEVELOPMENT: Should use file system calls to find the range
           of years with play data. This routine hardcodes it */
        int firstYear = 2008;
        int lastYear = 2011;
        if ((yearRange >= 0) && (lastYear - yearRange + 1 > firstYear))
            firstYear = lastYear - yearRange + 1;
        
        DataStore dataStore = new DataStore();

        int yearCounter;
        for (yearCounter = lastYear; yearCounter >= firstYear; yearCounter--)
            loadSingleSeason(wantedMatchups, yearCounter, dataStore);
        /* If the database is empty, assume the teams were specified
           incorrectly */
        if (dataStore.empty())
            throw new IllegalArgumentException(wantedMatchups + " invalid; returned no plays");
        return dataStore;
    }

    // Loads plays for the wanted teams for one season into the data store
    public void loadSingleSeason(NFLqualityControl wantedMatchups,
                                 int seasonYear, DataStore dataStore) throws Exception
    {
        try {
            /* Assemble the file name. Format is XXXX_nfl_pbp_data.csv, where
               XXXX is the year. The passed directory does not include the
               backslash needed before the filename, so add it
               TRICKY NOTE: Notice the double backslash below. Java uses '\' as
               an escape character. The first is the esacpe character needed to
               insert a litteral '\' in the string! */
            _name = _directory + '\\' + seasonYear + "_nfl_pbp_data.csv";
            _file = new BufferedReader(new FileReader(_name));

            if (_file == null) {
                /* Failed to open. Ignoring it will lead to incomplete data, so
                   treat it as an error */
                System.out.println("Error loading play data, file " + _name + " missing");
                throw new IOException("Error loading play data, file " + _name + " missing");
            } // File not opened
            else {
                // First line is a header. Read it to burn it
                _file.readLine();
                _buffer = _file.readLine();
                while (_buffer != null) {
                    /* Read a play from the data file and process it. Keep in
                       mind that not every line from the data file will result
                       in a play thanks to the team filter and other reasons */
                    SinglePlay play = processPlay(wantedMatchups);
                    if (play != null)
                        dataStore.insertPlay(play);
                    _buffer = _file.readLine();
                } // While lines in the data file to process
                closeFile();
            } // File successfully opened
        } // Try..catch block around entire routine
        catch (Exception e) {
            /* Clean up before continuing */
            closeFile();
            throw e;
        }
    }

    /** Process a single play from a data file */
    public SinglePlay processPlay(NFLqualityControl wantedMatchups)
    {
        /* Play data is organized in the following fields:
           gameid,qtr,min,sec,off,def,down,togo,ydline,description,offscore,defscore,season
           They are extracted by searching for the commas */
        _position = 0; // New extraction, so reset processing position

        // First category is a game ID, burn it
        burnField();
        
        // Second category is quarter, burn it
        if (bufferValid())
            burnField();
        
        // Third cagetory is minues. Extract it
        int minutes = 0;
        if (bufferValid())
            minutes = extractNumericField();

        // Fourth category is seconds. Burn it
        if (bufferValid())
            burnField();
            
        // Fifth category is offence, extract it
        String offense = null;
        if (bufferValid())
            offense = extractStringField();

        // Sixth category is defence, extract it
        String defense = null;
        if (bufferValid())
            defense = extractStringField();

        // Seventh category is down, extract it
        int down = 0;
        if (bufferValid()) {
            /* If the down is missing, this play is a non-down play like
               kickoffs and extra point attempts. The database deliberately
               ignores these. Test for it before doing the extract so these are
               not flagged as errors */
            int testPos = _buffer.indexOf(',', _position + 1);
            /* SUBTLE NOTE: Strictly speaking, the first part of the test is not
               needed, since anything that matches the second part also matches
               the first. It is here for clarity to point out the comma must
               exist to be a valid field with no down data */
            if ((testPos != -1) && (testPos == (_position + 1)))
                return null; // Non-down play, so not included
            else
                down = extractNumericField();
        } // Properly formatted input to this point
        
        /* Convert the down number to a category. Unknown value indicates
           bad input */
        SinglePlay.DownNumber downNumber = null;
        if (bufferValid()) {
            switch(down) {
            case 1:
                downNumber = SinglePlay.DownNumber.FIRST_DOWN;
                break;
            case 2:
                downNumber = SinglePlay.DownNumber.SECOND_DOWN;
                break;
            case 3:
                downNumber = SinglePlay.DownNumber.THIRD_DOWN;
                break;
            case 4:
                downNumber = SinglePlay.DownNumber.FOURTH_DOWN;
                break;
            default:
                setBufferInvalid(); // Unknown down
            } // Switch on down from input
        }

        /* At this point, have enough information to determine whether this
           play is wanted or not. If it fails selection, return now */
        if (bufferValid()) {
            if (!wantedMatchups.selectGame(offense, defense))
                return null; // Play not for wanted team combination, ignore
        }

        /* If get to here, want the play. Extract remaining data snd
           insert into the data store. Some of it requires a tricky search
           of the description field */

        // Eighth category is distance needed, extract it.
        int distanceNeeded = 0;
        if (bufferValid())
            /* NOTE: If this is a non-down play, the distance won't be set
               either. They are rejected above, so missing the distance here is
               an error */
            distanceNeeded = extractNumericField();
        
        // Ninth category is position on the field, extract it.
        int yardLine = 0;
        if (bufferValid())
            yardLine = extractNumericField();

        /* Tenth category is play description. It gets processed below.
           Extract it */
        String description = null;
        if (bufferValid())
            description = extractStringField();

        // Eleventh category is offence current score, extract it.
        int ownScore = 0;
        if (bufferValid())
            ownScore = extractNumericField();
        
        // Twelveth category is defense current score, extract it.
        int oppScore = 0;
        if (bufferValid())
            oppScore = extractNumericField();

        /* To get play type, yardage gained, and turnover, need to parse the
           description. Thankfully, it has a standard format */
        ParsePlayData playData = null;
        if (bufferValid()) {
            try {
                playData = parsePlayDescription(description);
            } // Try block around play description parsing
            catch (Exception e) {
                System.out.println("Exception " + e + " parsing play description");
                // Don't output descrition, message below wil handle it
                setBufferInvalid();
            }
        } // No errors to this point

        if (!bufferValid()) { // Problems parsing the input
            System.out.println("Improperly formatted input: " + _buffer);
            return null; // No play read
        }
        else if (playData.havePlayData()) {
            _playCount++;
            return new SinglePlay(_playCount, playData.getPlayType(),
                                  downNumber, distanceNeeded, yardLine, minutes,
                                  ownScore, oppScore, playData.getDistance(),
                                  playData.getTurnedOver());
        }
        else
            return null; // Not a known play type
    } // processPlay method

    /* Burns the field after the passed in position, and sets the position to
       the next field. If the field does not exist, position is set to -1 */
    private void burnField()
    {
        _position = _buffer.indexOf(',', _position + 1);
    }

    /* Extract the value of a numeric field, and move Position to the start of
       the next field. Any error sets position to -1. */
    private int extractNumericField()
    {
        int prevPos = _position + 1; // Move off  the comma
        _position = _buffer.indexOf(',', prevPos);
        if (bufferValid())
            try {
                return Integer.parseInt(_buffer.substring(prevPos,
                                                          _position));
            }
            catch (Exception e) { // Any problem indicates misformatted input
                setBufferInvalid();
            }
        return -1; // TOKEN VALUE
    }

    /* Extract the value of a string field, and move Position to the start of
       the next field. Any error sets position to -1. */
    private String extractStringField()
    {
        int prevPos = _position + 1; // Move off  the comma
        _position = _buffer.indexOf(',', prevPos);
        if (bufferValid())
            try {
                return _buffer.substring(prevPos, _position);
            }
            catch (Exception e) { // Any problem indicates misformatted input
                setBufferInvalid();
            }
        return null;
    }

    /* Parses a play description to find out what type of play was executed
       and its results */
    private ParsePlayData parsePlayDescription(String description)
    {
        int wordLoc;
        SinglePlay.PlayType playType = null;
        PlayResults results = null;

        // ' pass ' or ' passed ' indicates a pass play
        wordLoc = description.indexOf(" pass ");
        if (wordLoc < 0)
            wordLoc = description.indexOf(" passed ");

        if (wordLoc >= 0) {
            wordLoc += 5; // Move to next word
            if (description.charAt(wordLoc) != ' ')
                wordLoc += 2; // Are sitting on 'ed '
            wordLoc++; // Move off the space

            // If next word is 'incomplete ', no yards and no turnover
            boolean haveIncomplete = false;
            if (description.regionMatches(wordLoc, "incomplete ", 0, 11)) {
                haveIncomplete = true;
                wordLoc += 11;
            } // Incomplete pass

            // Next word should be either 'short ' or 'deep '.
            boolean haveDeep = description.regionMatches(wordLoc, "deep ",
                                                         0, 5);
            if (haveDeep)
                // Move off the word
                wordLoc += 5;
            else {
                /* Unfortunately, not all pass descriptions include the
                   distance. These are treated as short. The effect here is
                   that 'short' should only be skipped if it is there */
                if (description.regionMatches(wordLoc, "short ", 0, 6))
                    wordLoc+=6;
            }
            // Next word will be 'left', 'right', or 'middle '
            if (description.regionMatches(wordLoc, "left ", 0, 5)) {
                if (haveDeep)
                    playType = SinglePlay.PlayType.PASS_DEEP_LEFT;
                else
                    playType = SinglePlay.PlayType.PASS_SHORT_LEFT;
            } // Left pass
            else if (description.regionMatches(wordLoc, "right ", 0, 6)) {
                if (haveDeep)
                    playType = SinglePlay.PlayType.PASS_DEEP_RIGHT;
                else
                    playType = SinglePlay.PlayType.PASS_SHORT_RIGHT;
            } // Left pass
            else {
                /* Unfortunately, not all pass descriptions include the
                   direction either. They are treated as passes to the middle,
                   so they will end up here whether the word 'middle' exists or
                   not */
                if (haveDeep)
                    playType = SinglePlay.PlayType.PASS_DEEP_MIDDLE;
                else
                    playType = SinglePlay.PlayType.PASS_SHORT_MIDDLE;
            } // Right pass
            if (haveIncomplete)
                results = new PlayResults(0, false);
            else {
                /* If the description has 'INTERCEPTION', the play is listed
                   as an interception for no yards */
                if (description.indexOf("INTERCEPT", wordLoc) >= 0)
                    results = new PlayResults(0, true);
                else
                    results = extractPlayYardageTurnover(description, wordLoc);
            } // Not an incomplete pass
        } // Pass play

        /* Running plays are listed by specifying a player and a direction.
           Thankfully, the directions are unique to running plays! Need to find
           each one individually */
        if (playType == null) {
            wordLoc = description.indexOf(" left end ");
            if (wordLoc < 0)
                wordLoc = description.indexOf(" left guard ");
            if (wordLoc < 0)
                wordLoc = description.indexOf(" left tackle ");
            if (wordLoc >= 0) {
                playType = SinglePlay.PlayType.RUN_LEFT;
                results = extractPlayYardageTurnover(description, wordLoc);
            } // Run left
        } // No play yet
        
        if (playType == null) {
            wordLoc = description.indexOf(" right end ");
            if (wordLoc < 0)
                wordLoc = description.indexOf(" right guard ");
            if (wordLoc < 0)
                wordLoc = description.indexOf(" right tackle ");
            if (wordLoc >= 0) {
                playType = SinglePlay.PlayType.RUN_RIGHT;
                results = extractPlayYardageTurnover(description, wordLoc);
            } // Run left
        } // No play yet
    
        if (playType == null) {
            /* Some rush plays have ' rushed ' with no direction. A few more
               have 'scrambled' for quarterback scrambles. Treat these as up the
               middle */
            wordLoc = description.indexOf(" up the middle ");
            if (wordLoc < 0)
                wordLoc = description.indexOf(" rushed ");
            if (wordLoc < 0)
                wordLoc = description.indexOf(" scrambles ");
            if (wordLoc >= 0) {
                playType = SinglePlay.PlayType.RUN_MIDDLE;
                results = extractPlayYardageTurnover(description, wordLoc);
            } // Run up the middle
        } // No play yet
        
        if (playType == null) {
            /* Quarter back sacks appear as ' sacked '. The phrase
               'FUMBLES (Aborted)' means the quaterback fumbled the ball
               without being sacked. In practice both situations are almost
               always busted pass plays. The type of pass play is unknown, so
               they are evenly divided between the pass play types. */
            boolean haveSack = false;
            wordLoc = description.indexOf(" sacked ");
            if (wordLoc >= 0)
                haveSack = true;
            else
                wordLoc = description.indexOf(" FUMBLES (Aborted) ");
            if (wordLoc >= 0) {
                switch (_sackCount % 6) {
                case 0:
                    playType = SinglePlay.PlayType.PASS_SHORT_LEFT;
                    break;
                case 1:
                    playType = SinglePlay.PlayType.PASS_SHORT_MIDDLE;
                    break;
                case 2:
                    playType = SinglePlay.PlayType.PASS_SHORT_RIGHT;
                    break;
                case 3:
                    playType = SinglePlay.PlayType.PASS_DEEP_LEFT;
                    break;
                case 4:
                    playType = SinglePlay.PlayType.PASS_DEEP_MIDDLE;
                    break;
                case 5:
                default: // Keep the compiler happy
                    playType = SinglePlay.PlayType.PASS_DEEP_RIGHT;
                    break;
                } // Switch on number of processed sacks
                _sackCount++;

                // A sack has yardage and can also fumble
                if (haveSack) {
                    wordLoc += 8; // Move to next word
                    results = extractPlayYardageTurnover(description, wordLoc);
                }
                else // Quarterback fumble
                    results = new PlayResults(0, true);
            } // Quarterback sack or fumble
        } // Play not found so far

        if (playType == null) {
            /* ' punts ' or ' punted ' indicates a successful punt, followed
               by the yardage */
            wordLoc = description.indexOf(" punts ");
            if (wordLoc < 0)
                wordLoc = description.indexOf(" punted ");
            if (wordLoc >= 0) {
                playType = SinglePlay.PlayType.PUNT;
                wordLoc += 6; // Skip word
                if (description.charAt(wordLoc) != ' ')
                    wordLoc++; // On 'd '
                wordLoc++; // Move off space
                int nextWordLoc = description.indexOf(' ', wordLoc);
                int distanceGained = Integer.parseInt(description.substring(wordLoc,
                                                                            nextWordLoc));
                results = new PlayResults(distanceGained, false);
            } // Punt
        } // Play not found so far

        if (playType == null) {
            // ' field goal ' indicates a field goal attempt.
            wordLoc = description.indexOf(" field goal ");
            if (wordLoc >= 0) {
                /* If the next words are 'is GOOD', it succeeded. The yardage
                   is found BEFORE the word 'yard' before the signal words. */
                playType = SinglePlay.PlayType.FIELD_GOAL;
                int distanceGained;
                if (description.regionMatches(wordLoc + 12, "is GOOD", 0, 7)) {
                    wordLoc = description.lastIndexOf(' ', wordLoc - 1); // Skip over 'yard'
                    int prevWordLoc = description.lastIndexOf(' ', wordLoc - 1) + 1;
                    distanceGained = Integer.parseInt(description.substring(prevWordLoc,
                                                                            wordLoc));
                } // Field goal made
                else
                    distanceGained = 0;
                /* Field goal attempts that result in turnovers appear
                   differently, so none of these resulted in a turnover
                   (turnover on downs doesn't count) */
                results = new PlayResults(distanceGained, false);
            } // Field goal attempt
        } // No play so far
        
        if (playType == null) {
            /* The phrase ' Aborted. ' indicates a few types of busted plays.
               All three involve turning the ball over */
            if (description.indexOf(" Aborted. ") >= 0) {
                if (description.indexOf("Punt") >= 0)
                    playType = SinglePlay.PlayType.PUNT;
                else if (description.indexOf("Field Goal") >= 0)
                    playType = SinglePlay.PlayType.FIELD_GOAL;
                else
                    // Bad handoff on a running play
                    playType = SinglePlay.PlayType.RUN_MIDDLE;
                results = new PlayResults(0, true);
            } // Busted play
        } // Play not found so far

        if (playType == null) {
            /* ' punt is BLOCKED ' indicates an unsuccessful punt. This is
               treated as a turnover with no yardage */
            wordLoc = description.indexOf(" punt is BLOCKED "); // Note carefully the spaces on either end
            if (wordLoc >= 0) {
                playType = SinglePlay.PlayType.PUNT;
                // Unsuccessful punts are treated as turnovers
                results = new PlayResults(0, true);
            } // Punt
        } // Play not found so far

        /* If get to here without a play, have yet another possibility. Some
           run plays do not have a direction specified, so they are listed
           '[name] to [team] [location] for [yards]'. Assume anything with this
           pattern is a running play, unless it contains 'kneels', indicating a
           kneel down */
        if (playType == null) {
            if (description.indexOf(" kneels ") < 0) {
                wordLoc = description.indexOf(" to ");
                wordLoc += 4; // Skip 'to'
                wordLoc = description.indexOf(' ', wordLoc + 1); // Skip team name in location
                if (wordLoc >= 0)
                    wordLoc = description.indexOf(' ', wordLoc + 1); // Skip yardage in location
                if (wordLoc >= 0)
                    if (description.regionMatches(wordLoc, " for ", 0, 5)) {
                        playType = SinglePlay.PlayType.RUN_MIDDLE;
                        results = extractPlayYardageTurnover(description,
                                                             wordLoc);
                    } // Word pattern indicates a running play
            } // Does NOT indicate a kneel down
        } // Haven't found a play yet

        /* If get to here, some running plays that fail to gain yardage are
           listed as ' lost ' by itself followed by yardage. Treat as a run
           up the middle, since the direction is unknown */
        if (playType == null) {
            wordLoc = description.indexOf(" lost ");
            if (wordLoc >= 0) {
                playType = SinglePlay.PlayType.RUN_MIDDLE;
                wordLoc += 6;
                int nextWordLoc = description.indexOf(' ', wordLoc);
                int distanceGained;
                distanceGained = Integer.parseInt(description.substring(wordLoc,
                                                                        nextWordLoc));
                distanceGained *= -1; // Play had negative yardage
                results = new PlayResults(distanceGained, false);
            } // Have negative running play
        } // No play yet

        /* At this point, have checked for every known thing that indicates a
           play in the description. Certain things signal that this description
           is for a non-play. If none of them match, output the description as
           an unknown play. The list is:
           1. Penalties after a play get their own play line
           2. Kneel downs at the end of the game are ignored; their use is obvious
           3. Spikes to stop the clock are ignored; their use is  pretty obvious
           4. Video reviews get their own line
           5. Some kickoffs mistakenly have a down listed
        */
        if (playType == null) {
            if ((description.indexOf("PENALTY") < 0) &&
                (description.indexOf("penalized") < 0) &&
                (description.indexOf("kneels") < 0) &&
                (description.indexOf("spiked") < 0) &&
                (description.indexOf("kicked") < 0) &&
                (description.indexOf(" play under review ") < 0))
                System.out.println("UNKNOWN PLAY TYPE: " + description);
            return new ParsePlayData(null, 0, false);
        } // Not a known play type
        else
            return new ParsePlayData(playType, results);
    } // parsePlayDescription method

    // Finds the yardage achieved from a play, and whether the ball was fumbled
    private PlayResults extractPlayYardageTurnover(String description, int pos)
    {
        int distanceGained;
        boolean turnedOver;
        
        /* Yards gained always appears as ' for XXX yards'. Search for the
           ' for ' to find it */
        int wordLoc = description.indexOf(" for ", pos);
        if (wordLoc < 0)
            throw new IllegalArgumentException();

        wordLoc += 5; // Skip over string
        /* Now on next word of input (which may be numeric). Find its
           end and test what it is */
        int nextWordLoc = description.indexOf(' ', wordLoc);
        if (nextWordLoc < 0)
            throw new IllegalArgumentException();
        
        // Plays of zero yards are listed as 'no gain'. Check for 'no '
        if (description.regionMatches(wordLoc, "no ", 0, nextWordLoc - wordLoc))
            distanceGained = 0;
        else {
            /* Just to confuse things, some descriptions use 'a loss of'
               to indicate negative yardage. Check for 'a ' */
            boolean flipSign = false;
            if (description.regionMatches(wordLoc, "a ", 0,
                                          nextWordLoc - wordLoc)) {
                flipSign = true;
                wordLoc += 10;
                nextWordLoc = description.indexOf(' ', wordLoc);
            } // Loss flagged in words
            distanceGained = Integer.parseInt(description.substring(wordLoc,
                                                                    nextWordLoc));
            if (flipSign)
                distanceGained *= -1;
        } // Non-zero yardage

        /* If the description contains 'FUMBLE', the ball carrier turned
           it over */
        turnedOver = (description.indexOf("FUMBLE", nextWordLoc) > 0);
        return new PlayResults(distanceGained, turnedOver);
    } // extractPlayYardageTurnover method

    // Returns true if an argument is a switch
    private static boolean isSwitch(String arg)
    {
        // A switch is a dash, then a single letter
        if (arg.length() != 2)
            return false;
        else if (arg.charAt(0) != '-')
            return false;
        else
            /* Some consider this an anti-pattern, but it is the most
               efficient test */
            return ((arg.charAt(1) < '0') || (arg.charAt(1) > '9'));
    }

    // Object test program. Creates a data store, loads it, and outputs it
    public static void main(String[] args) throws Throwable
    {
        String ourTeam = null;
        String opposition = null;
        String ourSimiliar = null;
        String oppSimiliar = null;

        /* Extract teams to test. First two positions are required, the rest
           given by switches. Find the switches by looking for strings starting
           with '-'. This assumes that team names will not start with that
           character, which is true currently and likely into the future.
           Specifying an unknown switch, the same switch twice, or a switch with
           no arguments is an error */
        boolean invalidInput = false;
        if (args.length < 2)
            invalidInput = true;
        else {
            ourTeam = args[0];
            opposition = args[1];
        }
        if (args.length > 2) {
            // NOT finding a switch in the third position is an error
            if (!isSwitch(args[2]))
                invalidInput = true;
            else {
                int index = 2;
                boolean ourSimiliarTeams = false;
                while ((index < args.length) && (!invalidInput)) {
                    if (isSwitch(args[index])) {
                        /* Determine the type of switch and initialize
                           data to process arguments. If data it already
                           initialized, the switch was specified twice, an
                           error */
                        if (args[index].charAt(1) == 'u') {
                            // Similiar to our team
                            if (ourSimiliar == null)
                                ourSimiliar = new String();
                            else
                                invalidInput = true;
                            ourSimiliarTeams = true;
                        }
                        else if (args[index].charAt(1) == 'o') {
                            // Similiar to opposition
                            if (oppSimiliar == null)
                                oppSimiliar = new String();
                            else
                                invalidInput = true;
                            ourSimiliarTeams = false;
                        }
                        else
                            invalidInput = true;
                    } // Switch
                    else {
                        /* Process the argument. In this case, both legal
                           switches do the same thing to different data */
                        if (ourSimiliarTeams) {
                            ourSimiliar = ourSimiliar.concat(args[index]);
                            ourSimiliar = ourSimiliar.concat(" ");
                        } // Similiar to us
                        else {
                            oppSimiliar = oppSimiliar.concat(args[index]);
                            oppSimiliar = oppSimiliar.concat(" ");
                        }
                    } // Data between switches
                    index++;
                } // While arguments to process and input data valid

                // Finding switches but no team data is an error
                if ((!invalidInput) && (ourSimiliar != null))
                    invalidInput = ourSimiliar.isEmpty();
                if ((!invalidInput) && (oppSimiliar != null))
                    invalidInput = oppSimiliar.isEmpty();
            } // Third argument is a switch
            
        } // More than three arguments

        if (invalidInput) {
            System.out.println("Usage: US OPPONENT [-u] [SIMILIAR US TEAMS] [-o] [SIMILIAR OTHER TEAMS]");
            throw new IllegalArgumentException();
        }

        try {
            System.out.println(ourTeam + " " + opposition + " " + ourSimiliar + " " + oppSimiliar);
            NFLqualityControl wantTeams = new NFLqualityControl(ourTeam,
                                                                opposition,
                                                                ourSimiliar,
                                                                oppSimiliar);
            PlayLoader test = new PlayLoader("Data");
            DataStore result = test.loadPlays(wantTeams, 1);
            System.out.println(result);
            System.out.println(wantTeams);
        }
        catch (Throwable e) {
            System.out.println("Exeption " + e + " caught");
            throw e; // Force improper termination, so error is obvious
        }
    }
}