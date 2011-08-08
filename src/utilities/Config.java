package utilities;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.net.ServerSocket;
import java.text.SimpleDateFormat;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

public class Config implements Constants
{
    public static boolean RESTART_XBMC = false;
    public static Map<String, CompressionDefinition> COMPRESSION_DEFINITIONS = new LinkedHashMap<String, CompressionDefinition>();
    public static boolean COMSKIP_DOWNLOADED_VIDEOS = false;
    public static int EDL_TYPE = 0;
    public static String JDOWNLOADER_HOST = "http://localhost:10025";
    public static String DOWNLOADED_VIDEOS_DROPBOX = "\\\\localhost\\c$\\dropbox\\Downloaded";
    
    //the writers for the current and historical log files
    public static ServerSocket SINGLE_INSTANCE_SOCKET = null;
    public static int SINGLE_INSTANCE_PORT = -1;
    public static Logger logger;
    public static BufferedWriter historicalLog = null;//log historically by day
    public static BufferedWriter currentLog = null;//log the last execution of the program
    
    public static int LOGGING_LEVEL = 4;//default of INFO
    private static Map<Integer,String> LOGGING_LEVELS = new LinkedHashMap<Integer,String>();
    public static int LOG_EXPIRE_DAYS = 30;
    public static String BASE_PROGRAM_DIR = "C:\\XBMC.MyLibrary";
    public static String DROPBOX = null;
    public static Set<Source> ALL_SOURCES = new LinkedHashSet<Source>();
    
    public static List<String> XBMC_THUMBNAILS_FOLDERS = new ArrayList<String>();;
    public static final String[] KNOWN_XBMC_THUMBNAIL_EXTENSIONS = {".tbn", ".dds"};
    public static Database archivedFilesDB, queuedChangesDB, scraperDB;//tracker SQLite DB's
    
    public static boolean MANUAL_ARCHIVING_ENABLED = true;
    public static double HOURS_OLD_BEFORE_MANUAL_ARCHIVE = 6.0;
    
    //Note, this use my API key for yahoo
    public static boolean SCRAPE_MUSIC_VIDEOS = true;
        
    public static double MISSING_HOURS_DELETE_THRESHOLD = 12.0;
    public static int MISSING_COUNT_DELETE_THRESHOLD = 3;
    public static double LIBRARY_SCAN_WAIT_MINUTES = 2.0;

    //populated with chars that cant be used in windows file names
    public static Map<Integer,String> ILLEGAL_FILENAME_CHARS = new HashMap<Integer,String>();

    //populated with uncommon characters that are ignored when comparing strings for matches
    public static Map<Integer,String> UNCOMMON_CHARS = new HashMap<Integer,String>();

    public static int XBMC_SERVER_WEB_PORT = 8000;

     //TheTVDB config
    public static String TVDB_API_KEY = "[no TVDB API Key found -- Please specify in config file]";//API key for tvdb lookups. Can be obtained by registerin at thetvdb.com
    public static String TVDB_API_KEY_OBSCURED = null;//for printing in log
    public static long MAX_TVDB_QUERY_INTERVAL_MS = (1000 * 60 * 60);//1 hour default wait time between TVDB identical requeries
    public static final int TVBD_QUERY_RECYCLE_MINUTES = 24*60;//forces tvdb queries to be re-cycled (remove them from tracker file) after this many minutes passes
    public static boolean OBSCURE_TVDB_KEY_IN_LOG = false;

    //JSON-RPC config
    public static long JSON_RPC_RESPONSE_TIMEOUT_MS = 50;//the millisecond timeout once data starts being received to know that all data has been receieved
    public static long JSON_RPC_TIMEOUT_SECONDS = 60;//time timeout in seconds to wait for json-rpc to start sending data after a request has been made
    public static String JSON_RPC_SERVER = "[unknown]";
    public static boolean USE_CURL = false;
    public static int JSON_RPC_WEBSERVER_PORT = 8080;
    public static String JSON_RPC_WEBSERVER_USERNAME = "";
    public static String JSON_RPC_WEBSERVER_PASSWORD = "";    

    //Restart PlayOn before scan
    public static boolean RESTART_PLAYON_BEFORE_SCAN = false;
    public static boolean RESTART_PLAYON_ONLY_IF_NOT_STREAMING = false;    
    public static List<String> PLAYON_CLIENT_IP_RANGES = new ArrayList<String>();
    public static String PLAYON_SERVICE_NAME = "MediaMall Server";

    //IP Change
    public static boolean IP_CHANGE_ENABLED = false;
    public static List<IPChange> IP_CHANGES = new ArrayList<IPChange>();
    

    //XBMC MySQL    
    public static String XBMC_MYSQL_SERVER =null;
    public static String XBMC_MYSQL_UN =null;
    public static String XBMC_MYSQL_PW =null;
    public static String XBMC_MYSQL_VIDEO_SCHEMA =null;
    public static String XBMC_MYSQL_MUSIC_SCHEMA =null;
    public static int XBMC_MYSQL_PORT = 3306;

    //ThubnailCleaner variables:
    public static String DATABASE_TYPE = null;
    public static String sqlLiteVideoDBPath = "C:\\Users\\[USERNAME]\\AppData\\Roaming\\XBMC\\userdata\\Database\\MyVideos34.db";
    public static String sqlLiteMusicDBPath = "C:\\Users\\[USERNAME]\\AppData\\Roaming\\XBMC\\userdata\\Database\\MyMusic7.db";
    public static String sqlLiteTexturesDBPath = "C:\\Users\\[USERNAME]\\AppData\\Roaming\\XBMC\\userdata\\Database\\Textures.db";
    public static double TEXTURE_LAST_USED_DAYS_THRESHOLD = 7.0;
    public static String TEXTURE_AND_OR_FOR_THRESHOLD = "and";
    public static int TEXTURE_USE_COUNT_THRESHOLD = 2;
    public static boolean CONFIRM_PATHS_EXIST = true;//todo: add to config

    public static int SPOT_CHECK_MAX_IMAGES = 150;
    public static String SPOT_CHECK_DIR;
    public static boolean SIMULATION;
    public static String MYSQL_CHARACTER_SET = "latin1";//default is latin1
    //sdf's
    public static final SimpleDateFormat tvdbFirstAiredSDF = new SimpleDateFormat("yyyy-MM-dd");//for lookup on thetvdb based on first aired date
    public static final SimpleDateFormat log_sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");//for logging    

    boolean IS_THUMB_CLEANER=false, IS_MY_LIBRARY=false;
    String logFileNameNoExt;
    public Config(String which)
    {

        if(which.equals(THUMB_CLEANER)) IS_THUMB_CLEANER = true;
        if(which.equals(MY_LIBRARY)) IS_MY_LIBRARY = true;
        if(IS_MY_LIBRARY) SINGLE_INSTANCE_PORT = 52872;
        else SINGLE_INSTANCE_PORT = 52873;
        
        //instantite logging levels
        LOGGING_LEVELS.put(1, "ERROR");
        LOGGING_LEVELS.put(2, "WARNING");
        LOGGING_LEVELS.put(3, "NOTICE");
        LOGGING_LEVELS.put(4, "INFO");
        LOGGING_LEVELS.put(5,"DEBUG");
        setShortLogDesc("Init...");

        if(!SingleInstance.isSingleInstance()) System.exit(1);

        
        BASE_PROGRAM_DIR = getBaseDirectory();
        Config.log(NOTICE, "Base program dir = "+ BASE_PROGRAM_DIR);
                

         //populate charactes that we do not allow in file names
        char[] specialChars = {'<', '>', ':', '"', '/', '\\', '|', '?', '*', '*', '~', '’'};
        for(char c : specialChars) ILLEGAL_FILENAME_CHARS.put(new Integer((int) c), "illegal");

        char[] uncommonChars = {'<', '>', ':', '"', '/', '\\', '|', '?', '*', '#', '$', '%', '^', '*', '!', '~','\'', '’', '=', '[' ,']', '(', ')', ';', '\\' ,',', '_'};
        for(char c : uncommonChars) UNCOMMON_CHARS.put(new Integer((int) c), "illegal");

        //set up logs
        logFileNameNoExt = IS_MY_LIBRARY ? "XBMC.MyLibrary" : "XBMC.ThumbCleaner";
        File currentLogFile = new File(BASE_PROGRAM_DIR + "\\"+logFileNameNoExt+".log");
        try
        {
            
            currentLog = new BufferedWriter(new FileWriter(currentLogFile,false));//dont append, overwrite
            
        }
        catch(Exception x)
        {
            currentLog = null;
            log(WARNING, "Warning: could not create current log file at: "+currentLogFile,x);
        }

        File historicalLogDir = new File(BASE_PROGRAM_DIR + "\\logs");
        if(!historicalLogDir.exists())historicalLogDir.mkdir();
        File historicalLogFile = new File(Config.BASE_PROGRAM_DIR + "\\logs\\"+logFileNameNoExt+"." + Config.tvdbFirstAiredSDF.format(new Date())+".log");
        try
        {
            historicalLog = new BufferedWriter(new FileWriter(historicalLogFile,true));//append
        }
        catch(Exception x)
        {
            historicalLog = null;
            log(WARNING, "Warning: could not create historical log file at: "+historicalLogFile,x);
        }
        logger = new Logger(currentLog,historicalLog);  

        /*
         * Only needed for MyLibrary
         */
        if(IS_MY_LIBRARY)
        {
            if(!initializeSQLiteDbs()) return;                           
        }
       

    }

    public boolean loadConfig()
    {
        String xmlFileName = "Config.xml";//dfault
        if(IS_THUMB_CLEANER) xmlFileName = "ThumbCleanerConfig.xml";
        String strConfigFile = BASE_PROGRAM_DIR+SEP+ xmlFileName;
        File configFile = new File(strConfigFile);
        if(!configFile.exists())
        {
            log(ERROR, "Config file does not exits at: \""+strConfigFile+"\". Cannot continue.");
            return false;
        }

        //parse the XML
        Document ConfigXml;
        try
        {
            SAXBuilder builder = new SAXBuilder();
            ConfigXml = builder.build(configFile);
            if(ConfigXml == null || ConfigXml.getRootElement() == null)
                throw new Exception("No root element found in config file.");
        }
        catch(Exception x)
        {
            log(ERROR, "Could not find valid xml document at: "+ configFile +". Cannot continue... Please check config.xml with an XML validator.");
            return false;
        }

        Element root = ConfigXml.getRootElement();

        /*
         * Needed always
         */
        //logging level
        try
        {
            String strLogLevel = root.getChildText("LoggingLevel");
            try
            {
                LOGGING_LEVEL =  Integer.parseInt(strLogLevel);
                log(DEBUG, "Logging level is set to: " + LOGGING_LEVEL);
            }
            catch(Exception x)
            {
                log(DEBUG, "LoggingLevel of \""+strLogLevel+"\" is not an integer, will attempt to use String identifier.");
                //try getting it from a string.
                boolean found = false;
                for(Map.Entry<Integer,String> entry : LOGGING_LEVELS.entrySet())
                {
                    if(entry.getValue().equalsIgnoreCase(strLogLevel))
                    {
                        LOGGING_LEVEL = entry.getKey();
                        log(DEBUG, "Logging level of \""+strLogLevel+"\" maps to level: "+ LOGGING_LEVEL);
                        found = true;
                        break;
                    }                    
                }
                if(!found) log(WARNING, "Logging level of \""+strLogLevel+"\" is not known, defaulting to level "+ LOGGING_LEVEL +", \""+ LOGGING_LEVELS.get(LOGGING_LEVEL)+"\"");
            }
            
            try
            {
                String strExpireDays = root.getChild("LoggingLevel").getAttributeValue("expiredays");
                if(strExpireDays != null)
                {
                    LOG_EXPIRE_DAYS = Integer.parseInt(strExpireDays);
                }
            }
            catch(Exception x2)
            {
                log(WARNING, "Failed to parse expiredays attribute in <LoggingLevel>, defaulting to " + LOG_EXPIRE_DAYS,x2);
            }
            log(DEBUG, "Logs will be deleted after " + LOG_EXPIRE_DAYS + " days.");
        }
        catch(Exception x)
        {
            LOGGING_LEVEL = 3;
            LOG_EXPIRE_DAYS = 30;
            log(WARNING, "Failed while parsing log level, defaulting to level of INFO ("+LOGGING_LEVEL+"), and expire days of "+LOG_EXPIRE_DAYS + " days",x);
        }
        
        //JSON-RPC
        Map<String,String> jsonRPCChildren = getChildren(root.getChild("JSONRPC"));
        JSON_RPC_SERVER = jsonRPCChildren.get("xbmcname");
        USE_CURL = "curl".equalsIgnoreCase(jsonRPCChildren.get("method"));
        JSON_RPC_WEBSERVER_PORT = tools.isInt(jsonRPCChildren.get("port")) ? Integer.parseInt(jsonRPCChildren.get("port")) : 8080;
        JSON_RPC_WEBSERVER_USERNAME = jsonRPCChildren.get("username");
        JSON_RPC_WEBSERVER_PASSWORD = jsonRPCChildren.get("password");
        JSON_RPC_TIMEOUT_SECONDS = tools.isInt(jsonRPCChildren.get("timeout")) ? Integer.parseInt(jsonRPCChildren.get("timeout")) : 60;
        if(IS_THUMB_CLEANER) JSON_RPC_RESPONSE_TIMEOUT_MS *= 1.5;//allow extra timeout because queries aren't as rapid
        log(INFO, "JSON-RPC config: XBMCName="+JSON_RPC_SERVER+", timeout="+JSON_RPC_TIMEOUT_SECONDS+" seconds, method="+(USE_CURL ? "Curl, port="+JSON_RPC_WEBSERVER_PORT+", username="+JSON_RPC_WEBSERVER_USERNAME+", password="+JSON_RPC_WEBSERVER_PASSWORD : "Raw, port=9090"));
        

         //thumbnail dir
        Element thumbnailDir = root.getChild("ThumbnailDir");
        if(thumbnailDir != null)
        {
            XBMC_THUMBNAILS_FOLDERS = new ArrayList<String>();//this is a list because it was thought of possible having multiple thumb dirs. TODO: review if this is necessary
            String strFolder = thumbnailDir.getText();
            if(strFolder.endsWith("/")||strFolder.endsWith("\\")) strFolder = strFolder.substring(0, strFolder.length()-1);//trim traling slash
            XBMC_THUMBNAILS_FOLDERS.add(strFolder);
            log(DEBUG, "XBMC Thumbnail dir = "+ XBMC_THUMBNAILS_FOLDERS.get(0));
        }
        else
        {
            log(ERROR, "No <ThumbnailDir> element found, cannot continue.");
            return false;
        }

        //Database (MySQL or SQLite)

        Element databaseElem = root.getChild("XBMCDatabase");
        if(databaseElem != null)
        {
            Element sqlite = databaseElem.getChild("SQLite");
            if(sqlite != null)
            {
                boolean enabled = "true".equalsIgnoreCase(sqlite.getAttributeValue("enabled"));
                if(enabled)
                {
                    DATABASE_TYPE = SQL_LITE;
                    
                    sqlLiteVideoDBPath = sqlite.getChildText("VideoDBPath");
                    Config.log(INFO, "XBMC SQLite VideoDBPath = "+ sqlLiteVideoDBPath);
                    if(!new File(sqlLiteVideoDBPath).exists())
                    {
                        Config.log(ERROR, "No file exists at XBMC SQLite path for video db. Cannot continue. Path = "+ sqlLiteVideoDBPath);
                        return false;
                    }

                    if(IS_THUMB_CLEANER)//thumbcleaner uses music DB as well
                    {
                        sqlLiteMusicDBPath = sqlite.getChildText("MusicDBPath");
                        Config.log(INFO, "XBMC SQLite MusicDBPath = "+ sqlLiteMusicDBPath);
                        if(!new File(sqlLiteMusicDBPath).exists())
                        {
                            Config.log(ERROR, "No file exists at XBMC SQLite path for music db. Cannot continue. Path = "+ sqlLiteMusicDBPath);
                            return false;
                        }
                    }                    
                }
                else
                {
                    Config.log(INFO, "SQLite is disabled.");
                }
            }
            else
            {
                log(WARNING, "<SQLite> element not found. Will look for <MySQL>...");
            }


            //XBMCMySQLServer
             //load XBMC MySQL Server info
            if(SQL_LITE.equals(DATABASE_TYPE))
            {
                Config.log(INFO, "Skipping MySQL config because SQLite is already enabled.");
            }
            else
            {
                Element xbmcMySQL = databaseElem.getChild("MySQL");
                Map<String,String> xbmcMySQLChildren = getChildren(xbmcMySQL);
                boolean mySQLEnabled = false;
                if(xbmcMySQL != null)
                {
                    mySQLEnabled = "true".equalsIgnoreCase(xbmcMySQL.getAttributeValue("enabled"));
                    XBMC_MYSQL_SERVER = xbmcMySQLChildren.get("host");
                    XBMC_MYSQL_VIDEO_SCHEMA = xbmcMySQLChildren.get("videoschema");
                    if(valid(xbmcMySQLChildren.get("musicschema"))) XBMC_MYSQL_MUSIC_SCHEMA = xbmcMySQLChildren.get("musicschema");
                    if(valid(xbmcMySQLChildren.get("characterset"))) MYSQL_CHARACTER_SET = xbmcMySQLChildren.get("characterset");
                    XBMC_MYSQL_UN = xbmcMySQLChildren.get("username");
                    XBMC_MYSQL_PW = xbmcMySQLChildren.get("password");
                    XBMC_MYSQL_PORT = tools.isInt(xbmcMySQLChildren.get("port")) ? Integer.parseInt(xbmcMySQLChildren.get("port")): 3306;


                    String ifThumbCleaner = "";
                    if(IS_THUMB_CLEANER) ifThumbCleaner = ", XBMCMusicSchema="+XBMC_MYSQL_MUSIC_SCHEMA;//thumb cleaner uses music schema too
                    log(mySQLEnabled ? INFO : DEBUG,"XBMCMySQL config: enabled="+mySQLEnabled+", "+"XBMCServerName="+XBMC_MYSQL_SERVER+", XBMCVideoSchema="+XBMC_MYSQL_VIDEO_SCHEMA+", "
                        + "MySQLUserName="+XBMC_MYSQL_UN+", MySQLPassword="+XBMC_MYSQL_PW+", MySQLPort="+XBMC_MYSQL_PORT+ifThumbCleaner + ", CharacterSet="+MYSQL_CHARACTER_SET);

                    //test the connections
                    if(mySQLEnabled)
                    {
                        DATABASE_TYPE = MYSQL;
                        Map<String, String> schemas = new LinkedHashMap<String,String>();
                        schemas.put("video", XBMC_MYSQL_VIDEO_SCHEMA);
                        schemas.put("music", XBMC_MYSQL_MUSIC_SCHEMA);
                        for(Map.Entry<String,String> entry : schemas.entrySet())
                        {
                            String schemaType = entry.getKey();
                            if(schemaType.equals("music") && IS_MY_LIBRARY) continue;//MyLibrary doesnt use music schema, no need to test
                            String schema = entry.getValue();
                            log(INFO, "Testing connection to XBMC SQL Database "+schemaType+" schema: "+schema);
                            XBMCInterface xbmcMySQLConnection = new XBMCInterface(MYSQL, schema);
                            log(INFO, "Connected successfully = " + xbmcMySQLConnection.isConnected());
                            if(!xbmcMySQLConnection.isConnected())
                            {                                                                
                                log(ERROR, "Failed to connect to XBMC MySQL Server, "+schemaType+" schema named " + schema+"; Cannot continue.");
                                return false;
                            }
                            if(xbmcMySQLConnection != null) xbmcMySQLConnection.close();
                        }//end looping thru schemas
                    }
                }
                else log(WARNING, "<XBMCMySQLServer> element not found in config file.");
            }//end if SQLite is not enabled

            if(DATABASE_TYPE == null)
            {
                Config.log(ERROR, "Neither SQLite nor MySQL are enabled in config. Cannot continue.");
                return false;
            }
        }//end database elem is not null
        else
        {
            Config.log(ERROR, "No <XBMCDatabase> element was found in config. Cannot continue.");
            return false;
        }

        //specific config for MyLibrary
        if(IS_MY_LIBRARY)
        {
            //Restart XBMC before scan
            Element restartXBMCElem = root.getChild("XBMCRestart");
            if(restartXBMCElem == null)Config.log(WARNING, "No <XBMCRestart> element found, defaulting to restart="+RESTART_XBMC);
            else RESTART_XBMC = "true".equalsIgnoreCase(restartXBMCElem.getAttributeValue("enabled"));
            log(DEBUG, "XBMCRestart enabled = "+ RESTART_XBMC);
            
            //Restart Service Before Scan
            RESTART_PLAYON_BEFORE_SCAN = false;//todo: review if this is wanted anymore. currently disabling
            /*
            Element restartB4Scan = root.getChild("RestartServiceBeforeScan");
            if(restartB4Scan != null)
            {
                PLAYON_SERVICE_NAME = restartB4Scan.getChildText("ServiceName");
                RESTART_PLAYON_BEFORE_SCAN = "true".equalsIgnoreCase(restartB4Scan.getAttributeValue("enabled"));
                if(RESTART_PLAYON_BEFORE_SCAN)
                {
                    Element restartOnlyIfNotStreaming = restartB4Scan.getChild("SkipRestartIfCurrentlyStreaming");
                    RESTART_PLAYON_ONLY_IF_NOT_STREAMING = "true".equalsIgnoreCase(restartOnlyIfNotStreaming.getAttributeValue("enabled"));
                    if(RESTART_PLAYON_ONLY_IF_NOT_STREAMING)
                    {
                        //PLAYON_MEDIA_DIR = restartOnlyIfNotStreaming.getChildText("PlayOnMediaDir");
                        Element ipRanges = restartOnlyIfNotStreaming.getChild("ClientIPRanges");
                        List<Element> ranges = ipRanges.getChildren();
                        for(Element range : ranges)
                        {
                            String strRange = range.getText();
                            //verify
                            if(tools.verifyIpRange(strRange))
                                PLAYON_CLIENT_IP_RANGES.add(strRange);
                            else
                                log(WARNING, "This IP range is not valid and won't be used to filter clients: \""+strRange+"\" is not in format \"xxx.xxx.xxx.xxx-xxx.xxx.xxx.xxx\"");
                        }
                    }
                }
            }
            Config.log(INFO, "Restart PlayOn service before scan = " + RESTART_PLAYON_BEFORE_SCAN +". Only if not currently streaming = "+ RESTART_PLAYON_ONLY_IF_NOT_STREAMING +". Streaming client IP ranges = "+ Arrays.toString(PLAYON_CLIENT_IP_RANGES.toArray()));
            */
            
            //LibraryScanWaitMinutes
            Element libraryScanWaitMinElem = root.getChild("LibraryScanWaitMinutes");
            if(libraryScanWaitMinElem != null)
            {
                String strMin = libraryScanWaitMinElem.getText().trim();
                try
                {
                    LIBRARY_SCAN_WAIT_MINUTES = Double.parseDouble(strMin);
                }catch(NumberFormatException x)
                {
                    Config.log(WARNING, "Invalid number for <LibraryScanWaitMinutes>: \""+strMin+"\" is not a valid decimal, will default to " + LIBRARY_SCAN_WAIT_MINUTES +" minutes.");
                }
            }
            Config.log(DEBUG, "Library scan wait minutes = "+ LIBRARY_SCAN_WAIT_MINUTES);

            //IP Change
            Element ipChangeElem = root.getChild("IPChange");
            if(ipChangeElem != null)            
                IP_CHANGE_ENABLED = "true".equalsIgnoreCase(ipChangeElem.getAttributeValue("enabled"));
            
            Config.log(DEBUG, "IP Change is " + (IP_CHANGE_ENABLED ? "enabled for "+ IP_CHANGES.size()+" changes.":"disabled"));
            if(IP_CHANGE_ENABLED)
            {
                List<Element> changes = ipChangeElem.getChildren("change");
                for(Element change : changes)
                {
                    String from = change.getAttributeValue("from");
                    String to = change.getAttributeValue("to");
                    if(!valid(from))
                    {
                        Config.log(WARNING, "Skipping IPChange, from is not valid: \""+from+"\"");
                        continue;
                    }
                    if(!valid(to))
                    {
                        Config.log(WARNING, "Skipping IPChange, to is not valid: \""+to+"\"");
                        continue;
                    }
                    IP_CHANGES.add(new IPChange(from, to));
                    Config.log(DEBUG, "Added IPChange from: "+from +" to "+ to);
                }
            }
            

            
            //Dropbox
            Element dropboxElem = root.getChild("Dropbox");
            if(dropboxElem == null)
            {
                log(ERROR, "<Dropbox> config not found. Cannot continue.");
                return false;
            }

            String streamingDropbox = dropboxElem.getChildText("streaming");
            if(!valid(streamingDropbox))
            {
                log(ERROR, "Dropbox was not found in Config.xml. Please verify your <streaming> element is filled in. Cannot contine until this is fixed.");
                return false;
            }
            if(streamingDropbox.endsWith("/") || streamingDropbox.endsWith("\\")) streamingDropbox = streamingDropbox.substring(0, streamingDropbox.length()-1);//trim trailing slash
            DROPBOX = streamingDropbox;
            //create the dropbox if it doesnt exist
            File db = new File(DROPBOX);
            if(!db.exists())db.mkdir();
            log(DEBUG, "Streaming Dropbox = "+ DROPBOX);
            
            String downloadedDropbox = dropboxElem.getChildText("downloaded");
            if(!valid(downloadedDropbox))
            {
                log(ERROR, "Downloaded Dropbox was not found in Config.xml. Please verify your <downloaded> element is filled in. Cannot contine until this is fixed.");
                return false;
            }
            if(downloadedDropbox.endsWith("/") || downloadedDropbox.endsWith("\\")) downloadedDropbox = downloadedDropbox.substring(0, downloadedDropbox.length()-1);//trim trailing slash
            DOWNLOADED_VIDEOS_DROPBOX = downloadedDropbox;
            //create the dropbox if it doesnt exist
            File db2 = new File(DOWNLOADED_VIDEOS_DROPBOX);
            if(!db2.exists())db2.mkdir();
            log(DEBUG, "Download Dropbox = "+ DOWNLOADED_VIDEOS_DROPBOX);
            

            //TVDB api key
            TVDB_API_KEY = "05EB6802977A1FFE";//my key
            OBSCURE_TVDB_KEY_IN_LOG = true;
            /*
            try
            {
                Element apiKey =  root.getChild("TheTVDBApiKey");
                TVDB_API_KEY = apiKey.getText();

                OBSCURE_TVDB_KEY_IN_LOG = "true".equalsIgnoreCase(apiKey.getAttributeValue("obscure_in_logs"));                
            }
            catch(Exception x)
            {
                log(WARNING, "Could not find TheTvDB Api Key in config file. Please make sure your <TheTVDBApiKey> entry is correct. Cannot continue.",x);
                return false;//api key is required, so end here
            }
            */
            if(OBSCURE_TVDB_KEY_IN_LOG)
                TVDB_API_KEY_OBSCURED = (TVDB_API_KEY.length() > 10 ? ("XXXXXXXXXX"+TVDB_API_KEY.substring(10, TVDB_API_KEY.length())) : "XXXXXXXXXX");
            else
                TVDB_API_KEY_OBSCURED = TVDB_API_KEY;
            log(DEBUG, "Found TheTVDB Api Key: " + TVDB_API_KEY_OBSCURED);

            //ManualArchiving
            Element manualArchivingElem = root.getChild("ManualArchiving");
            if(manualArchivingElem != null)
            {
                MANUAL_ARCHIVING_ENABLED = "true".equalsIgnoreCase(manualArchivingElem.getAttributeValue("enabled"));
                if(MANUAL_ARCHIVING_ENABLED)
                {
                    try
                    {
                        HOURS_OLD_BEFORE_MANUAL_ARCHIVE = Double.parseDouble(manualArchivingElem.getChildText("HoursThreshold"));
                    }
                    catch(Exception x)
                    {
                        log(WARNING, "Cannot determine HoursThreshold for ManualArchiving, will use default of: "+ HOURS_OLD_BEFORE_MANUAL_ARCHIVE +" hours: "+x);
                    }
                    log(DEBUG, "Manual Archiving is enabled with an HoursThreshold of "+ HOURS_OLD_BEFORE_MANUAL_ARCHIVE +" hours");
                }
                 else
                     log(DEBUG, "Manual Archiving is disabled.");
            }
            else
            {
                MANUAL_ARCHIVING_ENABLED = false;
                log(WARNING, "<ManuarArchiving> element not found, manual archiving will be disabled.");
            }

            //VideoCleanUp
            Element VideoCleanUpElem = root.getChild("VideoCleanUp");
            if(VideoCleanUpElem != null)
            {
                try
                {
                    MISSING_HOURS_DELETE_THRESHOLD = Double.parseDouble(VideoCleanUpElem.getChildText("HoursThreshold"));
                }
                catch(Exception x)
                {
                    log(WARNING, "Cannot determine HoursThreshold for VideoCleanUp, will use default of: "+ MISSING_HOURS_DELETE_THRESHOLD +" hours: "+ x);
                }
                log(DEBUG, "VideoCleanUp has an HoursThreshold of "+ MISSING_HOURS_DELETE_THRESHOLD +" hours");

                try
                {
                    MISSING_COUNT_DELETE_THRESHOLD = Integer.parseInt(VideoCleanUpElem.getChildText("ConsecutiveThreshold"));
                }
                catch(Exception x)
                {
                    log(WARNING, "Cannot determine ConsecutiveThreshold for VideoCleanUp, will use default of: "+ MISSING_COUNT_DELETE_THRESHOLD +" hours: "+ x);
                }
                log(DEBUG, "VideoCleanUp has an ConsecutiveThreshold of "+ MISSING_COUNT_DELETE_THRESHOLD +" consecutive missing times.");
            }
            else
            {              
                log(WARNING, "<VideoCleanUp> element not found, will use defaults of "+MISSING_HOURS_DELETE_THRESHOLD+" hours and " +MISSING_COUNT_DELETE_THRESHOLD+" consecutive counts");
            }

            Element preScrapeMusicVidsElem = root.getChild("PreScrapeMusicVids");
            if(preScrapeMusicVidsElem == null)
            {
                Config.log(WARNING, "<PreScrapeMusicVids> element not found, pre-scraping will be disabled for music videos");
                SCRAPE_MUSIC_VIDEOS = false;
            }
            else
            {
                SCRAPE_MUSIC_VIDEOS = "true".equalsIgnoreCase(preScrapeMusicVidsElem.getAttributeValue("enabled"));
                Config.log(DEBUG, "PreScrapeMusicVids enabled = "+ SCRAPE_MUSIC_VIDEOS);
            }

            Element jdownloaderElem = root.getChild("JDownloader");
            String jdHost = null;
            if(jdownloaderElem != null)
                jdHost = jdownloaderElem.getText();
            if(valid(jdHost))
            {
                JDOWNLOADER_HOST = jdHost;
                Config.log(DEBUG, "JDownloader host = "+ JDOWNLOADER_HOST);
            }
            else
                Config.log(WARNING, "No <JDownloader> host was specified. Will not be able to download files.");

            Element comskipElem = root.getChild("ComSkipDownloadedVideos");
            if(comskipElem == null)log(WARNING, "No <ComSkipDownloadedVideos> element was found, comskip wil be disabled.");
            else COMSKIP_DOWNLOADED_VIDEOS = "true".equalsIgnoreCase(comskipElem.getAttributeValue("enabled"));
            log(DEBUG, "ComSkipDownloadedVideos = "+ COMSKIP_DOWNLOADED_VIDEOS);
            if(COMSKIP_DOWNLOADED_VIDEOS)
            {
                if(tools.isInt(comskipElem.getAttributeValue("type")))
                    EDL_TYPE = Integer.parseInt(comskipElem.getAttributeValue("type"));
                log(DEBUG, "Comskip edl type set to: "+ EDL_TYPE);
            }

            
            //get SearchFilters
            Element searchFilters = root.getChild("SearchFilters");
            if(searchFilters == null)
            {
                log(ERROR, "No <SearchFilters> found, cannot continue.");
                return false;
            }

            List<Element> sources = searchFilters.getChildren();
            
            for(Element sourceElement : sources)
            {
                String sourceName = sourceElement.getName();                
                String sourcePath = sourceElement.getAttributeValue("path");
                if(!tools.valid(sourcePath))
                {
                    sourcePath = "";
                    log(DEBUG, "Source path was not specified. Will try to auto-determine it later.");
                }
                else//valid path was specified
                {
                    //normalize the path (must end with /)
                    if(!sourcePath.endsWith("/")) sourcePath = sourcePath+"/";
                }
                log(INFO, "Found source "+sourceName +" with path of "+ (valid(sourcePath) ? sourcePath : "[auto-determine]"));
                
                                
                Source src = new Source(sourceName, sourcePath);
                boolean inUniqueSource = addSource(src);//check for unique names
                if(!inUniqueSource) continue;//skip this source since it was not successfully added

                String customParser = sourceElement.getAttributeValue("custom_parser");
                if(tools.valid(customParser))                                    
                    src.setCustomParser(customParser);//use the specified custom parser name                
                else src.setCustomParser(src.getName());//use the name as the parser name
                log(INFO, "Setting source's custom_parser to: "+ src.getCustomParser());

                //get subfolders (allowing nested subfolders)
                List<Element> topSubfolders = sourceElement.getChildren("subfolder");//these are the subfolders directly under the source
                for(Element topSubfolder : topSubfolders)
                {
                    topSubfolder.setAttribute("level_deep","0");//0 is the absolute top of the levels
                    //digs to the deepest subfolder under each "root" subfolder elem and adds all elements. Start at level 1 since topSubfolder took level 0
                    List<Element> subfolders = getAllChildElements(topSubfolder, "subfolder", 1);
                                     
                    subfolders.add(0, topSubfolder);//add the top subfolder at the top of the list
                                        
                    //sort so the deepest subfolders are at the top.
                    //User would expect a deeper subfolder to over-ride a parent subfolder
                    Collections.sort(subfolders, new Comparator<Element>()
                    {
                        public int compare(Element e1, Element e2)
                        {
                            int level1 = Integer.parseInt(e1.getAttributeValue("level_deep"));
                            int level2 = Integer.parseInt(e2.getAttributeValue("level_deep"));
                            if(level1 == level2) return 0;
                            if(level1 >  level2) return -1;
                            else /*if(level1 < level2)*/ return 1;
                        }
                    });

                    for(Element subfolder : subfolders)
                    {
                        String subfolderName = inheritName(subfolder);
                        if(!valid(subfolderName))
                        {
                            log(ERROR, "No name attribute specified for subfolder, it will be skipped!");
                            continue;
                        }
                        else//trim a traling slash if it exists
                        {
                            if(subfolderName.endsWith("/"))
                                subfolderName = subfolderName.substring(0,subfolderName.length()-1);
                        }

                        //inherit attributes from the parent (sourceElement), and over-ride if specified at the subfolder level
                        boolean regexName = "true".equalsIgnoreCase(inherit("regex_name", sourceElement, subfolder));
                        boolean recursive = "true".equalsIgnoreCase(inherit("recursive", sourceElement, subfolder));
                        boolean forceTVDB = "true".equalsIgnoreCase(inherit("force_tvdb", sourceElement, subfolder));
                        boolean download = "true".equalsIgnoreCase(inherit("download", sourceElement, subfolder));
                        boolean containsMultiPartVideos = "true".equalsIgnoreCase(inherit("multi_part", sourceElement, subfolder));
                        String type = (inherit("type", sourceElement, subfolder));
                        String strMaxSeries = (inherit("max_series", sourceElement, subfolder));
                        int max_series = tools.isInt(strMaxSeries) ? Integer.parseInt(strMaxSeries) : -1;
                        String strMaxVideos = (inherit("max_videos", sourceElement, subfolder));
                        int max_vidoes = tools.isInt(strMaxVideos) ? Integer.parseInt(strMaxVideos) : -1;
                        String movie_set = (inherit("movie_set", sourceElement, subfolder));
                        String prefix = (inherit("prefix", sourceElement, subfolder));
                        String suffix = (inherit("suffix", sourceElement, subfolder));
                        int level_deep = Integer.parseInt(subfolder.getAttributeValue("level_deep"));
                        String compression = (inherit("compression", sourceElement, subfolder));

                        Subfolder subf = new Subfolder(src, subfolderName);
                        subf.setRecursive(recursive);
                        subf.setRegexName(regexName);
                        if(valid(type)) subf.setType(type);
                        if(max_series > 0) subf.setMaxSeries(max_series);
                        if(max_vidoes > 0) subf.setMaxVideos(max_vidoes);
                        subf.setForceTVDB(forceTVDB);
                        subf.setMovieSet(movie_set);
                        subf.setPrefix(prefix);
                        subf.setSuffix(suffix);
                        subf.setCanContainMultiPartVideos(containsMultiPartVideos);
                        subf.setDownload(download);
                        subf.setLevelDeep(level_deep);
                        subf.setCompression(compression);

                        String indent = "";
                        for(int i=subf.getLevelDeep(); i>=0; i--)indent+="\t";
                            
                        Config.log(INFO, indent+"Next Subfolder: name="+subf.getFullName()+", recursive="+subf.isRecursive()+", type="+subf.getType()+", max_series="+subf.getMaxSeries()+", "
                                + "max_videos="+subf.getMaxVideos()+", movie_set="+subf.getMovieSet()+", prefix="+subf.getPrefix()+", suffix="+subf.getSuffix()+", download="+download+", compression="+(valid(compression) ? compression:"")+", multi_part="+containsMultiPartVideos);

                        //check for excludes/filters at the subfolder level
                        //inherit any excludes/filters from parent subfolders
                        List<Element> subfolderAndParents = getParentElements(subfolder);
                        subfolderAndParents.add(0, sourceElement);//add source at the top. Will inherit excludes/filters from it as well
                        Collections.reverse(subfolderAndParents);//start at subfolder level and work up for easier understanding in logs
                        for(Element nextSubf : subfolderAndParents)
                        {
                            Element excludesElem = nextSubf.getChild("exclude");
                            if(excludesElem != null)
                            {
                                List<Element> excludes = excludesElem.getChildren();
                                for(Element exclude : excludes)
                                {
                                    subf.addExclude(exclude.getName(), exclude.getText());
                                    log(DEBUG, indent+"\tAdded Exclude: type="+exclude.getName()+", value="+exclude.getText());
                                }
                            }
                        }

                        for(Element nextSubf : subfolderAndParents)
                        {
                            Element filtersElem = nextSubf.getChild("filter");
                            if(filtersElem != null)
                            {
                                List<Element> filters = filtersElem.getChildren();
                                for(Element filter : filters)
                                {
                                    subf.addFitler(filter.getName(), filter.getText());
                                    log(DEBUG, indent+"\tAdded subfolder Filter: type="+filter.getName()+", value="+filter.getText());
                                }
                            }
                        }
                        src.addSubfolder(subf);
                    }//end subfolders
                }//end top subfolders               
            }//end Sources            
            
            
            //GlobalExcludes
            Element globalExcludesElement = root.getChild("GlobalExcludes");
            if(globalExcludesElement != null)
            {
                List<Element> excludes = globalExcludesElement.getChildren();
                for(Element exclude : excludes)
                {
                    Exclude.addGlobalExclude(exclude.getName(), exclude.getText());
                    log(INFO, "Added Global Exclude, type="+exclude.getName()+", value="+exclude.getText());
                }
            }

            //compression definitions
            Element compressionElem = root.getChild("Compression");
            List<Element> defs = compressionElem.getChildren();
            for(Element def : defs)
            {
                String name = def.getName();
                String encodeTo = def.getChildText("encode_to");
                String command = def.getChildText("command");
                if(!tools.valid(encodeTo))
                {
                    log(WARNING, "Skipping: Compression definition <"+name+"> does not have a valid <encode_to>");
                    continue;
                }
                if(!tools.valid(command))
                {
                    log(WARNING, "Skipping: Compression definition <"+name+"> does not have a valid <command>");
                    continue;
                }
                List<String> verificationLines = new ArrayList<String>();
                Element verifLinesElem = def.getChild("VerificationLines");
                if(verifLinesElem == null)
                {
                    log(WARNING, "No <VerificationLines> element found for compression definition <"+name+">. Cannot use this compression definition.");
                    continue;
                }
                List<Element> lines = verifLinesElem.getChildren();
                for(Element line : lines)
                {
                    verificationLines.add(line.getText());
                    log(DEBUG, "Found verification line: "+ line.getText());
                }
                if(verificationLines.isEmpty())
                {
                    log(WARNING, "No <VerificationLines> exists for the compression definition \""+name+"\". Cannot use this definition.");
                    continue;
                }
                CompressionDefinition cd = new CompressionDefinition(name, command, encodeTo, verificationLines);
                if(COMPRESSION_DEFINITIONS.get(cd.getName().toLowerCase()) != null)
                {
                    log(WARNING, "A compression definition named \""+name+"\" (case-insensitive) already exists. Will not skip this one.");
                    continue;
                }
                COMPRESSION_DEFINITIONS.put(cd.getName().toLowerCase(), cd);
                log(DEBUG, "Added compression definition named \""+name+"\" with encode_to = "+ encodeTo +", command = "+ command);
            }
        }//end if IS_MY_LIBRARY

        //specific config for thumb cleaner
        if(IS_THUMB_CLEANER)
        {
            //simulation
            Element simulationElem = root.getChild("Simulation");
            if(simulationElem == null)
            {
                log(WARNING, "No <Simulation> elment found. Setting simulation = true");
                SIMULATION=true;
            }
            else
                SIMULATION = !"false".equalsIgnoreCase((simulationElem.getText()).trim());//default to true to be safe
            
            Config.log(INFO, "Simulation = "+ SIMULATION);

            Element confirmExistsElem = root.getChild("ConfirmPathsExist");
            if(confirmExistsElem == null)
            {
                log(WARNING, "No <ConfirmPathsExist> elment found. Setting ConfirmPathsExist = false");
                CONFIRM_PATHS_EXIST = false;
            }
            else
                CONFIRM_PATHS_EXIST = "true".equalsIgnoreCase(confirmExistsElem.getText());
            log(INFO, "ConfirmPathsExist = "+ CONFIRM_PATHS_EXIST);

            //SpotCheck
            Element spotcheck = root.getChild("SpotCheck");
            if(spotcheck != null)
            {
                SPOT_CHECK_DIR = spotcheck.getText();
                if(tools.valid(SPOT_CHECK_DIR) && SPOT_CHECK_DIR.endsWith(SEP))
                    SPOT_CHECK_DIR = SPOT_CHECK_DIR.substring(0, SPOT_CHECK_DIR.length()-SEP.length());//trim trailing slash
                try{SPOT_CHECK_MAX_IMAGES = Integer.parseInt(spotcheck.getAttributeValue("max"));}catch(Exception x){}
                Config.log(INFO, "SpotCheck directory = " + SPOT_CHECK_DIR+", max images = "+ SPOT_CHECK_MAX_IMAGES);
            }
            else
            {
                Config.log(INFO, "No <SpotCheck> element found. Spot check will be disabled");
                SPOT_CHECK_DIR = null;
            }

            //Textures
            try
            {
                Element texturesElem = root.getChild("XBMCTextures");
                if(texturesElem == null) throw new Exception("No <XBMCTexturesDB> elem found.");

                //db path
                Element textureDBPathElem = texturesElem.getChild("TextureDBPath");
                if(textureDBPathElem == null) throw new Exception("No <TextureDBPath> found.");
                sqlLiteTexturesDBPath = textureDBPathElem.getText();
                if(!valid(sqlLiteMusicDBPath))throw new Exception("No path to Textures.db was specified in <TextureDBPath> element. Cannot continue");
                log(INFO, "Path to Textures.db = "+ sqlLiteTexturesDBPath);

                //last used threshold
                Element lastUsedThresholdElem = texturesElem.getChild("LastUsedThresholdDays");
                if(lastUsedThresholdElem == null) throw new Exception("No <LastUsedThresholdDays> found.");
                try{TEXTURE_LAST_USED_DAYS_THRESHOLD = Double.parseDouble(lastUsedThresholdElem.getText());}
                catch(Exception x){log(WARNING, "Could not find vaild number for <LastUsedThresholdDays>, defaulting to: "+ TEXTURE_LAST_USED_DAYS_THRESHOLD +" days");}                
                log(INFO, "Texture last used threshold = "+ TEXTURE_LAST_USED_DAYS_THRESHOLD +" days ago.");

                //and/or
                Element andOrElem = texturesElem.getChild("AndOr");
                if(andOrElem == null) throw new Exception("No <AndOr> element found.");
                String andOrTxt = andOrElem.getText();
                if(!valid(andOrTxt)) throw new Exception("Nothing specified in <AndOr> element.");
                andOrTxt = andOrTxt.trim();
                if(!"and".equalsIgnoreCase(andOrTxt) && !"or".equalsIgnoreCase(andOrTxt)) throw new Exception("Invalid config for <AndOr>, must be either \"and\" or \"or\", found: \""+andOrTxt+"\"");
                TEXTURE_AND_OR_FOR_THRESHOLD = andOrTxt.toLowerCase();
                
                //use count threshold
                Element useCountThresholdElem = texturesElem.getChild("UseCountThreshold");
                if(useCountThresholdElem == null) throw new Exception("No <UseCountThreshold> found.");
                try{TEXTURE_USE_COUNT_THRESHOLD = Integer.parseInt(useCountThresholdElem.getText());}
                catch(Exception x){log(WARNING, "Could not find vaild number for <UseCountThreshold>, defaulting to: "+ TEXTURE_USE_COUNT_THRESHOLD);}
                log(INFO, "Texture use count threshold = "+ TEXTURE_USE_COUNT_THRESHOLD +". Textures that have been used less than "+ TEXTURE_USE_COUNT_THRESHOLD +" times will be deleted.");

            }
            catch(Exception x)
            {
                log(ERROR, "Error with <XBMCTexturesDB> configuration: "+ x,x);
                return false;
            }
        }                               
        return true;//got to end w/o issues
    }

    public boolean addSource(Source src)
    {
        //make sure we don't have any duplicate sources
        boolean isAUniqueSource = !ALL_SOURCES.contains(src);
        if(!isAUniqueSource)
        {
            log(ERROR, "The is more than 1 source element named \""+src.getName()+"\" (case-insensitive). "
                + "This is not allowed. Please use unique source names. Only the first source with this name will be used.");
            return false;
        }
        else//new, unique source
        {
            ALL_SOURCES.add(src);
            return true;
        }

    }

    //init the tracker DB's for MyLibrary    
    private boolean initializeSQLiteDbs()
    {
        //Archived Files tracker database
        final String archivedFilesDbLocation = BASE_PROGRAM_DIR+SEP+"res"+SEP+"ArchivedFiles.db";                
        Config.log(INFO, "Initializing SQLite database at: "+ archivedFilesDbLocation);
        archivedFilesDB = new Database(SQL_LITE, archivedFilesDbLocation, null, null, null, -1);
        //create the db table if it doesnt exist
        try
        {
            String tableName = "ArchivedFiles";            
            archivedFilesDB.getStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "                    
                    + "source_name NOT NULL, "
                    + "dropbox_location NOT NULL, "
                    + "original_path NOT NULL, "
                    + "date_archived TIMESTAMP NOT NULL, "
                    + "missing_since TIMESTAMP, "
                    + "missing_count INTEGER"
            + ")");
            archivedFilesDB.closeStatement();

            archivedFilesDB.getStatement().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_dropbox_location ON "+tableName+" (dropbox_location)");
            archivedFilesDB.closeStatement();
            
            /*additional changes since initial realease*/
            String[] varcharColumns = new String[] {"video_type", "title", "series", "artist"};//varchar columns
            for(String columnName : varcharColumns)
            {
                if(!archivedFilesDB.hasColumn(tableName, columnName))
                {
                    archivedFilesDB.getStatement().executeUpdate("ALTER TABLE "+ tableName +" ADD COLUMN "+columnName+" DEFAULT NULL");
                    archivedFilesDB.closeStatement();
                }
            }
            
            String[] intColumns = new String[] {"episode_number", "season_number", "year", "is_tvdb_lookup"};//integer columns
            for(String columnName : intColumns)
            {
                if(!archivedFilesDB.hasColumn(tableName, columnName))
                {
                    archivedFilesDB.getStatement().executeUpdate("ALTER TABLE "+ tableName +" ADD COLUMN "+columnName+" INTEGER DEFAULT NULL");
                    archivedFilesDB.closeStatement();
                }
            }

            //download tables
            tableName = "Downloads";
            archivedFilesDB.getStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "
                    + "archived_file_id INTEGER NOT NULL, "
                    + "started TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "status NOT NULL,"
                    + "dest_no_ext NOT NULL"
            + ")");
            archivedFilesDB.closeStatement();

            /*additional changes since initial realease for Downloads table*/
            varcharColumns = new String[] {"compression"};//varchar columns
            for(String columnName : varcharColumns)
            if(!archivedFilesDB.hasColumn(tableName, columnName))
            {
                archivedFilesDB.getStatement().executeUpdate("ALTER TABLE "+ tableName +" ADD COLUMN compression DEFAULT NULL");
                archivedFilesDB.closeStatement();
            }

            archivedFilesDB.getStatement().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_download ON "+tableName+" (archived_file_id)");
            archivedFilesDB.closeStatement();

            tableName = "DownloadFiles";
            archivedFilesDB.getStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "
                    + "download_id INTEGER  NOT NULL, "
                    + "url NOT NULL, "
                    + "file NOT NULL,"
                    + "dropbox_location DEFAULT NULL"
            + ")");
            archivedFilesDB.closeStatement();
            archivedFilesDB.getStatement().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_download_url ON "+tableName+" (download_id, url)");
            archivedFilesDB.closeStatement();
            archivedFilesDB.getStatement().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_download_file ON "+tableName+" (download_id, file)");
            archivedFilesDB.closeStatement();

            
            //EDLChanges
            tableName = "EDLChanges";
            archivedFilesDB.getStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "
                    + "file NOT NULL, "//path to the edl file
                    + "converted_to INTEGER NOT NULL"        //edl_type
            + ")");
            archivedFilesDB.closeStatement();
            archivedFilesDB.getStatement().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_edl_file ON "+tableName+" (file)");
            archivedFilesDB.closeStatement();            

        }
        catch(Exception x)
        {
            Config.log(ERROR, "Error while initializing SQLite database: "+ archivedFilesDbLocation,x);
            return false;
        }
        finally
        {
            archivedFilesDB.closeStatement();
        }

        //Queued Meta Data Changes tracker database
        final String queuedMetaDataChangesLocation = BASE_PROGRAM_DIR+SEP+"res"+SEP+"QueuedMetaDataChanges.db";
        Config.log(INFO, "Initializing SQLite database at: "+ queuedMetaDataChangesLocation);
        queuedChangesDB = new Database(SQL_LITE, queuedMetaDataChangesLocation, null, null, null, -1);
        //create the db table if it doesnt exist
        try
        {
            final String tableName = "QueuedChanges";                        
            //stmt.executeUpdate("drop table if exists "+tableName);
            queuedChangesDB.getStatement().executeUpdate("create table if not exists "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "
                    + "dropbox_location NOT NULL, "
                    + "video_type NOT NULL, "
                    + "meta_data_type NOT NULL, "
                    + "value NOT NULL, "
                    + "status NOT NULL)");
            queuedChangesDB.closeStatement();
            queuedChangesDB.getStatement().executeUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_queued_change ON QueuedChanges (dropbox_location, meta_data_type)");
            queuedChangesDB.closeStatement();
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Error while initializing SQLite database: "+queuedMetaDataChangesLocation,x);
            return false;
        }
        finally
        {
            queuedChangesDB.closeStatement();
        }

        //scraper query database
        final String scraperDBLocation = BASE_PROGRAM_DIR+SEP+"res"+SEP+"scraper.db";
        Config.log(INFO, "Initializing SQLite database at: "+ scraperDBLocation);
        scraperDB = new Database(SQL_LITE, scraperDBLocation, null, null, null, -1);
        //create the db table if it doesnt exist
        try
        {
            final String tableName = "APIQueries";

            //stmt.executeUpdate("drop table if exists "+tableName);
            scraperDB.getStatement().executeUpdate("create table if not exists "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "
                    + "api_name NOT NULL, "
                    + "query_url NOT NULL, "
                    + "query_time TIMESTAMP NOT NULL)");
            scraperDB.closeStatement();
                        
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Error while initializing SQLite database: "+scraperDBLocation,x);
            return false;
        }
        finally
        {
            scraperDB.closeStatement();
        }
        
        return true;
    }

    public static Map<String,String> getChildren(Element e)
    {
        Map<String,String> map = new LinkedHashMap<String,String>();
        if(e == null) return map;
        List<Element> children = e.getChildren();
        for(Element child : children)
        {
            map.put(child.getName().toLowerCase(), child.getText());
        }
        return map;
    }

    //Dynamically determine where this program is running
    public static String getBaseDirectory()
    {
        try
        {
            String fullDir = new File(Config.class.getProtectionDomain().getCodeSource().getLocation().getPath()).toString();//looks like "C:\SVN\SageXBMC\build\classes"
            //System.out.println("FullDir = " + fullDir);
            String baseDir;
            try
            {
               baseDir =  fullDir.substring(0, fullDir.indexOf("\\build"));//trim off \build\classes to get to base dir
            }
            catch(Exception x)
            {
                try
                {
                   baseDir =  fullDir.substring(0, fullDir.indexOf("\\dist"));//trim off \build\classes to get to base dir
                }
                catch(Exception x2)
                {
                    baseDir = null;
                }
            }

            if(baseDir != null)
            {
                //System.out.println("Successfully found baseDir: "+ baseDir);
                return baseDir.replace("%20", " ");
            }
            else
                throw new Exception("Cannot find base directory from \""+fullDir+"\"");

        }
        catch(Exception x)
        {
            System.out.println("Cannot determine base directory, please make sure you program is located at "+BASE_PROGRAM_DIR);
            x.printStackTrace();
            return BASE_PROGRAM_DIR;
        }
    }

    /*          
     * The list is always sorted with the highest parent at the beginning.
     */
    public static List<Element> getParentElements(Element elem)
    {
        List<Element> elementList = new ArrayList<Element>();
        Element currentElem = elem;
        elementList.add(currentElem);//always add the intial elem
        while(true)
        {
            Element nextElem = currentElem.getParentElement();
            
            if
            (
                nextElem == null || !nextElem.getName().equals(currentElem.getName())//if name is different, means we've gotten to a parent that isn't the same type
            )
            {break;}                                    
            elementList.add(0, nextElem);//add to the beginning, this is a higher parent
            currentElem = nextElem;//continue up the tree
        }
        return elementList;
    }
    
    /*
     * Gets all child elements, ordered by least deep to most deep
     */
    public List<Element> getAllChildElements(Element parentElem, String childElemName)
    {
        return getAllChildElements(parentElem, childElemName, 0);
    }
    
    private List<Element> getAllChildElements(Element parentElem, String childElemName, int levelDeep)
    {
        List<Element> allChildren = new ArrayList<Element>();
        List<Element> deeperElems = parentElem.getChildren(childElemName);
        if(!deeperElems.isEmpty())
        {
            //set the current level for all elements
            for(Element child : deeperElems)
                child.setAttribute("level_deep", String.valueOf(levelDeep));
            allChildren.addAll(deeperElems);//add the children from this level

            //and goto the next level
            levelDeep++;
            for(Element child : deeperElems)
            {
                allChildren.addAll(getAllChildElements(child,childElemName,levelDeep));//add even deeper children recursively
            }

        }        
        return allChildren;
    }
    
    public String inheritName(Element deepSubf)
    {
        //get a list of all subfolders above and including this one in this tree
        List<Element> subfolders = getParentElements(deepSubf);//sorted frop top to bottom

        StringBuilder fullName = new StringBuilder();
        //start at top of heirarchy and build name
        for(Iterator<Element> it = subfolders.iterator(); it.hasNext();)
        {
            Element elem = it.next();
            String name = elem.getAttributeValue("name");
            if(!valid(name))
            {
                log(ERROR, "A subfolder does not have a valid name attribute. Cannot continue");
                return null;
            }
            name = name.trim();
            //trim leading seperators
            if(name.startsWith("/")) name = name.substring(1,name.length());

            //alwasy have a trailing seperator if this isn't the last elem
            if(it.hasNext())
            {
                if(!name.endsWith("/")) //add traling slash in prep for next subfolder's name
                    name+= "/";
            }
            else//last element, trim traling slash
            {
                if(name.endsWith("/"))
                    name = name.substring(0, name.length()-1);
            }            
            fullName.append(name);
        }
        return fullName.toString();
    }

    /*
     * Inherit's attribute values. prefers lower value in the tree, defaults to parent
     * returns empty string if neither parent nor child(ren) have the arrtibute
     */    
    public String inherit(String attributeName, Element source, Element child)
    {        
        List<Element> children = getParentElements(child);//ordered from top to bottom
        //add the source at the very top of the tree
        children.add(0, source);

        Collections.reverse(children);//start with lowest child and work our way up, using the first valid value
        String value = "";//default of emtpy string
        for(Element elem : children)
        {
            String nextVal = elem.getAttributeValue(attributeName);
            if(valid(nextVal))
            {
                value = nextVal;
                break;
            }
        }
        return value;
    }


    public static boolean valid(String s)
    {
        return tools.valid(s);//convenience
    }
    public static String escapePath(String path)
    {
        String escaped = path.replace(DELIM, "/");
        return escaped;
    }

    public static String shortLogDesc = null;
    public static void setShortLogDesc(String s)
    {
        shortLogDesc=s;
    }

    
    //log to sytem out, current log, and historical log
    private static String previousLogString = "";
    private static int logRepeatCount = 0;
    private static int PREFIX_LENGTH = 8, SHORT_DESC_LENGTH = 16;
    public static void log(int level, String logString)
    {
        log(level,logString,null);//no throwable
    }
    public static void log(int level, String logString, Exception ex)
    {
        log(level,logString,null,ex);//no short description         
    }
    public static void log(int level, String logString, String shortDesc, Exception ex)
    {
        //lower level means more severe
        if(level <= LOGGING_LEVEL)
        {
            if(logString==null)logString="";
            String strLevel = LOGGING_LEVELS.get(level);
            if(!valid(strLevel)) strLevel ="UNKNOWN";            
            
            String exception = tools.getStacktraceAsString(ex);//null if no exception
            if(valid(exception)) exception = LINE_BRK+exception;
            else exception = "";
            
            java.util.Date now = new java.util.Date();
            String prefix = log_sdf.format(now) + " "+ tools.tfl(strLevel,PREFIX_LENGTH);

            String shortDescToUse = (shortDesc == null ? shortLogDesc : shortDesc);
            if(!valid(shortDescToUse)) shortDescToUse = " ";
            shortDescToUse = tools.tfl(shortDescToUse, SHORT_DESC_LENGTH)+" ";

            if(OBSCURE_TVDB_KEY_IN_LOG && logString.contains(TVDB_API_KEY)) logString = logString.replace(TVDB_API_KEY, TVDB_API_KEY_OBSCURED);
            logString += exception;
            logString = logString.replace("\n", "\n"+tools.tfl(" ", prefix.length()+shortDescToUse.length())+tools.tfl(" ", 4));//indent new lines the length of the prefix plus a tab
            if(logString.equals(previousLogString))
            {
                logRepeatCount++;
            }
            else//a different line
            {                 
                if(logRepeatCount > 0)
                {
                     String repeatNoticeLine = log_sdf.format(now) + " "+ tools.tfl("INFO",PREFIX_LENGTH)//same as prefix, but with INFO hard coded
                                                 + tools.tfl("Repeat",SHORT_DESC_LENGTH)
                                                 + " Previous line repeats "+ logRepeatCount + (logRepeatCount==1 ? " time":" times") +LINE_BRK;
                     logLine(repeatNoticeLine);
                     logRepeatCount = 0;
                }
                                
                //always log the normal line
                String line = prefix + shortDescToUse+ logString + LINE_BRK;
                logLine(line);

                //reset and track previous vals                
                previousLogString = logString;
            }
        }       
    }

    private static void logLine(String line)
    {
        System.out.print(line);//print to screen without waiting for queue. Only queue for file write
        if(logger != null) logger.queueLine(line);
    }

    public void logFileExpiration()
    {
        try
        {
            setShortLogDesc("LogExpiration");
            File logDir = new File(BASE_PROGRAM_DIR +"\\logs");
            Iterator<File> logFiles = FileUtils.iterateFiles(logDir, new String[]{"log"}, true);//get all log files, recursive=true

            long cutoff = System.currentTimeMillis() - (ONE_DAY*LOG_EXPIRE_DAYS);
            log(INFO, "Deleting logfiles older than "+LOG_EXPIRE_DAYS+" days (" + log_sdf.format(new java.util.Date(cutoff))+")");
            int numberDeleted = 0;
            int numberChecked = 0;
            while(logFiles.hasNext())
            {                
                File logFile = logFiles.next();
                if(!logFile.getName().contains(logFileNameNoExt)) continue;//only clean up this program's log files
                numberChecked++;
                long lastModified = logFile.lastModified();
                long daysOld = (System.currentTimeMillis() - lastModified) / ONE_DAY;
                if(lastModified < cutoff)
                {
                    log(INFO, "Deleting this log file, it is " +daysOld+ " days old: "+ logFile);
                    if(logFile.delete())
                    {
                        numberDeleted++;
                        log(INFO,"Successfully deleted.");
                    }
                    else
                        log(INFO, "Failed to delete, will try again next time.");
                }
                else
                    log(DEBUG, "Not expired, only " + daysOld +" days old (" + log_sdf.format(new java.util.Date(lastModified))+"): "+ logFile);
            }
            log(numberDeleted > 0 ? NOTICE : INFO, "Checked " + numberChecked + " log files, deleted " + numberDeleted +" expired log files.");
        }
        catch(Exception x)
        {
            log(ERROR, "Failed to clean up log files: "+ x,x);
        }
    }
    
    public static void end()
    {
        if(logger != null)logger.canStop();        
        if(queuedChangesDB != null)queuedChangesDB.close();
        if(archivedFilesDB != null)archivedFilesDB.close();
        if(scraperDB != null)scraperDB.close();
        try{if(SINGLE_INSTANCE_SOCKET != null) SINGLE_INSTANCE_SOCKET.close();}catch(Exception x){}finally{SINGLE_INSTANCE_SOCKET=null;}
    }
}