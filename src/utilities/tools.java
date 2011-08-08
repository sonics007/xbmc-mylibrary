
package utilities;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.io.FileUtils;
import org.jdom.Document;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;
import org.json.JSONException;
import org.json.JSONStringer;


public class tools implements Constants
{        
    
    /**
   * Create an Internet shortcut (will overwrite existing internet shortcut)
   * @param where    location of the shortcut
   * @param URL      URL
   * @throws IOException
   */
    public static boolean  createInternetShortcut(File where, String URL)
    {
        if(!valid(URL))
        {
            Config.log(ERROR, "Cannot create a URL because invalid parameters are specified, file="+where +", URL="+URL);
            return false;
        }
       
        if(Config.IP_CHANGE_ENABLED)
        {
            for(IPChange change : Config.IP_CHANGES)
            {                        
                URL = URL.replace(change.getFrom(), change.getTo());
                Config.log(DEBUG, "After changing IP from \""+change.getFrom()+"\" to \""+change.getTo()+"\", URL="+URL);
            }
        }
                
        try
        {            
            FileWriter fw = new FileWriter(where);
            //fw.write("[InternetShortcut]"+LINE_BRK);
            //fw.write("URL=" + URL);
            fw.write(URL);
            fw.close();
            return true;
        }
        catch (Exception ex)
        {
            Config.log(INFO, "Creating shortcut failed: "+ ex.getMessage(),ex);
            return false;
        }
    }

    public static String getInternetShortcutURL(File shortcut)
    {
        String URL = null;
        Scanner s;
        try
        {
            s = new Scanner(shortcut);
        }
        catch (FileNotFoundException ex)
        {
            Config.log(ERROR, "Cannot get URL from shortcut because shortcut is not available at: "+ shortcut,ex);
            return null;
        }

        while (s.hasNextLine())
        {
            String line = s.next();
            if(valid(line))
            {
                line = line.trim();
                if(line.toUpperCase().startsWith("URL="))
                {
                    URL = line.substring("URL=".length(), line.length());
                    break;
                }
            }
        }
        s.close();
        return URL;
    }

    public static String jsonKeyValue(String key, Object value)
    {
        try{
            String json = new JSONStringer().object().key(key).value(value).endObject().toString();
            return json.substring(1, json.length()-1);//trim off the surrounding { }
        } catch (JSONException ex) {
            Config.log(ERROR, "Cannot create JSON key value pair from key:"+key+", value:"+value,ex);
            return "";
        }

    }
    
    public static boolean valid(String s)
    {
        return s != null && !s.trim().isEmpty();
    }

    
    public static String spacesToDots(String s)
    {
        if(s == null) return "";
        else s = s.replaceAll(" ",".").replaceAll(",", "");
        return s;
    }
    public static String safeFileName(String s)
    {
        if(!valid(s)) return "";
        
        String normal = "";
        for(int i=0;i<s.length();i++)
        {
            char c = s.charAt(i);
            if(c == '/') c = ' ';//replace slash with a space
            else if(Config.ILLEGAL_FILENAME_CHARS.get((int) c) == null)
                normal += c;
        }
       return stripInvalidXMLChars(normal.trim());
    }
    public static boolean isInt(String s)
    {
        try
        {
            Integer.parseInt(s);
            return true;
        }
        catch(Exception x){return false;}

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
                        Config.log(INFO, "Cached XML could not be parsed, reading from online source...",x);
                    }
                }
            }

            //get XML from URL and cache it when we get it
            File tempXMLFile = new File(Config.BASE_PROGRAM_DIR + "\\res\\temp.xml");
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
            Config.log(ERROR, "Could not get valid XML data from URL: " + url,x);

            //check for Yahoo API over-load
            String stack = getStacktraceAsString(x).toLowerCase();
            if(stack.contains("server returned http response code: 999")
                && stack.contains("us.music.yahooapis.com"))
            {
                Config.SCRAPE_MUSIC_VIDEOS = false;
                Config.log(WARNING, "Disabling future Yahoo Music Video scraping because requests are over-limit (Response code 999).");
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
        String parent = Config.BASE_PROGRAM_DIR+SEP+"XMLCache"+SEP;
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
        Config.log(DEBUG, "Caching XML from "+ url +" to "+ cachedFileLocation);
        try
        {
            cachedFileLocation.createNewFile();
            FileUtils.copyFile(XMLFromOnline, cachedFileLocation);
        }
        catch(Exception x)
        {
            Config.log(INFO, "Failed to copy file "+ XMLFromOnline + " to "+ cachedFileLocation,x);
        }
    }
    
    private static File getXMLFromCache(URL url)
    {        
        File cachedFile = new File(getCachedXMLFileName(url));

        if(cachedFile.exists())
        {            
            Config.log(DEBUG, "Using XML from cached file: \""+cachedFile+"\" for URL: "+ url);
            return cachedFile;
        }
        else
        {
            Config.log(DEBUG, "No XML cache exists ("+cachedFile+") for URL, will read from online source");
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
                Config.log(WARNING, "Failed to overwrite file at: "+ f,x);
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
            Config.log(ERROR, "Cannot write to file: "+targetFile);
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
            Config.log(ERROR, "Cannot read file contents: "+ x.getMessage(),x);
            return null;
        }
    }
    
    public static Set<File> getFilesArchivedBySource(String sourceName)
    {
        Set<File> files = new LinkedHashSet<File>();
        try
        {
            String sql = "SELECT dropbox_location FROM ArchivedFiles WHERE source_name = "+ sqlString(sourceName);
            
            ResultSet rs = Config.archivedFilesDB.getStatement().executeQuery(sql);
            while(rs.next())
            {
                String dropboxLocation = rs.getString("dropbox_location");
                if(valid(dropboxLocation))
                    files.add(new File(dropboxLocation));
            }
            rs.close();            
            Config.log(INFO, "Found "+ files.size() +" videos that are already archived in dropbox from source \""+sourceName+"\"");
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Cannot get source's archived files from SQLLite: "+x,x);
        }
        finally
        {
            Config.archivedFilesDB.closeStatement();
        }
        return files;
    }

   
    
    public static boolean addMetaDataChangeToDatabase(XBMCFile video, String typeOfMetaData, String newValue)
    {

        String dropboxLocation = convertToStrm(video.getFinalLocation());//always stored as a .strm in the db
        String videoType = video.getType();

        //check if the changes  is already stored in the database
        //unique index on dropbox_location and meta_data_type
        String checkSQL = "SELECT id FROM QueuedChanges WHERE dropbox_location = "+sqlString(dropboxLocation)+" AND meta_data_type = "+ sqlString(typeOfMetaData);
        int id = Config.queuedChangesDB.getSingleInt(checkSQL);
        if(id == SQL_ERROR)
        {
            Config.log(ERROR, "Failed to check if this video already has a meta-data-change queues for type \""+typeOfMetaData+"\": "+ dropboxLocation);
            return false;
        }

        String sql;
        final String insertSQL = "INSERT INTO QueuedChanges(dropbox_location, video_type, meta_data_type, value, status) "
                + "VALUES("+sqlString(dropboxLocation)+", "+sqlString(videoType)+", "+sqlString(typeOfMetaData)+", "+ sqlString(newValue)+", "+sqlString(QUEUED)+")";
        final String updateAsQueuedSQL = "UPDATE QueuedChanges SET "//do an update instead of a insert. Update video_type and value, and set status = QUEUED
                    + "video_type = "+sqlString(videoType)+", value = "+ sqlString(newValue)+", status = "+sqlString(QUEUED)+" WHERE id = " + id;

        if(id > -1)//if it's already in the database
        {
            String currrentVal = Config.queuedChangesDB.getSingleString("SELECT value FROM QueuedChanges WHERE id = "+ id);
            boolean valueChanged = currrentVal == null || !currrentVal.equals(newValue);
            if(valueChanged)
            {
                String status = Config.queuedChangesDB.getSingleString("SELECT status FROM QueuedChanges WHERE id = "+ id);
                if(QUEUED.equalsIgnoreCase(status))
                {
                    //update the queued change with the new value
                    Config.log(INFO, "Changing queued meta-data "+typeOfMetaData +" from \""+currrentVal+"\"  to \""+newValue+"\" for "+ videoType +" at: "+ dropboxLocation);
                    sql = updateAsQueuedSQL;//update the value
                }
                else if(COMPLETED.equalsIgnoreCase(status))
                {
                    Config.log(INFO,"Meta-data has changed. Will remove old meta-data and queue new meta-data for: " +dropboxLocation);
                    //Remove the meta data change from XBMC's database, to prepare for the new one
                    XBMCInterface xbmc = new XBMCInterface(Config.DATABASE_TYPE, (Config.DATABASE_TYPE.equals(MYSQL) ? Config.XBMC_MYSQL_VIDEO_SCHEMA : Config.sqlLiteVideoDBPath));
                    File archivedVideo = new File(dropboxLocation);
                    String xbmcPath = XBMCInterface.getFullXBMCPath(archivedVideo.getParentFile());//the path (not including file)

                    //check if it is in the database
                    String fileIdSQL = "SELECT idFile "
                        + "FROM files "
                        + "WHERE idPath IN(SELECT idPath FROM path WHERE strPath = "+tools.sqlString(xbmcPath)+") "
                        + "AND strFileName = "+tools.sqlString(archivedVideo.getName());
                    int file_id = xbmc.getDB().getSingleInt(fileIdSQL);
                    if(file_id < 0)
                    {
                        if(file_id == SQL_ERROR) Config.log(WARNING, "Cannot update meta-data. Failed to determine idFile using: "+ fileIdSQL);
                        else Config.log(INFO, "Will not update meta-data. No file exists in XBMC's database as determined by: "+ fileIdSQL);
                        return true;//not an error, so return true
                    }
                    int video_id = xbmc.getVideoId(videoType, file_id);
                    if(video_id < 0)
                    {
                        if(file_id == SQL_ERROR) Config.log(WARNING, "Cannot update meta-data. Failed to determine video id");
                        else Config.log(INFO, "Will not update meta-data. No video exists in XBMC's database with idFile = "+ file_id);
                        return true;//not really an error, so return true
                    }
                    
                    //now remove the old meta-data                     
                    if(MOVIE_SET.equalsIgnoreCase(typeOfMetaData))
                    {
                        if(valid(currrentVal))
                        {
                            //remove the movie from the movie set
                            String  removeMetadataSql = "DELETE FROM setlinkmovie "
                                    + "WHERE idSet = (SELECT idSet FROM sets where strSet = "+tools.sqlString(currrentVal)+") "
                                    + "AND idMovie = "+video_id;
                            int rowsUpdated = xbmc.getDB().executeMultipleUpdate(removeMetadataSql);
                            if(rowsUpdated != SQL_ERROR)
                            {
                                //success, even if rows updated is zero, it just means that the movie no longer exists in the set. Ok for new meta-data
                                Config.log(INFO, "Successfully removed movie from old set \""+currrentVal+"\" in preperation for adding to new set named \""+newValue+"\"");
                            }
                            else//sql error
                            {
                                Config.log(ERROR, "Cannot update meta-data. Failed to remove movie from old set named \""+currrentVal+"\" using "+ removeMetadataSql);
                                return false;
                            }
                        }
                    }
                    else if(PREFIX.equalsIgnoreCase(typeOfMetaData) || SUFFIX.equalsIgnoreCase(typeOfMetaData))
                    {
                        //remove the current suffix / prefix if it exists
                        String xFixToRemove = currrentVal;
                        if(valid(xFixToRemove))
                        {
                            String idField, field, table;
                            if(videoType.equals(MOVIE))
                            {
                                field ="c00";
                                table = "movie";
                                idField = "idMovie";
                            }
                            else if(videoType.equals(TV_SHOW))
                            {
                                field = "c00";
                                table = "episode";
                                idField = "idEpisode";
                            }
                            else if(videoType.equals(MUSIC_VIDEO))
                            {
                                field = "c00";
                                table = "musicvideo";
                                idField = "idMVideo";
                            }
                            else
                            {
                                Config.log(WARNING,"Unknown video type: \""+ videoType+"\", will not update meta data");
                                return false;
                            }
                            String getCurrentValue = "SELECT " +field +" FROM "+ table +" WHERE "+ idField+" = "+ video_id;
                            String dbValue = xbmc.getDB().getSingleString(getCurrentValue);
                            String newDBValue = dbValue;
                            if(PREFIX.equalsIgnoreCase(typeOfMetaData))
                            {
                                if(dbValue.startsWith(xFixToRemove))
                                {
                                    newDBValue = dbValue.substring(xFixToRemove.length(), dbValue.length());
                                    Config.log(INFO, "Removing old prefix of \""+xFixToRemove+"\" from \""+dbValue+"\" for new value of \""+newDBValue+"\"");
                                }
                            }
                            else if(SUFFIX.equalsIgnoreCase(typeOfMetaData))
                            {
                                if(dbValue.endsWith(xFixToRemove))
                                {
                                    newDBValue = dbValue.substring(0, dbValue.indexOf(xFixToRemove));
                                    Config.log(INFO, "Removing old suffix of \""+xFixToRemove+"\" from \""+dbValue+"\" for new value of \""+newDBValue+"\"");
                                }
                            }
                            if(newDBValue.equals(dbValue))//this is OK. don't return false,here, continue queueing the new change
                                Config.log(WARNING, "The old suffix/prefix was not removed because it was not found. \""+xFixToRemove+"\" not found in \""+dbValue+"\"");
                            else
                            {
                                //do the update in XBMC
                                String removeXFixSQL = "UPDATE " + table +" SET " + field +" = "+ sqlString(newDBValue)+" WHERE " + idField +" = "+video_id;
                                boolean updated = xbmc.getDB().executeSingleUpdate(removeXFixSQL);
                                if(!updated) Config.log(ERROR, "Failed to remove old prefix/suffix. Will not update meta-data. Sql = "+ removeXFixSQL);
                            }
                        }
                    }
                    xbmc.close();

                    //Update sqlite db with the new value and status of QUEUED
                    sql = updateAsQueuedSQL;
                }
                else//unknown status
                {
                    Config.log(WARNING, "Unknown status in QueuedChanged table: \""+status+"\". Will not update meta-data for: "+ dropboxLocation);
                    return false;
                }                                
            }
            else//no changes in the meta data, do nothing
            {
                Config.log(DEBUG, "Meta-data has not changed for this video. Not updating. type="+typeOfMetaData+", value="+newValue+", file="+dropboxLocation);
                return true;//true because this is not a problem.
            }
        }
        else//it's not in the database yet, insert new record
        {
            Config.log(valid(newValue) ? INFO : DEBUG, "Queueing new meta-data change: type="+typeOfMetaData+", value="+newValue+", file="+dropboxLocation);
            sql = insertSQL;
        }
        return Config.queuedChangesDB.executeSingleUpdate(sql);
    }

    public static void comskipVideos(List<File> videos)
    {
        Config.log(NOTICE,"Comskip is enabled for downloading videos. Starting comskip processing for this video now.");
        for(File archivedDownloadedFile : videos)
        {
            File edl = new File(fileNameNoExt(archivedDownloadedFile)+".edl");
            if(edl.exists() && edl.isFile())
            {
                Config.log(INFO,"Skipping comskip for this video because an edl already exists: "+ edl);
                continue;
            }
            String cmd = "\""+Config.BASE_PROGRAM_DIR+SEP+"res"+SEP+"comskip"+SEP+"comskip.exe\" \""+archivedDownloadedFile+"\"";
            Config.log(INFO, "Comskip command = "+ cmd);
            try
            {
                Runtime.getRuntime().exec(cmd);
                Config.log(INFO, "Comskip process started...");
            }
            catch(Exception x)
            {
                Config.log(ERROR, "Failed to execute Comskip for the downloaded file at: "+ archivedDownloadedFile +" using cmd: "+ cmd,x);
                continue;
            }
        }
    }
    public static List<QueuedChange> getQueuedChanges()
    {
        List<QueuedChange> changes = new ArrayList<QueuedChange>();
        String sql = "SELECT id, dropbox_location, video_type, meta_data_type, value FROM QueuedChanges WHERE status = "+sqlString(QUEUED);
        try
        {
            
            ResultSet rs = Config.queuedChangesDB.getStatement().executeQuery(sql);
            while(rs.next())
            {
                changes.add(new QueuedChange(rs.getInt("id"), rs.getString("dropbox_location"), rs.getString("video_type"), rs.getString("meta_data_type"), rs.getString("value")));
            }
            rs.close();             
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to get queued meta data changed from SQLite DB: "+x,x);
        }
        finally
        {
            Config.queuedChangesDB.closeStatement();
        }
        return changes;
    }

    public static Long getDateArchived(String archivedFile)
    {               
        String sql = "SELECT date_archived FROM ArchivedFiles WHERE dropbox_location = "+sqlString(convertToStrm(archivedFile));
        Long dateArchived = Config.archivedFilesDB.getSingleTimestamp(sql);

        if(dateArchived != null)
        {
            Config.log(DEBUG, "Found date archived: "+ new Date(dateArchived)+" for archived file: "+ archivedFile);
            return dateArchived;
        }
        else
        {
            File f = new File(archivedFile);
            if(f.exists())
            {
                Config.log(WARNING, "Could not get date archived for file \""+archivedFile+"\". Will use last modified date of "+ new Date(f.lastModified()));
                return f.lastModified();
            }
            else
            {
                Config.log(ERROR, "Cannot find date archive for file \""+archivedFile+"\" and the file no longer exits.");
                return null;
            }
        }
    }

    /*
     Returns the full file path with the ".ext" chopped off
     */
    public static String fileNameNoExt(File f)
    {
        String path = f.getPath();
        
        if(!f.getName().contains("."))
        {//no extension!
            Config.log(ERROR, "This files does not have an extension: " + path);
            return null;
        }
        int dotIndx = path.lastIndexOf(".");
        return path.substring(0, dotIndx);
    }
    
    public static String convertToStrm(String fullPath)
    {         
        if(!fullPath.endsWith(".strm"))
        {
            String pathNoExt = fullPath.substring(0,fullPath.lastIndexOf("."));
            fullPath = pathNoExt + ".strm";
        }
        return fullPath;
    }

    /*
     * Returns a list of all Downloads whose status is not FINAL.
     * Ordered by started time ASC.
     */
    public static List<Download> getIncompleteDownloads()
    {
        List<Download> incompleteDownloads = new ArrayList<Download>();
         //get a list of all downloads where status is not final
        String sql = "SELECT d.id, d.archived_file_id, d.status, d.started, d.compression, af.dropbox_location "
                    + "FROM Downloads d, ArchivedFiles af "
                    + "WHERE d.status != "+ tools.sqlString(DOWNLOAD_FINAL)+" "
                    + "AND d.archived_file_id = af.id "
                    + "ORDER BY d.started ASC";
        try
        {
            ResultSet rs = Config.archivedFilesDB.getStatement().executeQuery(sql);

            while(rs.next())
            {
                int downloadId = rs.getInt("id");
                int archivedFileId = rs.getInt("archived_file_id");
                String status = rs.getString("status");
                java.sql.Timestamp startTimeStamp =  rs.getTimestamp("started");
                //Long downloadStarted = startTimeStamp == null ? null : startTimeStamp.getTime();
                String archivedStrm = rs.getString("dropbox_location");
                String compression = rs.getString("compression");
                Download d = new Download(downloadId, archivedFileId, status, startTimeStamp, archivedStrm, compression);
                incompleteDownloads.add(d);
            }
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to get list of current downloads from database using: "+ sql,x);
        }
        finally
        {
            Config.archivedFilesDB.closeStatement();
            return incompleteDownloads;
        }
    }
    public static boolean trackArchivedFile(String sourceName, String dropboxLocation, XBMCFile video)
    {

        String originalPath = video.getFullPath();
        dropboxLocation = convertToStrm(dropboxLocation);//alwasy add to DB as .strm
        //check if this file already exists in the tracker (unique index on dropbox_location)
        String checkSQL = "SELECT id, source_name, original_path, missing_since, missing_count, video_type, title, series, artist, episode_number, season_number, year, is_tvdb_lookup "
                + "FROM ArchivedFiles "
                + "WHERE dropbox_location = "+sqlString(convertToStrm(dropboxLocation));

        int currentId =-1, currentMissingCount =-1, episodeNumber = -1, seasonNumber=-1, year=-1;
        String currentSourceName=null, currentOriginalPath=null, videoType=null, title=null, series=null, artist=null;
        java.sql.Timestamp currentMissingSince =null;
        boolean isTVDBLookup = false;
        try
        {            
            ResultSet rs = Config.archivedFilesDB.getStatement().executeQuery(checkSQL);
            if(rs.next())
            {
                currentId = rs.getInt("id");
                currentSourceName = rs.getString("source_name");
                currentOriginalPath = rs.getString("original_path");
                currentMissingSince = rs.getTimestamp("missing_since");
                currentMissingCount = rs.getInt("missing_count");
                //the data about the video:
                videoType = rs.getString("video_type");
                title = rs.getString("title");
                series = rs.getString("series");
                artist = rs.getString("artist");
                episodeNumber = rs.getInt("episode_number"); if(rs.wasNull()) episodeNumber = -1;
                seasonNumber = rs.getInt("season_number"); if(rs.wasNull()) seasonNumber = -1;
                year = rs.getInt("year"); if(rs.wasNull()) year = -1;
                isTVDBLookup = rs.getInt("is_tvdb_lookup") == 1;//1=true, 0=false
            }            
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to determine id for file at: "+ dropboxLocation+". Cannot continue with updating/tracking this file: "+ x,x);
            return false;
        }
        finally
        {
            Config.archivedFilesDB.closeStatement();
        }
        
        boolean updating = currentId > -1;//this dropbox_location already exists if it has a valid id, update the entry if needed
        String sql;
        if(updating)
        {
            //determine if data has changed and update record if so
            boolean changed =//if any values are not already what they will be set to, update with the new values. We already know dropbox_location is the same.
                       !sourceName.equals(currentSourceName)//source name is different
                    || !originalPath.equals(currentOriginalPath)//original path is different
                    || currentMissingSince != null//missing since is set
                    || currentMissingCount != 0;//missing count is started

            if(!changed)
            {
                //the basic's haven't changed, check if metadata has  changed
                changed = !video.getType().equals(videoType) || !video.getTitle().equals(title) || video.hasBeenLookedUpOnTVDB() != isTVDBLookup;
                if(!changed)
                {
                    if(video.isTvShow())
                        changed = !video.getSeries().equals(series) || video.getSeasonNumber() != seasonNumber || video.getEpisodeNumber() != episodeNumber;
                    else if(video.isMovie())
                        changed = video.getYear() != year;
                    else if(video.isMusicVideo())
                        changed = !video.getArtist().equals(artist);
                }                    
            }
            if(!changed)
            {
                Config.log(DEBUG, "Nothing has changed for this video, no need to update tracker database for: "+ dropboxLocation);
                return true;
            }
            else//fields changed, update with the new values
            {
                Config.log(DEBUG, "Changes occurred for this video. updating database: "+ dropboxLocation);
                //if any of the values have changed, update the entry to catch any changed values (also reverts any missing_since or missing_count since this file is no longer missing)
                sql = "UPDATE ArchivedFiles SET source_name = ?, dropbox_location = ?, "
                        + "original_path = ?, missing_since = ?, missing_count = ?, date_archived = ?,"
                        + "video_type = ?, title = ?, series = ?, artist = ?, episode_number = ?, season_number = ?, year = ?, is_tvdb_lookup = ? "
                        + "WHERE id = "+ currentId;
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
            prep = (PreparedStatement) Config.archivedFilesDB.getStatement(PREPARED_STATEMENT,sql);
            prep.setString(1, sourceName);//source_name
            prep.setString(2, dropboxLocation);//dropbox_location
            prep.setString(3, originalPath);//original_path            
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

            
            int updateCount = prep.executeUpdate();            
            if(updateCount == 1)
            {
                Config.log(DEBUG, "Successfully "+(updating ? "updated":"added")+" file in ArchivedFiles tracking table from source "+ sourceName+": "+Config.escapePath(originalPath)+": "+ dropboxLocation);
                return true;
            }
            else throw new Exception(updateCount +" rows were updated (expected 1).");
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to add archived file \""+dropboxLocation+"\" to SQLite Database: "+ x,x);
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
        int id = Config.archivedFilesDB.getSingleInt("SELECT id FROM ArchivedFiles WHERE dropbox_location = "+sqlString(convertToStrm(path)));
        if(id == SQL_ERROR)
        {
            Config.log(WARNING, "Failed to determine filed id for video at "+path+". Will not mark this video as missing or delete it right now.");
            return false;
        }
        if(id < 0)
        {
            Config.log(WARNING, "This file was not found in the ArchivedFiles database, cannot mark it as missing. Will set this file to be deleted.");
            return true;
        }

        String missingSinceSQL = "SELECT missing_since FROM ArchivedFiles WHERE id = "+id;
        Long missingSince = Config.archivedFilesDB.getSingleTimestamp(missingSinceSQL);
        if(missingSince != null && missingSince.longValue() == ((long)SQL_ERROR))
        {
            Config.log(WARNING,"SQL failed. Cannot determine how long this video has been missing. Skipping deleting this file for now. SQL used: "+ missingSinceSQL);
            return false;
        }
        int missingCount = Config.archivedFilesDB.getSingleInt("SELECT missing_count FROM ArchivedFiles WHERE id = "+id);
        if(missingCount <0) missingCount = 0;
       
        //update the counts
        missingCount++;
        long now = System.currentTimeMillis();
        if(missingSince == null) missingSince = now;
        
        String updateSQL = "UPDATE ArchivedFiles SET missing_count = ?, missing_since = ? WHERE id = "+ id;
        try
        {
            PreparedStatement prep = (PreparedStatement) Config.archivedFilesDB.getStatement(PREPARED_STATEMENT, updateSQL);
            prep.setInt(1, missingCount);
            prep.setTimestamp(2, new java.sql.Timestamp(missingSince.longValue()));
            prep.execute();            
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to update file as missing using: "+ updateSQL,x);
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
            Config.log(INFO, "This video should be deleted because it has been missing for "+ toTwoDecimals(missingForHours) +" hours (threshold is "+Config.MISSING_HOURS_DELETE_THRESHOLD+"), "
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
            Config.log(INFO,  reason);
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
        //log(DEBUG, "Removed these invalid XML characters: " + inValidChars);
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
            Config.log(ERROR, "Cannot format date as TVDB style using "+Config.tvdbFirstAiredSDF.toPattern() +": "+ dt);
            return null;
        }
    }
    public static boolean isNetworkShare(File f)
    {
        return isNetworkShare(f.getPath());
    }
    public static boolean isNetworkShare(String fullSharePath)
    {
        return Config.valid(fullSharePath) && fullSharePath.startsWith("\\\\");
    }
    public static boolean isShareAvailable(String fullSharePath)
    {
        if(!isNetworkShare(fullSharePath))
        {
            Config.log(Config.WARNING, "Checking if share is available, but the file \""+fullSharePath+"\" is not a network share");
            return new File(fullSharePath).getParentFile().exists();
        }

        int slashCount = 0;
        for(char c : fullSharePath.toCharArray())
            if(c == '\\') slashCount++;
        if(slashCount >= 3)//something like \\share\folder\file.txt
        {
            int firstSeperator = fullSharePath.indexOf("\\", 2);
            int secondSeperator = fullSharePath.indexOf("\\", firstSeperator+1);
            if(secondSeperator == -1) secondSeperator = fullSharePath.length();
            String baseShare = fullSharePath.substring(0, secondSeperator);
            File f = new File(baseShare);
            Config.log(DEBUG, "Checking if share at \""+ baseShare +"\" is available = "+ f.exists());
            return  f.exists();
        }
        else//something like \\share
        {
            Config.log(Config.WARNING, "Cannot check if the network share is available because the path is too short: "+ fullSharePath);
            return false;
        }
    }

  /**
  * Recursively walk a directory tree and return a List of all
  * Directories found; 
  *
  * @param baseDirectory is a valid directory, which can be read.
  */
  static public List<File> getDirectories(File baseDirectory) throws FileNotFoundException
  {
    validateDirectory(baseDirectory);
    List<File> result = getDirectoriesRecursively(baseDirectory);
    return result;
  }

  // PRIVATE //
  static private List<File> getDirectoriesRecursively(File baseDirectory) throws FileNotFoundException
  {
    List<File> result = new ArrayList<File>();
    File[] filesAndDirs = baseDirectory.listFiles();
    List<File> filesDirs = Arrays.asList(filesAndDirs);
    for(File file : filesDirs)
    {
          if ( ! file.isFile() )
          {
            //must be a directory
            result.add(file); //add the directory

            //recursive call!
            List<File> deeperList = getDirectoriesRecursively(file);
            result.addAll(deeperList);
          }
    }
    return result;
  }

  /**
  * Directory is valid if it exists, does not represent a file, and can be read.
  */
  static private void validateDirectory (File aDirectory) throws FileNotFoundException
  {
    if (aDirectory == null) {
      throw new IllegalArgumentException("Directory should not be null.");
    }
    if (!aDirectory.exists()) {
      throw new FileNotFoundException("Directory does not exist: " + aDirectory);
    }
    if (!aDirectory.isDirectory()) {
      throw new IllegalArgumentException("Is not a directory: " + aDirectory);
    }
    if (!aDirectory.canRead()) {
      throw new IllegalArgumentException("Directory cannot be read: " + aDirectory);
    }
  }

      public static String getStacktraceAsString(Exception x)
    {
        if(x == null) return null;

        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        x.printStackTrace(printWriter);
        return writer.toString();
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
     public static int getLevenshteinDistance(String s, String t) {
      if (s == null || t == null) {
          throw new IllegalArgumentException("Strings must not be null");
      }

      int n = s.length(); // length of s
      int m = t.length(); // length of t

      if (n == 0) {
          return m;
      } else if (m == 0) {
          return n;
      }

      if (n > m) {
          // swap the input strings to consume less memory
          String tmp = s;
          s = t;
          t = tmp;
          n = m;
          m = t.length();
      }

      int p[] = new int[n+1]; //'previous' cost array, horizontally
      int d[] = new int[n+1]; // cost array, horizontally
      int _d[]; //placeholder to assist in swapping p and d

      // indexes into strings s and t
      int i; // iterates through s
      int j; // iterates through t

      char t_j; // jth character of t

      int cost; // cost

      for (i = 0; i<=n; i++) {
          p[i] = i;
      }

      for (j = 1; j<=m; j++) {
          t_j = t.charAt(j-1);
          d[0] = j;

          for (i=1; i<=n; i++) {
              cost = s.charAt(i-1)==t_j ? 0 : 1;
              // minimum of cell to the left+1, to the top+1, diagonally left and up +cost
              d[i] = Math.min(Math.min(d[i-1]+1, p[i]+1),  p[i-1]+cost);
          }

          // copy current distance counts to 'previous row' distance counts
          _d = p;
          p = d;
          d = _d;
      }

      // our last action in the above loop was to switch d and p, so p now
      // actually has the most recent cost counts
      return p[n];
  }

    public static String tfl(String s, int fixedLength)
    {
        if(s == null) return s;
        if(s.length() >= fixedLength) return s.substring(0,fixedLength);

        //else needs padding
        int pads = fixedLength - s.length();
        for(int i=0;i<pads; i++)
            s += " ";
        return s;
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
            Config.log(WARNING, "IP Range \""+range+"\" is not valid: "+ x);
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
    public static String getRegexMatch(String regex, String test)
    {
        Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
        Matcher m = p.matcher(test);
        if(m.find()) return m.group();
        else return null;
    }
    public static String toTwoDecimals(double d)
    {
	DecimalFormat TWO_DECIMALS = new DecimalFormat("0.00");
        if((d+"").equals("NaN"))
            return "0.00";
        else
            return TWO_DECIMALS.format(d);
    }
    public static synchronized String sqlString(String s)
    {
        if(s == null) return s;
        else
        {
            s = s.replace("'", "''"); //    ' = ''
            s = s.replace("\"", "\\\"");//  " = \"
            s = s.replace("\n", "\\n");//   newline = \n
            s = s.replace("\r", "\\r");//   carriage = \r
            s = s.replace("\t", "\\t");//   tab = \t
            return "'"+s+"'";
        }
    }

    public static String getURLFromString(String s)
    {
        String urlRegex = "\\b(https?|ftp|file)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_|]";
        Pattern p = Pattern.compile(urlRegex);

        Matcher m = p.matcher(s);
        if(m.find())
            return m.group();
        else
        {
            try {
                //try it with the string unencoded
                s = URLDecoder.decode(s, "UTF-8");
                Matcher m2 = p.matcher(s);
                if(m2.find()) return m2.group();
            } catch (UnsupportedEncodingException ex) {
                //ignored
            }
        }
        return null;//no match
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

    private static boolean convertEDL(File edlFile)
    {
        //read the current edl into memory and convert
        Config.log(INFO, "Converting .edl to type " + Config.EDL_TYPE + " at " + edlFile);
        List<String> currentLines = readFile(edlFile);
        List<String> newLines = new ArrayList<String>();
        try
        {
            for(String nextLine : currentLines)
            {                
                Config.log(DEBUG, ".edl Line before: " + nextLine);
                //edls are tab-seperated into 3 columns like so: 2.32\t3.42\t0
                String newLine = nextLine.substring(0, nextLine.lastIndexOf("\t")) +"\t" + Config.EDL_TYPE;
                Config.log(DEBUG, ".edl Line after:  " + newLine);
                newLines.add(newLine);
            }            
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Could not convert .edl to type " + Config.EDL_TYPE + " at: " + edlFile,x);
            return false;
        }

        try
        {
            boolean overwrite = true;//overwrite with the new lines
            boolean success = writeToFile(edlFile, currentLines, overwrite);
            if(success)
            {
                Config.log(INFO, "Successfully changed .edl to type " + Config.EDL_TYPE + " at " + edlFile);
                return true;
            }

            else throw new Exception("Failed to overwrite the .edl file: "+ edlFile);
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Could not overwrite .edl as type " + Config.EDL_TYPE + " at: " + edlFile,x);
            return false;
        }
    }

    /*
     * Converts any edls in downloaded videos dropbox that havent yet been converted
     */
    public static void convertEDLs()
    {
        Config.setShortLogDesc("EDLConversion");
        Collection<File> edls = FileUtils.listFiles(new File(Config.DOWNLOADED_VIDEOS_DROPBOX), new String[] {"edl"}, true);

        int numberConverted = 0, numberFailed = 0, numberAlreadyConverted =0;;
        for(File edl : edls)
        {
            boolean alreadyConverted = Config.archivedFilesDB.getSingleInt(
                    "SELECT id FROM EDLChanges "
                    + "WHERE file = "+ tools.sqlString(edl.getPath()) +" "
                    + "AND converted_to = "+ Config.EDL_TYPE)
                > -1;
            
            if(!alreadyConverted)
            {                
                
                if(convertEDL(edl))
                {

                    numberConverted++;                    
                    //delete old file record if it exists (was previously converted to a different type)
                    Config.archivedFilesDB.executeSingleUpdate("DELETE FROM EDLChanges WHERE file = "+ tools.sqlString(edl.getPath()));
                    boolean updated = Config.archivedFilesDB.executeSingleUpdate("INSERT INTO EDLChanges (file, converted_to) VALUES("+tools.sqlString(edl.getPath())+", "+Config.EDL_TYPE+")");
                    if(updated) Config.log(INFO, "Successfully updated edl tracking entry in database.");
                    else 
                    {
                        Config.log(WARNING, "Failed to update edl tracking entry in database. This .edl may be unnecessarily converted again in the future.");
                    }
                }
                else
                {
                    Config.log(WARNING, "Failed to convert edl to type "+ Config.EDL_TYPE+". WIll try again later: "+ edl);
                    numberFailed++;
                    continue;
                }
            }
            else
            {
                Config.log(DEBUG, "Skipping .edl conversion because it has already been converted: "+ edl);
                numberAlreadyConverted++;
                continue;
            }
        }//end edl file loop
        
        Config.log(INFO, numberConverted +" .edl files were converted to type "+ Config.EDL_TYPE+". "+
                numberFailed+" failed to be converted and "+ numberAlreadyConverted +" were skipped because they've previously been converted.");

        if(numberConverted > 0)
        {
            //clean up any junk files left over from comskip
            String[] exts = new String[]{"incommercial", "txt"};//comskip artifacts
            Collection<File> artifacts = FileUtils.listFiles(new File(Config.DOWNLOADED_VIDEOS_DROPBOX), exts, true);
            Config.log(INFO, "Deleting "+ artifacts.size() +" comskip artifacts in "+ Config.DOWNLOADED_VIDEOS_DROPBOX);
            for(File artifact : artifacts)
            {
                try
                {                    
                    if(artifact.delete()) Config.log(DEBUG, "Deleted comskip artifact at: "+ artifact);
                    else Config.log(INFO, "Failed to delete comskip artifact at: "+ artifact);
                }
                catch(Exception ignored){ }
            }
        }
    }

    public static boolean checkEncodeStatus(File log, List<String> verificationLines)
    {        
        try
        {            
            if(!log.exists())
                throw new Exception("Encode log file does not exist.");

            Config.log(INFO, "Encode log file size is: "+ (log.length() / 1024) + " KB, at: "+ log);

        }
        catch(Exception x)
        {
            Config.log(ERROR, "While attempting to verify encoding, the encode log file could no be found at \""+log+"\". Cannot verify, will assume the encoding was UNsuccessful.",x);
            return false;
        }

        
        if(verificationLines == null || verificationLines.isEmpty())
        {
            Config.log(ERROR,"No verification lines exist for this encoding definition. Cannot verify encoding, assuming UNsuccessful.");
            return false;
        }
        else
        {
            Config.log(INFO, "Verification lines are: ");
            for(Iterator it = verificationLines.iterator(); it.hasNext();)
                Config.log(INFO, "\t"+it.next());
        }

        //read the encoding log and verify the lines are in it
        List<String> lastTenLines = new ArrayList<String>();
        try
        {
            Scanner s = new Scanner(log);
            int matchIndex = 0;
            int lineNumber = 0;
            boolean success = false;
            while(s.hasNextLine())
            {
                lineNumber++;
                String nextLine = s.nextLine();
                if(!nextLine.trim().isEmpty())//skip empty lines
                {
                    lastTenLines.add(nextLine);
                    if(lastTenLines.size() > 10)
                        lastTenLines.remove(0);//limit to 10 lines

                    if(nextLine.toLowerCase().contains(verificationLines.get(matchIndex).toLowerCase()))
                    {
                        Config.log(INFO, "Found matching verification line at lineNumber " + lineNumber + ": "+nextLine);
                        //found the verification line at this index, move on to next index, or end if at end of verification lines
                        if(matchIndex == verificationLines.size()-1)
                        {
                            //successfully matched all the verification lines
                            success = true;
                            break;
                        }
                        else
                        {
                            matchIndex++;//check for the next verification line
                        }
                    }
                }
            }
            s.close();

            if(!success)
            {
                Config.log(INFO, "External encoding at \"" + log +"\"  was determined to be unsuccessful. Checked " + lineNumber + " lines in the log file, but the verification lines were not all found, or were not in the correct order. Log file = "+log +". Last line of log file = " + lastTenLines.get(lastTenLines.size()-1));
                if(!lastTenLines.isEmpty())
                {
                    Config.log(INFO, "Last "+lastTenLines.size()+" lines in the log file are: ");
                    for(Iterator i = lastTenLines.iterator(); i.hasNext();)
                        Config.log(INFO, "\t"+i.next());
                }
            }

            return success;
            
        }
        catch(Exception x)
        {            
            Config.log(ERROR, "Error while reading encoding log at \"" + log +"\". Cannot verify if encoding was successful. Will assume encoding was UNsuccessful.",x);
            return false;
        }
    }


    public static XBMCFile getVideoFromOriginalLocation(String originalLocationUnescaped)
    {
        String sql = "SELECT original_path, dropbox_location, video_type, title, series, artist, episode_number, season_number, year, is_tvdb_lookup "
                + "FROM ArchivedFiles "
                + "WHERE original_path = "+ sqlString(originalLocationUnescaped);
      return getVideoWithMetaDataFromDB(sql);
    }

    public static XBMCFile getVideoFromDropboxLocation(File f)
    {
        String sql = "SELECT original_path, dropbox_location, video_type, title, series, artist, episode_number, season_number, year, is_tvdb_lookup "
                + "FROM ArchivedFiles "
                + "WHERE dropbox_location = "+ sqlString(convertToStrm(f.getPath()));
        return getVideoWithMetaDataFromDB(sql);        
    }
    
    private static XBMCFile getVideoWithMetaDataFromDB(String sql)
    {
        XBMCFile video = null;
        try
        {
            ResultSet rs = Config.archivedFilesDB.getStatement().executeQuery(sql);

            if(rs.next())
            {
                video = new XBMCFile(rs.getString("original_path"));
                video.setType(rs.getString("video_type"));
                video.setTitle("title");
                video.setHasBeenLookedUpOnTVDB(rs.getInt("is_tvdb_lookup") == 1);
                
                if(video.isTvShow())
                {
                    video.setSeries(rs.getString("series"));
                    video.setSeasonNumber(rs.getInt("season_number"));
                    video.setEpisodeNumber(rs.getInt("episode_number"));
                }
                else if(video.isMovie())
                {
                    video.setYear(rs.getInt("year")); if(rs.wasNull()) video.setYear(-1);//year is optional
                }
                else if(video.isMusicVideo())
                {
                    video.setArtist(rs.getString("artist"));
                }
                else
                {
                    Config.log(WARNING, "Video type (TV/Movie/Music Video) cannot be determined using: "+sql);
                    return null;
                }
                String dropboxLocation = rs.getString("dropbox_location");//always a .strm out of the database
                //determine what the real extension is                
                File dropboxStrm = new File(dropboxLocation);
                File dropboxMpg = new File(tools.fileNameNoExt(dropboxStrm)+".mpg");
                File dropboxDownloaded = new File(tools.fileNameNoExt(dropboxStrm)+".downloaded");
                if(dropboxStrm.exists())//.strm
                    video.setFinalLocation(dropboxStrm.getPath());
                else if (dropboxMpg.exists())//.mpg
                    video.setFinalLocation(dropboxMpg.getPath());
                else if (dropboxDownloaded.exists())//.downloaded
                    video.setFinalLocation(dropboxDownloaded.getPath());
                
                   
               Subfolder subf = new Subfolder(new Source("ManualArchive","ManualArchive"), "ManualArchive");//to avoid NPE's, instantiate dummy objects for the lookup.
               video.setSubfolder(subf);
            }
            else
            {
                Config.log(WARNING, "No video found in the database using SQL: "+sql);
                return null;
            }
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to get meta data from database for using SQL: "+ sql,x);
            return null;
        }
        finally
        {
            Config.archivedFilesDB.closeStatement();
            return video;
        }
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
}