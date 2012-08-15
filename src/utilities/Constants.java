
package utilities;

import java.io.File;


public interface Constants
{
    //available log levels
    public static final int DEBUG =     5;
    public static final int INFO =      4;
    public static final int NOTICE =    3;
    public static final int WARNING =   2;
    public static final int ERROR =     1;

    public static final int STATEMENT = 0;
    public static final int PREPARED_STATEMENT = 1;

    
    
    public static final int SQL_ERROR = -1001;
    public static final int SECOND_BEFORE_DB_CONNECTION_FORCE_CLOSED = 60 * 10;//x min timeout
    public static final long ONE_DAY = 1000 * 60 * 60 * 24;
    public static final String LINE_BRK ="\r\n";
    public final static String SEP = File.separator;    
    public final static String  AUTO_TYPE = "auto_determine_type";
    public final static String  TV_SHOW = "episodes";
    public final static String  MOVIE = "movies";
    public final static String  MUSIC_VIDEO = "music_videos";
    public final static String  DELIM = "-zXz-";
    public final static String  REGEXP = "regexp";
    public final static String  CONTAINS = "contains";
    public final static String  MOVIE_SET = "MOVIE_SET";
    public final static String  PREFIX = "PREFIX";
    public final static String  SUFFIX = "SUFFIX";
	
	//AngryCamel - 20120805 2351
    public final static String  RUNTIME = "runtime";

    public final static String  FOLDERS_ONLY = "FOLDERS_ONLY";
    public final static String  FILES_ONLY = "FILES_ONLY";
    public final static String  FOLDERS_AND_FILES = "FOLDERS_AND_FILES";

    public final static String  FILE = "FILE";
    public final static String  DIRECTORY = "DIRECTORY";

    public final static String  MYSQL = "MySQL";
    public final static String  SQL_LITE = "SQLite";

    public final static String  THUMB_CLEANER = "ThumbCleaner";
    public final static String  MY_LIBRARY = "MyLibrary";

    public final static String NO_SUBFOLDERS_SPECIFIED = "***NO_SUBFOLDERS***";

    public final static String  QUEUED = "QUEUED";
    public final static String  COMPLETED = "COMPLETED";

    public final static int MAX_FILENAME_LENGTH = 255;


    public final static String  DOWNLOAD_QUEUED = "DOWNLOAD_QUEUED";
    public final static String  DOWNLOADING = "DOWNLOADING";
    public final static String  DOWNLOAD_COMPLETE = "DOWNLOAD_COMPLETE";
    public final static String  DOWNLOAD_FINAL = "DOWNLOAD_FINAL";
    public final static String  DOWNLOAD_COMPRESSING = "DOWNLOAD_COMPRESSING";

    public static final int UP =     1;
    public static final int DOWN =   0;

    public static final String ICEFILMS_IDENTIFIER_LC = "plugin.video.icefilms";
    
}
