

package utilities;

import java.io.File;
import java.io.FileNotFoundException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;
import mylibrary.ThumbCleaner;
import org.apache.commons.io.FileUtils;

public class XBMCInterface implements Constants
{    

    
    Database dbConnection = null;
    public static SimpleDateFormat xbmcLastPlayedSDF = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
    boolean isSQLLite = false;
    public XBMCInterface(String mySqlOrSqlLite, String schemaOrDBPath)
    {        
        //initialize connection to XBMC's mysql database
        if(SQL_LITE.equalsIgnoreCase(mySqlOrSqlLite))
        {
            isSQLLite = true;
            dbConnection = new Database(mySqlOrSqlLite, schemaOrDBPath, null, null, null, -1);//SQLite DB connection
        }
        else//MySQL
        {
            isSQLLite = false;
            //, XBMC_MYSQL_SERVER, XBMC_MYSQL_VIDEO_SCHEMA, XBMC_MYSQL_UN, XBMC_MYSQL_PW, XBMC_MYSQL_PORT
            dbConnection = new Database(mySqlOrSqlLite, schemaOrDBPath, Config.XBMC_MYSQL_SERVER, Config.XBMC_MYSQL_UN, Config.XBMC_MYSQL_PW, Config.XBMC_MYSQL_PORT);
        }                
            
    }

    public Database getDB()
    {
        return dbConnection;
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
        return dbConnection.isConnected();
    }

    public void close()
    {
        dbConnection.close();
        Config.log(Config.DEBUG, "Closed connection to XBMC database.");
    }

    public void addMetaDataFromQueue()
    {
        Config.setShortLogDesc("Meta-Data");
        List<QueuedChange> queuedChanges = tools.getQueuedChanges();
        Config.log(NOTICE, "Attempting to integrate "+ queuedChanges.size() +" queued meta-data changes into XBMC's MySQL video library.");
        List<QueuedChange> changesToDelete = new ArrayList<QueuedChange>();//these are the changes that are no longer valid
        List<QueuedChange> changesCompleted = new ArrayList<QueuedChange>();//these are the changes that were succseefully implemented
        for(QueuedChange qc : queuedChanges)
        {            
            try
            {                
                String archivedLocation = qc.getDropboxLocation();
                String fullPathNoExt = archivedLocation.substring(0, archivedLocation.lastIndexOf("."));//trim the extension
                File archivedVideo = new File(fullPathNoExt+".strm");//by the time it has gotten here, the file should have been changed from .mpg to .strm
                File archivedVideoAsMPG =new File(fullPathNoExt+".mpg");
                File archivedVideoDownloaded =new File(fullPathNoExt+".downloaded");
                if(!archivedVideo.exists() && !archivedVideoDownloaded.exists())
                {//.strm doesnt exist

                    if(archivedVideoAsMPG.exists())//not yet in XBMC's library (.mpg hasnt been converted to .strm)
                    {
                        throw new Exception("This video isn't in XBMC's library yet: "+ archivedVideoAsMPG);
                    }
                    else///strm doesn't exist and neither does .mpg or .downloaded
                    {
                        changesToDelete.add(qc);
                        Config.log(INFO, "This video no longer exists in the dropbox, so it is being removed from meta-data queue: "+ archivedVideo);
                        continue;
                    }
                }

                String fileName;
                boolean limitByPath;

                if(archivedVideoDownloaded.exists())//this has been downloaded, find the video files that were downloaded for it
                {
                     List<String> archivedVideos = Config.archivedFilesDB.getStringList("SELECT dropbox_location FROM DownloadFiles df WHERE df.download_id "
                         + "IN (SELECT id FROM Downloads d WHERE d.archived_file_id IN (SELECT id FROM ArchivedFiles af WHERE af.dropbox_location = " +tools.sqlString(archivedVideo.getPath())+"))");
                     if(archivedVideos.isEmpty())
                     {
                         Config.log(WARNING, "Could not find any downloaded videos for the file archived at: "+ archivedVideoDownloaded+". Will skip meta-data changes for this video.");
                         continue;
                     }
                     List<File> videoFiles = new ArrayList<File>();//to support stack:// if needed
                     for(String strFile : archivedVideos)
                     {
                         videoFiles.add(new File(strFile));
                     }
                     fileName = XBMCInterface.getXBMCFileName(videoFiles);
                     limitByPath = (videoFiles.size()>1);//stack:// protocol doesnt worry about path
                }
                else//regular strm, get the single file name
                {
                    fileName = archivedVideo.getName();
                    limitByPath = true;//default behaviour for single file
                }
                
                String videoType = qc.getTypeOfVideo();
                String metaDataType = qc.getTypeOfMetaData();
                String value = qc.getValue();

                String xbmcPath = XBMCInterface.getFullXBMCPath(archivedVideo.getParentFile());//the path (not including file)                

                //check if it is in the database
                String videoExistsSql = "SELECT idFile "
                    + "FROM files "
                    + "WHERE "+(limitByPath ? "idPath IN(SELECT idPath FROM path WHERE strPath = "+tools.sqlString(xbmcPath)+") AND " : "")
                           + "strFileName = "+tools.sqlString(fileName);
                
                int fileId = dbConnection.getSingleInt(videoExistsSql);
                if(fileId == SQL_ERROR) throw new Exception("Error determining file id using: "+ videoExistsSql);
                boolean videoIsInXBMCsLibrary = fileId > -1;
                if(videoIsInXBMCsLibrary)
                {
                    //this video is in the database, update it                                        
                    int video_id = getVideoId(videoType, fileId);
                    if(video_id < 0) throw new Exception(videoType+" ID was not found in database for file_id "+fileId +", located at "+ archivedVideo);

                    if(MOVIE_SET.equals(metaDataType))
                    {
                        String setName = value;
                        if(!tools.valid(setName))
                        {
                            Config.log(DEBUG, "OK: movie set name is empty, will not add to a movie set: "+ archivedVideo);
                            changesCompleted.add(qc);
                            continue;
                        }

                        int setId = getMovieSetIDAndCreateIfDoesntExist(setName);
                        if(setId < 0) throw new Exception("Failed to find movie set named \""+setName+"\". Will skip this video for now: "+ archivedVideo);
                        String checkSql = "SELECT idSet FROM setlinkmovie where idSet = "+ setId +" AND idMovie = "+ video_id;
                        int movieId = dbConnection.getSingleInt(checkSql);
                        if(movieId == SQL_ERROR)
                            throw new Exception("Cannot determine if movie already exists in movie set using: "+ checkSql);

                        boolean movieAlreadyExistsInSet = movieId > -1;
                        if(movieAlreadyExistsInSet)
                        {
                            Config.log(DEBUG, "OK: This movie already exists in the set \""+setName+"\", skipping. Determined by: "+ checkSql);
                            changesCompleted.add(qc);
                            continue;
                        }
                        
                        String addToMovieSetSQL = "INSERT INTO setlinkmovie (idSet, idMovie) VALUES("+setId+", "+video_id+")";
                        boolean success = dbConnection.executeSingleUpdate(addToMovieSetSQL);
                        if(success)
                        {
                            Config.log(INFO, "SUCCESS: added to movie set named \"" + value + "\": " + archivedVideo);
                            changesCompleted.add(qc);
                        }
                        else throw new Exception("Failed to add to movie set \""+value+"\": "+archivedVideo+" using SQL: "+addToMovieSetSQL);
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
                            Config.log(WARNING,"Unknown video type: \""+ videoType+"\", will not update meta data");
                            changesToDelete.add(qc);
                            continue;
                        }

                        String concat, safeUpdate, xFix;
                        if(PREFIX.equals(metaDataType))
                        {
                            xFix = "prefix";
                            if(isSQLLite)
                                concat = "("+tools.sqlString(value)+" || "+field+")";
                            else
                                concat = "CONCAT("+tools.sqlString(value)+","+field+")";
                            safeUpdate = field + " NOT LIKE "+ tools.sqlString(value+"%");
                        }
                        else//suffix
                        {
                            xFix = "suffix";
                            if(isSQLLite)
                                concat = "("+field+" || "+tools.sqlString(value)+")";
                            else
                                concat = "CONCAT("+field+","+tools.sqlString(value)+")";
                            safeUpdate = field + " NOT LIKE "+tools.sqlString("%"+value);
                        }
                        
                        if(!tools.valid(value))
                        {
                            Config.log(DEBUG, "OK: "+xFix +" is empty, no need to change anything for: "+ archivedVideo);
                            changesCompleted.add(qc);
                            continue;
                        }
                        
                        String safeSQL = "SELECT "+idField + " FROM " + table +" WHERE "+idField+" = " + video_id + " AND "+safeUpdate;
                        int videoIdNeedingXfix = dbConnection.getSingleInt(safeSQL);
                        if(videoIdNeedingXfix == SQL_ERROR) throw new Exception("Skipping this video because cannot determine if it already has the "+xFix +" using: "+ safeSQL);
                        boolean needsUpdate = videoIdNeedingXfix > -1;//needs the update if this returns a valid id. Means it doesnt alread have the xFix value
                        if(!needsUpdate)
                        {
                            Config.log(DEBUG,"OK: Not updating because this video already has the "+ xFix + "\""+value+"\": "+ archivedVideo);
                            changesCompleted.add(qc);
                            continue;
                        }
                        
                        String sql = "UPDATE "+table+ " SET " + field + " = " + concat +" WHERE "+idField+" = " + video_id + " AND "+safeUpdate;
                        Config.log(DEBUG,"Executing: "+sql);
                        boolean success = dbConnection.executeSingleUpdate(sql);
                        if(!success)
                            Config.log(WARNING, "Failed to add prefix/suffix with SQL: "+ sql+". Will try again next time...");
                        else
                        {
                            Config.log(INFO, "SUCCESS: Added "+xFix +" of \""+value+"\" to title of video: "+ archivedVideo + " using: "+ sql);
                            changesCompleted.add(qc);
                        }
                    }
                }
                 else
                     throw new Exception("This video: \""+archivedVideo+"\" is not yet in XBMC's database"
                             + " (as determined by "+videoExistsSql+") so meta data will remain in queue.");
            }
            catch(Exception x)
            {
                Config.log(INFO, "SKIPPING meta-data update. Will try again next time: "+x.getMessage());
            }
        }
        int changesDone = (changesCompleted.size() + changesToDelete.size());
        int changesRemaining = queuedChanges.size()-changesDone;
        int numChangesCompleted = queuedChanges.size()- changesRemaining;
        Config.log(NOTICE, "Successfully completed "+numChangesCompleted+" queued metadata changes. There are now "+ changesRemaining +" changes left in the queue.");

        for(QueuedChange qc : changesCompleted)
        {
            //update the status as COMPLETED in the sqlite tracker
            boolean updated = Config.queuedChangesDB.executeSingleUpdate("UPDATE QueuedChanges SET status = "+tools.sqlString(COMPLETED)+" WHERE id = "+ qc.getId());
            if(!updated)Config.log(ERROR, "Could not update status as "+COMPLETED +" for queued change. This may result in duplicate meta-data for file: "+ qc.getDropboxLocation());
        }
        
        //delete the ones that can be deleted
        for(QueuedChange qc : changesToDelete)
        {            
            boolean deleted = Config.queuedChangesDB.executeSingleUpdate("DELETE FROM QueuedChanges WHERE id = "+ qc.getId());
            if(!deleted)Config.log(ERROR, "Could not delete metadata queue file in prep for re-write. This may result in duplicate entries for file: "+ qc.getDropboxLocation());
        }
    }

    public int getMovieSetIDAndCreateIfDoesntExist(String setName)
    {
        String selectSql = "SELECT idSet FROM sets where strSet = "+tools.sqlString(setName);
        int id = dbConnection.getSingleInt(selectSql);
        if(id > -1) return id;
        else
        {
            //create movie set
            String updateSql = "INSERT INTO sets(strSet) VALUES("+tools.sqlString(setName)+")";
            boolean created = dbConnection.executeSingleUpdate(updateSql);
            if(created)
                return dbConnection.getSingleInt(selectSql);
            else
            {
                Config.log(ERROR, "Failed to create new movie set named \""+setName+"\" using: "+ updateSql);
                return SQL_ERROR;
            }
        }
    }

    //change from  .mpg to .strm
    public void updateDatabaseExt(String xbmcPathName, String fileName)
    {
        int pathId = getPathIdFromPathString(xbmcPathName);
        if(pathId < 0)
        {
            Config.log(WARNING, "Cannot contiue with database file entry update. "
                    + "This path does not yet exist in XBMC, this is unexpected and should not happen. Path = \""+xbmcPathName+"\"");
            return;
        }

        String fileNameNoExt = fileName.substring(0, fileName.lastIndexOf("."));
        int currentFileId = getFileId(xbmcPathName, fileNameNoExt+".mpg");
        if(currentFileId == SQL_ERROR)
        {
            Config.log(ERROR, "Error while determining file id for: "+xbmcPathName+fileName);
            return;
        }
        if(currentFileId < 0)
        {
            Config.log(WARNING, "Cannot continue with database file entry update. "
                    + "This file does not yest exist in the XBMC database. Cannot continue until it has been added to XBMC's library: "+ fileNameNoExt+".mpg");
            return;
        }

        //check if the name we are replacing this file with already exists as a file
        String indexCheckSQL = "SELECT idFile FROM files WHERE idPath = " + pathId +" AND strFileName = "+ tools.sqlString(fileNameNoExt+".strm");
        int fileReplacingId = dbConnection.getSingleInt(indexCheckSQL);
        if(fileReplacingId == SQL_ERROR)
        {
            Config.log(WARNING, "Cannot continue. Failed to determine if this file already exists in the files table. SQL error while executing: "+ indexCheckSQL);
            return;
        }
        if(fileReplacingId > -1)
        {
            Config.log(INFO, "The .strm file already exists in the files table, so will change all movie, episode, and music video pointers to the existing .strm file entry");
            int totalChanges = updateAllVideoFilePointers(currentFileId, fileReplacingId);//change all library pointers to point to the .strm file
            Config.log(INFO, totalChanges+" library videos were successfully updated");
        }
        else
        {
            //a .strm doesnt alrady exist inthe database, change this file to be a .strm instead of a .mpg
            String sql ="UPDATE files SET "
                        + "strFileName =  " +tools.sqlString(fileNameNoExt+".strm") +" "
                        + "WHERE idFile = "+ currentFileId + " "
                        + "AND idPath = "+ pathId;
            boolean success = dbConnection.executeSingleUpdate(sql);
            Config.log(Config.DEBUG, success ? "Successfully changed" : "Failed to change" +" video file in XBMC's database ("+fileNameNoExt+".mpg) from .mpg to .strm using: "+sql);
        }

    }

    public boolean updateFilePointer(XBMCFile currentVideo, String newParentPath, String newFileName)
    {
         
        String table, idField;
        String videoType = currentVideo.getType()+"";
        if(videoType.equals(MOVIE))
        {
            table = "movie";
            idField = "idMovie";
        }
        else if(videoType.equals(TV_SHOW))
        {
            table = "episode";
            idField = "idEpisode";
        }
        else if(videoType.equals(MUSIC_VIDEO))
        {
            table = "musicvideo";
            idField = "idMVideo";
        }
        else
        {
            Config.log(WARNING, "Unkown video type, cannot update XBMC's database to point to the downloaded file.");
            return false;
        }
        File currentFileLocation = new File(currentVideo.getFinalLocation());
        int currentFileId = getFileId(getFullXBMCPath(currentFileLocation.getParentFile()), currentFileLocation.getName());
        if(currentFileId < 0)
        {
            Config.log(WARNING, "Cannot determine the current file id for this video. Will not be able to update XBMC to use the downloaded video for: "+ currentFileLocation);
            return false;
        }

        int videoId = dbConnection.getSingleInt("SELECT "+idField +" FROM "+ table +" WHERE idFile = "+ currentFileId);
        if(currentFileId < 0)
        {
            Config.log(WARNING, "Cannot determine the current video id for this video. Will not be able to update XBMC to use the downloaded video for (file id "+idField+"): "+ currentFileLocation);
            return false;
        }

        Config.log(Config.INFO, "Will update XBMC's file pointer for video id: " + videoId + " to new location of path: \"" + newParentPath+"\", filename: \""+newFileName+"\"");

        //get current info for checking if it's really changing
        Config.log(Config.DEBUG, "Determing what the current pathId and fileName are in the XBMC database...");
        int currentPathId = dbConnection.getSingleInt("SELECT idPath FROM files WHERE idFile = " + currentFileId);
        String currentPath = dbConnection.getSingleString("SELECT strPath FROM path WHERE idPath = " + currentPathId);
        String currentFileName = dbConnection.getSingleString("SELECT strFilename from files WHERE idFile = " + currentFileId);
        Config.log(Config.DEBUG, "The current pathId is: " + currentPathId + "("+currentPath+")");
        Config.log(Config.DEBUG, "The current fileName is: " + currentFileName);

        Config.log(Config.DEBUG, "Determing what the new pathId and fileName will be...");
        //get the new path id. If new path does not already exists, this mentod will add it and returm the new value
        int newPathId = getPathIdAndAddIfNeeded(newParentPath);//logs error if it fails
        if(newPathId == -1) return false;
        Config.log(Config.DEBUG, "The new pathId will be: " + newPathId);
        Config.log(Config.DEBUG, "The new fileName will be: " + newFileName);

        if(currentPathId == newPathId && currentFileName.equalsIgnoreCase(newFileName))
        {
            Config.log(Config.WARNING, "The values in the XBMC database alraedy have the location of the video at " + currentPath+currentFileName +". Will not update XBMC");
            return false;
        }

        //checks if this file already exists in the database, adds it if not. Returns the file id.
        Config.log(Config.DEBUG, "Determining what the new fileId will be based on newPathId and newFileName");
        int newFileId = getFileIdAndAddIfNeeded(newPathId, newFileName);
        if(newFileId == -1)
            return false;

        //update video entry to point to the new file id       
        String sql = "UPDATE "+table+" SET idFile = " + newFileId + " WHERE "+idField+" = " + videoId;
        Config.log(Config.DEBUG, "Updating video to point to new file: " + sql);
        boolean success = dbConnection.executeSingleUpdate(sql);
        if(success)
            Config.log(Config.INFO,"Successfully updated video's source file in XBMC, moving on to updating Thumbnails/Fanart");
        else
            Config.log(Config.ERROR, "Failed to update video's source file in XBMC. The video at " +currentPath+currentFileName+" in XBMC will remain at same location");       

        if(success)
        {
            //update the thumbnail/fanart
            String previousFullFilePath = getFullXBMCPath(currentFileLocation);
            String newFullFilePath = newParentPath+newFileName;
            boolean moved = moveThumbnails(previousFullFilePath, newFullFilePath);
            if(!moved)
                Config.log(Config.WARNING, "Thumbnail/fanart not found (or errors renaming) for the video at " +previousFullFilePath);
        }
        return success;
    }

    public int updateAllVideoFilePointers(int from, int to)
    {
        int totalChanges = 0;
        totalChanges+= dbConnection.executeMultipleUpdate("UPDATE movie SET idFile = "+ to +" WHERE idFile = "+ from);
        totalChanges+= dbConnection.executeMultipleUpdate("UPDATE episode SET idFile = "+ to +" WHERE idFile = "+ from);
        totalChanges+= dbConnection.executeMultipleUpdate("UPDATE musicvideo SET idFile = "+ to +" WHERE idFile = "+ from);
        return totalChanges;
    }

    /*
     * Change extension from .mpg to .strm for .mpg's that have been successfully added to the library
     */
    public int updateFilePointers(File folder)
    {
        Config.setShortLogDesc("DBUpdate:"+folder.getName());
        int renameCount = 0;
        try
        {
            if(!folder.exists() || !folder.isDirectory())
            {
                Config.log(WARNING, "The directory \""+folder+"\" does not exist or is invalid. Skipping .mpg-->.strm renaming for this directory.");
                return 0;
            }
            
            Collection<File> files = FileUtils.listFiles(folder, new String[]{"mpg"}, true);
            int filesNotInDB = 0;
            int renameFail = 0;
            for(File mpg : files)
            {
                String path = mpg.getPath();
                String sql = "SELECT idFile "
                    + "FROM files "
                    + "WHERE idPath IN(SELECT idPath FROM path WHERE strPath = "+tools.sqlString(getFullXBMCPath(mpg.getParentFile()))+") "
                           + "AND strFileName = "+tools.sqlString(mpg.getName());
                int fileId = dbConnection.getSingleInt(sql);

                if(fileId == SQL_ERROR)
                {
                    Config.log(ERROR, "Failed to check if file exists in XBMC's database using: "+ sql);
                    continue;
                }
                
                //System.out.println(sql);
                if(fileId > -1)//means that XBMC added the mpg to the library. now replace it with a strm
                {
                    Config.log(INFO, "Found video in libray, now changing extension from .mpg to .strm for: "+ mpg);
                    File strm = new File(path.substring(0, path.lastIndexOf(".mpg"))+".strm");                    
                    if(strm.exists()) strm.delete();//it shouldn't exist yet, but if it does, get it out of the way
                    
                    FileUtils.copyFile(mpg, strm);//using copy instead of rename seems to be more stable...
                    updateDatabaseExt(getFullXBMCPath(mpg.getParentFile()), strm.getName());//update in XBMC's database now that the actual file has been updated


                    if(strm.exists())
                    {
                        //update thumbnails because the path has changed
                        moveThumbnails(getFullXBMCPath(mpg), getFullXBMCPath(strm));
                    
                        renameCount++;
                        boolean deleted = mpg.delete();//clear out the old mpg (no longer needed since XBMC has already scraped with it
                        if(!deleted)Config.log(WARNING, "Cannot delete .mpg that is no longer needed at: "+ mpg);
                    }
                    else
                    {
                        Config.log(Config.WARNING,"Failed to rename .mpg file to .strm: "+ mpg);
                        renameFail++;
                    }
                }
                else// not added to the library yet
                {
                    Config.log(Config.INFO,"SKIPPING: Not changing to .strm because video is not yet in XBMC's library: "+ mpg + (Config.LOGGING_LEVEL==DEBUG ? " (as determined by: "+sql+")":""));
                    filesNotInDB++;
                }
            }
            Config.log(Config.NOTICE, "Renamed " +renameCount +" out of "+files.size()+" .mpg "+folder.getName()+" files "
                    + "from .mpg to .strm in " + folder +"."+LINE_BRK+filesNotInDB +" videos were not renamed because they are not yet in the XBMC database."
                    + " "+renameFail+" videos failed to be renamed.");
            
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR,"Failed while changing files from .mpg to .strm in "+folder+": "+x.getMessage(),x);
        }
        return renameCount;
    }       
    
    public int getVideoId(String videoType, int fileId)
    {
        //get the path id first
        String sql;
        if(MOVIE.equalsIgnoreCase(videoType))
            sql = "SELECT idMovie FROM movie WHERE idFile = " + fileId;
        else if(TV_SHOW.equalsIgnoreCase(videoType))//tv episode
            sql = "SELECT idEpisode FROM episode WHERE idFile = " + fileId;
        else if(MUSIC_VIDEO.equalsIgnoreCase(videoType))
            sql = "SELECT idMvideo FROM musicvideo where idFile = "+fileId;
        else
        {
            Config.log(Config.ERROR, "Updating pointer is currently only available for TV Shows, Movies, and Music Videos. \""+videoType+"\" is not supported.");
            return SQL_ERROR;
        }
        Config.log(Config.DEBUG, "Getting video ID with: " + sql);
        int id = dbConnection.getSingleInt(sql);
        return id;
    }

    /*
     * Gets the filename only. Supports stacking for multiple files
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
    private boolean moveThumbnails(String currentVideoLocation, String newVideoLocation)
    {
        //determine what the current hash is and what the new hash wil be.
        final String stackID = "stack://";//if its stacked, don't include the parent path
        
        int stackIndx = currentVideoLocation.indexOf(stackID);
        if(stackIndx != -1) currentVideoLocation = currentVideoLocation.substring(stackIndx, currentVideoLocation.length());
        String currentHash = Hash.generateCRC(currentVideoLocation);
        Config.log(Config.DEBUG, "Current hash, generated from \""+currentVideoLocation+"\" = " + currentHash);

        stackIndx = newVideoLocation.indexOf(stackID);
        if(stackIndx != -1) newVideoLocation = newVideoLocation.substring(stackIndx, newVideoLocation.length());
        String newHash = Hash.generateCRC(newVideoLocation);
        Config.log(Config.DEBUG, "New hash, generated from \""+newVideoLocation+"\" = " + newHash);

        int originalNumber = 0, numberRenamed =0, numberFailedToRename = 0;    
        for(String folder : Config.XBMC_THUMBNAILS_FOLDERS)
        {
            if(new File(folder).exists())
                Config.log(Config.INFO, "Updating Thumbnails and Fanart. Changing from hash of " + currentHash + " to " + newHash +" for extensions "
                        + Arrays.toString(Config.KNOWN_XBMC_THUMBNAIL_EXTENSIONS) +" in " +folder);
            else
            {
                Config.log(Config.WARNING, "Not updating Thumbnails/Fanart hash names because this thumbnails folder specified in Config.xml ("+folder+") does not exist.");
                return false;
            }        

            for(String extension : Config.KNOWN_XBMC_THUMBNAIL_EXTENSIONS)//.tbn, .dds
            {
                //look for thumbnail
                String currentThumbLocation = folder + "\\Video\\"+currentHash.substring(0,1)+"\\"+currentHash+extension;
                File currentThumb = new File(currentThumbLocation);

                //look for auto- thummbnail
                String currentAutoThumbLocation = folder + "\\Video\\"+currentHash.substring(0,1)+"\\auto-"+currentHash+extension;
                File currentAutoThumb = new File(currentAutoThumbLocation);

                //look for fanart
                String currentFanartLocation = folder + "\\Video\\Fanart\\"+currentHash+extension;
                File currentFanart = new File(currentFanartLocation);

                //look for auto- fanart
                String currentAutoFanartLocation = folder + "\\Video\\Fanart\\auto-"+currentHash+extension;
                File currentAutoFanart = new File(currentAutoFanartLocation);

                //look for bookmarks
                //TODO: implement

                Config.log(Config.DEBUG,("Checking for thumbnail at " + currentThumb));
                if(currentThumb.exists())
                {
                    Config.log(Config.DEBUG, "Thumbnail was found, attempting to rename.");
                    originalNumber++;
                    boolean success = renameThumb(currentThumb, newHash, extension);
                    if(success) numberRenamed++;
                    else numberFailedToRename++;
                }
                else
                    Config.log(Config.DEBUG, "Thumbnail does not exist");

                Config.log(Config.DEBUG,("Checking for auto-generated thumbnail at " + currentAutoThumbLocation));
                if(currentAutoThumb.exists())
                {
                    Config.log(Config.DEBUG, "Auto-generated thumbnail was found, attempting to rename.");
                    originalNumber++;
                    boolean success = renameThumb(currentAutoThumb, newHash, extension);
                    if(success) numberRenamed++;
                    else numberFailedToRename++;
                }
                else
                    Config.log(Config.DEBUG, "Auto-generated thumbnail does not exist");


                Config.log(Config.DEBUG,("Checking for fanart at " + currentFanart));
                if(currentFanart.exists())
                {
                    Config.log(Config.DEBUG, "Fanart was found, attempting to rename.");;
                    originalNumber++;
                    boolean success = renameThumb(currentFanart, newHash, extension);
                    if(success) numberRenamed++;
                    else numberFailedToRename++;
                }
                else
                    Config.log(Config.DEBUG, "Fanart does not exist");

                Config.log(Config.DEBUG,("Checking for auto-generated fanart at " + currentAutoFanart));
                if(currentAutoFanart.exists())
                {
                    Config.log(Config.DEBUG, "Auto-generated fanart was found, attempting to rename.");
                    originalNumber++;
                    boolean success = renameThumb(currentAutoFanart, newHash, extension);
                    if(success) numberRenamed++;
                    else numberFailedToRename++;
                }
                else
                    Config.log(Config.DEBUG, "Auto-generated fanart does not exist");
            }
        }//end looping thru thumbnail folder(s)

        Config.log(Config.INFO, "Found " + originalNumber + " images, successfully renamed " + numberRenamed + ", failed to rename " + numberFailedToRename);
        if(originalNumber == 0) 
        {
            Config.log(Config.WARNING, "No Thubmnails/Fanart found for video at " + currentVideoLocation+". Thumbs/Fanart will not be renamed to match new video location at: " + newVideoLocation);
            return true;//return true to avoid sending an addition error
        }
        else
            return numberRenamed > 0 && numberFailedToRename == 0;
        
    }
    
    public static boolean renameThumb(File currentThumb, String newHash, String extension)
    {
        try
        {
            String currentLocation = currentThumb.toString();
            boolean autoGenerated = currentThumb.toString().toLowerCase().contains("\\auto-");//auto-[hash].ext
            boolean isFanart = currentThumb.toString().toLowerCase().contains("\\fanart\\");//fanart folder
            Config.log(Config.DEBUG, "Moving Image: autoGenerated="+autoGenerated +", isFanart="+isFanart);

            File newThumb;
            String parent = currentThumb.getParent();
            if(isFanart)//dont need to worry about the start char folder, all go in the same Fanart folder
                newThumb = new File(parent + "\\"+ (autoGenerated ? "auto-"+newHash : newHash) +extension);
            else//need the single char folder to be correct
            {
                parent = parent.substring(0, parent.lastIndexOf("\\"));//now it looks like ...Thumbnails\Video
                parent += "\\"+newHash.substring(0, 1);//add the single letter folder
                newThumb = new File(parent +"\\"+(autoGenerated ? "auto-"+newHash : newHash) +extension);
            }

            if(newThumb.exists())
            {
                Config.log(Config.INFO, "The destination file already exists at: " + newThumb+". Will attempt to replace it");
                boolean deleted = newThumb.delete();//delete so it can be replaced by the new thumb
                if(!deleted)Config.log(Config.INFO, "Can not overwrite. Check if the image is currently in open in another program: " + newThumb);
            }
            
            boolean success = currentThumb.renameTo(newThumb);
            Config.log(Config.INFO, (success ? "SUCCESSFUL" : "FAILED") + ": Moving image from " +currentLocation + " to " + newThumb);
            return success;
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR, "Exception encountered while trying to Renaming from thumbnail: " + x, x);
            return false;
        }
    }           

    public int getPathIdFromPathString(String path)
    {
        String sql = "SELECT idPath FROM path WHERE strPath = " + tools.sqlString(path);
        Config.log(Config.DEBUG, "Getting pathId with: " + sql);
        int pathId = dbConnection.getSingleInt(sql);
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
            String sql = "SELECT idFile FROM files WHERE idPath = " + pathId + " AND strFilename = " + tools.sqlString(fileName);
            Config.log(Config.DEBUG, "Getting fileId with: " + sql);
            fileId = dbConnection.getSingleInt(sql);
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
            
            ResultSet rs = dbConnection.getStatement().executeQuery(sql);
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
            dbConnection.closeStatement();
        }
         return xbmcFiles;
    }
    private int getFileIdAndAddIfNeeded(int pathId, String fileName)
    {
                                                                //path and filename are a unique index
        String getFileIdsql = "SELECT idFile FROM files WHERE idPath = " +pathId+ " AND strFileName = "+ tools.sqlString(fileName);
        int fileId = dbConnection.getSingleInt(getFileIdsql);
        if(fileId == SQL_ERROR)
        {
            Config.log(Config.ERROR, "Failed to get file id using sql: " + getFileIdsql);
            return -1;
        }

        if(fileId != -1)
            Config.log(Config.DEBUG, "The new file already exists in the database, do not need to create a new entry. FileId: " + fileId + " found from " + getFileIdsql);
        else //if(fileId == -1)//not found, add it
        {
            Config.log(Config.INFO, "No fileId found from query: " + getFileIdsql+"; Will add an entry for this file to the database.");
            String insertFileIdsql = "INSERT INTO files(idPath, strFilename) VALUES (" + pathId +", " + tools.sqlString(fileName)+")";
            Config.log(Config.DEBUG, "Inserting new fileId with: " + insertFileIdsql);
            boolean success = dbConnection.executeSingleUpdate(insertFileIdsql);
            if(!success)
            {
                Config.log(Config.ERROR, "Could not insert new fileId using " + insertFileIdsql +"; Will not update XBMC.");
                return -1;
            }
            else//succesful insert, now get
            {
                fileId = dbConnection.getSingleInt(getFileIdsql);//get the newly added value
                Config.log(Config.INFO, "Successfully added. The new file id is now " + fileId);
            }
        }
        return fileId;
    }

    /*
     * path - the new path that should be found and added if it doesnt exist.
     * pathIdToClone - if this is >0, and the new path needs to be added to the db,
        it will used the scraper settings from this path id,
        in order to make sure you can refresh scraped info
     */

    private int getPathIdAndAddIfNeeded(String path)
    {
        String getPathIdsql = "SELECT idPath FROM path WHERE strPath = " + tools.sqlString(path);
        int pathId = dbConnection.getSingleInt(getPathIdsql);

        if(pathId != -1)
            Config.log(Config.DEBUG, "Found pathId: " + pathId + " from " + getPathIdsql);
        else //if(pathid == -1)//not found, add it
        {
            Config.log(Config.INFO, "No pathId exists for the path \""+path+"\", will add one to the database.");
            String insertPathIdsql = "INSERT INTO path(strPath) VALUES (" + tools.sqlString(path)+")";
            Config.log(Config.DEBUG, "Inserting new pathid with: " + insertPathIdsql);
            boolean success = dbConnection.executeSingleUpdate(insertPathIdsql);
            if(!success)
            {
                Config.log(Config.ERROR, "Could not insert new pathId using " + insertPathIdsql +", will not update XBMC.");
                return -1;
            }
            else//succesful insert, now get
            {
                pathId = dbConnection.getSingleInt(getPathIdsql);//get the newly added value
                Config.log(Config.INFO, "The new path id is now " + pathId);
            }
        }

        return pathId;
    }

    //set the download path's scraper settings to the same as the streaming path's settings
    public void synchronizePathSettings()
    {
        //synchronize downloaded paths with the settings from the streaming dropbox paths
        final String[] contentDirs = new String[]{"TV Shows", "Movies", "Music Videos"};        
        for(int i=0;i<contentDirs.length;i++)
        {
            String contentDir = contentDirs[i];
            String downloadedPath = getFullXBMCPath(new File(Config.DOWNLOADED_VIDEOS_DROPBOX+SEP+contentDir));
            String streamingPath = getFullXBMCPath(new File(Config.DROPBOX+SEP+contentDir));

            int downloadPathId = getPathIdAndAddIfNeeded(downloadedPath);
            String getPathIdsql = "SELECT idPath FROM path WHERE strPath = " + tools.sqlString(streamingPath);
            int streamingPathId = dbConnection.getSingleInt(getPathIdsql);
            if(streamingPathId == SQL_ERROR)
            {
                Config.log(ERROR, "Failed to execute sql to determine path id for streaming path: "+ getPathIdsql);
                continue;
            }
            if(streamingPathId < 0)
            {
                Config.log(INFO, "There is currently no streaming path in XBMC's database for "+contentDir+". Skipping sync for this path.");
                continue;
            }
            if(downloadPathId < 0)
            {
                Config.log(ERROR, "Failed to get path id for downloaded folder. Cannot contiue sync for this path: "+ downloadedPath);
                continue;
            }
                        
            Config.log(INFO, "Synchronizing path/scraper settings from streaming path ("+streamingPath+") to download path ("+downloadedPath+")");
             String clone = "UPDATE path "
                    + "SET "
                    //need to use double sub queries to avoid mysql error 1093. see here: http://whatislinux.net/mysql/mysql-error1093-you-cant-specify-target-table-jos_content-for-update-in-from-clause
                    + "strContent = (SELECT strContent FROM (SELECT * FROM path) AS c1 WHERE c1.idPath = "+streamingPathId+"), "
                    + "strScraper = (SELECT strScraper FROM (SELECT * FROM path) AS c2 WHERE c2.idPath = "+streamingPathId+"), "
                    + "useFolderNames = (SELECT useFolderNames FROM (SELECT * FROM path) AS c3 WHERE c3.idPath = "+streamingPathId+"), "
                    + "strSettings = (SELECT strSettings FROM (SELECT * FROM path) AS c4 WHERE c4.idPath = "+streamingPathId+"), "
                    + "scanRecursive = (SELECT scanRecursive FROM (SELECT * FROM path) AS c5 WHERE c5.idPath = "+streamingPathId+"), "
                    + "strHash = 0, "
                    + "noUpdate = 1, "//exclude this new path from updates because it does not need to be scanned. The values are added automatically by this program
                    + "exclude = 0 "//not positive what this does different than noUpdate, but if it is set to 1, movie info cannot be refreshed.
                    + "WHERE idPath = "+downloadPathId;
             
            boolean cloneSuccess = dbConnection.executeSingleUpdate(clone);
            if(cloneSuccess)
                Config.log(INFO, "Successfully cloned settings from streaming path and set noUpdate=true for the download path.");
            else
                Config.log(WARNING, "Failed to clone settings from streaming path. The downloaded videos' information may not be able to be refreshed in XBMC's GUI");            
        }
    }
}