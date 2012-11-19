package com.bradvido.mylibrary.util;

import com.bradvido.db.*;
import com.bradvido.util.logger.*;
import com.bradvido.mylibrary.db.ArchivedFilesDB;
import com.bradvido.mylibrary.db.QueuedChangesDB;
import com.bradvido.mylibrary.db.ScraperDB;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;

import com.bradvido.xbmc.db.XBMCDbConfig;
import static com.bradvido.mylibrary.util.Constants.*;
import static com.bradvido.util.tools.BTVTools.*;

public class Config extends Constants
{
    public static boolean RESTART_XBMC = false;                      
    public static String DROPBOX = null;
    public static String LINUX_SAMBA_PREFIX = null;
    public static Set<Source> ALL_SOURCES = new LinkedHashSet<Source>();
    
    public static List<String> XBMC_THUMBNAILS_FOLDERS = new ArrayList<String>();;
    public static final String[] KNOWN_XBMC_THUMBNAIL_EXTENSIONS = {".tbn", ".dds"};
    
    //tracker SQLite DB's
    public static ArchivedFilesDB archivedFilesDB;
    public static QueuedChangesDB queuedChangesDB;
    public static ScraperDB scraperDB;
    
    public static boolean MANUAL_ARCHIVING_ENABLED = true;
    public static double HOURS_OLD_BEFORE_MANUAL_ARCHIVE = 6.0;
    
    //Note, this use my API key for yahoo
    public static boolean SCRAPE_MUSIC_VIDEOS = true;
        
    public static double MISSING_HOURS_DELETE_THRESHOLD = 12.0;
    public static int MISSING_COUNT_DELETE_THRESHOLD = 3;    

    //populated with chars that cant be used in windows file names
    public static Map<Integer,String> ILLEGAL_FILENAME_CHARS = new HashMap<Integer,String>();

    //populated with uncommon characters that are ignored when comparing strings for matches
    public static Map<Integer,String> UNCOMMON_CHARS = new HashMap<Integer,String>();

    public static int XBMC_SERVER_WEB_PORT = 8000;

     //TheTVDB config    
    public static String TVDB_API_KEY_OBSCURED = null;//for printing in log
    public static long MAX_TVDB_QUERY_INTERVAL_MS = (1000 * 60 * 60);//1 hour default wait time between TVDB identical requeries
    public static final int TVBD_QUERY_RECYCLE_MINUTES = 24*60;//forces tvdb queries to be re-cycled (remove them from tracker file) after this many minutes passes
    public static boolean OBSCURE_TVDB_KEY_IN_LOG = false;

    //JSON-RPC config
    public static int JSON_RPC_RAW_PORT = 9090;
    public static String XBMC_WEB_SERVER_URL = "[unknown]";                
    public static MyLibraryJsonRpc jsonRPCSender;
    
    
    //IP Change
    public static boolean IP_CHANGE_ENABLED = false;
    public static List<IPChange> IP_CHANGES = new ArrayList<IPChange>();
    

    //XBMC MySQL    Sqlite        
    private static String XBMC_MYSQL_SERVER =null;
    private static String XBMC_MYSQL_UN =null;
    private static String XBMC_MYSQL_PW =null;
    private static String XBMC_MYSQL_VIDEO_SCHEMA =null;    
    private static int XBMC_MYSQL_PORT = 3306;
    private static String DATABASE_TYPE = null;
    private static String sqlLiteVideoDBPath = "C:\\Users\\[USERNAME]\\AppData\\Roaming\\XBMC\\userdata\\Database\\MyVideos.db";            
    
    
    //sdf's
    public static final SimpleDateFormat tvdbFirstAiredSDF = new SimpleDateFormat("yyyy-MM-dd");//for lookup on thetvdb based on first aired date
    public static final SimpleDateFormat log_sdf = new SimpleDateFormat("MM/dd/yyyy hh:mm:ss a");//for logging    

    
    String logFileNameNoExt;
    public Config()
    {
        super(PROGRAM_NAME+".properties", LOGGING_LEVEL, SINGLE_INSTANCE, SINGLE_INSTANCE_PORT, -1);                                
                                
        setShortLogDesc("Init...");        
        Logger.NOTICE("Start "+PROGRAM_NAME+", v"+VERSION+", compatible with XBMC "+ XBMC_COMPATIBILITY);
                                

         //populate charactes that we do not allow in file names
        char[] specialChars = {'<', '>', ':', '"', '/', '\\', '|', '?', '*', '*', '~', '�'};
        for(char c : specialChars) ILLEGAL_FILENAME_CHARS.put(new Integer((int) c), "illegal");

        char[] uncommonChars = {'<', '>', ':', '"', '/', '\\', '|', '?', '*', '#', '$', '%', '^', '*', '!', '~','\'', '�', '=', '[' ,']', '(', ')', ';', '\\' ,',', '_'};
        for(char c : uncommonChars) UNCOMMON_CHARS.put(new Integer((int) c), "illegal");

                
        if(!initializeSQLiteDbs()) return; //need these!

    }

    public boolean loadConfig()
    {
        String xmlFileName = "Config.xml";//dfault        
        String strConfigFile = BASE_DIR+SEP+ xmlFileName;
        File configFile = new File(strConfigFile);
        if(!configFile.exists())
        {
            Logger.ERROR( "Config file does not exist at: \""+strConfigFile+"\". Cannot continue.");
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
            Logger.ERROR( "Could not find valid xml document at: "+ configFile +". Cannot continue... Please check config.xml with an XML validator.");
            return false;
        }

        Element root = ConfigXml.getRootElement();

     
        //Logging Config        
        String strLogLevel = root.getChildText("LoggingLevel");
        try
        {
            LOGGING_LEVEL =  BTVLogLevel.valueOf(strLogLevel.toUpperCase());
            Logger.DEBUG( "Logging level is set to: " + LOGGING_LEVEL);
        }
        catch(Exception x)
        {
            Logger.DEBUG( "LoggingLevel of \""+strLogLevel+"\" is not valid, will default to " + BTVLogLevel.INFO);               
            LOGGING_LEVEL =  BTVLogLevel.INFO;
        }
        Logger.getOptions().setLevelToLogAt(LOGGING_LEVEL);

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
            LOG_EXPIRE_DAYS = 30;
            Logger.WARN( "Failed to parse expiredays attribute in <LoggingLevel>, defaulting to " + LOG_EXPIRE_DAYS,x2);
        }
        Logger.DEBUG( "Logs will be deleted after " + LOG_EXPIRE_DAYS + " days.");
        Logger.getOptions().setLogExpireDays(LOG_EXPIRE_DAYS);

        
        
        //JSON-RPC
        Map<String,String> jsonRPCChildren = getChildren(root.getChild("JSONRPC"));
        XBMC_WEB_SERVER_URL = jsonRPCChildren.get("xbmcwebserver");        
        JSON_RPC_RAW_PORT = isInt(jsonRPCChildren.get("announcementport")) ? Integer.parseInt(jsonRPCChildren.get("announcementport")) : JSON_RPC_RAW_PORT;        
        Logger.INFO( "JSON-RPC config: XBMC Webserver URL="+XBMC_WEB_SERVER_URL);
        Logger.INFO( "JSON-RPC AnnouncementPort = "+JSON_RPC_RAW_PORT);        
        
        //init JSON-RPC connection
        jsonRPCSender = new MyLibraryJsonRpc(XBMC_WEB_SERVER_URL);  //init only, does not attempt to connect yet                   
                
        
        //11/12/2012 - bradvido - Database connection no longer needed. Everything has been converted to use JSON-RPC!
//        //Database (MySQL or SQLite)
//        XBMCDbConfig dbConfig = new XBMCDbConfig();
//        Element databaseElem = root.getChild("XBMCDatabase");
//        if(databaseElem != null)
//        {
//            Element sqlite = databaseElem.getChild("SQLite");
//            if(sqlite != null)
//            {
//                boolean enabled = "true".equalsIgnoreCase(sqlite.getAttributeValue("enabled"));
//                if(enabled)
//                {
//                    DATABASE_TYPE = SQL_LITE;
//                    dbConfig.setDB_TYPE(DbType.SQLITE);
//                    
//                    sqlLiteVideoDBPath = sqlite.getChildText("VideoDBPath");
//                    dbConfig.setSQLITE_PATH(sqlLiteVideoDBPath);
//                    
//                    Logger.INFO( "XBMC SQLite VideoDBPath = "+ sqlLiteVideoDBPath);
//                    if(!new File(sqlLiteVideoDBPath).exists())
//                    {
//                        Logger.ERROR( "No file exists at XBMC SQLite path for video db. Cannot continue. Path = "+ sqlLiteVideoDBPath);
//                        return false;
//                    }
//                                    
//                }
//                else
//                {
//                    Logger.INFO( "SQLite is disabled.");
//                }
//            }
//            else
//            {
//                Logger.WARN( "<SQLite> element not found. Will look for <MySQL>...");
//            }
//
//
//            //XBMCMySQLServer
//             //load XBMC MySQL Server info
//            if(SQL_LITE.equals(DATABASE_TYPE))
//            {
//                Logger.INFO( "Skipping MySQL config because SQLite is already enabled.");
//            }
//            else
//            {
//                Element xbmcMySQL = databaseElem.getChild("MySQL");
//                Map<String,String> xbmcMySQLChildren = getChildren(xbmcMySQL);
//                boolean mySQLEnabled = false;
//                if(xbmcMySQL != null)
//                {
//                    mySQLEnabled = "true".equalsIgnoreCase(xbmcMySQL.getAttributeValue("enabled"));
//                    XBMC_MYSQL_SERVER = xbmcMySQLChildren.get("host");
//                    dbConfig.setMYQSL_HOST(XBMC_MYSQL_SERVER);
//                    
//                    XBMC_MYSQL_VIDEO_SCHEMA = xbmcMySQLChildren.get("videoschema");
//                    dbConfig.setMYSQL_SCHEMA(XBMC_MYSQL_VIDEO_SCHEMA);
//                    
//                    XBMC_MYSQL_UN = xbmcMySQLChildren.get("username");
//                    dbConfig.setMYSQL_USERNAME(XBMC_MYSQL_UN);
//                    
//                    XBMC_MYSQL_PW = xbmcMySQLChildren.get("password");
//                    dbConfig.setMYSQL_PASSWORD(XBMC_MYSQL_PW);
//                    
//                    XBMC_MYSQL_PORT = isInt(xbmcMySQLChildren.get("port")) ? Integer.parseInt(xbmcMySQLChildren.get("port")): 3306;
//                    dbConfig.setMYSQL_PORT(XBMC_MYSQL_PORT);
//
//                    
//                    Logger.log(mySQLEnabled ? BTVLogLevel.INFO : BTVLogLevel.DEBUG,"XBMCMySQL config: enabled="+mySQLEnabled+", "+"XBMCServerName="+XBMC_MYSQL_SERVER+", XBMCVideoSchema="+XBMC_MYSQL_VIDEO_SCHEMA+", "
//                        + "MySQLUserName="+XBMC_MYSQL_UN+", MySQLPassword="+XBMC_MYSQL_PW+", MySQLPort="+XBMC_MYSQL_PORT,null);
//
//                    //test the connections
//                    if(mySQLEnabled)
//                    {
//                        DATABASE_TYPE = MYSQL;
//                        dbConfig.setDB_TYPE(DbType.MYSQL);                                                
//                        
//                    }
//                }
//                else Logger.WARN( "<XBMCMySQLServer> element not found in config file.");
//            }//end if SQLite is not enabled
//
//            if(DATABASE_TYPE == null)
//            {
//                Logger.ERROR( "Neither SQLite nor MySQL are enabled in config. Cannot continue.");
//                return false;
//            }
//            
//            
//            //init database (connects automatically)            
//
//            
//            //init the db interface
//            xbmcDb = new MyLibraryXBMCDBInterface(dbConfig);
//        
//            //test conection
//            if(!xbmcDb.isConnected())
//            {
//                Logger.ERROR("Failed to connect to XMBC Video Database. Cannot continue!");
//                return false;
//            }
//            
//        }//end database elem is not null
//        else
//        {
//            Logger.ERROR( "No <XBMCDatabase> element was found in config. Cannot continue.");
//            return false;
//        }        
        
    
        //Restart XBMC before scan
        Element restartXBMCElem = root.getChild("XBMCRestart");
        if(restartXBMCElem == null)Logger.WARN( "No <XBMCRestart> element found, defaulting to restart="+RESTART_XBMC);
        else RESTART_XBMC = "true".equalsIgnoreCase(restartXBMCElem.getAttributeValue("enabled"));
        Logger.DEBUG( "XBMCRestart enabled = "+ RESTART_XBMC);                                                


        //IP Change
        Element ipChangeElem = root.getChild("IPChange");
        if(ipChangeElem != null)            
            IP_CHANGE_ENABLED = "true".equalsIgnoreCase(ipChangeElem.getAttributeValue("enabled"));

        Logger.DEBUG( "IP Change is " + (IP_CHANGE_ENABLED ? "enabled for "+ IP_CHANGES.size()+" changes.":"disabled"));
        if(IP_CHANGE_ENABLED)
        {
            List<Element> changes = ipChangeElem.getChildren("change");
            for(Element change : changes)
            {
                String from = change.getAttributeValue("from");
                String to = change.getAttributeValue("to");
                if(!valid(from))
                {
                    Logger.WARN( "Skipping IPChange, from is not valid: \""+from+"\"");
                    continue;
                }
                if(!valid(to))
                {
                    Logger.WARN( "Skipping IPChange, to is not valid: \""+to+"\"");
                    continue;
                }
                IP_CHANGES.add(new IPChange(from, to));
                Logger.DEBUG( "Added IPChange from: "+from +" to "+ to);
            }
        }



        //Dropbox
        Element dropboxElem = root.getChild("Dropbox");
        if(dropboxElem == null)
        {
            Logger.ERROR( "<Dropbox> config not found. Cannot continue.");
            return false;
        }

        String streamingDropbox = dropboxElem.getChildText("streaming");
        if(!valid(streamingDropbox))
        {
            Logger.ERROR( "Dropbox was not found in Config.xml. Please verify your <streaming> element is filled in. Cannot contine until this is fixed.");
            return false;
        }
        if(streamingDropbox.endsWith("/") || streamingDropbox.endsWith("\\")) streamingDropbox = streamingDropbox.substring(0, streamingDropbox.length()-1);//trim trailing slash
        DROPBOX = streamingDropbox;
        //create the dropbox if it doesnt exist
        File db = new File(DROPBOX);
        if(!db.exists())db.mkdir();
        Logger.DEBUG( "Streaming Dropbox = "+ DROPBOX);

        //Linux Samba Prefix - Used to support meta-data alteration on Linux systems
        String linuxSambaPrefix = dropboxElem.getChildText("LinuxSambaPrefix");
        if (valid(linuxSambaPrefix))
        {
            if(linuxSambaPrefix.endsWith("/") || linuxSambaPrefix.endsWith("\\")) linuxSambaPrefix = linuxSambaPrefix.substring(0, linuxSambaPrefix.length()-1);//trim trailing slash
            LINUX_SAMBA_PREFIX = linuxSambaPrefix;
            Logger.DEBUG( "Using Linux Samba Prefix = "+ LINUX_SAMBA_PREFIX);
        }

        String downloadedDropbox = dropboxElem.getChildText("downloaded");
        if(!valid(downloadedDropbox))
        {
            //this is expected now that downloading support has been removed
            //Logger.ERROR( "Downloaded Dropbox was not found in Config.xml. Please verify your <downloaded> element is filled in. Cannot contine until this is fixed.");
            //return false;
        }
        else
        {
            Logger.WARN( "Downloading is not longer supported for this program. Nothing will be downloaded to: "+ downloadedDropbox);                
        }

        //TVDB api key

        OBSCURE_TVDB_KEY_IN_LOG = true;

        if(OBSCURE_TVDB_KEY_IN_LOG)
            TVDB_API_KEY_OBSCURED = (TVDB_API_KEY.length() > 10 ? ("XXXXXXXXXX"+TVDB_API_KEY.substring(10, TVDB_API_KEY.length())) : "XXXXXXXXXX");
        else
            TVDB_API_KEY_OBSCURED = TVDB_API_KEY;
        Logger.DEBUG( "Found TheTVDB Api Key: " + TVDB_API_KEY_OBSCURED);

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
                    Logger.WARN( "Cannot determine HoursThreshold for ManualArchiving, will use default of: "+ HOURS_OLD_BEFORE_MANUAL_ARCHIVE +" hours: "+x);
                }
                Logger.DEBUG( "Manual Archiving is enabled with an HoursThreshold of "+ HOURS_OLD_BEFORE_MANUAL_ARCHIVE +" hours");
            }
             else
                 Logger.DEBUG( "Manual Archiving is disabled.");
        }
        else
        {
            MANUAL_ARCHIVING_ENABLED = false;
            Logger.WARN( "<ManuarArchiving> element not found, manual archiving will be disabled.");
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
                Logger.WARN( "Cannot determine HoursThreshold for VideoCleanUp, will use default of: "+ MISSING_HOURS_DELETE_THRESHOLD +" hours: "+ x);
            }
            Logger.DEBUG( "VideoCleanUp has an HoursThreshold of "+ MISSING_HOURS_DELETE_THRESHOLD +" hours");

            try
            {
                MISSING_COUNT_DELETE_THRESHOLD = Integer.parseInt(VideoCleanUpElem.getChildText("ConsecutiveThreshold"));
            }
            catch(Exception x)
            {
                Logger.WARN( "Cannot determine ConsecutiveThreshold for VideoCleanUp, will use default of: "+ MISSING_COUNT_DELETE_THRESHOLD +" hours: "+ x);
            }
            Logger.DEBUG( "VideoCleanUp has an ConsecutiveThreshold of "+ MISSING_COUNT_DELETE_THRESHOLD +" consecutive missing times.");
        }
        else
        {              
            Logger.WARN( "<VideoCleanUp> element not found, will use defaults of "+MISSING_HOURS_DELETE_THRESHOLD+" hours and " +MISSING_COUNT_DELETE_THRESHOLD+" consecutive counts");
        }

        Element preScrapeMusicVidsElem = root.getChild("PreScrapeMusicVids");
        if(preScrapeMusicVidsElem == null)
        {
            Logger.WARN( "<PreScrapeMusicVids> element not found, pre-scraping will be disabled for music videos");
            SCRAPE_MUSIC_VIDEOS = false;
        }
        else
        {
            SCRAPE_MUSIC_VIDEOS = "true".equalsIgnoreCase(preScrapeMusicVidsElem.getAttributeValue("enabled"));
            Logger.DEBUG( "PreScrapeMusicVids enabled = "+ SCRAPE_MUSIC_VIDEOS);
        }

        //get SearchFilters
        Element searchFilters = root.getChild("SearchFilters");
        if(searchFilters == null)
        {
            Logger.ERROR( "No <SearchFilters> found, cannot continue.");
            return false;
        }

        List<Element> sources = searchFilters.getChildren();

        for(Element sourceElement : sources)
        {
            String sourceName = sourceElement.getName();                
            String sourcePath = sourceElement.getAttributeValue("path");
            if(!valid(sourcePath))
            {
                sourcePath = "";
                Logger.DEBUG( "Source path was not specified. Will try to auto-determine it later.");
            }
            else//valid path was specified
            {
                //normalize the path (must end with /)
                if(!sourcePath.endsWith("/")) sourcePath = sourcePath+"/";
            }
            Logger.INFO( "Found source "+sourceName +" with path of "+ (valid(sourcePath) ? sourcePath : "[auto-determine]"));


            Source src = new Source(sourceName, sourcePath);
            boolean inUniqueSource = addSource(src);//check for unique names
            if(!inUniqueSource) continue;//skip this source since it was not successfully added

            String customParser = sourceElement.getAttributeValue("custom_parser");
            if(valid(customParser))                                    
                src.setCustomParser(customParser);//use the specified custom parser name                
            else src.setCustomParser(src.getName());//use the name as the parser name
            Logger.INFO( "Setting source's custom_parser to: "+ src.getCustomParser());

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
                        Logger.ERROR( "No name attribute specified for subfolder, it will be skipped!");
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

                    //Download support has been removed, notify user if they still are requesting it
                    boolean download = "true".equalsIgnoreCase(inherit("download", sourceElement, subfolder));
                    if(download)
                        Logger.WARN( "Found download attribute set to true, but downloading is no longer support. Nothing will be downloaded, but it will still be streamed.");

                    boolean containsMultiPartVideos = "true".equalsIgnoreCase(inherit("multi_part", sourceElement, subfolder));
                    String type = (inherit("type", sourceElement, subfolder));
                    String strMaxSeries = (inherit("max_series", sourceElement, subfolder));
                    int max_series = isInt(strMaxSeries) ? Integer.parseInt(strMaxSeries) : -1;
                    String strMaxVideos = (inherit("max_videos", sourceElement, subfolder));
                    int max_vidoes = isInt(strMaxVideos) ? Integer.parseInt(strMaxVideos) : -1;
                    String movie_set = (inherit("movie_set", sourceElement, subfolder));
                    String strMovieTags = (inherit("movie_tags", sourceElement, subfolder));
                    String prefix = (inherit("prefix", sourceElement, subfolder));
                    String suffix = (inherit("suffix", sourceElement, subfolder));
                    int level_deep = Integer.parseInt(subfolder.getAttributeValue("level_deep"));
                    //String compression = (inherit("compression", sourceElement, subfolder));

                    //AngryCamel - 20120817 1620
                    //force_series will override any parsed series name with the value specified
                    //The reason this was developed was for TED talks. I did not spend much time 
                    // thinking about it's possible usage outside of that particular use
                    // case, but I'm sure someone will find another reason to use it.
                    String force_series = (inherit("force_series", sourceElement, subfolder));

                    Subfolder subf = new Subfolder(src, subfolderName);
                    subf.setRecursive(recursive);
                    subf.setRegexName(regexName);
                    if(valid(type)) subf.setType(type);
                    if(max_series > 0) subf.setMaxSeries(max_series);
                    if(max_vidoes > 0) subf.setMaxVideos(max_vidoes);
                    subf.setForceTVDB(forceTVDB);
                    subf.setMovieSet(movie_set);


                    //check for movie tags (split multiple with pipe)
                    if(valid(strMovieTags)){
                        List<String> movieTags = new ArrayList<String>();
                        if(strMovieTags.contains("|"))                            
                            movieTags.addAll(Arrays.asList(strMovieTags.split("\\|")));
                        else
                            movieTags.add(strMovieTags);//single tag

                        subf.setMovieTags(movieTags);
                    }


                    subf.setPrefix(prefix);
                    subf.setSuffix(suffix);
                    subf.setCanContainMultiPartVideos(containsMultiPartVideos);
                    //subf.setDownload(download);
                    subf.setLevelDeep(level_deep);
                    //subf.setCompression(compression);

                    //AngryCamel - 20120817 1620
                    subf.setForceSeries(force_series);

                    String indent = "";
                    for(int i=subf.getLevelDeep(); i>=0; i--)indent+="\t";

                    Logger.INFO( indent+"Next Subfolder: name="+subf.getFullName()+", recursive="+subf.isRecursive()
                            +", type="+subf.getType()+", max_series="+subf.getMaxSeries()+", "
                            + "max_videos="+subf.getMaxVideos()+", movie_set="+subf.getMovieSet()+", prefix="+subf.getPrefix()+", suffix="+subf.getSuffix()+
                            /*", download="+download+", compression="+(valid(compression) ? compression:"")+*/", multi_part="+containsMultiPartVideos +", force_series="+subf.getForceSeries());

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
                                Logger.DEBUG( indent+"\tAdded Exclude: type="+exclude.getName()+", value="+exclude.getText());
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
                                Logger.DEBUG( indent+"\tAdded subfolder Filter: type="+filter.getName()+", value="+filter.getText());
                            }
                        }
                    }

                    //AngryCamel - 20120815 2246
                    //Parsers override the default series and title parser in Archiver.addTVMetaData()
                    //If multiple parsers are supplied, the order that they are read
                    // from the XML is the priority order they will be processed in
                    // until one finds a match.
                    for(Element nextSubf : subfolderAndParents)
                    {
                        Element parserElem = nextSubf.getChild("parser");
                        if(parserElem != null)
                        {
                            List<Element> parsers = parserElem.getChildren();
                            for(Element parser : parsers)
                            {
                                subf.addParser(parser.getName(), parser.getText());
                                Logger.DEBUG( indent+"\tAdded subfolder Parser: type="+parser.getName()+", value="+parser.getText());
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
                Logger.INFO( "Added Global Exclude, type="+exclude.getName()+", value="+exclude.getText());
            }
        }

        Element compressionElem = root.getChild("Compression");
        if(compressionElem != null)
            Logger.WARN( "Compression definitions found, but will be ignored because downloading is no longer supported.");


        
        return true;//got to end w/o issues
    }

    public boolean addSource(Source src)
    {
        //make sure we don't have any duplicate sources
        boolean isAUniqueSource = !ALL_SOURCES.contains(src);
        if(!isAUniqueSource)
        {
            Logger.ERROR( "The is more than 1 source element named \""+src.getName()+"\" (case-insensitive). "
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
        final String archivedFilesDbLocation = BASE_DIR+SEP+"res"+SEP+"ArchivedFiles.db";                
        Logger.INFO( "Initializing SQLite database at: "+ archivedFilesDbLocation);
        
        //create the db table if it doesnt exist
        try
        {
            archivedFilesDB = new ArchivedFilesDB(archivedFilesDbLocation);
            String tableName = "ArchivedFiles";            
            archivedFilesDB.executeSingleUpdate(
                    "CREATE TABLE IF NOT EXISTS "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "                    
                    + "source_name NOT NULL, "
                    + "dropbox_location NOT NULL, "
                    + "original_path NOT NULL, "
                    + "date_archived TIMESTAMP NOT NULL, "
                    + "missing_since TIMESTAMP, "
                    + "missing_count INTEGER"
            + ")",(Object[]) null);
            archivedFilesDB.closeStatement();

            archivedFilesDB.executeSingleUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_dropbox_location ON "+tableName+" (dropbox_location)",(Object[])null);            
            
            /*additional changes since initial realease*/
            String[] varcharColumns = new String[] {"video_type", "title", "series", "artist"};//varchar columns
            for(String columnName : varcharColumns)
            {
                if(!archivedFilesDB.hasColumn(tableName, columnName))
                {
                    archivedFilesDB.executeSingleUpdate("ALTER TABLE "+ tableName +" ADD COLUMN "+columnName+" DEFAULT NULL",(Object[])null);                    
                }
            }
            
            String[] intColumns = new String[] {"episode_number", "season_number", "year", "is_tvdb_lookup"};//integer columns
            for(String columnName : intColumns)
            {
                if(!archivedFilesDB.hasColumn(tableName, columnName))
                {
                    archivedFilesDB.executeSingleUpdate("ALTER TABLE "+ tableName +" ADD COLUMN "+columnName+" INTEGER DEFAULT NULL",(Object[])null);                    
                }
            }        
        }
        catch(Exception x)
        {
            Logger.ERROR( "Error while initializing SQLite database: "+ archivedFilesDbLocation,x);
            return false;
        }
        finally
        {
            archivedFilesDB.closeStatement();
        }

        //Queued Meta Data Changes tracker database
        final String queuedMetaDataChangesLocation = BASE_DIR+SEP+"res"+SEP+"QueuedMetaDataChanges.db";
        Logger.INFO( "Initializing SQLite database at: "+ queuedMetaDataChangesLocation);        
        //create the db table if it doesnt exist
        try
        {
            queuedChangesDB = new QueuedChangesDB(queuedMetaDataChangesLocation);
            final String tableName = "QueuedChanges";                        
            //stmt.executeUpdate("drop table if exists "+tableName);
            queuedChangesDB.executeSingleUpdate("CREATE table if not exists "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "
                    + "dropbox_location NOT NULL, "
                    + "video_type NOT NULL, "
                    + "meta_data_type NOT NULL, "
                    + "value NOT NULL, "
                    + "status NOT NULL)",(Object[])null);            
            queuedChangesDB.executeSingleUpdate("CREATE UNIQUE INDEX IF NOT EXISTS unique_queued_change ON QueuedChanges (dropbox_location, meta_data_type)",(Object[])null);            
        }
        catch(Exception x)
        {
            Logger.ERROR( "Error while initializing SQLite database: "+queuedMetaDataChangesLocation,x);
            return false;
        }
        finally
        {
            queuedChangesDB.closeStatement();
        }

        //scraper query database
        final String scraperDBLocation = BASE_DIR+SEP+"res"+SEP+"scraper.db";
        Logger.INFO( "Initializing SQLite database at: "+ scraperDBLocation);        
        //create the db table if it doesnt exist
        try
        {
            scraperDB = new ScraperDB(scraperDBLocation);
            final String tableName = "APIQueries";

            //stmt.executeUpdate("drop table if exists "+tableName);
            scraperDB.executeSingleUpdate("create table if not exists "+tableName+" (id INTEGER PRIMARY KEY AUTOINCREMENT , "
                    + "api_name NOT NULL, "
                    + "query_url NOT NULL, "
                    + "query_time TIMESTAMP NOT NULL)",(Object[])null);                                    
        }
        catch(Exception x)
        {
            Logger.ERROR( "Error while initializing SQLite database: "+scraperDBLocation,x);
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
                Logger.ERROR( "A subfolder does not have a valid name attribute. Cannot continue");
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


    public static String escapePath(String path)
    {
        String escaped = path.replace(com.bradvido.xbmc.util.Constants.DELIM, "/");
        return escaped;
    }

    public static String shortLogDesc = null;
    public static void setShortLogDesc(String s)
    {
        shortLogDesc=s;
    }

    
    
    public static int SHORT_DESC_LENGTH = 16;
   
    public static void end()
    {                                
        if(queuedChangesDB != null)queuedChangesDB.close();
        if(archivedFilesDB != null)archivedFilesDB.close();
        if(scraperDB != null)scraperDB.close();      
//        if(xbmcDb != null) xbmcDb.close();
        Logger.NOTICE("Ended: "+PROGRAM_NAME+", v"+VERSION+", compatible with XBMC "+ XBMC_COMPATIBILITY);
        if(Logger != null) Logger.close();
    }
}