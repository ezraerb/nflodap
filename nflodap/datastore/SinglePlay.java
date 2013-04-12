package nflodap.datastore;

import java.util.*;

/* This class defines a single NFL play from the database. Data about the play
   is its type, the situation under which it was called, and the results.
   Actual play sheets list plays based on categories within each situation.
   Accordingly, this class has enumerations for those categories, and methods
   to convert the raw play data into the appropriate categories. */
public final class SinglePlay {

    /* Play characteristics are divided into categories based on values that
       affect play calling. These enumerated types define them. They are set
       up as nested classes because they only have context within a play */

    // Play types are derived from those listed in the original data set
    public static enum PlayType { RUN_LEFT, RUN_MIDDLE, RUN_RIGHT,
            PASS_SHORT_RIGHT, PASS_SHORT_MIDDLE, PASS_SHORT_LEFT,
            PASS_DEEP_RIGHT, PASS_DEEP_MIDDLE, PASS_DEEP_LEFT, FIELD_GOAL,
            PUNT };
    
    // Down number
    public static enum DownNumber { FIRST_DOWN, SECOND_DOWN, THIRD_DOWN,
            FOURTH_DOWN };
    
    /* Distance needed to make a first down. Categories are based on ranges that
       affect play calling */
    public static enum DistanceNeeded { OVER_TWENTY, TWENTY_TO_TEN, TEN_TO_FOUR,
            FOUR_TO_ONE, ONE_OR_LESS };
    
    /* Location on field at time of play. The categories are based on reseach on
       where it affects play selection. NOTE: 'red zone' here means the 10 yards
       closest to the goal line; most commentators define it as 20 yards */
    public static enum FieldLocation { OWN_RED_ZONE, MIDDLE, OPP_RED_ZONE };
    
    /* Time remaining in half. The categories are based on research on when it
       becomes important enough to affect play selection */
    public static enum TimeRemaining { OUTSIDE_TWO_MINUTES, INSIDE_TWO_MINUTES };
    
    /* Score differential. The categories are based on research on when it will
       affect play calling */
    public static enum ScoreDifferential { DOWN_OVER_FOURTEEN, DOWN_OVER_SEVEN,
            DOWN_SEVEN_LESS, EVEN_SCORE, UP_SEVEN_LESS, UP_OVER_SEVEN,
            UP_OVER_FOURTEEN };
    

    /* Listing of field types with numeric values. It allows clients to handle
       integer fields in a generic way without writing case statements
       everywhere. Note that most of these ALSO map to an enumerated type,
       which is fetched using the actual type, not this list
       NOTE: Fields for play count and turnover count seem silly at first, since
       they will always have value 1 or 0 for a single play. Including them
       means that code tha extracts field totals over sets of plays (where they
       DO make sense) can extract and manipulate them like any other */       
    public static enum NumericFields { DISTANCE_NEEDED, FIELD_LOCATION,
            TIME_REMAINING, SCORE_DIFFERENTIAL, DISTANCE_GAINED, PLAY_COUNT,
            TURNOVER_COUNT }

    /* Reference ID, used to trace a play through the system for debugging.
       Clients must set these and ensure the level of integrity needed */
    private int _refId;

    private PlayType _playType;

    // Data of the particular play
    private int _distanceNeeded;
    private int _fieldLocation; // Yards to OPPONENT'S goal line
    private int _timeRemaining; // Minutes to end of game
    private int _scoreDifferential;
    private int _distanceGained;
    private boolean _turnedOver;

    // Categories of play that effect how often the type is called
    private DownNumber _down;
    private DistanceNeeded _distanceNeededCategory;
    private FieldLocation _fieldLocationCategory;
    private TimeRemaining _timeRemainingCategory;
    private ScoreDifferential _scoreDifferentialCategory;

    // Constructor, supply all specified data
    public SinglePlay(int refId, PlayType playType, DownNumber down,
                      int distanceNeeded, int fieldLocation, int timeRemaining,
                      int ownScore, int oppScore, int distanceGained,
                      boolean turnedOver)
    {
        _refId = refId;
        _playType = playType;
        _down = down;
        _distanceNeeded = distanceNeeded;
        _fieldLocation = fieldLocation;
        _timeRemaining = timeRemaining;
        _scoreDifferential = ownScore - oppScore;
        _distanceGained = distanceGained;
        _turnedOver = turnedOver;
        
        /* Can either find the category values once and cache, or find them
           explictly every time a category getter is called. They will be called
           often enough that the extra space should be worth it */
        _distanceNeededCategory = distanceToDistanceNeeded(_distanceNeeded);
        _fieldLocationCategory = yardsToFieldLocation(_fieldLocation);
        _timeRemainingCategory = minutesToTimeRemaining(_timeRemaining);
        _scoreDifferentialCategory = scoreToScoreDifferential(_scoreDifferential);
    }
    
    // Getter methods
    public int getRefId()
    { return _refId; }

    public PlayType getPlayType()
    { return _playType; }

    public int getDistanceNeeded()
    { return _distanceNeeded; }

    public int getFieldLocation()
    { return _fieldLocation; }

    public int getTimeRemaining()
    { return _timeRemaining; }

    public int getScoreDifferential()
    { return _scoreDifferential; }

    public int getDistanceGained()
    { return _distanceGained; }

    public boolean getTurnedOver()
    { return _turnedOver; }

    public DownNumber getDown()
    { return _down; }

    public DistanceNeeded getDistanceNeededCategory()
    { return _distanceNeededCategory; }

    public FieldLocation getFieldLocationCategory()
    { return _fieldLocationCategory; }

    public TimeRemaining getTimeRemainingCategory()
    { return _timeRemainingCategory; }

    public ScoreDifferential getScoreDifferentialCategory()
    { return _scoreDifferentialCategory; }


    // Get a category value of the play given the category
    // NOTE: This only works because every category has a unique type
    public <V> V getValue(Class<V> type)
    {
        V result = null;
        if (type.isAssignableFrom(PlayType.class))
            result = type.cast(getPlayType());
        else if (type.isAssignableFrom(DownNumber.class))
            result = type.cast(getDown());
        else if (type.isAssignableFrom(DistanceNeeded.class))
            result = type.cast(getDistanceNeededCategory());
        else if (type.isAssignableFrom(FieldLocation.class))
            result = type.cast(getFieldLocationCategory());
        else if (type.isAssignableFrom(TimeRemaining.class))
            result = type.cast(getTimeRemainingCategory());
        else if (type.isAssignableFrom(ScoreDifferential.class))
            result = type.cast(getScoreDifferentialCategory());
        // else not a supported type, return null
        return result;
    }

    /* Reads an integer value out of the play given the field. This method
       exists so clients can handle integer fields in a generic way without
       writing case statements everywhere */
    public int getIntValue(NumericFields field)
    {
        int result = -1000; // Chosen as an obviously wrong value
        switch (field) {
        case DISTANCE_NEEDED:
            result = getDistanceNeeded();
            break;
        case FIELD_LOCATION:
            result = getFieldLocation();
            break;
        case TIME_REMAINING:
            result = getTimeRemaining();
            break;
        case SCORE_DIFFERENTIAL:
            result = getScoreDifferential();
            break;
        case DISTANCE_GAINED:
            result = getDistanceGained();
            break;
        case PLAY_COUNT:
            result = 1;
            break;
        case TURNOVER_COUNT:
            if (getTurnedOver())
                result = 1;
            else
                result = 0;
            break;
        default:
            // Do nothing
        }
        return result;
    }
                
    // Convert a distance needed into a distance category
    public static DistanceNeeded distanceToDistanceNeeded(int distanceNeeded)
    {
        /* NOTE: distance is done in increasing order, so most likely
           values occur first */
        if (distanceNeeded <= 1)
            return DistanceNeeded.ONE_OR_LESS;
        else if (distanceNeeded <= 4)
            return DistanceNeeded.FOUR_TO_ONE;
        else if (distanceNeeded <= 10)
            return DistanceNeeded.TEN_TO_FOUR;
        else if (distanceNeeded < 20)
            return DistanceNeeded.TWENTY_TO_TEN;
        else
            return DistanceNeeded.OVER_TWENTY;
    }
    
    // Convert a field yardage into location
    public static FieldLocation yardsToFieldLocation(int yardLine)
    {
        // In the data, yardage is always given in terms of offence yards to go
        if (yardLine >= 90)
            return FieldLocation.OWN_RED_ZONE;
        else if (yardLine > 10)
            return FieldLocation.MIDDLE;
        else
            return FieldLocation.OPP_RED_ZONE;
    }

    // Convert a minute count to time remaining category
    public static TimeRemaining minutesToTimeRemaining(int minutes)
    {
        // Game time in data is specifed in as time remaining in the overall game
        if ((minutes < 2) || ((minutes >= 30) && (minutes < 32)))
            return TimeRemaining.INSIDE_TWO_MINUTES;
        else
            return TimeRemaining.OUTSIDE_TWO_MINUTES;
    }

    // Convert a score differential into a score differential category
    public static ScoreDifferential scoreToScoreDifferential(int scoreDiff)
    {
        if (scoreDiff < -14)
            return ScoreDifferential.DOWN_OVER_FOURTEEN;
        else if (scoreDiff < -7)
            return ScoreDifferential.DOWN_OVER_SEVEN;
        else if (scoreDiff < 0)
            return ScoreDifferential.DOWN_SEVEN_LESS;
        else if (scoreDiff == 0)
            return ScoreDifferential.EVEN_SCORE;
        else if (scoreDiff <= 7)
            return ScoreDifferential.UP_SEVEN_LESS;
        else if (scoreDiff <= 14)
            return ScoreDifferential.UP_OVER_SEVEN;
        else
            return ScoreDifferential.UP_OVER_FOURTEEN;
    }

    public String toString()
    {
        return " _refId:" + _refId + " _playType:" + _playType
            + " _distanceGained:" + _distanceGained
            + " _turnedOver:" + _turnedOver + "\n _down:" + _down
            + " _distanceNeededCategory:" + _distanceNeededCategory
            + " _fieldLocationCategory:" + _fieldLocationCategory
            + " _timeRemainingCategory:" + _timeRemainingCategory
            + " _scoreDifferentialCategory:" + _scoreDifferentialCategory;
    }

};