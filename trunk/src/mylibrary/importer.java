package mylibrary;
import java.io.File;
import java.sql.ResultSet;
import utilities.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import utilities.JDownloaderInterface;

public class importer extends Config implements Constants
{
    public static void main(String[] args)
    {
        long start = System.currentTimeMillis();
        if(args.length == 0)
        {
            Config.log(INFO, "Attempting to auto-determine base directory. If this fails, specify the base directory of this program as a command line parameter.");
            BASE_PROGRAM_DIR = getBaseDirectory();
        }
        else 
            BASE_PROGRAM_DIR = args[0];
        
        Config.log(NOTICE, "Base program dir = "+ BASE_PROGRAM_DIR);
        
        importer i = null;
        try
        {            
            i = new importer();

            if(i.loadedConfig())//load config
            {
                if(i.importVideos())//import vidoes
                {
                    //clean up
                    i.dropboxCleanUp();
                    i.fileExpiration();
                }
            }
        }
        catch(Exception x)
        {
            log(ERROR, "Cannot continue, general error occurred: "+ x,x);            
        }
        finally
        {
            try{Archiver.globalSummary();}catch(Throwable x){}//dont care much if this fails, really care about Config.end()
            long end = System.currentTimeMillis();
            long seconds = (end-start) / 1000;

            setShortLogDesc("Ending");
            Config.log(NOTICE,"Done... Total processing time: "+ (seconds/60) +" minute(s), "+ (seconds%60)+" second(s)");
            Config.end();//stop all the background processes            
        }
    } 

    private boolean loadedConfigSuccessfully = false;
    public importer()
    {
        //Load Config
        super(MY_LIBRARY);
                  
        loadedConfigSuccessfully = loadConfig();
        setLoadedConfig(loadedConfigSuccessfully);
        if(!loadedConfigSuccessfully)
        {
            log(ERROR, "Failed while loading Configuration and testing connections... cannot continue. Please check your settings in Config.xml");            
            return;
        }

         //summary of the sources/subfolders
        //for(Source src : ALL_SOURCES)
///            Config.log(NOTICE, "Found source <"+ src.getName() +"> ("+src.getPath()+") with "+ src.getSubfolders().size() +" subfolders");

    }
    private void setLoadedConfig(boolean b)
    {
        this.loadedConfigSuccessfully = b;
    }
    public boolean loadedConfig()
    {
        return loadedConfigSuccessfully;
    }


    /**
     * This is the guts of the program. Imports videos from configured sources.
     * @return 
     */
    public boolean importVideos()
    {
                        
        setShortLogDesc("Init");
        XbmcJsonRpc jsonRPC = new XbmcJsonRpc(JSON_RPC_SERVER);     
        
        if(false)
        {
            //Config.LOGGING_LEVEL = DEBUG;
            jsonRPC.getLibraryMusic(true);
            jsonRPC.getLibraryVideos(true);
            if(1==1) return false;
        }
        
        //check if XBMC should be restarted
        if(RESTART_XBMC)
        {            
            String restartCmdFile = BASE_PROGRAM_DIR+SEP+"res"+SEP+"RestartXBMC.cmd";
            log(NOTICE, "Restart XBMC is enabled, will send Quit command to XBMC, then execute restart script at: "+ restartCmdFile);
            //set quit commnd
            if(jsonRPC.ping())
            {
                JSONObject response = jsonRPC.callMethod("Application.Quit", 1, null);
                if(response != null)
                {
                    try
                    {
                        String result = response.getString("result");
                        if(result.equalsIgnoreCase("OK"))
                        {
                            log(NOTICE, "Quit command successfully sent to XBMC.");
                            int sec = 10;
                            log(INFO, "Waiting "+ sec +" seconds for XBMC to gracefully quit.");
                            //wait for XBMC to shutdown before executing restart command
                            try{Thread.sleep(1000 * sec);}catch(Exception x){}
                        }
                        else
                            log(WARNING, "Quit command was not successfully sent to XBMC, response = "+ response);
                    }
                    catch(Exception x)
                    {
                        log(ERROR, "Unknown reponse from XBMC: "+ response,x);
                    }                    
                }
                else
                    log(WARNING, "Failed to call XBMC.Quit. XBMC may not be running. Will now execute restart script at: "+ restartCmdFile);
            }
             else log(INFO, "JSON-RPC could not connect to XBMC, will execute restart script and test connectivity again: "+ restartCmdFile);
            
            try//always execute the restart script
            {
                ProcessBuilder pb = new ProcessBuilder("\""+restartCmdFile+"\"");
                pb.redirectErrorStream();
                log(INFO, "Executing restart script for XBMC.");
                java.lang.Process pr = pb.start();//don't read input/wait because it won't end until XBMC ends                
                log(INFO, "Restart executed. Waiting for JSON-RPC connectivity to resume...");
                try{Thread.sleep(3000);}catch(Exception x){}
                boolean connected = false;
                for(int i=0;i<3;i++)
                {
                     connected = jsonRPC.ping();
                     if(connected) break;
                     else try{Thread.sleep(2000);}catch(Exception ignored){}//wait & try again until loop runs out
                }
                if(!connected)
                {
                    log(ERROR, "It appears XMBC did not restart. JSON-RPC connectivity cannot be re-established. "
                            + "Ending. Please check your restart command, located at: "+ restartCmdFile);
                    return false;
                }
                else
                    log(INFO, "XBMC has successfully started, JSON-RPC connectivity re-established.");
            }
            catch(Exception x)
            {
                log(ERROR, "Failed while executing XBMC restart script. Exiting.",x);
                return false;
            }
         }//end XBMC restart

        //do a connectivity test
        log(INFO, "Testing connectivity to JSON-RPC interface...");
        boolean connected = jsonRPC.ping();
        log(INFO, "JSON-RPC connected = "+ connected);
        if(!connected)
        {
            log(ERROR, "Cannot continue because JSON-RPC is not connected. Please check that XBMC is running at: "+ JSON_RPC_SERVER
                    +(!USE_HTTP ?" and TCP Port 9090 is not blocked":" and the XBMC webserver is running at port: "+ XBMC_SERVER_WEB_PORT));
            return false;
        }
        
         for(Source source : ALL_SOURCES)
         {
             if(source.getSubfolders().isEmpty())
             {
                 log(WARNING, "The source named "+source.getName()+" has no subfolders associated with it. Nothing will be added from this source.");
                 continue;
             }
             
             if(!valid(source.getPath()))
             {
                 log(INFO,"This source's path was not specified, will check list of video sources in XBMC for a matching label named \""+source.getName()+"\"");
                 try
                 {
                     Map<String, Object> sourcesParams = new HashMap<String,Object>();
                     sourcesParams.put("media", "video");
                     JSONObject xbmcSources = jsonRPC.callMethod("Files.GetSources", 1, sourcesParams);
                     JSONObject result = xbmcSources.getJSONObject("result");
                     JSONArray sourceArray = result.getJSONArray("sources");
                     for(int i=0;i<sourceArray.length();i++)
                     {
                         JSONObject nextSource = sourceArray.getJSONObject(i);
                         String label = nextSource.getString("label");
                         log(DEBUG, "Found label \""+label+"\", looking for match on: \""+source.getName()+"\"");
                         if(label.equalsIgnoreCase(source.getName()))
                         {
                             String path = nextSource.getString("file");
                             log(INFO, "Found matching path from XBMC's sources, source \""+source.getName()+"\" maps to: "+path);
                             source.setPath(path);
                             break;
                         }
                     }
                 }
                 catch(Exception x)
                 {
                     log(ERROR, "Failed to find source's path from XBMC's source list, will skipp the source named \""+source.getName()+"\"",x);
                     continue;
                 }
                 
                 if(!valid(source.getPath()))
                 {
                     log(ERROR, "No source named \""+source.getName()+"\" was found in XBMC's video source list. Will skip this source and all subfolders.");
                     continue;
                 }
             }

             
             if(false)
             {/*Disable all IceFilms support because their servers appparantly can't handle the bandwidth used by this program/icefilms plugin*/
                 if(source.getPath().toLowerCase().contains(ICEFILMS_IDENTIFIER_LC))
                 {
                     log(WARNING, "IceFilms support has been disabled. Skipping IceFilms source. See here for details: http://forum.icefilms.info/viewtopic.php?f=24&t=21205");
                     continue;
                 }
             }
             
             for(Subfolder subf : source.getSubfolders())
             {
                //find the subfolder
                String fullPathLabel  = subf.getFullName().replace("/", DELIM);//TODO: allows /'s to be escaped in config.xml if they appear in the name natively
                setShortLogDesc("Find:Subfolder");
                //get the subfolder from JSON-RPC to determine what xbmc path it's name maps to
                log(INFO, "Searching for subfolder: " + escapePath(fullPathLabel));
                
                XBMCFile subfolderDir =  jsonRPC.getSubfolderDirectory(subf);
                if(subfolderDir == null) continue;
                setShortLogDesc("Found!");
                log(NOTICE, source.getName()+"'s subfolder \""+subf.getFullName() +"\" maps to source: " + subfolderDir.getFile() + " ("+subfolderDir.getFullPathEscaped()+")");
                if(subf.isRegexName())
                    subf.setRegexMatchingName(subfolderDir.getFullPathEscaped());

                //get list of files in this subfolder
                setShortLogDesc("Search:"+source.getName());
                Map<String,Object> params = new LinkedHashMap<String,Object>();
                params.put("media", "video");

                //seperate thread for archiving simultaneously
                Archiver archiver = new Archiver(source);
                BlockingQueue<XBMCFile> filesInThisSubfolder = new ArrayBlockingQueue<XBMCFile>(20000, true);//init capacity, fair
                
                archiver.start(subf, filesInThisSubfolder);
                
                log(NOTICE, "Finding all matching videos under subfolder: "+ subfolderDir.getFullPathEscaped());
                try
                {
                    jsonRPC.getFiles(//recursively list all files in this subdirectory (based on filters)
                        subf,
                        subfolderDir.getFile(),
                        subfolderDir.getFullPath(),
                        FILES_ONLY,
                        filesInThisSubfolder);                                           
                }
                catch(Exception x)
                {
                    log(ERROR, "Failed to get files from JSON-RPC",x);
                }
                finally
                {
                    archiver.canStop();//let it know that the queue is not being fed any more so the archive can finishe once it runs out of files to check
                }
                log(NOTICE, "Done retrieving files from JSON-RPC for subfolder: "+subf.getFullName());

                //wait for archiver to finish if need
                if(!filesInThisSubfolder.isEmpty())
                    log(INFO, "There are "+ filesInThisSubfolder.size() +" files that stil need to be archived, waiting for archiving to finish before proceeding to next subfolder.");
                while(!archiver.isStopped())
                    try{Thread.sleep(250);}catch(Exception ignored){}

                log(NOTICE, "Archiving has finished for "+ subf.getFullName()+".");
                if(!filesInThisSubfolder.isEmpty())
                    log(ERROR, "Archiver has finished but the queue was not emptied, unexpected. Some videos may not be archived!");

                if(archiver.archiveSuccess == 0 && archiver.archiveSkip == 0)//no files found
                {
                    subf.getSource().setJSONRPCErrors(true);//if we didn't find any files, assume an error occurred
                    log(ERROR, "Since no files were successfully archived in this subfolder, assuming an error occured. "
                            + "This will prevent the dropbox from being cleaned for files from this source");
                }

                source.trackArchivedVideos(archiver.filesArchived, archiver.videosSkippedBecauseAlreadyArchived); //need to track for clean-up purposes
            }//end looping thru the subfolders of this source
             
            //clean the dropbox for this source's archived videos (as long as there weren't errors getting the videos from this source)
            if(true || //for now, always clean, regardless of errors
                    !source.hadJSONRPCErrors())//TODO: investigate this. hadJSONErrors was true too many times for large sources, such as PlayOn, preventing clean-ups. Maybe the clean-up thresholds in condig.xml are enough?
            {
               cleanDropbox(source);
            }
            else
            {
                setShortLogDesc("Skip-Clean");
                log(NOTICE, "Skipping cleaning dropbox for videos from \""+source.getName()+"\" because there were error(s) "
                        + "while getting File list for its Subfolders from JSON-RPC interface/PlayOn");
                setShortLogDesc("");
            }            
        }//end looping thru Sources
        
        if(Archiver.globalarchiveSuccess == 0)
        {
            Config.log(NOTICE, "No videos were successfully archived, skipping XBMC Library scan.");
        }
        else
        {
             Config.log(NOTICE, Archiver.globalarchiveSuccess +" videos were successfully archived/updated. Triggering XBMC content scan");
             //trigger XBMC databaase update and integrate meta-data changes. Also do manual archiving after intitial update (if enabled)
             for(int loop=1; loop<=(MANUAL_ARCHIVING_ENABLED ? 2 : 1);loop++)//need to loop 2 times if manual archiving is enabled, 2nd time is after .nfo generation
             {
                setShortLogDesc("ContentScan"+(MANUAL_ARCHIVING_ENABLED ? "-"+loop :""));
                log(INFO, "Getting baseline number of videos in library before triggering update.");
                int prevCount = jsonRPC.getLibraryVideos().size();

                log(NOTICE, "Triggering content scan via JSON-RPC interface by calling VideoLibrary.Scan");
                JSONObject result = jsonRPC.callMethod("VideoLibrary.Scan", 1, null);
                String strResult;
                try{strResult = result.getString("result");}catch(Exception x){strResult = "[no result available]";}
                log(NOTICE, "VideoLibrary.Scan Result = \"" + strResult+"\"");
                if(!strResult.equalsIgnoreCase("OK"))
                {
                    log(WARNING, "JSON-RPC XBMC interface for method 'VideoLibrary.Scan' failed; Will not add meta-data or do Manual Archiving. Result = \""+strResult+"\"");
                }
                else//update trigger was successful
                {
                    //wait until XBMC is done adding videos to the library.
                    while(true)
                    {
                        try
                        {
                            log(NOTICE, "Waiting "+LIBRARY_SCAN_WAIT_MINUTES+" minutes for more videos to be added to XBMC's library");
                            Thread.sleep((long) (1000 * 60 * LIBRARY_SCAN_WAIT_MINUTES));
                        }
                        catch(Exception x){}

                        int newCount = jsonRPC.getLibraryVideos().size();
                        int difference = newCount-prevCount;
                        String strDif = difference > 0 ? "+"+difference : difference+"";
                        log(NOTICE, newCount +" videos found in library. (Previous count was "+ prevCount+", a change of " + strDif +")");
                        if(newCount <= prevCount)
                        {
                            log(NOTICE, "Since no more videos were added in the last " +LIBRARY_SCAN_WAIT_MINUTES + " minutes, continuing on...");
                            break;
                        }
                        else
                            log(NOTICE, "Will wait longer because it appears videos are still being added to the library.");
                        prevCount = newCount;
                    }
                    setShortLogDesc("XBMC-"+DATABASE_TYPE);
                    updateDatabase();

                    if(MANUAL_ARCHIVING_ENABLED)
                    {
                        if(loop == 1)//on the first loop. generate .nfo's if needed
                        {
                            boolean videosWereManuallyArchived = manualArchiveIfNeeded(Archiver.allVideosArchived, jsonRPC.getLibraryVideos());
                            if(videosWereManuallyArchived)
                            {
                                //trigger a content scan, but no need to wait for it
                                log(NOTICE, "Since videos were manually archived with .nfo files, triggering another content scan to catch manually archived videos.");
                            }
                             else
                            {
                                log(INFO, "No new .nfo's were created, will not trigger another content scan.");
                                 break;//no need to loop again if no .nfo's were generated
                            }
                        }
                    }
                    else
                    {
                        log(NOTICE, "Will not manually archive any videos (.nfo) because manual archiving is disabled in Config file.");
                        continue;//skip the second loop
                    }
                }//end if update trigger was successful
            }//end loop for manual archiving if needed

        }//end if any videos were successfully scanned
        processDownloads();//* This still needs some work to cleanup when a download fails.
        setShortLogDesc("");
        return true;//got to end w/o issue
    }
    
    public static void processDownloads()
    {

        setShortLogDesc("Download");
        //LOGGING_LEVEL=DEBUG;

        boolean checkedConnectivity = false;//check the first time we find a download=true video
        
        
        int numberOfUnfinishedDownloads = archivedFilesDB.getSingleInt("SELECT count(id) FROM Downloads WHERE status != "+ tools.sqlString(DOWNLOAD_FINAL));
        if(numberOfUnfinishedDownloads != 0)
        {
            Config.log(INFO, "There are currently "+ numberOfUnfinishedDownloads +" unfinished downloads. Will check status and clean up before checking if more can be added.");
            checkCurrentDownloadStatuses();
            cleanUpDownloads();
            setShortLogDesc("Download");
            //see if it changes
            numberOfUnfinishedDownloads = archivedFilesDB.getSingleInt("SELECT count(id) FROM Downloads WHERE status != "+ tools.sqlString(DOWNLOAD_FINAL));
            Config.log(INFO, "After status check and clean up, there are "+ numberOfUnfinishedDownloads +" unfinished downloads.");
        }

        if(numberOfUnfinishedDownloads != 0)
        {
            log(INFO, "Skipping downloading more videos until the " + numberOfUnfinishedDownloads +" unfinished download"+(numberOfUnfinishedDownloads==1?"":"s")+" finish"+(numberOfUnfinishedDownloads==1?"es":""));
            return;
        }
        else//another download can be started
        {
            //check the files that were archived and filter for ones that are set to download=true
            downloadSearch:for(Map.Entry<File, XBMCFile> entry : Archiver.allVideosArchived.entrySet())
            {
                XBMCFile video = entry.getValue();
                if(video.getSubfolder() != null && video.getSubfolder().download())
                {//downloading is enabled for this video

                    File archivedFile = entry.getKey();
                    log(DEBUG, "Downloading is enabled for this video: "+ archivedFile);
                    if(!valid(DOWNLOADED_VIDEOS_DROPBOX) || !new File(DOWNLOADED_VIDEOS_DROPBOX).exists())
                    {
                        log(ERROR, "Downloading is enabled, but the downloaded dropbox is not valid. Pleaase specify a valid directory in the configuration file. Skipping all downloads.");
                        return;
                    }
                    
                    if(!checkedConnectivity)
                    {
                        checkedConnectivity=true;
                        log(INFO, "Checking connectivity to JDownloader at: "+ JDOWNLOADER_HOST);
                        JDownloaderInterface jdi = new JDownloaderInterface(JDOWNLOADER_HOST);
                        if(!jdi.isConnected())
                        {
                            log(WARNING, "Could not connect to JDownloader at: "+ JDOWNLOADER_HOST+". Will not process downloads...");
                            return;
                        }
                    }

                    String nameNoExt = tools.fileNameNoExt(archivedFile);
                    File downloaded = new File(nameNoExt+".downloaded");

                    if(downloaded.exists())
                    {
                        log(INFO,"Skipping, this file has already been downloaded because its extension has been changed to .downloaded: "+ downloaded);
                        continue downloadSearch;
                    }

                    String strm = nameNoExt+".strm";
                    archivedFile = new  File(strm);//the file in allVideoArchived may not be a strm if it was converted this run. Catch that here. All videos must be strms to be processed here.
                    if(!archivedFile.exists())
                    {
                        //will get here if it's an .mpg because it hasn't been added to xbmc's library yet
                        log(INFO, "The archived file at: "+ archivedFile +" does not exist. Skipping download processing for it. This usually means the video has not yet been added to XBMC's library");
                        continue downloadSearch;
                    }

                    //check if this strm has already been queued as a download
                    int archivedFileId = archivedFilesDB.getSingleInt("SELECT id FROM ArchivedFiles WHERE dropbox_location = "+tools.sqlString(strm));
                    if(archivedFileId < 0)
                    {
                        log(WARNING, "Failed to determine ID for archived file at: "+ strm +" Cannot continue with file download processing for this file...");
                        continue downloadSearch;
                    }

                    int downloadId = archivedFilesDB.getSingleInt("SELECT id FROM Downloads WHERE archived_file_id = "+ archivedFileId);
                    if(downloadId == SQL_ERROR)
                    {
                        log(WARNING, "SQL error while trying to determine download id, will skip download processing for this file: "+ strm);
                        continue downloadSearch;
                    }

                    //check if it's already been finalized
                    boolean downloadAlreadyComplete = DOWNLOAD_FINAL.equals(archivedFilesDB.getSingleString("SELECT status FROM Downloads WHERE id = "+ downloadId));
                    if(downloadAlreadyComplete)
                    {
                        log(INFO, "Skipping download processing for this video because it has previously been downloaded and archived: "+strm);
                        continue downloadSearch;
                    }

                    boolean alreadyQueuedAsDownload = downloadId > 0;//has a valid download id
                    if(!alreadyQueuedAsDownload)
                    {
                        log(INFO, "Will attempt to queue download video(s) for: "+ strm);
                        //read in the strm and parse the source strings, looking for valid URLs to download
                        List<String> urls = tools.readFile(archivedFile);
                        log(INFO, "Found "+ urls.size() +" source string"+(urls.size()==1?"":"s")+" from video, attempting to parse URL"+(urls.size()==1?"":"s")+" from "+(urls.size()==1?"it":"them"));
                        for(int i=0;i<urls.size();i++)
                        {
                            String s = urls.get(i);
                            String url = tools.getURLFromString(s);//get the URL from the strim
                                                        
                            if(tools.valid(url))
                            {
                                log(INFO, "Found URL: \""+url+"\" from source string: \""+s+"\"");
                                urls.set(i, url);//replace it with the valid URL found from the source string
                            }
                            else
                            {
                                Config.log(INFO, "Skipping downloading for this video because a valid URL could to be parsed from source string \""+s+"\" in strm at: "+ strm +".");
                                continue downloadSearch;// skip downloading for this file
                            }
                        }

                        //got the URL's, now attempt to queue the download in JDownloader                        
                        Map<String, String> urlFileMap = new LinkedHashMap<String,String>();
                        Config.log(INFO, "Will attempt to queue "+ urls.size() +" url"+(urls.size()==1 ? "":"s")+" in Jdownloader");

                        for(String url : urls)
                        {
                            JPackage queuedPackage = queueJdownload(url);
                            String downloadLocation = null;
                            if(queuedPackage != null && !queuedPackage.getDownloads().isEmpty())
                                downloadLocation = queuedPackage.getDownloads().get(0).getPathToDownloadedFile();

                            if(!valid(downloadLocation))
                            {
                                Config.log(WARNING, "Since the download was not successfully queued in JDownloader, skipping download for this video: "+ video.getFinalLocation());
                                continue downloadSearch;//skip this video. don't update db //TODO: cancel Jdownload for this file?
                            }
                            urlFileMap.put(url, downloadLocation);
                        }
                        

                        //get the final location that the downloaded video will be dropped to
                        String destNoExt = Archiver.getDroboxDestNoExt(Config.DOWNLOADED_VIDEOS_DROPBOX, video);
                        log(INFO, "The downloaded file(s) will be archived using this format: "+ destNoExt+".partx.ext");

                        log(INFO, "Updating database with information about the queued download");
                        //successfully queued, now update the database tables
                        String insert = "INSERT INTO Downloads (archived_file_id, started, status, dest_no_ext, compression) "
                            + "VALUES("+archivedFileId+", CURRENT_TIMESTAMP, " + tools.sqlString(DOWNLOAD_QUEUED)+
                            ", "+tools.sqlString(destNoExt)+", "+tools.sqlString(video.getSubfolder().getCompression())+")";

                        if(archivedFilesDB.executeSingleUpdate(insert))
                        {
                            log(DEBUG, "Added entry to Downloads table for this download.");
                            //get the new download id that was created from the insert
                            downloadId = archivedFilesDB.getSingleInt("SELECT id FROM Downloads WHERE archived_file_id = "+ archivedFileId);//unique index on archived file id
                            if(downloadId < 0)
                            {
                                Config.log(ERROR, "Failed to determine download ID for this download, will not be able to track the downloaded files. The downloaded file will not be archived correctly.");
                                //todo: revert db change?
                                continue downloadSearch;
                            }
                            else
                            {
                                //continue with the other updates (url-->file mapping)
                                for(Map.Entry<String,String> e : urlFileMap.entrySet())
                                {
                                    String url = e.getKey();
                                    File file = new File(e.getValue());//use File() object for db naming consistency when retrieveing later
                                    String insertFile = "INSERT INTO DownloadFiles (download_id, url, file) VALUES("+downloadId+", "+tools.sqlString(url)+", "+tools.sqlString(file.toString())+")";
                                    if(!archivedFilesDB.executeSingleUpdate(insertFile))
                                    {
                                        Config.log(ERROR, "Failed to add url-->file mapping to DownloadFiles table. The downloaded file may be incomplete and improperly archived. SQL = "+insertFile);
                                        //todo: revert?
                                    }
                                    else
                                    {
                                        log(DEBUG, "Added entry to DownloadFiles table for url: "+ url +", file: "+ file);
                                    }
                                }
                                log(INFO, "Done adding download information to the database.");
                                
                                //limit 1 at a time
                                log(NOTICE, "This download was successfully queued. Will not add more downloads until this one has finished.");
                                break downloadSearch;
                            }
                        }
                        else//inserting into the DB failed
                        {
                            Config.log(ERROR, "Failed to add record to Databse to track downloading file. The downloaded file will no be properly archived. SQL = " +insert);
                            continue downloadSearch;
                        }
                    }
                    else//already queued as a download, check where it's at
                    {
                        log(INFO, "This file was already queued as a download... skipping. Will check download status later: "+strm);
                        //will check later by querying the db in checkCurrentDownloadStatuses()
                    }
                }
                //may want to disable this log becasue it will generate a lot of output
                //else log(DEBUG, "Not downloading because download=false for video: "+ video.getFinalLocation());
            }

            checkCurrentDownloadStatuses();
            cleanUpDownloads();
        }//end if more downloads can be started        
    }

    static public void cleanUpDownloads()
    {
        //clean up the completed downloads if necessary
        //When a downloaded file is deleted from the filesystem, it will be caught here and the download entry will be removed from the DB
        setShortLogDesc("Download:Clean");
        String finalDownloadsSQL = "SELECT d.id, df.dropbox_location FROM DownloadFiles df, Downloads d WHERE d.status = "+ tools.sqlString(DOWNLOAD_FINAL) + " AND d.id = df.download_id";
        Map<Integer,List<String>> finalDownloads = new LinkedHashMap<Integer,List<String>>();//download id is key, list of files is value
        try
        {
            ResultSet rs = archivedFilesDB.getStatement().executeQuery(finalDownloadsSQL);
            while(rs.next())
            {
                int id = rs.getInt("id");
                String drobpoxLocation = rs.getString("dropbox_location");
                if(finalDownloads.get(id) == null)
                {//init
                    finalDownloads.put(id, new ArrayList<String>());
                }
                finalDownloads.get(id).add(drobpoxLocation);
            }
        }
        catch(Exception x)
        {
            log(ERROR, "Failed while getting list of final downloads using: "+ finalDownloadsSQL,x);
        }
        finally
        {
            archivedFilesDB.closeStatement();
        }


        findDeletedFiles: for(Map.Entry<Integer, List<String>> entry : finalDownloads.entrySet())
        {
            int download_id = entry.getKey();
            List<String> fileLocations = entry.getValue();
            for(String s : fileLocations)
            {
                if(!valid(s))
                {
                    log(ERROR, "Final download found, but dropbox_location is unknown. Cannot clean up.");
                    continue findDeletedFiles;
                }

                File f = new File(s);
                if(!f.exists() && (!tools.isNetworkShare(f) || tools.isShareAvailable(s)))
                {
                    removeDownloadEntryFromDB(download_id);
                }
            }
        }
    }

    static public void removeDownloadEntryFromDB(int download_id)
    {
        String strmFileLocation = archivedFilesDB.getSingleString("SELECT dropbox_location FROM ArchivedFiles WHERE id = (SELECT archived_file_id FROM Downloads WHERE id = "+ download_id+")");
        log(INFO, "Found a video that was downloaded that no longer exists. Removing download information from database for download id "+ download_id+". Downloaded from .strm file: "+ strmFileLocation);
        boolean deleted = archivedFilesDB.executeSingleUpdate("DELETE FROM Downloads WHERE id = "+ download_id);
        Config.log(deleted ? INFO : WARNING, (deleted ? "Successfully deleted":"Failed to delete") + " download entry from database.");
        int rowsDeleted = archivedFilesDB.executeMultipleUpdate("DELETE FROM DownloadFiles WHERE download_id = "+ download_id);
        Config.log(rowsDeleted > 0 ? INFO : WARNING, (rowsDeleted > 0 ? "Successfully deleted "+rowsDeleted:"Failed to delete") +" associated downloaded files from database");

        //change .strm name back
        if(valid(strmFileLocation))
        {
            String nameNoExt = tools.fileNameNoExt(new File(strmFileLocation));
            File strmFile = new File(nameNoExt+".strm");
            File downloadedFile = new File(nameNoExt+".downloaded");

            if(downloadedFile.exists())
            {
                //rename the .downloaded file back to normal .strm and let normal expiration take over for the .strm
                boolean renamed = downloadedFile.renameTo(strmFile);
                if(!renamed)
                    log(WARNING, "Failed to rename file \""+downloadedFile+"\" to \""+strmFile+"\"");
                else
                    log(INFO, "Successfully changed stream name back from .downloaded to .strm for file at: "+ strmFileLocation);
            }
            else
            {
                if(strmFile.exists())
                {
                    log(INFO, "The .strm file alrady exists where expected, not changing it: "+strmFileLocation);
                }
                else
                {
                    log(INFO, "Neither the .strm or the .downloaded file exists anymore. This video will no longer be available unless it is archived again in a future scan: "+ strmFileLocation);
                }
            }
        }
    }

    //TODO: clean up this function. Hard to read!
    static public void checkCurrentDownloadStatuses()
    {
        setShortLogDesc("Download:Check");
        try
        {
           List<Download> incompleteDownloads = tools.getIncompleteDownloads();
           log(INFO, "Will check status for "+ incompleteDownloads.size() +" incomplete downloads.");
           downloadCheck:for(Download download : incompleteDownloads)
           {
                int downloadId = download.getDownloadID();
                int archivedFileId = download.getArchivedFileID();
                String status = download.getStatus();                
                Long downloadStarted = download.getStartMs();
                String archivedStrm = download.getArchivedStreamLocation();
                String compression = download.getCompression();

                Config.log(INFO, "Current status: "+ status +".");//need to fix downloadStarted time
                //It was queued for downloading on: "+ (downloadStarted == null ? "(unknown)" : new Date(downloadStarted.longValue())));
                
                String newStatus = null;
                if(!DOWNLOAD_COMPRESSING.equals(status) && !DOWNLOAD_COMPLETE.equals(status))//determine new status
                {
                
                    //find the package that this download maps to
                    JDownloaderInterface jdownloader = new JDownloaderInterface(JDOWNLOADER_HOST);

                    //find all of the jpackages that have downloads associated with this file
                    List<JPackage> jpackages = new ArrayList<JPackage>();
                    Map<String, JPackage> currentpackages = jdownloader.getDownloadPackages();
                    if(currentpackages == null)
                    {
                        log(WARNING, "Current downloads could not be retrieved from JDownloader, which check filesystem instead.");
                    }
                    else
                    {
                        for(JPackage jp : currentpackages.values())
                        {
                            for(JDownload jd : jp.getDownloads())
                            {
                                String downloadFileLocation = jd.getPathToDownloadedFile();
                                int downloadIdAssociated = archivedFilesDB.getSingleInt("SELECT download_id FROM DownloadFiles WHERE file = "+ tools.sqlString(downloadFileLocation));
                                if(downloadIdAssociated == downloadId)
                                {
                                    //this download is associated with the one we are currently checking, add the package to its list
                                    jpackages.add(jp);
                                    break;//don't need to check the downloads anymore since we've already saved the parent package (still check the rest of the jpackages)
                                }
                            }
                        }
                    }

                    if(jpackages.isEmpty())//might have been cleared out of Jdownloader or retrieving from Jdownloader failed, try secondary method
                    {
                        log(INFO, "Could not determine associated downloads from JDownloader. Will check filesystem for completed and in-progress downloads.");
                        //secondary method: check if the downloaded files exist or it  .part exists. This means that the info has been removed from jdownloader, but the download can still be tracked
                        List<String> files = archivedFilesDB.getStringList("SELECT file FROM DownloadFiles WHERE download_id = "+ downloadId);
                        boolean allFilesAreDownloaded = true;
                        boolean downloadInProgress = false;
                        for(String s : files)
                        {
                            if(!valid(s))
                            {
                                Config.log(WARNING, "The location of the downloaded file(s) is unknown, cannot process this download.");
                                break;
                            }
                            File videoFile = new File(s);

                            //Check if .part exits, means download is in progress
                            File videoPart = new File(tools.fileNameNoExt(videoFile)+".part");
                            if(videoPart.exists())
                            {
                                log(INFO, "This download is in progress because the .part file exits at: "+ videoPart);
                                downloadInProgress = true;
                                break;
                            }

                            if(!videoFile.exists())
                            {
                                if(allFilesAreDownloaded)
                                {
                                    log(INFO, "The downloaded file does not exist at: "+ videoFile+", so this download is not complete.");
                                    allFilesAreDownloaded = false;
                                }
                            }
                        }

                        if(allFilesAreDownloaded)
                        {
                            log(INFO, "All files have been downloaded, marking this download as complete.");
                            newStatus = DOWNLOAD_COMPLETE;
                        }
                        else if(downloadInProgress)
                        {
                            newStatus = DOWNLOADING;
                        }
                        else//download isn't complete or in progress
                        {
                            Config.log(WARNING, "Cannot find information for this download in database or file system. Will skip it and try again later: "+ archivedStrm);
                            continue;//TODO: remove from database since we cant get any info from it (only remove if Jdownloader interface is connected)
                        }
                    }

                    if(newStatus == null)//if status hasn't already been determined, use percentage to determine it
                    {
                        //determine how complete the jpackages are
                        double percentComplete = 0.0;
                        int fileCount = 0;
                        boolean stillDownloading = false;//will be set to true if any files have an ETA status (catches if Jdownloader doesnt know the file size and forces percent to 100.00)
                        packageLoop:for(JPackage jp : jpackages)
                        {
                            fileCount++;
                            percentComplete += jp.percentCompelte();
                            log(DEBUG, "Downloading package #"+fileCount+" percent complete: "+ tools.toTwoDecimals(jp.percentCompelte())+"%");
                            for(JDownload jd : jp.getDownloads())
                            {
                                String jdStatus = jd.getStatus();
                                if(valid(jdStatus) &&jdStatus.toUpperCase().startsWith("ETA "))
                                {
                                    log(DEBUG, "This file's status is \""+jdStatus+"\", setting overall status to: "+ DOWNLOADING);
                                    stillDownloading = true;
                                    break packageLoop;
                                }
                            }
                        }


                        if(!stillDownloading)
                        {
                            percentComplete /= jpackages.size();
                            log(DEBUG, "Overall percent complete for "+ jpackages.size() +" package"+(jpackages.size()==1?"":"s")+" for this file: "+ tools.toTwoDecimals(percentComplete)+"%");
                            if(percentComplete > 0.0 && percentComplete < 100.0)
                                newStatus = DOWNLOADING;
                            else if(percentComplete == 100.0)
                                newStatus = DOWNLOAD_COMPLETE;
                            else newStatus = DOWNLOAD_QUEUED;//percent complete = 0.00
                        }
                        else//force to downloading since one of the download's status is "ETA....."
                            newStatus = DOWNLOADING;
                    }//end determining new status
                }
                else//no change for DOWNLOAD_COMPLETE or DOWNLOAD_COMPRESSING statuses
                {
                    newStatus = status;
                }
                
                boolean statusChanged = !newStatus.equals(status);
                if(statusChanged) archivedFilesDB.executeSingleUpdate("UPDATE Downloads SET status = "+ tools.sqlString(newStatus) +" WHERE id = "+ downloadId);
                Config.log(INFO, "New status: " + newStatus + (statusChanged ? " (changed from "+ status+")": " (no change)"));
                if(newStatus.equals(DOWNLOAD_COMPLETE) || newStatus.equals(DOWNLOAD_COMPRESSING))
                {
                    //Finalize
                    boolean complete = newStatus.equals(DOWNLOAD_COMPLETE);
                    if(complete)
                        Config.log(INFO, "This download appears to be complete, will archive it in dropbox at: \""+DOWNLOADED_VIDEOS_DROPBOX+"\" and mark it as finalized.");
                    else//compressing
                        log(INFO, "This download is compressing, will check its compresion status and archive it if it's finished");
                    
                    //get alist of the file(s) associated with this download
                    List<String> strFiles = archivedFilesDB.getStringList("SELECT file FROM DownloadFiles WHERE download_id = "+ downloadId);
                    if(strFiles.isEmpty())
                    {
                        log(WARNING, "No downloaded files were found in the database for this download. Cannot continue. Will remove this download from tracking database.");
                        tools.removeDownloadFromDB(downloadId);                        
                        continue;
                    }

                    List<File> downloadedFiles = new ArrayList<File>();
                    boolean allFilesExist = true;
                    for(String filePath : strFiles)
                    {
                        if(!valid(filePath) || !(new File(filePath).exists()))
                        {
                            //check if the .part exists
                            Config.log(WARNING, "Cannot process finished download because the file at: \""+ filePath +"\" does not exist or the path is invalid. Will remove this download from tracking database.");
                            tools.removeDownloadFromDB(downloadId);
                            allFilesExist = false;
                        }
                        downloadedFiles.add(new File(filePath));
                    }
                    if(!allFilesExist)
                    {
                        continue;//skip this download
                    }

                    
                    //sort the files alphabetically (requires that the online source name the parts alphabetically for multi-part videos)
                    Collections.sort(downloadedFiles);
                    String destNoExt = archivedFilesDB.getSingleString("SELECT dest_no_ext FROM Downloads WHERE id = "+ downloadId);

                    //move to the downloaded dropbox and name appropriately
                    int count = 0;
                    boolean successfulMove = true;
                    List<File> archivedDownloadedFiles = new ArrayList<File>();
                    boolean startedCompression = false;
                    downloadedFileCheck:for(File downloadedFile : downloadedFiles)
                    {
                        count++;
                        String part = downloadedFiles.size() == 1 ? "" : ".part"+count;
                        String fileName = downloadedFile.getName();
                        String pathNoExt = downloadedFile.getPath().substring(0, downloadedFile.getPath().lastIndexOf("."));

                        File destFile = null;
                        File sourceFile = null;
                        try
                        {
                            boolean useCompression = tools.valid(compression);
                            CompressionDefinition cd = null;                            
                            if(useCompression) cd=COMPRESSION_DEFINITIONS.get(compression.toLowerCase());
                            boolean currentlyCompressing = newStatus.equals(DOWNLOAD_COMPRESSING);
                            if(!currentlyCompressing)
                            {
                                if(useCompression)
                                {
                                    log(INFO, "Will attempt to compress video using compression definition named \""+compression+"\"");                                    
                                    if(cd == null)
                                    {
                                        log(ERROR, "No compression definition named \""+compression+"\" was found. Will skip compression for this video");
                                        useCompression = false;
                                    }
                                    else//do compression
                                    {
                                        //start comskip now incase it will be in an incompatible format later
                                        if(COMSKIP_DOWNLOADED_VIDEOS)
                                        {
                                            log(INFO, "Starting comskip processing for downloaded files...");
                                            tools.comskipVideos(downloadedFiles);
                                        }
                                        String command = cd.getCommand();
                                        
                                        command = command.replace("[FILE_PATH_NO_EXT]", pathNoExt);
                                        command = command + " > \"" + pathNoExt+".encodelog" +"\" 2>&1";//save command stdout to this file. "2>&1" saves stderr to the same file

                                       log(DEBUG, "Command after replacing [FILE_PATH_NO_EXT] = " + command);
                                       String tmpCompressionCmdFile = pathNoExt+".cmd";
                                       tools.writeToFile(new File(tmpCompressionCmdFile), command, false);

                                       //start an external compression instance and thread for this file
                                       ExternalCompression extComp = new ExternalCompression(tmpCompressionCmdFile, command);
                                       extComp.start();
                                       startedCompression=true;
                                       //update DB with new status
                                       log(INFO, "Updating status as "+DOWNLOAD_COMPRESSING +". Compression has started for: "+ pathNoExt+"."+cd.getEncodeToExt());
                                       archivedFilesDB.executeSingleUpdate("UPDATE Downloads SET status = "+ tools.sqlString(DOWNLOAD_COMPRESSING) +" WHERE id = "+downloadId);
                                       continue;//go to next downloaded file
                                    }
                                }
                            }
                            else//currently compressing, check status of the last all files and only use compressed files if all are done
                            {                                
                                if(cd == null)
                                {
                                    log(ERROR, "No compression definition named \""+compression+"\" was found. Cannot determine verification lines for encoding. "
                                            + "Will use uncompressed video instead.");
                                    useCompression = false;
                                }
                                else
                                {
                                    boolean successfulCompressionForAllFiles = true;
                                    
                                    for(File nextFile : downloadedFiles)
                                    {
                                        String nextPathNoExt = nextFile.getPath().substring(0, nextFile.getPath().lastIndexOf("."));
                                        File log =new File(nextPathNoExt+".encodelog");

                                        if(!log.exists())
                                        {
                                            log(WARNING,"No compression log found at: "+log+". Assuming compression has failed. Continuing with uncompressed video.");
                                            useCompression=false;
                                            successfulCompressionForAllFiles=false;
                                            break;
                                        }
                                        
                                        long lastModified = log.lastModified();
                                        if(System.currentTimeMillis() - lastModified < (1000 * 60 * 5))//last 5 minutes
                                        {
                                            log(INFO, "SKIP: This log file was modified in the past 5 minutes. Assuming compression is still happening. Will skip this video and check again next time");
                                            continue downloadCheck;//continue the main loop
                                        }
                                        
                                        boolean successfulCompression = tools.checkEncodeStatus(log,cd.getVerificationLines());
                                        if(successfulCompression)
                                        {
                                            log(INFO, "The compression was verified successfully for this file: "+ nextFile);                                            
                                        }
                                        else
                                        {
                                            log(WARNING, "The compression was not verified successfully. Will not use compressed files.");
                                            successfulCompressionForAllFiles=false;                                            
                                        }
                                    }//end for loop
                                    useCompression = successfulCompressionForAllFiles;
                                }//end if valid CompressionDefinition
                            }//end if currently compressing

                            //move the video to the dropbox
                            String destinationExt = useCompression ? cd.getEncodeToExt() : fileName.substring(fileName.lastIndexOf("."), fileName.length());//either encoding ext, or orig ext
                            if(destinationExt.startsWith("."))destinationExt = destinationExt.substring(1,destinationExt.length());//for uniformity, trim a dot if it starts with one
                            //add partX identifier if more than 1 file
                            
                            String strNewLocation = destNoExt + part +"."+ destinationExt;
                            destFile = new File(strNewLocation);//the destination
                            File encodedFile = (useCompression ? new File(pathNoExt+"."+cd.getEncodeToExt()) : null);
                            final String downloadedFilePath = downloadedFile.toString();//get path before moving
                            sourceFile = (useCompression) ? encodedFile : downloadedFile;
                            if(destFile.exists()){
                                log(WARNING, "The destination file already exists, will over-write it at: "+ destFile);
                                destFile.delete();
                            }
                            log(NOTICE, "Moving video file from "+ sourceFile +" to "+ destFile);
                            FileUtils.moveFile(sourceFile , destFile);
                            log(INFO, "Successfully moved the file!");
                            File edl = new File(tools.fileNameNoExt(sourceFile)+".edl");
                            File edlDest= new File(tools.fileNameNoExt(destFile)+".edl");                            
                            if(edl.exists())
                            {
                                if(!edlDest.exists() || edlDest.delete())//if dest edl already exists, delte it and use the edl from the original video
                                {
                                    log(INFO, "Moving edl to "+edlDest);
                                    try
                                    {
                                        FileUtils.moveFile(edl, edlDest);
                                        log(INFO,"Successfully moved edl.");
                                    }
                                    catch(Exception x)
                                    {
                                        log(WARNING, "Failed to move .edl from "+ edl +" to "+ edlDest);
                                    }
                                }
                                else log(INFO, "Not moving edl because the .edl already exists at: " +edlDest);
                            }
                            
                            //clean up other junk from encode/comskip if it exists
                            try{
                            new File(downloadedFilePath).delete();
                            new File(pathNoExt+".encodelog").delete();
                            new File(pathNoExt+".cmd").delete();
                            new File(pathNoExt+".incommercial").delete();
                            new File(pathNoExt+".txt").delete();
                            }catch(Exception ignored){}
                                                       
                            //update the db with the archived location
                            boolean updated = archivedFilesDB.executeSingleUpdate
                                    ("UPDATE DownloadFiles set dropbox_location = "+ tools.sqlString(destFile.toString()) +" "
                                    + "WHERE file = "+tools.sqlString(downloadedFilePath));
                            if(!updated)
                                Config.log(WARNING, "Failed to update dropbox location for downloaded file at: "+ downloadedFilePath+". "
                                        + "This file will not be cleaned up if it is manually deleted from the dropbox location of: "+ destFile);
                            archivedDownloadedFiles.add(destFile);
                        }
                        catch(Exception x)
                        {
                            log(WARNING, "Failed to move downloaded file from: "+ sourceFile +" to "+ destFile,x);
                            successfulMove = false;
                            break;
                        }                        
                    }//end looping thru downloaded files

                    if(startedCompression) continue;//don't process anymore until compression is done
                    
                    if(!successfulMove)
                    {
                        log(WARNING, "Failed to move the downloaded videos to their new location. Cannot continue. Skipping.");
                        continue;
                    }
                    
                    if(COMSKIP_DOWNLOADED_VIDEOS)
                    {
                        tools.comskipVideos(archivedDownloadedFiles);
                    }
                    
                    //update XBMC's database
                    XBMCInterface xbmc = new XBMCInterface(Config.DATABASE_TYPE, (Config.DATABASE_TYPE.equals(MYSQL) ? Config.XBMC_MYSQL_VIDEO_SCHEMA : Config.sqlLiteVideoDBPath));
                    File parent = archivedDownloadedFiles.get(0).getParentFile();//if more than 1, they all share the same parent, so this is OK
                    String xbmcParentPath = XBMCInterface.getFullXBMCPath(parent);
                    String newFileName = XBMCInterface.getXBMCFileName(archivedDownloadedFiles);

                    //need XBMCFile object for updating XBMC's file pointer
                    XBMCFile currentVideo = tools.getVideoFromDropboxLocation(new File(archivedStrm));//the .strm
                    log(INFO, "Updating XBMC's database to point to downloaded video(s)");
                    boolean updated = xbmc.updateFilePointer(currentVideo,xbmcParentPath,newFileName);
                    if(updated)
                    {
                        Config.log(NOTICE, "Successfully updated database to use downloaded video(s) for "+ currentVideo.getType() +" originally located at: "+ currentVideo.getFinalLocation());
                        if(!archivedFilesDB.executeSingleUpdate("UPDATE Downloads SET status = "+ tools.sqlString(DOWNLOAD_FINAL) +" WHERE id = "+downloadId))
                            log(WARNING, "Failed to set status to "+ DOWNLOAD_FINAL +" for this video!");

                        //rename the original .strm to .downloaded (prevent it from future scans)
                        File af = new File(archivedStrm);
                        String newName = tools.fileNameNoExt(af)+".downloaded";
                        boolean renamed = af.renameTo(new File(newName));
                        if(!renamed)
                            log(WARNING, "Failed to rename file \""+archivedStrm+"\" to \""+newName+"\"");

                        Config.log(INFO, "Since a downloaded video was successfully added, synchronizing path settings between the streaming path and downloaded path.");
                        xbmc.synchronizePathSettings();
                    }
                    else
                        Config.log(ERROR, "Failed to update XBMC's database to use the downloaded video(s) for this "+ currentVideo.getType() + " located at: "+currentVideo.getFinalLocation());
                    xbmc.close();
                }
            }//end resultset loop            
        }
        catch(Exception x)
        {
            Config.log(ERROR, "General error while checking status of current downloads: "+x,x);
        }
        finally
        {
            archivedFilesDB.closeStatement();//incase it wasn't closed successfully
        }

        //comskip conversion if  necessary
        if(COMSKIP_DOWNLOADED_VIDEOS)
            tools.convertEDLs();
    }
    
    //need to queue one at a time so we can determine which URL is mapped to which download.
    //once the URL is queued, there is no way to determine which file it is mapped to, so if multiple were added at the same,
    //time, we couldn't track them
    public static JPackage queueJdownload(String url)
    {        
        JDownloaderInterface jdownloader = new JDownloaderInterface(JDOWNLOADER_HOST);

        //get a baseline of the packages before adding one
        Map<String, JPackage> baselinePackages = jdownloader.getDownloadPackages();
        if(baselinePackages == null)
        {
            log(ERROR, "Could not get download packages list from JDownloader. Cannot queue new download.");
            return null;
        }
        log(DEBUG, "Baseline found "+ baselinePackages.size() +" download packages currently in JDownloader");
        boolean queued = jdownloader.queueURL(url);

        if(!queued) return null;//failed

        //else wait a little bit and get a new list to diff
        JPackage newPackage = null;
        for(int tries = 0; tries < 5; tries++)
        {
            try{Thread.sleep(6000);}catch(Exception x){}
            Map<String, JPackage> newList = jdownloader.getDownloadPackages();
            if(newList == null) continue;//try again
            log(DEBUG, "Now "+ newList.size() +" download packages found in JDownloader");
            for(Map.Entry<String, JPackage> entry : newList.entrySet())
            {
                String id = entry.getKey();
                //log(DEBUG,id);
                if(baselinePackages.get(id) == null)//this id wasn't in the baseline, it must be the new package
                {
                    if(newPackage == null)
                    {
                        //this is the new package
                        newPackage = entry.getValue();
                    }
                    else
                    {
                        Config.log(ERROR, "Multiple new packages found in JDownloader! Cannot determine which one is from this URL: "+ url);
                        return null;
                    }
                }
            }
            if(newPackage != null) break;//found it
        }
        if(newPackage == null)
        {
            Config.log(ERROR, "Failed to find new download package in JDownloader's list. Cannot continue with the download.");
            return null;
        }

        if(newPackage.getDownloads().isEmpty())
        {
            Config.log(ERROR, "No downloading files were found in JDownloader package named \""+newPackage.getName()+"\". Cannot continue with the download.");
            return null;
        }

        if(newPackage.getDownloads().size() > 1)
        {
            Config.log(ERROR, "Multiple downloading files were found in JDownloader package named \""+newPackage.getName()+"\". Expected only 1 file. Cannot continue with the download.");
            return null;
        }

        JDownload download = newPackage.getDownloads().get(0);
        if(tools.valid(download.getPathToDownloadedFile()))
        {
            Config.log(INFO, "Successfully queued file with JDownloader. The file will be downloaded to: "+ download.getPathToDownloadedFile() + " from "+ url);

            //trigger download start incase jdownloader didn't do it
            Config.log(INFO, "Triggering download start in JDownloader");
            if(jdownloader.startDownloads())
                Config.log(INFO, "Successfully triggered start of downloads");
            else
                Config.log(WARNING, "Failed to trigger start of downloads, please manually start downloads in JDownloader.");
            
            return newPackage;
        }
        else
        {
            Config.log(ERROR, "Path to the downloaded file not found. Cannot continue with download.");
            return null;
        }        
    }
    
    public boolean manualArchiveIfNeeded(Map<File, XBMCFile> allVideosArchived, Map<String,XBMCFile> allVideoFilesInLibrary)
    {
        setShortLogDesc("ManualArchive");
        if(allVideoFilesInLibrary.isEmpty())
        {
            log(WARNING, "No videos were found in XBMC's library. Assumining there is a problem and skipping manual archiving.");
            return false;
        }
        

        Set<File> allVideosNotInLibrary = new LinkedHashSet<File>();        
        String[] videoTypes = new String[]{"TV Shows", "Movies", "Music Videos"};//must be same name as folders in dropbox
        for(String videoType : videoTypes)
        {

            File folder = new File(DROPBOX+SEP+videoType);
            Collection<File> videosInFolder;
            if(folder.exists())//get list of files
            {
                videosInFolder = FileUtils.listFiles(folder, new String[]{"strm", "mpg"}, true);
            }
            else
            {
                Config.log(NOTICE, "No folder found at "+ folder +". Will not manually archive any "+ videoType);
                continue;
            }
            
            int numberNotInLibrary = 0;
            int numberInLibrary = 0;
            List<File> videosNotInLibrary = new ArrayList();
            for(File video : videosInFolder)
            {
                String xbmcPathNoExt = XBMCInterface.getFullXBMCPath(video).toLowerCase();
                xbmcPathNoExt = xbmcPathNoExt.substring(0, xbmcPathNoExt.lastIndexOf("."));
                String strm = xbmcPathNoExt +".strm";
                String mpg = xbmcPathNoExt+".mpg";

                //if neither the mpg or strm is found, mark the video as not in library
                if(allVideoFilesInLibrary.get(strm) == null && allVideoFilesInLibrary.get(mpg) == null)
                {                    
                    numberNotInLibrary++;
                    videosNotInLibrary.add(video);
                }
                else
                {                    
                    numberInLibrary++;
                }
            }
            allVideosNotInLibrary.addAll(videosNotInLibrary);//add to global list

            for(File file : videosNotInLibrary) log(DEBUG, "Not in library: "+ file.getPath());

            log(NOTICE, "Of "+(videosInFolder.size())+" total "+videoType+" in dropbox, "
                    + "found " + numberNotInLibrary +" videos not yet in XBMC's library. "
                    + numberInLibrary +" files are in the library.");                       
        }
        
        log(NOTICE, "Will attempt manual archive for "+ allVideosNotInLibrary.size() +" video files that are not in XBMC's library.");
        int manualArchiveSuccess = 0, manualArchiveFail = 0;
        for(File f : allVideosNotInLibrary)
        {            
            boolean newVideoWasManuallArchived;
            XBMCFile video = allVideosArchived.get(f);            

            if(video != null)
            {
                log(DEBUG, "Video found in cache. Attempting manual archive.");
                newVideoWasManuallArchived = manualArchive(video);
            }
            else
            {
                log(INFO, "Could not find corresponding original video that was archived at: "+ f+". Will attempt secondary method. "
                        + "This usually means the original source that created this video no longer exists.");
                newVideoWasManuallArchived = manualArchive(f);
            }

            if(newVideoWasManuallArchived) manualArchiveSuccess++;
            else manualArchiveFail++;
        }
        
        log(NOTICE, manualArchiveSuccess + " new videos were successfully manually archived. "
                + manualArchiveFail+" videos were not manually archived (either skipped or failed).");

        return (manualArchiveSuccess>0);       
    }

    /*
     * Looks up the file in the database based on its archived location.
     */
    public boolean manualArchive(File f)
    {
        XBMCFile video = tools.getVideoFromDropboxLocation(f);
        if(video == null || !video.hasValidMetaData())
        {
            //delete the file since it can't be manually archived and it wasn't added by XBMC's library scan
            log(WARNING, "Failed to get meta-data for this video. It will be deleted since it was not scraped by XBMC and cannot be manually archived: "+f);
            boolean deleted = f.delete();
            if(!deleted)log(WARNING, "Failed to delete this file: "+ f);
            return false;
        }
        else
            return manualArchive(video);
    }
    

    public boolean manualArchive(XBMCFile video)
    {        
        if(video == null)
        {
            log(WARNING, "Invalid video passed to manualArchive(). Skipping null video.");
            return false;
        }
        if(!valid(video.getFinalLocation()))
        {
            log(WARNING, "Cannot manually archive because the location of the video in the dropbox is not known.");
            return false;
        }
        File archivedFile = new File(video.getFinalLocation());
        if(video.getFinalLocation().toLowerCase().endsWith(".strm") && archivedFile.isFile())//if the .strm already exists, something is wrong
        {
            log(WARNING, ".strm file found that is not yet in XBMC's library. This suggests a problem with the method used to determine which files are in the library. Will skip manual archiving for: "+ video.getFinalLocation());
            return false;
        }        
        if(video.getFinalLocation().toLowerCase().endsWith(".downloaded") && archivedFile.isFile())//if the .strm already exists, something is wrong
        {
            log(WARNING, ".downloaded file found. It will not be manually archived because it has already been downloaded locally: "+ video.getFinalLocation());
            return false;
        }
        if(!archivedFile.isFile())
        {
            log(WARNING, "Cannot manually archive because the file does not exist at: "+ archivedFile);
            return false;
        }
        
        Long dateCreated = tools.getDateArchived(archivedFile.getPath());
        if(dateCreated == null)
        {
            log(WARNING, "Cannot manually archive this file because cannot determine when it was created.");
            return false;
        }
        
        long secondsOld = (System.currentTimeMillis() - dateCreated) / 1000;
        double hoursOld = (secondsOld / 60.0 / 60.0);
        if(hoursOld < HOURS_OLD_BEFORE_MANUAL_ARCHIVE)
        {
            log(INFO, "Will not manually archive because the file was archived "+ tools.toTwoDecimals(hoursOld) +" hours ago, "
                    + "which is < threshold of "+ HOURS_OLD_BEFORE_MANUAL_ARCHIVE +" hours: "+ archivedFile);
            return false;
        }
        
        if(video.isTvShow())
        {
            //check if the series nfo exists
            File seriesDir = archivedFile.getParentFile()//season.x directory
                              .getParentFile();//Series.Title directory
            log(INFO, "Series Dir is: "+ seriesDir);

            File seriesNFO = new File(seriesDir+SEP+"tvshow.nfo");
            if(!seriesNFO.exists())
            {
                if(valid(video.getSeries()))
                {
                    log(INFO, "Creating series.nfo file because it does not exist: "+ seriesNFO);
                    List<String> lines = new ArrayList<String>();
                    lines.add("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>");
                    lines.add("<tvshow>");
                    lines.add("<title>"+video.getSeries()+"</title>");//maps to Airing.Show.ShowTitle
                    lines.add("</tvshow>");
                    if(tools.writeToFile(seriesNFO, lines, true))
                        log(INFO, "Successfully created .nfo at: "+ seriesNFO);
                    else
                    {
                        log(WARNING, "Failed to create .nfo at: "+ seriesNFO+". Cannot manually archive this video: "+ archivedFile);
                        return false;
                    }
                }
                else
                {
                    log(WARNING, "Cannot manually archive this video because the series is unknown: \""+archivedFile+"\"");
                    return false;
                }
            }

            if(valid(video.getTitle()))
            {
                //use the season/episode numbers from the file name
                Pattern p = Pattern.compile("s[0-9]+e[0-9]+", Pattern.CASE_INSENSITIVE);
                Matcher m = p.matcher(archivedFile.getPath());
                if(m.find())
                {
                    try
                    {
                        String SxxExx = m.group();
                        int eIndex = SxxExx.toLowerCase().indexOf("e");
                        int s = Integer.parseInt(SxxExx.substring(1, eIndex));
                        int e = Integer.parseInt(SxxExx.substring(eIndex+1, SxxExx.length()));
                        video.setSeasonNumber(s);
                        video.setEpisodeNumber(e);
                        log(INFO, "Season and episode number parsed successfully from file name. Season="+ s +", Episode="+ e);
                    }
                    catch(Exception x)
                    {
                        log(WARNING, "Season and Episode number cannot be parsed from filename: "+ archivedFile.getPath()+". Cannot manually archive this file.",x);
                        return false;
                    }

                    File episodeNFO = new File(archivedFile.getPath().substring(0,archivedFile.getPath().lastIndexOf("."))+".nfo");
                    boolean updating = (episodeNFO.exists());
                    if(updating)
                        log(INFO, "Episode .nfo already exits. It will be over-written: "+ episodeNFO);

                    String prefix = "";
                    String suffix = "";                    

                    if(video.getSubfolder() != null)
                    {
                        if(valid(video.getSubfolder().getPrefix()))
                            prefix = video.getSubfolder().getPrefix();
                        if(valid(video.getSubfolder().getSuffix()))
                            suffix = video.getSubfolder().getSuffix();
                    }

                    List<String> lines = new ArrayList<String>();
                    lines.add("<episodedetails>");
                    lines.add("<title>"+prefix+video.getTitle()+suffix+"</title>");
                    lines.add("<season>"+video.getSeasonNumber()+"</season>");
                    lines.add("<episode>"+video.getEpisodeNumber()+"</episode>");
                    lines.add("</episodedetails>");//end
                    if(tools.writeToFile(episodeNFO, lines, true))
                    {
                        log(INFO, "Successfully "+(updating ? "updated" : "created")+" .nfo at: "+ episodeNFO);
                        if(updating) return false; else return true;//only return true for new .nfo's
                    }
                    else
                    {
                        log(WARNING, "Failed to create .nfo at: "+ episodeNFO+". Cannot manually archive this video: "+ archivedFile);
                        return false;
                    }
                }
                else
                {
                    log(WARNING, "SxxExx pattern was not found in file name. Cannot manually archive: "+ archivedFile);
                    return false;
                }
            }
            else
            {
                log(WARNING, "Cannot manually archive because title was not found: "+ archivedFile);
                return false;
            }
        }
        else if(video.isMovie())
        {
            if(!valid(video.getTitle()))
            {
                log(WARNING, "Cannot manually archive .nfo because no title is available for movie: "+ archivedFile);
                return false;
            }
            File movieNFO = new File(archivedFile.getPath().substring(0,archivedFile.getPath().lastIndexOf("."))+".nfo");
            boolean updating = movieNFO.exists();
            if(updating)
                log(INFO, "Movie .nfo already exits. It will be over-written: "+ movieNFO);

            List<String> lines = new ArrayList<String>();
            
            String movieSet = null;//check for a set
                        
            if(video.getSubfolder() != null)
            {
                if(valid(video.getSubfolder().getMovieSet()))
                {
                    movieSet = video.getSubfolder().getMovieSet();
                    if(!movieSet.trim().equals(movieSet))
                    {
                        log(INFO, "Not adding <set> tag to .nfo because the movie set either starts or ends with a space. This would be trimmed by XBMC's .nfo parser. Will use XBMC-MySQL direct update instead.");
                        movieSet = null;
                    }
                }
            }
            
            lines.add("<movie>");
            lines.add("<title>"+video.getTitle()+"</title>");
            if(valid(movieSet))lines.add("<set>"+movieSet+"</set>");
            lines.add("</movie>");//end
            if(tools.writeToFile(movieNFO, lines, true))
            {
                log(INFO, "Successfully "+(updating ? "updated" : "created")+" .nfo at: "+ movieNFO);
                if(updating) return false; else return true;//only return true for new .nfo's
            }
            else
            {
                log(WARNING, "Failed to create .nfo at: "+ movieNFO+". Cannot manually archive this video: "+ archivedFile);
                return false;
            }
        }
        else if(video.isMusicVideo())
        {
            if(!valid(video.getTitle()))
            {
                log(WARNING, "Cannot manually archive .nfo because no title is available for music video: "+ archivedFile);
                return false;
            }
            if(!valid(video.getArtist()))
            {
                log(WARNING, "Cannot manuall archive .nfo because no artist is available for music video: "+ archivedFile);
                return false;
            }
            File musicVideoNFO = new File(archivedFile.getPath().substring(0,archivedFile.getPath().lastIndexOf("."))+".nfo");
            boolean updating = musicVideoNFO.exists();
            if(updating)
                log(INFO, "Music Video .nfo already exits. It will be over-written: "+ musicVideoNFO);

            List<String> lines = new ArrayList<String>();

            String prefix = "";
            String suffix = "";                        
            if(video.getSubfolder() != null)
            {
                if(valid(video.getSubfolder().getPrefix()))
                    prefix = video.getSubfolder().getPrefix();
                if(valid(video.getSubfolder().getSuffix()))
                    suffix = video.getSubfolder().getSuffix();
            }


            lines.add("<musicvideo>");
            lines.add("<title>"+prefix+video.getTitle()+suffix+"</title>");
            lines.add("<artist>"+video.getArtist()+"</artist>");
            lines.add("</musicvideo>");//end
            if(tools.writeToFile(musicVideoNFO, lines, true))
            {
                log(INFO, "Successfully "+(updating ? "updated" : "created")+" .nfo at: "+ musicVideoNFO);
                if(updating) return false; else return true;//only return true for new .nfo's
            }
            else
            {
                log(WARNING, "Failed to create .nfo at: "+ musicVideoNFO+". Cannot manually archive this video: "+ archivedFile);
                return false;
            }
        }
        return false;
    }
    
    static public String getFileType(File f)
    {
        final File TV_SHOW_DIR = new File(DROPBOX+SEP+"TV Shows");
        final File MOVIE_DIR = new File(DROPBOX+SEP+"Movies");
        final File MUSIC_VID_DIR = new File(DROPBOX+SEP+"Music Videos");

        File parent = f.getParentFile();
        while(true)
        {
            if(parent == null) break;
            if(parent.equals(TV_SHOW_DIR))
                return TV_SHOW;
            else if(parent.equals(MOVIE_DIR))
                return MOVIE;
            else if(parent.equals(MUSIC_VID_DIR))
                return MUSIC_VIDEO;

            parent = parent.getParentFile();//continue up
        }
        return null;//no match found

    }
    
    public void updateDatabase()
    {
        //update pointers for .mpg/.strm
        //get the connection to MySQL or SQLite
         XBMCInterface xbmc = new XBMCInterface(Config.DATABASE_TYPE, (Config.DATABASE_TYPE.equals(MYSQL) ? Config.XBMC_MYSQL_VIDEO_SCHEMA : Config.sqlLiteVideoDBPath));
         try
         {
             int numberRenamed = 0;
             File tvShowsFolder = new File(DROPBOX+SEP+"TV Shows");
             if(tvShowsFolder.exists())
                numberRenamed += xbmc.updateFilePointers(tvShowsFolder);

             File moviesFolder = new File(DROPBOX+SEP+"Movies");
             if(moviesFolder.exists())
                numberRenamed += xbmc.updateFilePointers(moviesFolder);

             File musicVideosFolder  = new File(DROPBOX+SEP+"Music Videos");
             if(musicVideosFolder.exists())
                numberRenamed += xbmc.updateFilePointers(musicVideosFolder);

             log(NOTICE, "Overall, "+numberRenamed+" videos were added to XBMC's library in the last scan.");
             //if(numberRenamed > 0)//don't do this, causing problems with orphaned metadatachanges
             {
                 Config.log(INFO, "Will now update meta-data.");
                 //update meta-data that's been queued
                 xbmc.addMetaDataFromQueue();
             }
            //else
            //    Config.log(INFO, "Skipping meta-data integration because no new videos were added.");
         }
         catch(Exception x)
         {
             Config.log(ERROR, "General exception while updating XBMC database: "+ x,x);
         }
         finally
         {
             if(xbmc != null) xbmc.close();
        }
    }

    
   
    private void dropboxCleanUp()
    {
        //searches for files in dropbox that are no longer in the tracking database and deletes the files
        
        setShortLogDesc("Clean Up");
        log(NOTICE, "Cleaning up dropbox...");
                      
        Collection<File> allVideoInDropbox = FileUtils.listFiles(new File(DROPBOX), new String[]{"strm","mpg"}, true);
        log(INFO, "Checking " + allVideoInDropbox.size()+ " archived videos in dropbox to make sure the archived video is still valid.");

        int deletedCount = 0;
        for(Iterator<File> it = allVideoInDropbox.iterator(); it.hasNext();)
        {
            //check if this file exists in the database, if not, delete it
            File f = it.next();                           
            String path = f.getPath();
            String fileNameNoExt = path.substring(0, path.lastIndexOf("."));//use w/o ext because we want to match on .mpg, .strm, and .downloaded
            String getIdSQL = "SELECT id FROM ArchivedFiles WHERE dropbox_location LIKE "+tools.sqlString(fileNameNoExt+".%");
            int archivedFileId = archivedFilesDB.getSingleInt(getIdSQL);
            if(archivedFileId == SQL_ERROR)
            {
                Config.log(WARNING, "Failed to determine archived id for file at: "+ fileNameNoExt +". Will skip cleanup for this file. SQL used = "+ getIdSQL);
                continue;
            }

            
            boolean existsInDatabase = archivedFileId > -1;
            if(!existsInDatabase)
            {
                //check if it is a downloaded file
                getIdSQL = "SELECT id FROM DownloadFiles WHERE file LIKE "+tools.sqlString(fileNameNoExt+".%");
                int downloadedFileID = archivedFilesDB.getSingleInt(getIdSQL);
                if(downloadedFileID == SQL_ERROR)
                {
                    Config.log(WARNING, "Failed to determine if file at: "+ fileNameNoExt +" is a downloaded file. Will skip cleanup for this file. SQL used = "+ getIdSQL);
                    continue;
                }
                existsInDatabase = downloadedFileID > -1;
            }

            if(!existsInDatabase)
            {
                log(INFO, "File found in dropbox that no longer exists in ArchivedFiles database. Deleting file at: "+ path);
                if(f.exists())
                {
                    boolean deleted = f.delete();
                    if(!deleted)
                        log(WARNING, "Failed to delete old video. Will try again next time: "+ f);
                    else//successfully deleted
                        deletedCount++;
                }
                else
                    log(WARNING, "Cannot delete the file because it does not exist... wtf?");
            }            
        }
        log(NOTICE, "After removing "+deletedCount+" old videos from dropbox, number of videos is now: "+ (allVideoInDropbox.size()-deletedCount));
        log(NOTICE, "Done with dropbox clean-up");
         
        setShortLogDesc("");
    }
    
    private void fileExpiration()
    {
        setShortLogDesc("CacheExpiration");
        try
        {
            Collection<File> cachedXML = FileUtils.listFiles(new File(BASE_PROGRAM_DIR+SEP+"XMLCache"), new String[]{"xml"}, false);
            int deleteCount = 0;
            for(File f : cachedXML)            
                if(f.delete()) deleteCount++;
            Config.log(deleteCount>0?INFO:DEBUG, "Successfully cleared out " + deleteCount +" of "+ cachedXML.size() +" cached XML files.");
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to clean up XML cache: "+ x,x);
        }

        logFileExpiration();
    }

    
    private void cleanDropbox(Source source)
    {
        setShortLogDesc("Clean:"+source.getFullName());
        try
        {            
            //get list of all videos in the dropbox. Compare against the vides scraped this time
            String [] exts = new String[]{"strm","mpg"};//don't get .downloaded files here. They are cleaned up in the cleanUpDownloads() method
            Collection<File> files = FileUtils.listFiles(new File(DROPBOX), exts, true);
            //remove any files that weren't archived from this source
            Set<File> filesArchivedByThisSource = tools.getFilesArchivedBySource(source.getName());
            for(Iterator<File> it = files.iterator(); it.hasNext();)
            {
                File f = it.next();
                String fileNoExt = f.getPath().substring(0, f.getPath().lastIndexOf("."));
                if(!filesArchivedByThisSource.contains(new File(fileNoExt+".mpg")) && !filesArchivedByThisSource.contains(new File(fileNoExt+".strm")))
                    it.remove();//this file wasn't archived from this source.
            }
            
            int numberOfFiles = files.size();//files now only contains files that were archived from this source
            int numberOfFilesDeleted = 0;
            log(NOTICE, "Cleaning dropbox of videos no longer used from source: \""+source.getFullName()+"\". Filecount from dropbox from this source is currently: "+ numberOfFiles);
            for(File archivedVideo : files)
            {
                String archivedVideoNoExt =tools.fileNameNoExt(archivedVideo);
                boolean valid = false;
                for(String ext : exts)//check against all exts
                {
                    File video = new File(archivedVideoNoExt+"."+ext);
                    valid = source.getVideosArchivedFromThisSource().get(video) != null;//valid if this file was archived during this run
                    if(valid) break;
                    
                    //catch if this file isn't in the filesArchived list because it was skipped due to already existing in a different source
                    valid = source.getVideosSkippedBecauseAlreadyArchived().get(video) != null;
                    if(valid) 
                    {
                        //TODO: remove when confirmed this isn't happening anymore
                       log(WARNING, "Skipping cleaning a video that was skipped because of already archived. Review this. Didn't expect this to happen.");
                        break;
                    }
                }
                
                if(!valid)
                {
                    setShortLogDesc("Missing");
                    log(INFO, "This archived video no longer exists in videos found from "+ source.getFullName()+". "
                            + "Will mark it as missing: "+ archivedVideo + " (file last modified "+new Date(archivedVideo.lastModified())+")");
                    boolean shouldDelete = tools.markVideoAsMissing(archivedVideo.getPath());
                    if(shouldDelete)
                    {
                        setShortLogDesc("Delete");
                        //delete from Database
                        String deleteSQL = "DELETE FROM ArchivedFiles WHERE dropbox_location = "+tools.sqlString(tools.convertToStrm(archivedVideo.getPath()));//unique indx on dropbox_location
                        int rowsDeleted = Config.archivedFilesDB.executeMultipleUpdate(deleteSQL);
                        boolean deletedFromDB = rowsDeleted >=0;///as long as it wasn't a SQL error, it's no longer in the database
                        if(!deletedFromDB)
                        {
                            Config.log(WARNING, "Failed to delete this entry from the tracker database. Won't delete file until DB entry is removed. "
                                    + "Nothing was deleted using: "+ deleteSQL);
                            continue;
                        }

                        //delete from File System
                        boolean deleted = archivedVideo.delete();                        
                        if(deleted)
                        {
                            log(INFO, "Successfully deleted video file: "+ archivedVideo);
                            numberOfFilesDeleted++;                            
                            
                            try//silently try and delete .nfo if it exists
                            {
                                File nfo = new File(archivedVideoNoExt+".nfo");
                                if(nfo.exists()) nfo.delete();
                            }
                            catch(Exception ignored){}
                            

                            //also remove any queued meta data changes that might still exist
                            int numberDeleted = queuedChangesDB.executeMultipleUpdate("DELETE FROM QueuedChanges WHERE dropbox_location LIKE "
                                    + tools.sqlString(archivedVideoNoExt+".%"));
                            if(numberDeleted > 0)
                                Config.log(INFO, "Successfully removed "+ numberDeleted +" meta-data entry for the deleted source: "+ archivedVideo);
                        }
                        else log(INFO, "Failed to delete video, will try again next time: "+ archivedVideo);                                                
                    }
                    setShortLogDesc("Clean:"+source.getFullName());//back to normal
                }
            }
            log(NOTICE, "After cleaning dropbox, " +numberOfFilesDeleted +" old files were deleted for a new size of "+ (numberOfFiles-numberOfFilesDeleted) + " files");

            if(numberOfFilesDeleted > 0)
            {
                log(INFO, "Now removing directories that have no videos inside of them");
                Collection<File> folders = tools.getDirectories(new File(DROPBOX));
                log(INFO, "Found "+ folders.size() +" folders in dropbox.");
                int dirsDeleted = 0;
                for(File dir : folders)
                {
                    if(!dir.exists())
                    {
                        dirsDeleted++;//this folder was recursively deleted in a FileUtils.deleteDirectory call
                        log(INFO, "Deleted empty directory: "+ dir);
                        continue;
                    }

                    if(!dir.isDirectory())
                    {
                        log(INFO, "This file is not a directory, skipping: "+ dir);
                        continue;
                    }

                    String[] validVideoExts = new String[]{"strm","mpg","downloaded"};
                    if(FileUtils.listFiles(dir, validVideoExts, true).isEmpty())//recursively get all .mpg, .strm, and .downloaded files
                    {
                        try
                        {
                            FileUtils.deleteDirectory(dir);//recursively deletes
                            dirsDeleted++;
                            log(INFO, "Deleted empty (contained no "+Arrays.toString(validVideoExts)+" files) directory: "+ dir);
                        }
                        catch(Exception x)
                        {
                            log(INFO, "Failed to delete empty directory, will try again next time: "+ dir,x);
                        }
                    }
                }
                log(dirsDeleted > 0 ? NOTICE : INFO, dirsDeleted +" empty directories were removed, new total = "+ (folders.size() -dirsDeleted) + " directories in dropbox");
            }
        }
        catch(Exception x)
        {
            log(ERROR, "Error while cleaning dropbox of videos no longer needed: "+x,x);
        }
    }


           
}