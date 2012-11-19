
package com.bradvido.mylibrary.util;

import com.bradvido.util.logger.BTVLogLevel;
import com.bradvido.xbmc.util.XbmcVideoLibraryFile;
import org.json.JSONArray;
import com.bradvido.xbmc.util.XbmcVideoType;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.json.JSONException;
import org.json.JSONStringer;

import com.bradvido.xbmc.db.XBMCVideoDbInterface;
import static com.bradvido.mylibrary.util.Constants.*;
import static com.bradvido.xbmc.db.XBMCVideoDbInterface.*;
import static com.bradvido.util.tools.BTVTools.*;

public class tools
{        
    
    /**
   * Create an Internet shortcut (will overwrite existing internet shortcut)
   * @param where    location of the shortcut
   * @param URL      URL
   * @throws IOException
   */
    public static StrmUpdateResult createStrmFile(File where, boolean fileExists, String URL)
    {
        if(!valid(URL))
        {
            Logger.ERROR( "Cannot create a strm file because invalid parameters are specified, file="+where +", URL="+URL);
            return StrmUpdateResult.ERROR;
        }
       
        if(Config.IP_CHANGE_ENABLED)
        {
            for(IPChange change : Config.IP_CHANGES)
            {               
                if(URL.contains(change.getFrom()))
                {
                    String newURL = URL.replace(change.getFrom(), change.getTo());
                    if(!URL.equals(newURL))
                    {
                        Logger.DEBUG( "After changing IP from \""+change.getFrom()+"\" to \""+change.getTo()+"\", newURL="+newURL);
                        URL = newURL;
                    }
                }
            }
        }
           
        //determine current url of the file by reading it
        String currentURL = null;
        if(fileExists)//is existing file
        { 
            currentURL = "";
            for(Iterator<String> it = tools.readFile(where).iterator(); it.hasNext();)
            {
                currentURL+= it.next();
                if(it.hasNext()) currentURL+=LINE_BRK;
            }
        }
        
        //only overwrite the file if it's content has changed
        if(currentURL != null && currentURL.equals(URL))
        {
            //same url, no need to update file on disk
            //Logger.DEBUG( "Not overwriting because file contents have not changed for: "+ where);
            return StrmUpdateResult.SKIPPED_NO_CHANGE;
        }
        else//different/new URL, update the file
        {
            try
            {            
                FileWriter fw = new FileWriter(where);                
                fw.write(URL);
                fw.close();//also flushes
                return StrmUpdateResult.CHANGED;
            }
            catch (Exception ex)
            {
                Logger.WARN( "Creating shortcut failed: "+ ex.getMessage(),ex);
                return StrmUpdateResult.ERROR;
            }
        }
    }         
    
    public static String spacesToDots(String s)
    {
        if(s == null) return "";
        else s = s.replaceAll(" ",".").replaceAll(",", "");
        return s;
    }
   


    //remove uncommon chars for better matching. Also, force all to lower case for case-instensitive matching
    public static String normalize(String s)
    {
        if(s == null) return "";
        String normal = "";
        for(int i=0;i<s.length();i++)
        {
            char c = s.charAt(i);
            if(c == '/') c = ' ';//replace slash with a space
            else if(Config.UNCOMMON_CHARS.get((int) c) == null)
                normal += c;
        }
       return tools.stripInvalidXMLChars(normal).trim();//strips 'extreme' special chars
    }

    public static Document getXMLFromURL(java.net.URL url)
    {
        return getXMLFromURL(url, true);//default is to use cache
    }
    public static Document getXMLFromURL(java.net.URL url, boolean useCaching)
    {
        try 
        {
            SAXBuilder builder = new SAXBuilder();
            if(useCaching)
            {
                File cachedXMLFile = getXMLFromCache(url);

                if(cachedXMLFile != null)
                {
                    try
                    {
                        Document xml = builder.build(cachedXMLFile);//read fromc cache
                        return xml;
                    }
                    catch(Exception x)
                    {
                        Logger.INFO( "Cached XML could not be parsed, reading from online source...",x);
                    }
                }
            }

            //get XML from URL and cache it when we get it
            File tempXMLFile = new File(Config.BASE_DIR + "\\res\\temp.xml");
            if(tempXMLFile.exists()) tempXMLFile.delete();
            tempXMLFile.createNewFile();
            FileWriter fstream = null;
            BufferedWriter tempXML = null;                        
            fstream = new FileWriter(tempXMLFile);
            tempXML = new BufferedWriter(fstream);
            
            URLConnection urlConn = url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));            
            while(true)
            {
                String line = in.readLine();
                if(line == null)break;                
                tempXML.write(stripInvalidXMLChars(line)+LINE_BRK);//convert to string and strip invalid chars for xml
                tempXML.flush();
            }
            in.close();
            tempXML.close();
            if(useCaching)
            {                
                cacheXML(url, tempXMLFile);//cache it for next time
            }
            Document xml = builder.build(tempXMLFile);
            //tools.printXML(xml);
            return xml;                                          
        }
        catch(Exception x)
        {
            Logger.ERROR( "Could not get valid XML data from URL: " + url,x);

            //check for Yahoo API over-load
            String stack = getStacktraceFromException(x).toLowerCase();
            if(stack.contains("server returned http response code: 999")
                && stack.contains("us.music.yahooapis.com"))
            {
                Config.SCRAPE_MUSIC_VIDEOS = false;
                Logger.WARN( "Disabling future Yahoo Music Video scraping because requests are over-limit (Response code 999).");
            }
            return null;
        }
    }

    public static String getCachedXMLFileName(URL url)
    {
        //get a safe filename for the cached file
        String cacheFileName = safeFileName(
                url.toString()
                .replace(Config.TVDB_API_KEY, Config.TVDB_API_KEY_OBSCURED)
                .replace("http://", "")
                .replace(SEP, "-")
                .replace("/", "-"));
        String parent = Config.BASE_DIR+SEP+"XMLCache"+SEP;
        String suffix =".xml";
        
        //check max length constraints
        int maxLength = MAX_FILENAME_LENGTH - (parent.length()+suffix.length());
        if(cacheFileName.length() > maxLength)
            cacheFileName = cacheFileName.substring(0,maxLength);
        
        return parent+cacheFileName+suffix;
    }
    
    private static void cacheXML(URL url, File XMLFromOnline)
    {
        File cachedFileLocation = new File(getCachedXMLFileName(url));
        if(!cachedFileLocation.getParentFile().exists()) cachedFileLocation.getParentFile().mkdir();
        if(cachedFileLocation.exists()) cachedFileLocation.delete();        
        Logger.DEBUG( "Caching XML from "+ url +" to "+ cachedFileLocation);
        try
        {
            cachedFileLocation.createNewFile();
            FileUtils.copyFile(XMLFromOnline, cachedFileLocation);
        }
        catch(Exception x)
        {
            Logger.INFO( "Failed to copy file "+ XMLFromOnline + " to "+ cachedFileLocation,x);
        }
    }
    
    private static File getXMLFromCache(URL url)
    {        
        File cachedFile = new File(getCachedXMLFileName(url));

        if(cachedFile.exists())
        {            
            Logger.DEBUG( "Using XML from cached file: \""+cachedFile+"\" for URL: "+ url);
            return cachedFile;
        }
        else
        {
            Logger.DEBUG( "No XML cache exists ("+cachedFile+") for URL, will read from online source");
            return null;
        }
    }

    public static boolean writeToFile(File f, Collection lines, boolean overWrite)
    {
        if(overWrite)
        {
            try
            {
                if(f.exists()) f.delete();
                f.createNewFile();
            }
            catch(Exception x)
            {
                Logger.WARN( "Failed to overwrite file at: "+ f,x);
                return false;
            }
        }
        for(Object o : lines)
        {
            String s = o.toString();
            if(!s.endsWith(LINE_BRK)) s+= LINE_BRK;
            if(!writeToFile(f, s, true))

                return false;
        }
        return true;
    }
    
    public static boolean writeToFile(File targetFile, String s, boolean append)
    {
        try
        {            
            FileWriter writer = new FileWriter(targetFile,append);
            writer.write(s);
            writer.close();
            return true;
        }
        catch(Exception x)
        {
            Logger.ERROR( "Cannot write to file: "+targetFile);
            return false;
        }
    }
    public static List<String> readFile(File f)
    {
        try
        {
            List<String> lines = new ArrayList<String>();
            Scanner scanner = new Scanner(f);
            while(scanner.hasNextLine())
            {
                lines.add(scanner.nextLine());
            }
            scanner.close();
            return lines;
        }
        catch(Exception x)
        {
            Logger.ERROR( "Cannot read file contents: "+ x.getMessage(),x);
            return null;
        }
    }
    
    public static Set<File> getFilesArchivedBySource(String sourceName)
    {
        Set<File> files = new LinkedHashSet<File>();
        try
        {
            String sql = "SELECT dropbox_location FROM ArchivedFiles WHERE source_name = ?";
            
            List<String> dropboxLocations= Config.archivedFilesDB.getStringList(sql, sourceName);
            for(String dropboxLocation : dropboxLocations)
            {                
                if(valid(dropboxLocation))
                    files.add(new File(dropboxLocation));
            }            
            Logger.INFO( "Found "+ files.size() +" videos that are already archived in dropbox from source \""+sourceName+"\"");
        }
        catch(Exception x)
        {
            Logger.ERROR( "Cannot get source's archived files from SQLLite: "+x,x);
        }        
        return files;
    }

   
    
    public static boolean addMetaDataChangeToDatabase(MyLibraryFile video, MetaDataType typeOfMetaData, String newValue)
    {
        Config.setShortLogDesc("MetaData");
        String dropboxLocation = video.getFinalLocation();
        String videoType = video.getType();

        //check if the change is already stored in the database
        //unique index on dropbox_location and meta_data_type
        final String checkSQL = "SELECT id FROM QueuedChanges WHERE dropbox_location = ? AND meta_data_type = ?";
                
        int id = Config.queuedChangesDB.getSingleInt(checkSQL, dropboxLocation,typeOfMetaData.toString());
        if(id == SQL_ERROR)
        {
            Logger.ERROR( "Failed to check if this video already has a meta-data-change queues for type \""+typeOfMetaData+"\": "+ dropboxLocation);
            return false;
        }

        
        String sql;
        Object[] params;
        
        //insert sql/params
        final String insertSQL = "INSERT INTO QueuedChanges(dropbox_location, video_type, meta_data_type, value, status) "
                + "VALUES(?, ?, ?, ?, ?)";        
        final Object[] insertParams = array(dropboxLocation,videoType,typeOfMetaData.toString(),newValue,QUEUED);
        
        //update sql/params
        final String updateAsQueuedSQL = "UPDATE QueuedChanges SET "//do an update instead of a insert. Update video_type and value, and set status = QUEUED
                    + "video_type = ?, value = ?, status = ? WHERE id = ?";
        final Object[] updateParams = array(videoType,newValue,QUEUED,id);
        
        if(id > -1)//if it's already in the database
        {
            //determine if the meta-data has changed
            String currrentVal = Config.queuedChangesDB.getSingleString("SELECT value FROM QueuedChanges WHERE id = ?",id);
            boolean valueChanged = currrentVal == null || !currrentVal.equals(newValue);
            if(valueChanged)
            {
                String status = Config.queuedChangesDB.getSingleString("SELECT status FROM QueuedChanges WHERE id = ?",id);
                if(QUEUED.equalsIgnoreCase(status))
                {
                    //update the queued change with the new value
                    Logger.INFO("Changing queued meta-data "+typeOfMetaData +" from \""+currrentVal+"\"  to \""+newValue+"\" for "+ videoType +" at: "+ dropboxLocation);
                    sql = updateAsQueuedSQL;//update the value
                    params = updateParams;
                }
                else if(COMPLETED.equalsIgnoreCase(status))
                {
                    Logger.INFO(typeOfMetaData+ " has changed from \""+currrentVal+"\"  to \""+newValue+"\". Queueing meta-data change for: " +dropboxLocation);
                    //Remove the meta data change from XBMC's database, to prepare for the new one                    
                    File archivedVideo = new File(dropboxLocation);
                    String xbmcPath = XBMCVideoDbInterface.getFullXBMCPath(archivedVideo);

                    XbmcVideoLibraryFile videoFile = Config.jsonRPCSender.getVideoFileDetails(xbmcPath);
                    if(videoFile == null)
                    {
                        Logger.WARN("Cannot find file in XBMC's database. Cannot update metat data for: "+ xbmcPath);
                        return false;
                    }
                    
                    int libraryId = videoFile.getLibraryId();
                    XbmcVideoType typeOfVideo = getProperVideoType(videoType);
                                       
                    if(libraryId < 0)
                    {
                        
                        Logger.INFO( "Will not update meta-data. No video exists in XBMC's library for "+getProperVideoType(videoType)+" "+ xbmcPath);
                        return true;//not really an error, so return true
                    }
                    
                    //now that we have the library id remove the old meta-data                     
                    if(MetaDataType.MOVIE_SET ==typeOfMetaData)
                    {
                        //bradvido - 11/12/2012
                        //We don't need to remove the existing movie set value because it will be REPLACED
                        //when it is updated using JSON-RPC
                    }
                    else if(MetaDataType.MOVIE_TAGS==typeOfMetaData)
                    {
                        //bradvido - 11/12/2012
                        //We don't need to remove the existing movie tags because they will be REPLACED
                        //when it is updated using JSON-RPC
                    }
                    else if(typeOfMetaData.isXFix())//prefix or suffix
                    {
                        //remove the current suffix / prefix if it exists
                        String xFixToRemove = currrentVal;
                        if(valid(xFixToRemove))
                        {
                            String existingTitle = videoFile.getTitle();
                            String desiredTitle = existingTitle;//init
                            if(MetaDataType.PREFIX == typeOfMetaData)
                            {
                                if(existingTitle.startsWith(xFixToRemove))
                                {
                                    desiredTitle = existingTitle.substring(xFixToRemove.length(), existingTitle.length());
                                    Logger.INFO( "Removing old prefix of \""+xFixToRemove+"\" from \""+existingTitle+"\" for new value of \""+desiredTitle+"\"");
                                }
                            }
                            else if(MetaDataType.SUFFIX == typeOfMetaData)
                            {
                                if(existingTitle.endsWith(xFixToRemove))
                                {
                                    desiredTitle = existingTitle.substring(0, existingTitle.indexOf(xFixToRemove));
                                    Logger.INFO( "Removing old suffix of \""+xFixToRemove+"\" from \""+existingTitle+"\" for new value of \""+desiredTitle+"\"");
                                }
                            }                            
                            
                            if(desiredTitle.equals(existingTitle))//this is OK. don't return false,here, continue queueing the new change
                                Logger.INFO( "The exsiting "+typeOfMetaData+" need not be removed because it was not found. \""+xFixToRemove+"\" not found in \""+existingTitle+"\"");
                            else
                            {
                                //do the update in XBMC
                                boolean updated = Config.jsonRPCSender.setTitle(typeOfVideo, libraryId, toAscii(desiredTitle));//ascii'ing this to avoid problem with json-rpc rejecting special characters TODO: figure out charset problems
                                if(!updated) 
                                    Logger.ERROR( "Failed to remove old prefix/suffix. Will not update meta-data for video: "+ xbmcPath);
                            }
                        }
                        //else no xFix found
                    }                    

                    //Update sqlite db with the new value and status of QUEUED
                    sql = updateAsQueuedSQL;
                    params = updateParams;
                }
                else//unknown status
                {
                    Logger.WARN( "Unknown status in QueuedChanged table: \""+status+"\". Will not update meta-data for: "+ dropboxLocation);
                    return false;
                }                                
            }
            else//no changes in the meta data, do nothing
            {
                Logger.DEBUG( "Meta-data has not changed for this video. Not updating. type="+typeOfMetaData+", value="+newValue+", file="+dropboxLocation);
                return true;//true because this is not a problem.
            }
        }
        else//it's not in the database yet, insert new record
        {
            Logger.log(valid(newValue) ? BTVLogLevel.INFO : BTVLogLevel.DEBUG, "Queueing new meta-data change: type="+typeOfMetaData+", value="+newValue+", file="+dropboxLocation,null);
            sql = insertSQL;
            params = insertParams;
        }
        
        return Config.queuedChangesDB.executeSingleUpdate(sql, params);
    }    

    

    /*
     Returns the full file path with the ".ext" chopped off
     */
    public static String fileNameNoExt(File f)
    {
        String path = f.getPath();        
        if(!f.getName().contains("."))
        {//no extension!
            Logger.ERROR( "This files does not have an extension: " + path);
            return null;
        }
        int dotIndx = path.lastIndexOf(".");
        return path.substring(0, dotIndx);
    }
    
  
    
    public static boolean trackArchivedFile(String sourceName, String dropboxLocation, MyLibraryFile video)
    {
        if(!dropboxLocation.endsWith(".strm"))
            Logger.ERROR( "File being archived is not a .strm: "+ dropboxLocation);
        
        ArchivedFile currentlyArchivedFile = Config.archivedFilesDB.getArchivedFileByLocation(dropboxLocation);
        
        boolean updating = currentlyArchivedFile != null;//this dropbox_location already exists if it has a valid id, update the entry if needed
        String sql;
        if(updating)
        {
            //determine if data has changed and update record if so
            boolean changed =//if any values are not already what they will be set to, update with the new values. We already know dropbox_location is the same.
                       !sourceName.equals(currentlyArchivedFile.sourceName)//source name is different
                    || !video.getFullPath().equals(currentlyArchivedFile.originalPath)//original path is different
                    || currentlyArchivedFile.missingSince != null//missing since is set
                    || currentlyArchivedFile.missingCount != 0;//missing count is started

            if(!changed)
            {
                //the basic's haven't changed, check if metadata has  changed
                changed = !video.getType().equals(currentlyArchivedFile.videoType) 
                        || !video.getTitle().equals(currentlyArchivedFile.title) 
                        || video.hasBeenLookedUpOnTVDB() != currentlyArchivedFile.isTvDbLookup;
                
                if(!changed)
                {//check type-specific fields
                    if(video.isTvShow())
                        changed = !video.getSeries().equals(currentlyArchivedFile.series) 
                                || video.getSeasonNumber() != currentlyArchivedFile.seasonNumber 
                                || video.getEpisodeNumber() != currentlyArchivedFile.episodeNumber;
                    else if(video.isMovie())
                        changed = video.getYear() != currentlyArchivedFile.year;
                    else if(video.isMusicVideo())
                        changed = !video.getArtist().equals(currentlyArchivedFile.artist);
                	//AngryCamel - 20120817 1620 - Added generic
                    else if(video.isGeneric())
                        changed = !video.getSeries().equals(currentlyArchivedFile.series)
                        		|| !video.getTitle().equals(currentlyArchivedFile.title);
                }                    
            }
            if(!changed)
            {
                Logger.DEBUG( "Nothing has changed for this video, no need to update tracker database for: "+ dropboxLocation);
                return true;
            }
            else//fields changed, update with the new values
            {
                Logger.DEBUG( "Changes occurred for this video. updating database: "+ dropboxLocation);
                //if any of the values have changed, update the entry to catch any changed values (also reverts any missing_since or missing_count since this file is no longer missing)
                sql = "UPDATE ArchivedFiles SET source_name = ?, dropbox_location = ?, "
                        + "original_path = ?, missing_since = ?, missing_count = ?, date_archived = ?,"
                        + "video_type = ?, title = ?, series = ?, artist = ?, episode_number = ?, season_number = ?, year = ?, is_tvdb_lookup = ? "
                        + "WHERE id = ?";//extra param for updates is current id of file
            }
        }
        else//new entry, INSERT
        {            
            sql = "INSERT INTO ArchivedFiles(source_name, dropbox_location, original_path, missing_since, missing_count, date_archived, "
                    + "video_type, title, series, artist, episode_number, season_number, year, is_tvdb_lookup) "
                    + "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        }

        //update the database
        PreparedStatement prep = null;
        try
        {
            prep = Config.archivedFilesDB.getStatement(sql);
            prep.setString(1, sourceName);//source_name
            prep.setString(2, dropboxLocation);//dropbox_location
            prep.setString(3, video.getFullPath());//original_path            
            prep.setTimestamp(4, null);//missing_since
            prep.setInt(5, 0);//missing_count
            prep.setTimestamp(6, new java.sql.Timestamp(System.currentTimeMillis()));//date_archived
            prep.setString(7, video.getType());//video_type
            prep.setString(8, video.getTitle());//title
            prep.setString(9, video.getSeries());//series
            prep.setString(10, video.getArtist());//artist
            prep.setInt(11, video.getEpisodeNumber());//episode_number
            prep.setInt(12, video.getSeasonNumber());//season_number
            prep.setInt(13, video.getYear());//year
            prep.setInt(14, video.hasBeenLookedUpOnTVDB() ? 1 : 0);//is_tvdb_lookup
            if(currentlyArchivedFile != null) prep.setInt(15, currentlyArchivedFile.id);//updating based on id
            
            int updateCount = prep.executeUpdate();            
            if(updateCount == 1)
            {
                Logger.DEBUG( "Successfully "+(updating ? "updated":"added")+" file in ArchivedFiles tracking table from source "+ sourceName+": "+Config.escapePath(video.getFullPath())+": "+ dropboxLocation);
                return true;
            }
            else throw new Exception(updateCount +" rows were updated (expected 1).");
        }
        catch(Exception x)
        {
            Logger.ERROR( "Failed to add archived file \""+dropboxLocation+"\" to SQLite Database: "+ x,x);
            return false;
        }
        finally
        {
            Config.archivedFilesDB.closeStatement();
        }
    }

    /*
     * Update's videos missing_since and missing_count attributes
     * returns trus if should delete
     */
    public static boolean markVideoAsMissing(String path)
    {
        ArchivedFile archivedFile = Config.archivedFilesDB.getArchivedFileByLocation(path);                
        if(archivedFile == null)
        {
            Logger.WARN( "This file was not found in the ArchivedFiles database, cannot mark it as missing. Will set this file to be deleted.");
            return true;
        }
        
        
        Long missingSince = archivedFile.missingSince;        
        int missingCount = archivedFile.missingCount;
        if(missingCount <0) missingCount = 0;
       
        //update the counts
        missingCount++;
        long now = System.currentTimeMillis();
        if(missingSince == null) missingSince = now;
        
        String updateSQL = "UPDATE ArchivedFiles SET missing_count = ?, missing_since = ? WHERE id = ?";
        try
        {
            PreparedStatement prep = Config.archivedFilesDB.getStatement(updateSQL);
            prep.setInt(1, missingCount);
            prep.setTimestamp(2, new java.sql.Timestamp(missingSince.longValue()));
            prep.setInt(3, archivedFile.id);
            prep.execute();            
        }
        catch(Exception x)
        {
            Logger.WARN( "Failed to update file as missing using: "+ updateSQL,x);
        }
        finally
        {
            Config.archivedFilesDB.closeStatement();
        }


        //check against thresholds
        long missingForSeconds = (now - missingSince.longValue()) / 1000;
        double missingForHours = missingForSeconds / 60.0 / 60.0;
        if(missingForHours >= Config.MISSING_HOURS_DELETE_THRESHOLD && missingCount >= Config.MISSING_COUNT_DELETE_THRESHOLD)
        {
            Logger.INFO( "This video should be deleted because it has been missing for "+ toTwoDecimals(missingForHours) +" hours (threshold is "+Config.MISSING_HOURS_DELETE_THRESHOLD+"), "
                  + "and has been missing the past "+ missingCount +" times this program has checked for it (threshold is "+ Config.MISSING_COUNT_DELETE_THRESHOLD+")");
            return true;
        }
        else
        {
            String reason = "This file will not yet be deleted because: ";
            if(missingForHours < Config.MISSING_HOURS_DELETE_THRESHOLD)
                reason += "It has only been missing for "+ toTwoDecimals(missingForHours) +" hours (less than threshold of "+ Config.MISSING_HOURS_DELETE_THRESHOLD+"). ";
            if(missingCount < Config.MISSING_COUNT_DELETE_THRESHOLD)
                reason += "It has been missing the past "+ missingCount +" times this program has checked, it must be missing for at least "+ Config.MISSING_COUNT_DELETE_THRESHOLD +" times before it is deleted.";
            Logger.INFO(  reason);
            return false;
        }
    }
      /**
     * This method ensures that the output String has only
     * valid XML unicode characters as specified by the
     * XML 1.0 standard. For reference, please see
     * <a href="http://www.w3.org/TR/2000/REC-xml-20001006#NT-Char">the
     * standard</a>. This method will return an empty
     * String if the input is null or empty.
     *
     * @param in The String whose non-valid characters we want to remove.
     * @return The in String, stripped of non-valid characters.
     */
    public static String stripInvalidXMLChars(String in) {
        StringBuilder validChars = new StringBuilder(); // Used to hold the output.
        StringBuilder inValidChars = new StringBuilder(); // Used to hold the output.        

        if(!valid(in)) return ""; // vacancy test.
        for (int i = 0; i < in.length(); i++)
        {
            char current = in.charAt(i);
            int c = (int) current;
            /*
            if ((current == 0x9) ||
                (current == 0xA) ||
                (current == 0xD) ||
                ((current >= 0x20) && (current <= 0xD7FF)) ||
                ((current >= 0xE000) && (current <= 0xFFFD)) ||
                ((current >= 0x10000) && (current <= 0x10FFFF)))
             *
             */
            if( (c >= 32 && c <= 126) || c==10 || c==13) //from unicode ' ' to '~' and \r \n
                validChars.append(current);
            else            
                inValidChars.append(current);           
        }
        //Logger.DEBUG( "Removed these invalid XML characters: " + inValidChars);
        return validChars.toString().trim();
    }

    public static String toTVDBAiredDate(Date dt)
    {
        try
        {
            return Config.tvdbFirstAiredSDF.format(dt);
        }
        catch(Exception x)
        {
            Logger.ERROR( "Cannot format date as TVDB style using "+Config.tvdbFirstAiredSDF.toPattern() +": "+ dt);
            return null;
        }
    }
   
    
    public static String cleanCommonWords(String s)
    {
        if(!valid(s)) return s;
        String[] articles = new String[]{"the","a", "an", "The", "A", "An", "part", "Part"};
        for(String article : articles)
        {
            // it is a word if it has a space around it
            s = s.replace(" "+article+" ", " ");
            s = s.replace(" " +article, " ");
            s = s.replace(article+" ", " ");
            s = s.replace("  ", " ");//catch any double spaces
        }
        return s;
    }
    public static String cleanParenthesis(String s)
    {
        Pattern p = Pattern.compile("[\\(].*[\\)]");///catch any characters inside of (...)
        Matcher m = p.matcher(s);
        while(m.find())
        {
            s = s.replace(m.group(), "");
        }
        return s;
    }    
    
    public static String stripExtraLabels(String source)
    {
        //strip (HD) labeling (commong from Hulu plugin)
        if(source.toUpperCase().contains(" (HD)"))
            source = source.replace(" (HD)", "").replace(" (hd)", "");//remove hd labeling if it exists
        
        if(source.toUpperCase().contains(" [HD]"))
            source = source.replace(" [HD]", "").replace(" [hd]", "");//remove hd labeling if it exists
        
        return source;
    }
    
    public static boolean fuzzyTitleMatch(String source, String test, int percentDiscrepencyAllowed)
    {
        //clean out articles and anything in parenthesis
        source = cleanCommonWords(cleanParenthesis(source.toLowerCase()));
        test = cleanCommonWords(cleanParenthesis(test.toLowerCase()));
        if(percentDiscrepencyAllowed > 100) percentDiscrepencyAllowed = 100;
        int fuzzyMatchMaxDifferent = source.length() / (100/percentDiscrepencyAllowed);//allow x% discrepency
        if(fuzzyMatchMaxDifferent <= 0) fuzzyMatchMaxDifferent = 1;//allow at least 1 char diff
        int difference = getLevenshteinDistance(source, test);
        return difference <= fuzzyMatchMaxDifferent;
    }
   
    public static boolean verifyIpRange(String range)
    {
        try
        {
            String[] ips = range.split("-");//looks like "192.168.88.1-192.168.88.255"
            for(String ipAddress : ips)
            {
                String[] octets = ipAddress.split("\\.");

                if ( octets.length != 4 )
                {
                    return false;
                }

                for ( String s : octets )
                {
                    int i = Integer.parseInt( s );

                    if ( (i < 0) || (i > 255) )
                    {
                        return false;
                    }
                }                
            }
            return true;
        }
        catch(Exception x)
        {
            Logger.WARN( "IP Range \""+range+"\" is not valid: "+ x);
            return false;
        }
    }
 
    /*
     * case-insensitive regex match
     */
    public static boolean regexMatch(String regex, String test)
    {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(test);
        return m.find();
    }
    

    public static String toTwoDecimals(double d)
    {
	return toFixedDecimal(d,2);
    }           

     public static <T extends Document> void printXML(T t)
    {
        try
        {
            XMLOutputter out = new XMLOutputter(Format.getPrettyFormat());
            out.output(t, System.out);
        }
        catch(IOException iox)
        {
             System.out.println("Failed to print xml: "+iox);
        }

    }    

    public static MyLibraryFile getVideoFromOriginalLocation(String originalLocationUnescaped)
    {
        String sql = "SELECT original_path, dropbox_location, video_type, title, series, artist, episode_number, season_number, year, is_tvdb_lookup "
                + "FROM ArchivedFiles "
                + "WHERE original_path = ?";
      return Config.archivedFilesDB.getVideoWithMetaDataFromDB(sql,originalLocationUnescaped);
    }

    public static MyLibraryFile getVideoFromDropboxLocation(File f)
    {
        String sql = "SELECT original_path, dropbox_location, video_type, title, series, artist, episode_number, season_number, year, is_tvdb_lookup "
                + "FROM ArchivedFiles "
                + "WHERE dropbox_location = ?";
        return Config.archivedFilesDB.getVideoWithMetaDataFromDB(sql,f.getPath());        
    }
           
    public static String cleanJSONLabel(String jsonString)
    {
        if(valid(jsonString))
        {
            jsonString = jsonString.replace("&#39;", "'");//json-rpc and json library don't handle single quotes properly. catch this here
            jsonString = jsonString.replace("/", "");//TODO: fix thing when slash's can be escaped
        }
        return jsonString;
    }
               
    
    public static boolean deleteStrmAndMetaFiles(File strmFile)
    {
        if(strmFile.exists())
        {                    
            //make sure it's really a .strm
            if(strmFile.getPath().toLowerCase().endsWith(".strm"))
            {
                String nameNoExt = tools.fileNameNoExt(strmFile);
                boolean deleted = strmFile.delete();
                if(!deleted)
                    Logger.WARN( "Failed to delete .strm file: "+ strmFile);
                else//successfully deleted
                {
                    String[] metaExts = new String[]{".nfo",".tbn","-fanart.jpg"};
                    //silently try to delete meta files
                    for(String ext : metaExts)
                    {
                        String path = nameNoExt+ext;
                        try
                        {
                            File metaFile = new File(path);
                            if(metaFile.isFile())
                            {
                                if(metaFile.delete())
                                    Logger.DEBUG( "Deleted "+ metaFile.getName());                                
                            }
                        }catch(Exception ignored){}
                    }
                }
                return deleted;//true if strm was deleted
            }
            else
            {
                Logger.WARN( "Not deleting file because it does not have a .strm extension: "+ strmFile.getPath());
                return false;
            }
        }
        else
        {
            Logger.INFO( "Not deleting file because it does not exist on disk: "+ strmFile);
            return false;
        }
    }
        
    
    public static XbmcVideoType getProperVideoType(String strType){
        //map string to proper type
        XbmcVideoType properType = null;
        
        if(Constants.TV_SHOW.equals(strType))
            properType = XbmcVideoType.TV_SHOW;
        else if(Constants.MOVIE.equals(strType))
            properType = XbmcVideoType.MOVIE;
        else if(Constants.MUSIC_VIDEO.equals(strType))
            properType = XbmcVideoType.MUSIC_VIDEO;
        
        return properType;
    }
    
   
}