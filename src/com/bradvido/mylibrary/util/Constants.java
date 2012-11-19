
package com.bradvido.mylibrary.util;

import com.bradvido.util.logger.BTVLogLevel;
import com.bradvido.util.logger.BTVLoggerOptions;
import com.bradvido.util.tools.BTVConstants;


public class Constants extends BTVConstants
{
    
    /**
     * Single Instance constructor.. 
     * If singleInstance is true, limits to single instance of this program, based on binding to singleInstancePort
     * @param propertiesFileName The name of the properties file in the base dir
     * @param singleInstance Set to true to limit to single instance
     * @param singleInstancePort Port to bind to 
     * @param singleInstanceTimeoutSec Seconds to wait for other instance to finish before proceeding. <0 means wait forever
     */
    public Constants(String propertiesFileName, BTVLogLevel logLevel, boolean singleInstance, int singleInstancePort, int singleInstanceTimeoutSec)
    {
        super(Constants.class, 
            //over-ride logger with custom MyLibraryLogger
            new MyLibraryLogger(Constants.class, 
                new BTVLoggerOptions()
                .setSingleInstance(singleInstance, singleInstancePort, singleInstanceTimeoutSec)
                .setLevelToLogAt(logLevel)
                .setLogToCurrentFile(true)
                .setLogToHistoryFile(true)
                .setLogToMemory(true)
                .setLogToSystemOut(true)
            ));        
                
        
        //FOR development
        setTestingIfComputerNameIs("bvidovic11l");
        
        if(TESTING)
        {
            BASE_DIR = BASE_DIR.getParentFile();
            Logger.INFO("TESTING = "+TESTING+", BASE_DIR is now: "+ BASE_DIR.getAbsolutePath());
        }
        
        //load config            
        //Config = new Config(new File(BASE_DIR, propertiesFileName));
        
        //over-ride any super static variables here
        //ENCODING_FORMAT="xxx";        
        SQL_ERROR = -1001;        
    }
    
    public static final String VERSION = "1.4.2"; //Version 1.4.0 and above is compatible w/ XBMC Frodo (not backwards compatible because of DB changes).
    public static final String XBMC_COMPATIBILITY = "Frodo";
    public static final String PROGRAM_NAME = "XBMC.MyLibrary";
    
    public static boolean SINGLE_INSTANCE = true;
    public static int SINGLE_INSTANCE_PORT = 52872;
    public static int LOG_EXPIRE_DAYS = 14;
            

    public static final int STATEMENT = 0;
    public static final int PREPARED_STATEMENT = 1;
            
    public static final int SECOND_BEFORE_DB_CONNECTION_FORCE_CLOSED = 60 * 10;//x min timeout
    public static final long ONE_DAY = 1000 * 60 * 60 * 24;        
    public final static String  AUTO_TYPE = "auto_determine_type";
    
    //OLD-DEPRICATED TYPES
    public final static String  TV_SHOW = "episodes";
    public final static String  MOVIE = "movies";
    public final static String  MUSIC_VIDEO = "music_videos";
    
    public final static String  REGEXP = "regexp";
    public final static String  CONTAINS = "contains";
    
    //Old-depricated Types of meta-data (now MetaDataType enum)
//    public final static String  MOVIE_SET = "MOVIE_SET";
//    public final static String  MOVIE_TAGS = "MOVIE_TAGS";
//    public final static String  PREFIX = "PREFIX";
//    public final static String  SUFFIX = "SUFFIX";
	
    public static final String TVDB_API_KEY = "05EB6802977A1FFE";//my key
    
	//AngryCamel - 20120805 2351
    public final static String  RUNTIME = "runtime";
    
	//AngryCamel - 20120815 2246
    public final static String  FORCE_SERIES = "force_series";

	//AngryCamel - 20120817 1620
    public final static String  GENERIC = "generic";

//    public final static String  FOLDERS_ONLY = "FOLDERS_ONLY";
//    public final static String  FILES_ONLY = "FILES_ONLY";
//    public final static String  FOLDERS_AND_FILES = "FOLDERS_AND_FILES";

           
    public final static String  QUEUED = "QUEUED";
    public final static String  COMPLETED = "COMPLETED";

    public final static int MAX_FILENAME_LENGTH = 255;    

    public static final int UP =     1;
    public static final int DOWN =   0;

    public static final String ICEFILMS_IDENTIFIER_LC = "plugin.video.icefilms";
    
}
