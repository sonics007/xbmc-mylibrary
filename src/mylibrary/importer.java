package mylibrary;
import java.io.File;
import utilities.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;

public class importer extends Config implements Constants
{
    public static void main(String[] args)
    {                                             
        String VERSION = "1.3.1";
        Config.log(NOTICE,"Starting XBMC.MyLibrary, v"+VERSION);
        
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
                try{Thread.sleep(6000);}catch(Exception x){}
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

             
             if(true)
             {/*Disable all IceFilms support*/
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
                    archiver.canStop();//let it know that the queue is not being fed any more so the archive can finish once it runs out of files to check
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
                    updateMetaData();

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
        
        //Downloading support has been removed
        //processDownloads();//* This still needs some work to cleanup when a download fails.
        
        setShortLogDesc("");
        return true;//got to end w/o issue
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
                videosInFolder = FileUtils.listFiles(folder, new String[]{"strm"}, true);
            }
            else
            {
                Config.log(NOTICE, "No folder found at "+ folder +". Will not manually archive any "+ videoType);
                continue;
            }
            
            int numberNotInLibrary = 0;
            int numberInLibrary = 0;
            List<File> strmsNotInLibrary = new ArrayList();
            for(File strmFile : videosInFolder)
            {
                String xbmcPathStrm = XBMCInterface.getFullXBMCPath(strmFile).toLowerCase();//key(path) is lowercase in: allVideoFilesInLibrary                
                //if strm not found, mark it as missing
                if(allVideoFilesInLibrary.get(xbmcPathStrm) == null)
                {                    
                    numberNotInLibrary++;
                    strmsNotInLibrary.add(strmFile);
                }
                else
                {                    
                    numberInLibrary++;
                }
            }
            allVideosNotInLibrary.addAll(strmsNotInLibrary);//add to global list

            for(File strm : strmsNotInLibrary) log(DEBUG, "Not in library: "+ strm.getPath());

            log(NOTICE, "Of "+(videosInFolder.size())+" total "+videoType+" in dropbox, "
                    + "found " + numberNotInLibrary +" videos not yet in XBMC's library. "
                    + numberInLibrary +" files are in the library.");                       
        }
        
        log(NOTICE, "Will attempt manual archive for "+ allVideosNotInLibrary.size() +" video files that are not in XBMC's library.");
        int manualArchiveSuccess = 0, manualArchiveFail = 0;
        for(File strm : allVideosNotInLibrary)
        {            
            boolean newVideoWasManuallArchived;
            XBMCFile video = allVideosArchived.get(strm);            

            if(video != null)
            {
                log(DEBUG, "Video found in cache. Attempting manual archive.");
                newVideoWasManuallArchived = manualArchive(video);
            }
            else
            {
                log(INFO, "Could not find corresponding original video that was archived at: "+ strm+". Will attempt secondary method. "
                        + "This usually means the original source that created this video no longer exists.");
                newVideoWasManuallArchived = manualArchive(strm);
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
    public boolean manualArchive(File strmFile)
    {
        XBMCFile video = tools.getVideoFromDropboxLocation(strmFile);
        if(video == null || !video.hasValidMetaData())
        {
            //delete the file since it can't be manually archived and it wasn't added by XBMC's library scan
            log(WARNING, "Failed to get meta-data for this video. It will be deleted since it was not scraped by XBMC and cannot be manually archived: "+strmFile);
            boolean deleted = tools.deleteStrmAndMetaFiles(strmFile);
            if(!deleted)log(WARNING, "Failed to delete this file: "+ strmFile);
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
       
        if(!archivedFile.isFile())
        {
            log(WARNING, "Cannot manually archive because the file does not exist at: "+ archivedFile);
            return false;
        }
        
        Long dateCreated = archivedFilesDB.getDateArchived(archivedFile.getPath());
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
                        log(INFO, "Not adding <set> tag to .nfo because the movie set either starts or ends with a space. "
                                + "This would be trimmed by XBMC's .nfo parser. Will use XBMC-MySQL direct update instead.");
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
    	//AngryCamel - 20120817 1620 - Added generic
        else if(video.isGeneric())
        {
            log(WARNING, "Cannot generate .nfo files for generic videos.");
            return false;
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
    
    public void updateMetaData()
    {
        
        //get the connection to MySQL or SQLite
         XBMCInterface xbmc = new XBMCInterface(Config.DATABASE_TYPE, (Config.DATABASE_TYPE.equals(MYSQL) ? Config.XBMC_MYSQL_VIDEO_SCHEMA : Config.sqlLiteVideoDBPath));
         try
         {                          
             Config.log(INFO, "Will now update meta-data for videos in XBMC's library.");
             //update meta-data that's been queued
             xbmc.addMetaDataFromQueue();

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
        //searches for strm files in dropbox that are no longer in the tracking database and deletes the files        
        setShortLogDesc("Clean Up");
        log(NOTICE, "Cleaning up dropbox...");
                      
        Collection<File> allVideoInDropbox = FileUtils.listFiles(new File(DROPBOX), new String[]{"strm"}, true);
        log(INFO, "Checking " + allVideoInDropbox.size()+ " archived strm videos in dropbox to make sure the archived video is still valid.");

        int deletedCount = 0;
        for(Iterator<File> it = allVideoInDropbox.iterator(); it.hasNext();)
        {
            //check if this file exists in the database, if not, delete it
            File strmFile = it.next();                           
            String path = strmFile.getPath();
            
            String getIdSQL = "SELECT id FROM ArchivedFiles WHERE lower(dropbox_location) = ?";
            
            int archivedFileId = archivedFilesDB.getSingleInt(getIdSQL, tools.params(path.toLowerCase()));
            if(archivedFileId == SQL_ERROR)
            {
                Config.log(WARNING, "Failed to determine archived id for file at: "+ path +". Will skip cleanup for this file. SQL used = "+ getIdSQL);
                continue;
            }
            
            boolean existsInDatabase = archivedFileId > -1;          
            if(!existsInDatabase)
            {
                log(INFO, "File found in dropbox that no longer exists in ArchivedFiles database");
                log(NOTICE, "Deleting old file at: "+ path);
                                    
                boolean deleted = tools.deleteStrmAndMetaFiles(strmFile);
                if(!deleted)
                    log(WARNING, "Failed to delete old video. Will try again next time: "+ strmFile);
                else//successfully deleted
                    deletedCount++;                
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
            String [] exts = new String[]{"strm"};
            Collection<File> strmFiles = FileUtils.listFiles(new File(DROPBOX), exts, true);
            //remove any files that weren't archived from this source
            Set<File> filesArchivedByThisSource = tools.getFilesArchivedBySource(source.getName());
            for(Iterator<File> it = strmFiles.iterator(); it.hasNext();)
            {
                File strmFile = it.next();
                String path = strmFile.getPath();
                if(!filesArchivedByThisSource.contains(new File(path)))
                    it.remove();//this file wasn't archived from this source.
            }
            
            int numberOfFiles = strmFiles.size();//files now only contains files that were archived from this source
            int numberOfFilesDeleted = 0;
            log(NOTICE, "Cleaning dropbox of videos no longer used from source: \""+source.getFullName()+"\". Filecount from dropbox from this source is currently: "+ numberOfFiles);
            for(File archivedStrmFile : strmFiles)
            {
                String path =archivedStrmFile.getPath();
                boolean valid = false;
                                
                File video = new File(path);
                valid = source.getVideosArchivedFromThisSource().get(video) != null;//valid if this file was archived during this run                
                if(!valid)
                {
                    //catch if this file isn't in the filesArchived list because it was skipped due to already existing in a different source
                    valid = source.getVideosSkippedBecauseAlreadyArchived().get(video) != null;
                    if(valid) 
                    {
                        //TODO: remove when confirmed this isn't happening anymore
                        //this shouldn't happen because it should have been caught in source.getVideosArchivedFromThisSource().get(video)
                       log(DEBUG, "Skipping cleaning a video that was skipped because of already archived. Review this. Didn't expect this to happen.");                            
                    }
                }                
                
                if(!valid)
                {
                    setShortLogDesc("Missing");
                    log(INFO, "This archived video no longer exists in videos found from "+ source.getFullName()+". "
                            + "Will mark it as missing: "+ path + " (file last modified "+new Date(archivedStrmFile.lastModified())+")");
                    boolean shouldDelete = tools.markVideoAsMissing(path);
                    if(shouldDelete)
                    {
                        setShortLogDesc("Delete");
                        //delete from Database
                        String deleteSQL = "DELETE FROM ArchivedFiles WHERE dropbox_location = ?";//unique indx on dropbox_location
                        int rowsDeleted = Config.archivedFilesDB.executeMultipleUpdate(deleteSQL,tools.params(path));
                        boolean deletedFromDB = rowsDeleted >=0;///as long as it wasn't a SQL error, it's no longer in the database
                        if(!deletedFromDB)
                        {
                            Config.log(WARNING, "Failed to delete this entry from the tracker database. Won't delete file until DB entry is removed. "
                                    + "Nothing was deleted using: "+ deleteSQL);
                            continue;
                        }
                        else log(INFO, "Successfully removed from ArchivedVideos db: "+archivedStrmFile);

                        //delete from File System
                        boolean deleted = tools.deleteStrmAndMetaFiles(archivedStrmFile);                        
                        if(deleted)
                        {
                            log(INFO, "Successfully deleted video file from disk: "+ archivedStrmFile);
                            numberOfFilesDeleted++;                                                                                                                

                            //also remove any queued meta data changes that might still exist
                            int numberDeleted = queuedChangesDB.executeMultipleUpdate("DELETE FROM QueuedChanges WHERE dropbox_location = ?", tools.params(path));
                            if(numberDeleted > 0)
                                Config.log(INFO, "Successfully removed "+ numberDeleted +" meta-data entry for the deleted source: "+ path);
                        }
                        else log(INFO, "Failed to delete video, will try again next time: "+ path);                                                
                    }
                    setShortLogDesc("Clean:"+source.getFullName());//back to normal
                }
            }
            log(NOTICE, "After cleaning dropbox, " +numberOfFilesDeleted +" old files were deleted for a new size of "+ (numberOfFiles-numberOfFilesDeleted) + " files");

            if(false)//DISABLING DELETING empty directories because keeping them will keep the fanart/thumbs/custom info that the user has saved about the show
                //incase the show gets added again later this meta-data will be saved
            {
                if(numberOfFilesDeleted > 0)
                {
                    log(INFO, "Now removing directories that have no videos inside of them");
                    Collection<File> folders = null;//TODO: recursively get folders below new File(DROPBOX)
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

                        String[] validVideoExts = new String[]{"strm"};
                        if(FileUtils.listFiles(dir, validVideoExts, true).isEmpty())//recursively get all strm files
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
            }//end skipping empty dir cleanup
        }
        catch(Exception x)
        {
            log(ERROR, "Error while cleaning dropbox of videos no longer needed: "+x,x);
        }
    }                 
}