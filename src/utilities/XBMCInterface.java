package utilities;

import db.Database;
import java.io.File;
import java.io.FileNotFoundException;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;

public class XBMCInterface implements Constants
{    
    
    Database xbmcDB = null;
    public static SimpleDateFormat xbmcLastPlayedSDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    boolean isSQLLite = false;
    public XBMCInterface(String mySqlOrSqlLite, String schemaOrDBPath)
    {        
        //initialize connection to XBMC's mysql database
        if(SQL_LITE.equalsIgnoreCase(mySqlOrSqlLite))
        {
            isSQLLite = true;
            xbmcDB = new Database(mySqlOrSqlLite, schemaOrDBPath, null, null, null, -1);//SQLite DB connection
        }
        else//MySQL
        {
            isSQLLite = false;
            //, XBMC_MYSQL_SERVER, XBMC_MYSQL_VIDEO_SCHEMA, XBMC_MYSQL_UN, XBMC_MYSQL_PW, XBMC_MYSQL_PORT
            xbmcDB = new Database(mySqlOrSqlLite, schemaOrDBPath, Config.XBMC_MYSQL_SERVER, Config.XBMC_MYSQL_UN, Config.XBMC_MYSQL_PW, Config.XBMC_MYSQL_PORT);
        }                
            
    }

    public Database getDB()
    {
        return xbmcDB;
    }

    /*
     * Gets the full path to the file im XBMC format
     */
     public static String getFullXBMCPath(File regularFile)
    {
        String path = regularFile.getPath();

        if(tools.isNetworkShare(path))//use xbmc samba naming "smb://" instead of UNC "\\" for shares
        {
            path = "smb://" + path.substring(2,path.length());//starts with "\\"

            if(!SEP.equals("/"))
                path = path.replace(SEP, "/");//change file seperator for XBMC compatibility.

            if(regularFile.isDirectory() && !path.endsWith("/"))
                path += "/";//XBMC needs the last slash on all its dirs

        }
        else//use filesystem default sep
        {
            if(regularFile.isDirectory() && !path.endsWith(SEP))
                path += SEP;//must end with trailing slash for XBMC dirs
        }

        return path;//no filename
    }

     /*
      * Convert XBMC file paths into Windows files.
      * Catches smb:// and local files. All others are added to the list as null.
      * stack:// files are handled as well, which is why this returns a list instead of a single file
      */
    public static List<File> getWindowsPath(String XBMCPath)
    {
        final String sambaId = "smb://";
        final String stackId = "stack://";
        final String stackSplitter = " , ";

        List<File> windowsFiles = new ArrayList<File>();
        String[] paths = new String[]{XBMCPath};//default is a single path
        if(XBMCPath.toLowerCase().startsWith(stackId))
        {
            //get all paths in this multi file
            paths = XBMCPath.substring(stackId.length(), XBMCPath.length())//cut the stack:// off the beginning
                    .split(stackSplitter);//split to get all stacked files
        }

        for(String nextXBMCPath : paths)
        {
            String windowsPath = nextXBMCPath;
            try
            {                
                //check for smb and convert into UNC path
                if(nextXBMCPath.toLowerCase().startsWith(sambaId))
                {
                    windowsPath = "\\\\"+nextXBMCPath.substring(sambaId.length(), nextXBMCPath.length());
                    windowsPath = windowsPath.replace("/", SEP);//replace the /'s with \'s
                    windowsFiles.add(new File(windowsPath));
                    continue;
                }

                //check if it's a local file
                String localDriveMatcher = "[a-z]:\\\\";//Like "C:\"
                String driveString = tools.getRegexMatch(localDriveMatcher, nextXBMCPath);
                if(driveString == null)throw new FileNotFoundException("Not a windows file.");
                //the path should start with the drivestring
                if(!nextXBMCPath.startsWith(driveString)) throw new FileNotFoundException("The path does not start with \""+driveString+"\"");

                //valid local file if we got here
                windowsFiles.add(new File(nextXBMCPath));

            }
            catch(FileNotFoundException x)//Not really an "exception" because this is expected for thing like plugin:// or http:// files
            {
                Config.log(DEBUG, "Will assume this file exists. This file is not on the Windows filesystem: "+ nextXBMCPath+". Details: "+x);
                windowsFiles.add(null);
            }
        }

        return windowsFiles;

    }

    public boolean isConnected()
    {
        return xbmcDB.isConnected();
    }

    public void close()
    {
        xbmcDB.close();
        Config.log(Config.DEBUG, "Closed connection to XBMC database.");
    }

    public void addMetaDataFromQueue()
    {
        Config.setShortLogDesc("Meta-Data");
        List<QueuedChange> queuedChanges = Config.queuedChangesDB.getQueuedChanges();
        Config.log(NOTICE, "Attempting to integrate "+ queuedChanges.size() +" queued meta-data changes into XBMC's MySQL video library.");
        List<QueuedChange> changesToDelete = new ArrayList<QueuedChange>();//these are the changes that are no longer valid
        List<QueuedChange> changesCompleted = new ArrayList<QueuedChange>();//these are the changes that were succseefully implemented
        Set<String> strmsNotInLibrary = new HashSet<String>();
        for(QueuedChange qc : queuedChanges)
        {            
            try
            {                
                String archivedLocation = qc.getDropboxLocation();                
                File archivedStrmFile = new File(archivedLocation);//by the time it has gotten here, the strm file should be in the library
                if(strmsNotInLibrary.contains(archivedStrmFile.toString())) continue;//we'ev already determined this isn't inthe libarary, no need to attempt more meta data changes
                
                if(!archivedStrmFile.exists())
                {//.strm doesnt exist in dropbox                   
                    changesToDelete.add(qc);
                    Config.log(INFO, "REMOVE: This video no longer exists in the dropbox, so it is being removed from meta-data queue: "+ archivedStrmFile);
                    continue;
                }

                String fileName;
                

                
                {
                    fileName = archivedStrmFile.getName();
                
                }
                
                String videoType = qc.getTypeOfVideo();
                String metaDataType = qc.getTypeOfMetaData();
                String value = qc.getValue();

                String xbmcPath = XBMCInterface.getFullXBMCPath(archivedStrmFile.getParentFile());//the path (not including file)
                
                if (utilities.Config.LINUX_SAMBA_PREFIX != null)
                {
                	xbmcPath = utilities.Config.LINUX_SAMBA_PREFIX + xbmcPath;
                	Config.log(DEBUG, "Using LINUX_SAMBA_PREFIX. Concatentated path: "+ xbmcPath);
                }

                //check if it is in the video library database
                String videoExistsSql = "SELECT idFile "
                    + "FROM files "
                    + "WHERE idPath IN(SELECT idPath FROM path WHERE lower(strPath) = ?) AND " 
                           + "lower(strFileName) = ?";
                
                int fileId = xbmcDB.getSingleInt(videoExistsSql, tools.params(xbmcPath.toLowerCase(),fileName.toLowerCase()));
                if(fileId == SQL_ERROR) throw new Exception("Error determining file id using: "+ videoExistsSql);
                boolean videoIsInXBMCsLibrary = fileId > -1;
                if(videoIsInXBMCsLibrary)
                {
                    //this video file is in the database, update it                                        
                    int video_id = getVideoIdInLibrary(videoType, fileId);
                    if(video_id < 0) throw new Exception(videoType+" ID was not found in library for file_id "+fileId +", located at "+ archivedStrmFile);

                    if(MOVIE_SET.equals(metaDataType))
                    {
                        String setName = value;
                        if(!tools.valid(setName))
                        {
                            Config.log(DEBUG, "OK: movie set name is empty, will not add to a movie set: "+ archivedStrmFile);
                            changesCompleted.add(qc);
                            continue;
                        }

                        int setId = getMovieSetIDAndCreateIfDoesntExist(setName);
                        if(setId < 0) throw new Exception("Failed to find movie set named \""+setName+"\". Will skip this video for now: "+ archivedStrmFile);
                        String checkSql = "SELECT idSet FROM setlinkmovie WHERE idSet = ? AND idMovie = ?";
                        int movieId = xbmcDB.getSingleInt(checkSql, tools.params(setId,video_id));
                        if(movieId == SQL_ERROR)
                            throw new Exception("Cannot determine if movie already exists in movie set using: "+ checkSql);

                        boolean movieAlreadyExistsInSet = movieId > -1;
                        if(movieAlreadyExistsInSet)
                        {
                            Config.log(DEBUG, "OK: This movie already exists in the set \""+setName+"\", skipping.");
                            changesCompleted.add(qc);
                            continue;
                        }
                        
                        String addToMovieSetSQL = "INSERT INTO setlinkmovie (idSet, idMovie) VALUES(?, ?)";
                        boolean success = xbmcDB.executeSingleUpdate(addToMovieSetSQL, tools.params(setId,video_id));
                        if(success)
                        {
                            Config.log(INFO, "SUCCESS: added to movie set named \"" + value + "\": " + archivedStrmFile);
                            changesCompleted.add(qc);
                        }
                        else throw new Exception("Failed to add to movie set \""+value+"\": "+archivedStrmFile+" using SQL: "+addToMovieSetSQL);
                    }
                    else if(PREFIX.equals(metaDataType) || SUFFIX.equals(metaDataType))
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
                            Config.log(WARNING,"REMOVE: Unknown video type: \""+ videoType+"\", will not update meta data");
                            changesToDelete.add(qc);
                            continue;
                        }

                        String concat, xFix, wildCardValue;
                        if(PREFIX.equals(metaDataType))
                        {
                            xFix = "prefix";
                            if(isSQLLite)
                                concat = "(? || "+field+")";
                            else
                                concat = "CONCAT(?,"+field+")";
                            wildCardValue = value+"%";//use for safe update (LIKE clause)
                            
                        }
                        else//suffix
                        {
                            xFix = "suffix";
                            if(isSQLLite)
                                concat = "("+field+" || ?)";
                            else
                                concat = "CONCAT("+field+",?)";
                            wildCardValue = "%"+value;
                        }
                        
                        
                        if(!tools.valid(value))
                        {
                            Config.log(DEBUG, "OK: "+xFix +" is empty, no need to change anything for: "+ archivedStrmFile);
                            changesCompleted.add(qc);
                            continue;
                        }
                        String safeUpdate =  " AND "+field + " NOT LIKE ?";
                        String safeSQL = "SELECT "+idField + " FROM " + table +" WHERE "+idField+" = ?" + safeUpdate;
                        int videoIdNeedingXfix = xbmcDB.getSingleInt(safeSQL, tools.params(video_id,wildCardValue));
                        if(videoIdNeedingXfix == SQL_ERROR) throw new Exception("Skipping this video because cannot determine if it already has the "+xFix +" using: "+ safeSQL);
                        boolean needsUpdate = videoIdNeedingXfix > -1;//needs the update if this returns a valid id. Means it doesnt alread have the xFix value
                        if(!needsUpdate)
                        {
                            Config.log(INFO,"OK: Not updating because this video already has the "+ xFix + "\""+value+"\" at "+archivedStrmFile);
                            changesCompleted.add(qc);
                            continue;
                        }
                        
                        String sql = "UPDATE "+table+ " SET " + field + " = " + concat +" WHERE "+idField+" = ?"+safeUpdate;
                        
                        Config.log(DEBUG,"Executing: "+sql);
                        boolean success = xbmcDB.executeSingleUpdate(sql,tools.params(value,video_id,wildCardValue));
                        if(!success)
                            Config.log(WARNING, "Failed to add prefix/suffix with SQL: "+ sql+". Will try again next time...");
                        else
                        {
                            Config.log(INFO, "SUCCESS: Added "+xFix +" of \""+value+"\" to title of video: "+ archivedStrmFile + " using: "+ sql);
                            changesCompleted.add(qc);
                        }
                    }
                }
                else
                {
                    strmsNotInLibrary.add(archivedStrmFile.toString());
                    throw new Exception("the video is not yet in XBMC's library: "+archivedStrmFile);                           
                }
            }
            catch(Exception x)
            {
                Config.log(INFO, "SKIPPING meta-data update: "+x.getMessage());
            }
        }
        int changesDone = (changesCompleted.size() + changesToDelete.size());
        int changesRemaining = queuedChanges.size()-changesDone;
        int numChangesCompleted = queuedChanges.size()- changesRemaining;
        Config.log(NOTICE, "Successfully completed "+numChangesCompleted+" queued metadata changes. There are now "+ changesRemaining +" changes left in the queue.");

        Config.log(NOTICE, "Updating QueuedChanges database to reflect the "+changesCompleted.size()+" successful metatadata changes.");
        for(QueuedChange qc : changesCompleted)
        {
            //update the status as COMPLETED in the sqlite tracker
            boolean updated = Config.queuedChangesDB.executeSingleUpdate("UPDATE QueuedChanges SET status = ? WHERE id = ?"
                    ,tools.params(COMPLETED,qc.getId()));
            if(!updated)Config.log(ERROR, "Could not update status as "+COMPLETED +" for queued change. This may result in duplicate meta-data for file: "+ qc.getDropboxLocation());
        }
        
        Config.log(NOTICE, "Updating QueuedChanges database to reflect the "+changesToDelete.size()+" metatadata changes that are no longer needed.");
        //delete the ones that can be deleted
        for(QueuedChange qc : changesToDelete)
        {            
            boolean deleted = Config.queuedChangesDB.executeSingleUpdate("DELETE FROM QueuedChanges WHERE id = ?",tools.params(qc.getId()));
            if(!deleted)Config.log(ERROR, "Could not delete metadata queue file in prep for re-write. This may result in duplicate entries for file: "+ qc.getDropboxLocation());
        }
        Config.log(NOTICE, "Done updating metadata queued changed database.");
    }

    public int getMovieSetIDAndCreateIfDoesntExist(String setName)
    {
        String selectSql = "SELECT idSet FROM sets where strSet = ?";
        int id = xbmcDB.getSingleInt(selectSql,tools.params(setName));
        if(id > -1) return id;
        else
        {
            //create movie set
            String updateSql = "INSERT INTO sets(strSet) VALUES(?)";
            boolean created = xbmcDB.executeSingleUpdate(updateSql,tools.params(setName));
            if(created)
                return xbmcDB.getSingleInt(selectSql,tools.params(setName));//new id
            else
            {
                Config.log(ERROR, "Failed to create new movie set named \""+setName+"\" using: "+ updateSql);
                return SQL_ERROR;
            }
        }
    }

    
    /**
     * Gets the id of the file in the library if it is in the library.
     * @param videoType The type of video (movie/tv show/music video). This determines what library to look in
     * @param fileId The id of the file that we are lookin for in the library
     * @return positive id if it exists in the library, otherwise -1
     */
    public int getVideoIdInLibrary(String videoType, int fileId)
    {
        //get the path id first
        String sql;
        if(MOVIE.equalsIgnoreCase(videoType))
            sql = "SELECT idMovie FROM movie WHERE idFile = ?";
        else if(TV_SHOW.equalsIgnoreCase(videoType))//tv episode
            sql = "SELECT idEpisode FROM episode WHERE idFile = ?";
        else if(MUSIC_VIDEO.equalsIgnoreCase(videoType))
            sql = "SELECT idMvideo FROM musicvideo where idFile = ?";
        else
        {
            Config.log(Config.ERROR, "Updating pointer is currently only available for TV Shows, Movies, and Music Videos. \""+videoType+"\" is not supported.");
            return SQL_ERROR;
        }
        Config.log(Config.DEBUG, "Getting video ID with: " + sql);
        int id = xbmcDB.getSingleInt(sql,tools.params(fileId));
        return id;
    }

    /*
     * Gets the filename only. Supports stack:// protocol for multiple files
     */
    public static String getXBMCFileName(List<File> files)
    {
        if(files.isEmpty()) return null;
        String newFileName;
        if(files.size() == 1)
            newFileName = files.get(0).getName();
        else//use XBMC's stack:// multi-file protocol
        {
            //stack://smb://ONYX/Data-2tb/Videos/Movies/Knight and Day/Knight.and.Day.part1.avi , smb://ONYX/Data-2tb/Videos/Movies/Knight and Day/Knight.and.Day.part2.avi , smb://ONYX/Data-2tb/Videos/Movies/Knight and Day/Knight.and.Day.part3.avi , smb://ONYX/Data-2tb/Videos/Movies/Knight and Day/Knight.and.Day.part4.avi
            newFileName = "stack://";
            for(Iterator<File> it = files.iterator(); it.hasNext();)
            {
                File f = it.next();
                String xbmcPath = XBMCInterface.getFullXBMCPath(f);
                newFileName += xbmcPath;
                if(it.hasNext())newFileName += " , ";//seperator used by XBMC's stack:// protocol
            }
        }
        return newFileName;
    }
    
    public int getPathIdFromPathString(String path)
    {
        String sql = "SELECT idPath FROM path WHERE lower(strPath) = ?";
        Config.log(Config.DEBUG, "Getting pathId with: " + sql);
        int pathId = xbmcDB.getSingleInt(sql,tools.params(path.toLowerCase()));
        return pathId;
    }
    public int getFileId(String path, String fileName)
    {
        //get the path id first
        int pathId = getPathIdFromPathString(path);

        int fileId =-1;
        if(pathId > -1)
        {
            //get the file id based on path id and fileName
            String sql = "SELECT idFile FROM files WHERE idPath = ? AND lower(strFilename) = ?";
            Config.log(Config.DEBUG, "Getting fileId with: " + sql);
            fileId = xbmcDB.getSingleInt(sql,tools.params(pathId, fileName.toLowerCase()));
        }
        else
            Config.log(Config.DEBUG, "Not attempting to get file id because path was not found.");
        return fileId;
    }


    public List<String> getAllFiles()
    {
        List<String> xbmcFiles = new ArrayList<String>();
         String sql = "SELECT f.idFile, p.strPath, f.strFileName "
                 + "FROM files f, path p "
                 + "WHERE f.idPath = p.idPath";
        try
        {
            
            ResultSet rs = xbmcDB.executeQuery(sql,null);
            while(rs.next())
            {
                String path = rs.getString("p.strPath");
                String fileName = rs.getString("f.strFileName");
                String filePath = (path + fileName);
                xbmcFiles.add(filePath);
            }
            rs.close();            
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR, "Could not execute query: " + sql, x);
            return null;
        }
        finally
        {
            xbmcDB.closeStatement();
        }
         return xbmcFiles;
    }  
}