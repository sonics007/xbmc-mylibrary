package mylibrary;
import xbmc.util.XbmcVideoLibraryFile;
import xbmc.db.XBMCVideoDbInterface;
import btv.logger.BTVLogLevel;
import java.io.File;
import java.net.URL;
import utilities.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import xbmc.jsonrpc.XbmcJsonRpcListener;
import xbmc.util.XBMCFile;
import xbmc.util.XbmcBox;

import xbmc.util.XbmcMovie;
import static utilities.Constants.*;
import static xbmc.util.Constants.*;
import static btv.tools.BTVTools.*;

public class importer extends Config
{
    
    private static String desiredBaseDir = null;
    public static void main(String[] args)
    {                            
        
        setShortLogDesc("Init");                                        
        long start = System.currentTimeMillis();
        if(args.length > 0)
        {
            desiredBaseDir = args[0];            
        }   
        
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
            Logger.ERROR( "Cannot continue, general error occurred: "+ x,x);            
        }
        finally
        {
            if(xbmcBox != null){
                try{
                    if(xbmcBox.getJsonRpcListener().isConnected())
                    {
                        xbmcBox.getJsonRpcListener().stop();
                        Logger.INFO( "Successfully stopped JSON-RPC listener.");
                    }
                }catch(Exception x){
                    Logger.ERROR( "Failed to end xbmcBox listener.");
                }
            }
            try{Archiver.globalSummary();}catch(Throwable x){}//dont care much if this fails, really care about Config.end()
            long end = System.currentTimeMillis();
            long seconds = (end-start) / 1000;

            setShortLogDesc("Ending");
            Logger.NOTICE("Done... Total processing time: "+ (seconds/60) +" minute(s), "+ (seconds%60)+" second(s)");
            Config.end();//stop all the background processes            
            
        }
    } 

    private boolean loadedConfigSuccessfully = false;
    private boolean libraryScanFinished = false;
    private static XbmcBox xbmcBox;
    
    public static boolean connectedToXbmc = false;//globally track if we are connected to json-rpc interface
    public importer()
    {
        super();//Load Config
        
        
        if(!valid(desiredBaseDir))
            Logger.INFO( "Attempting to auto-determine base directory. If this fails, specify the base directory of this program as a command line parameter.");            
        else
            BASE_DIR = new File(desiredBaseDir);
        
        Logger.NOTICE("Base program directory = "+ BASE_DIR);
        
                  
        loadedConfigSuccessfully = loadConfig();
        setLoadedConfig(loadedConfigSuccessfully);
        if(!loadedConfigSuccessfully)
        {
            Logger.ERROR( "Failed while loading Configuration and testing connections... cannot continue. Please check your settings in Config.xml");            
            return;
        }
                

         //summary of the sources/subfolders
        //for(Source src : ALL_SOURCES)
///            Logger.NOTICE( "Found source <"+ src.getName() +"> ("+src.getPath()+") with "+ src.getSubfolders().size() +" subfolders");

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
                                
        
        
        //\\DEBUG TESTING\\//
        if(false && TESTING)
        {//debug testing json-rpc
            //Logger.getOptions().setLevelToLogAt(BTVLogLevel.DEBUG);
            //jsonRPCSender.sendGUINotification("'ello there", "Gov'na", 3000);                                    
            //jsonRPC.getLibraryMusic(true);
            //jsonRPC.getLibraryVideos(true);
            //jsonRPCSender.getLibraryVideos();
            //jsonRPCSender.getVideoFileDetails("smb://localhost/dropbox$/Movies/Sexual Intelligence.strm");
            //jsonRPCSender.getMovieSets(true);
            
            XbmcMovie mov = jsonRPCSender.getMovieDetails(7);
            Logger.INFO(mov.getSetId()+": "+ mov.getSetName());
            Logger.INFO("Updated movieset ? "+jsonRPCSender.setMovieSet(7, ""));
            mov = jsonRPCSender.getMovieDetails(7);
            Logger.INFO(mov.getSetId()+": "+ mov.getSetName());
            
            //XbmcVideoLibraryFile f = jsonRPCSender.getVideoFileDetails("smb://localhost/dropbox$/Movies/The Gymnast.strm");
            
//            XbmcMovie mov = jsonRPCSender.getMovieDetails(7);
//            Logger.INFO(Arrays.toString(mov.getTags()));
//            Logger.INFO("Updated movie ? "+jsonRPCSender.setMovieTags(7, new String[0]));// new String[]{"tag1","tag2"}));
//            mov = jsonRPCSender.getMovieDetails(7);
//            Logger.INFO(Arrays.toString(mov.getTags()));
            
//            XbmcMovie mov = jsonRPCSender.getMovieDetails(7);
//            Logger.INFO(mov.getTitle());
//            Logger.INFO("Updated movie ? "+jsonRPCSender.setMovieTitle(7, "Craig'sList Joey"));
//            mov = jsonRPCSender.getMovieDetails(7);
//            Logger.INFO(mov.getTitle());
            
            if(true) return false;//done testing
        }
        
        //check if XBMC should be restarted
        if(RESTART_XBMC)
        {            
            String restartCmdFile = BASE_DIR+SEP+"res"+SEP+"RestartXBMC.cmd";
            Logger.NOTICE( "Restart XBMC is enabled, will send Quit command to XBMC, then execute restart script at: "+ restartCmdFile);
            //send quit commnd
            if(jsonRPCSender.ping())
            {
                JSONObject response = jsonRPCSender.callMethod("Application.Quit", 1, null);
                if(response != null)
                {
                    try
                    {
                        String result = response.getString("result");
                        if(result.equalsIgnoreCase("OK"))
                        {
                            Logger.NOTICE( "Quit command successfully sent to XBMC.");
                            int sec = 10;
                            Logger.INFO( "Waiting "+ sec +" seconds for XBMC to gracefully quit.");
                            //wait for XBMC to shutdown before executing restart command
                            try{Thread.sleep(1000 * sec);}catch(Exception x){}
                        }
                        else
                            Logger.WARN( "Quit command was not successfully sent to XBMC, response = "+ response);
                    }
                    catch(Exception x)
                    {
                        Logger.ERROR( "Unknown reponse from XBMC: "+ response,x);
                    }                    
                }
                else
                    Logger.WARN( "Failed to call XBMC.Quit. XBMC may not be running. Will now execute restart script at: "+ restartCmdFile);
            }
             else Logger.INFO( "JSON-RPC could not connect to XBMC, will execute restart script and test connectivity again: "+ restartCmdFile);
            
            try//always execute the restart script
            {
                ProcessBuilder pb = new ProcessBuilder("\""+restartCmdFile+"\"");
                pb.redirectErrorStream();
                Logger.INFO( "Executing restart script for XBMC.");
                java.lang.Process pr = pb.start();//don't read input/wait because it won't end until XBMC ends                
                Logger.INFO( "Restart executed. Waiting for JSON-RPC connectivity to resume...");
                try{Thread.sleep(7000);}catch(Exception x){}
                boolean connected = false;
                for(int i=0;i<3;i++)
                {
                     connected = jsonRPCSender.ping();
                     if(connected) break;
                     else try{Thread.sleep(2500);}catch(Exception ignored){}//wait & try again until loop runs out
                }
                if(!connected)
                {
                    Logger.ERROR( "It appears XMBC did not restart. JSON-RPC connectivity cannot be re-established. "
                            + "Ending. Please check your restart command, located at: "+ restartCmdFile);
                    return false;
                }
                else
                    Logger.INFO( "XBMC has successfully started, JSON-RPC connectivity re-established.");
            }
            catch(Exception x)
            {
                Logger.ERROR( "Failed while executing XBMC restart script. Exiting.",x);
                return false;
            }
         }//end XBMC restart

        //do a connectivity test
        Logger.INFO( "Testing connectivity to JSON-RPC web interface...");
        connectedToXbmc = jsonRPCSender.ping();
        Logger.INFO( "JSON-RPC web connected = "+ connectedToXbmc);
        
        if(!connectedToXbmc)
        {
            Logger.ERROR( "Cannot continue because JSON-RPC could not connect. Please check your Config.xml and make sure XBMC is running at: "+ XBMC_WEB_SERVER_URL);
            return false;
        }
        
        
        //Init JSON-RPC listener
        Logger.INFO( "Testing connectivity to JSON-RPC raw interface (listening for notifications)...");
        try
        {                        
            xbmcBox = new XbmcBox(new URL(XBMC_WEB_SERVER_URL), JSON_RPC_RAW_PORT);
            XbmcJsonRpcListener jsonRpcListener = new XbmcJsonRpcListener(xbmcBox) 
            {

                @Override
                public void fileStoppedPlaying(String filePath, JSONObject jsonAnnouncement, JSONObject currentPlayer) {
                    //not used
                }

                @Override
                public void libraryEpisodeUpdated(int episodeId, boolean watched) {
                    //not used
                }

                @Override
                public void videoLibraryScanFinished() {
                    libraryScanFinished=true;
                }

                @Override
                public void videoLibraryScanStarted() {
                    libraryScanFinished=false;
                }

                @Override
                public void disconnected() {
                    connectedToXbmc = false;
                    if(isConnected())
                    {
                        Logger.ERROR("JSON-RPC listener has disconnected... stopping");
                        this.stop();
                    }
                }

                @Override
                public void connected() {
                    connectedToXbmc = true;
                }
            };
            jsonRpcListener.setReconnectOnFail(false);
            
            if(!jsonRpcListener.isConnected())
            {
                throw new Exception("Failed to connect to "+ xbmcBox.getIpHost()+":"+xbmcBox.getJsonrpcPort());            
            }
            
            xbmcBox.setJsonRpcListener(jsonRpcListener);
            
        }catch(Exception x){
            Logger.ERROR( "Failed to connect to JSON-RPC on TCP Port: " + Config.JSON_RPC_RAW_PORT,x);
            connectedToXbmc = false;
            return false;
        }
        
        Logger.NOTICE("Connected to XBMC JSON-RPC interfaces successfully.");
        Logger.NOTICE("Starting source scan for "+ ALL_SOURCES.size() +" sources...");
        
         for(Source source : ALL_SOURCES)
         {
             if(source.getSubfolders().isEmpty())
             {
                 Logger.WARN( "The source named "+source.getName()+" has no subfolders associated with it. Nothing will be added from this source.");
                 continue;
             }
             
             if(!valid(source.getPath()))
             {
                 Logger.INFO("This source's path was not specified, will check list of video sources in XBMC for a matching label named \""+source.getName()+"\"");
                 try
                 {
                     Map<String, Object> sourcesParams = new HashMap<String,Object>();
                     sourcesParams.put("media", "video");
                     JSONObject xbmcSources = jsonRPCSender.callMethod("Files.GetSources", 1, sourcesParams);
                     JSONObject result = xbmcSources.getJSONObject("result");
                     JSONArray sourceArray = result.getJSONArray("sources");
                     for(int i=0;i<sourceArray.length();i++)
                     {
                         JSONObject nextSource = sourceArray.getJSONObject(i);
                         String label = nextSource.getString("label");
                         Logger.DEBUG( "Found label \""+label+"\", looking for match on: \""+source.getName()+"\"");
                         if(valid(label) && label.equalsIgnoreCase(source.getName()))
                         {
                             String path = nextSource.getString("file");
                             Logger.INFO( "Found matching path from XBMC's sources, source \""+source.getName()+"\" maps to: "+path);
                             source.setPath(path);
                             break;
                         }
                     }
                 }
                 catch(Exception x)
                 {
                     Logger.ERROR( "Failed to find source's path from XBMC's source list, will skipp the source named \""+source.getName()+"\"",x);
                     continue;
                 }
                 
                 if(!valid(source.getPath()))
                 {
                     Logger.ERROR( "No source named \""+source.getName()+"\" was found in XBMC's video source list. Will skip this source and all subfolders.");
                     continue;
                 }
             }

             
             if(true)
             {/*Disable all IceFilms support*/
                 if(source.getPath().toLowerCase().contains(ICEFILMS_IDENTIFIER_LC))
                 {
                     Logger.WARN( "IceFilms support has been disabled. Skipping IceFilms source. See here for details: http://forum.icefilms.info/viewtopic.php?f=24&t=21205");
                     continue;
                 }
             }
             
             for(Subfolder subf : source.getSubfolders())
             {
                //find the subfolder
                String fullPathLabel  = subf.getFullName().replace("/", xbmc.util.Constants.DELIM);//TODO: allows /'s to be escaped in config.xml if they appear in the name natively
                setShortLogDesc("Find:Subfolder");
                //get the subfolder from JSON-RPC to determine what xbmc path it's name maps to
                Logger.INFO( "Searching for subfolder: " + escapePath(fullPathLabel));
                
                XBMCFile subfolderDir =  jsonRPCSender.getSubfolderDirectory(subf);
                if(subfolderDir == null) continue;
                setShortLogDesc("Found!");
                Logger.NOTICE( source.getName()+"'s subfolder \""+subf.getFullName() +"\" maps to source: " + subfolderDir.getFile() + " ("+subfolderDir.getFullPathEscaped()+")");
                if(subf.isRegexName())
                    subf.setRegexMatchingName(subfolderDir.getFullPathEscaped());

                //get list of files in this subfolder
                setShortLogDesc("Search:"+source.getName());
                Map<String,Object> params = new LinkedHashMap<String,Object>();
                params.put("media", "video");

                //seperate thread for archiving simultaneously
                Archiver archiver = new Archiver(source);
                BlockingQueue<MyLibraryFile> filesInThisSubfolder = new ArrayBlockingQueue<MyLibraryFile>(20000, true);//init capacity, fair
                
                archiver.start(subf, filesInThisSubfolder);
                
                Logger.NOTICE( "Finding all matching videos under subfolder: "+ subfolderDir.getFullPathEscaped());
                try
                {
                    jsonRPCSender.getFilesInSubfolder(//recursively list all files in this subdirectory (based on filters)
                        subf,
                        subfolderDir.getFile(),
                        subfolderDir.getFullPath(),                        
                        FILES_ONLY,
                        filesInThisSubfolder);                                           
                }
                catch(Exception x)
                {
                    Logger.ERROR( "Failed to get files from JSON-RPC",x);
                }
                finally
                {
                    archiver.canStop();//let it know that the queue is not being fed any more so the archive can finish once it runs out of files to check
                }
                Logger.NOTICE( "Done retrieving files from JSON-RPC for subfolder: "+subf.getFullName());

                //wait for archiver to finish if need
                if(!filesInThisSubfolder.isEmpty())
                    Logger.INFO( "There are "+ filesInThisSubfolder.size() +" files that stil need to be archived, waiting for archiving to finish before proceeding to next subfolder.");
                while(!archiver.isStopped())
                    try{Thread.sleep(250);}catch(Exception ignored){}

                Logger.NOTICE( "Archiving has finished for "+ subf.getFullName()+".");
                if(!filesInThisSubfolder.isEmpty())
                    Logger.ERROR( "Archiver has finished but the queue was not emptied, unexpected. Some videos may not be archived!");

                if(archiver.archiveSuccess == 0 && archiver.archiveSkip == 0)//no files found
                {
                    subf.getSource().setJSONRPCErrors(true);//if we didn't find any files, assume an error occurred
                    Logger.ERROR( "Since no files were successfully archived in this subfolder, assuming an error occured. "
                            + "This will prevent the dropbox from being cleaned for files from this source");
                }

                source.trackArchivedVideos(archiver.filesArchived, archiver.videosSkippedBecauseAlreadyArchived); //need to track for clean-up purposes
            }//end looping thru the subfolders of this source
             
            //clean the dropbox for this source's archived videos (as long as there weren't errors getting the videos from this source)
            if(true || //for now, always clean, regardless of errors
                    !source.hadJSONRPCErrors())//TODO: investigate this. hadJSONErrors was true too many times for large sources, such as PlayOn, preventing clean-ups. Maybe the clean-up thresholds in condig.xml are enough?
            {
                if(connectedToXbmc)
                    cleanDropbox(source);
                else
                {
                    Logger.WARN("Skipping clean up because connection to JSON-RPC has been lost.");
                    setShortLogDesc("");
                }
            }
            else
            {
                setShortLogDesc("Skip-Clean");
                Logger.NOTICE( "Skipping cleaning dropbox for videos from \""+source.getName()+"\" because there were error(s) "
                        + "while getting File list for its Subfolders from JSON-RPC interface/PlayOn");
                setShortLogDesc("");
            }            
        }//end looping thru Sources
        
        if(Archiver.globalarchiveSuccess == 0)
        {
            Logger.NOTICE( "No videos were successfully archived, skipping XBMC Library scan.");
        }
        else
        {
             Logger.NOTICE( Archiver.globalarchiveSuccess +" videos were successfully archived/updated. Triggering XBMC content scan");
             //trigger XBMC databaase update and integrate meta-data changes. Also do manual archiving after intitial update (if enabled)
             for(int loop=1; loop<=(MANUAL_ARCHIVING_ENABLED ? 2 : 1);loop++)//need to loop 2 times if manual archiving is enabled, 2nd time is after .nfo generation
             {
                setShortLogDesc("ContentScan"+(MANUAL_ARCHIVING_ENABLED ? "-"+loop :""));                

                Logger.NOTICE( "Triggering video library content scan via JSON-RPC interface.");
                boolean success = jsonRPCSender.videoLibraryScan();                
                if(!success)
                {
                    Logger.WARN( "JSON-RPC XBMC interface for method 'VideoLibrary.Scan' failed; Will not add meta-data or do Manual Archiving.");
                }
                else//update trigger was successful
                {                    
                    //wait until XBMC is done adding videos to the library.
                    int sleepInterval= 1000;
                    long totalTimeSlept = 0;
                    long maxSleepTime = 1000 * 60 * 10;//TODO: Put into Config.xml
                    Logger.INFO( "Waiting for content scan to finish.");
                    do{
                        try{Thread.sleep(sleepInterval);}catch(InterruptedException ignored){}
                        totalTimeSlept += sleepInterval;
                        
                        if(libraryScanFinished){
                            Logger.NOTICE( "The Video Library Scan has finished, will continue now.");
                        }
                        
                        if(totalTimeSlept > maxSleepTime){
                            Logger.WARN( "Library update has not finished after max wait time of "+(maxSleepTime/1000) +" seconds. Will continue now.");
                            libraryScanFinished = true;
                        }
                    }while(!libraryScanFinished);
                    
                    
                    setShortLogDesc("XBMC-JSONRPC");
                    updateMetaData();

                    if(MANUAL_ARCHIVING_ENABLED)
                    {
                        if(loop == 1)//on the first loop. generate .nfo's if needed
                        {
                            boolean videosWereManuallyArchived = manualArchiveIfNeeded(Archiver.allVideosArchived, jsonRPCSender.getLibraryVideos());
                            if(videosWereManuallyArchived)
                            {
                                //trigger a content scan, but no need to wait for it
                                Logger.NOTICE( "Since videos were manually archived with .nfo files, triggering another content scan to catch manually archived videos.");
                            }
                             else
                            {
                                Logger.INFO( "No new .nfo's were created, will not trigger another content scan.");
                                 break;//no need to loop again if no .nfo's were generated
                            }
                        }
                    }
                    else
                    {
                        Logger.NOTICE( "Will not manually archive any videos (.nfo) because manual archiving is disabled in Config file.");
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
    
    
    public boolean manualArchiveIfNeeded(Map<File, MyLibraryFile> allVideosArchived, Map<String,XBMCFile> allVideoFilesInLibrary)
    {
        setShortLogDesc("ManualArchive");
        if(allVideoFilesInLibrary.isEmpty() && !TESTING)
        {
            Logger.WARN( "No videos were found in XBMC's library. Assumining there is a problem and skipping manual archiving.");
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
                Logger.NOTICE( "No folder found at "+ folder +". Will not manually archive any "+ videoType);
                continue;
            }
            
            int numberNotInLibrary = 0;
            int numberInLibrary = 0;
            List<File> strmsNotInLibrary = new ArrayList();
            for(File strmFile : videosInFolder)
            {
                String xbmcPathStrm = XBMCVideoDbInterface.getFullXBMCPath(strmFile).toLowerCase();//key(path) is lowercase in: allVideoFilesInLibrary                
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

            for(File strm : strmsNotInLibrary) 
                Logger.DEBUG( "Not in library: "+ strm.getPath());

            Logger.NOTICE( "Of "+(videosInFolder.size())+" total "+videoType+" in dropbox, "
                    + "found " + numberNotInLibrary +" videos not yet in XBMC's library. "
                    + numberInLibrary +" files are in the library.");                       
        }
        
        if(allVideosNotInLibrary.isEmpty())
        {
            Logger.NOTICE("No videos need to be manually archived (all are in XBMC's library already).");
            return false;
        }
        
        Logger.NOTICE( "Will attempt manual archive for "+ allVideosNotInLibrary.size() +" video files that are not in XBMC's library.");
        int manualArchiveSuccess = 0, manualArchiveFail = 0;
        for(File strm : allVideosNotInLibrary)
        {            
            boolean newVideoWasManuallArchived;
            MyLibraryFile video = allVideosArchived.get(strm);            

            if(video != null)
            {
                Logger.DEBUG( "Video found in cache. Attempting manual archive.");
                newVideoWasManuallArchived = manualArchive(video);
            }
            else
            {
                Logger.INFO( "Could not find corresponding original video that was archived at: "+ strm+". Will attempt secondary method. "
                        + "This usually means the original source that created this video no longer exists.");
                newVideoWasManuallArchived = manualArchive(strm);
            }

            if(newVideoWasManuallArchived) manualArchiveSuccess++;
            else manualArchiveFail++;
        }
        
        Logger.NOTICE( manualArchiveSuccess + " new videos were successfully manually archived. "
                + manualArchiveFail+" videos were not manually archived (either skipped or failed).");

        return (manualArchiveSuccess>0);       
    }

    /*
     * Looks up the file in the database based on its archived location.
     */
    public boolean manualArchive(File strmFile)
    {
        MyLibraryFile video = tools.getVideoFromDropboxLocation(strmFile);
        if(video == null || !video.hasValidMetaData())
        {
            //delete the file since it can't be manually archived and it wasn't added by XBMC's library scan
            Logger.WARN( "Failed to get meta-data for this video. It will be deleted since it was not scraped by XBMC and cannot be manually archived: "+strmFile);
            boolean deleted = tools.deleteStrmAndMetaFiles(strmFile);
            if(!deleted)Logger.WARN( "Failed to delete this file: "+ strmFile);
            return false;
        }
        else
            return manualArchive(video);
    }
    

    public boolean manualArchive(MyLibraryFile video)
    {        
        if(video == null)
        {
            Logger.WARN( "Invalid video passed to manualArchive(). Skipping null video.");
            return false;
        }
        if(!valid(video.getFinalLocation()))
        {
            Logger.WARN( "Cannot manually archive because the location of the video in the dropbox is not known.");
            return false;
        }
        File archivedFile = new File(video.getFinalLocation());
       
        if(!archivedFile.isFile())
        {
            Logger.WARN( "Cannot manually archive because the file does not exist at: "+ archivedFile);
            return false;
        }
        
        Long dateCreated = archivedFilesDB.getDateArchived(archivedFile.getPath());
        if(dateCreated == null)
        {
            Logger.WARN( "Cannot manually archive this file because cannot determine when it was created.");
            return false;
        }
        
        long secondsOld = (System.currentTimeMillis() - dateCreated) / 1000;
        double hoursOld = (secondsOld / 60.0 / 60.0);
        if(hoursOld < HOURS_OLD_BEFORE_MANUAL_ARCHIVE)
        {
            Logger.INFO( "Will not manually archive because the file was archived "+ tools.toTwoDecimals(hoursOld) +" hours ago, "
                    + "which is < threshold of "+ HOURS_OLD_BEFORE_MANUAL_ARCHIVE +" hours: "+ archivedFile);
            return false;
        }
        
        if(video.isTvShow())
        {
            
            //bradvido
            //DISABLING tvshow.nfo generation because it messes up show.
            //We will only manually archive the actual episode.
            //The TV Show has to exist in the online scraper.
        /*             
            //check if the series nfo exists
            File seriesDir = archivedFile.getParentFile()//season.x directory
                              .getParentFile();//Series.Title directory            
            
            Logger.INFO( "Series Dir is: "+ seriesDir);

            File seriesNFO = new File(seriesDir+SEP+"tvshow.nfo");
            if(!seriesNFO.exists())
            {
                if(valid(video.getSeries()))
                {
                    Logger.INFO( "Creating tvshow.nfo file because it does not exist: "+ seriesNFO);
                    List<String> lines = new ArrayList<String>();
                    lines.add("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>");
                    lines.add("<tvshow>");
                    lines.add("<title>"+video.getSeries()+"</title>");//maps to Airing.Show.ShowTitle
                    lines.add("</tvshow>");
                    if(tools.writeToFile(seriesNFO, lines, true))
                        Logger.INFO( "Successfully created .nfo at: "+ seriesNFO);
                    else
                    {
                        Logger.WARN( "Failed to create .nfo at: "+ seriesNFO+". Cannot manually archive this video: "+ archivedFile);
                        return false;
                    }
                }
                else
                {
                    Logger.WARN( "Cannot manually archive this video because the series is unknown: \""+archivedFile+"\"");
                    return false;
                }
            }
         */
            
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
                        Logger.INFO( "Season and episode number parsed successfully from file name. Season="+ s +", Episode="+ e);
                    }
                    catch(Exception x)
                    {
                        Logger.WARN( "Season and Episode number cannot be parsed from filename: "+ archivedFile.getPath()+". Cannot manually archive this file.",x);
                        return false;
                    }

                    File episodeNFO = new File(archivedFile.getPath().substring(0,archivedFile.getPath().lastIndexOf("."))+".nfo");
                    boolean updating = (episodeNFO.exists());
                    if(updating)
                        Logger.INFO( "Episode .nfo already exits. It will be over-written: "+ episodeNFO);

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
                        Logger.INFO( "Successfully "+(updating ? "updated" : "created")+" .nfo at: "+ episodeNFO);
                        if(updating) return false; else return true;//only return true for new .nfo's
                    }
                    else
                    {
                        Logger.WARN( "Failed to create .nfo at: "+ episodeNFO+". Cannot manually archive this video: "+ archivedFile);
                        return false;
                    }
                }
                else
                {
                    Logger.WARN( "SxxExx pattern was not found in file name. Cannot manually archive: "+ archivedFile);
                    return false;
                }
            }
            else
            {
                Logger.WARN( "Cannot manually archive because title was not found: "+ archivedFile);
                return false;
            }
        }
        else if(video.isMovie())
        {
            if(!valid(video.getTitle()))
            {
                Logger.WARN( "Cannot manually archive .nfo because no title is available for movie: "+ archivedFile);
                return false;
            }
            File movieNFO = new File(archivedFile.getPath().substring(0,archivedFile.getPath().lastIndexOf("."))+".nfo");
            boolean updating = movieNFO.exists();
            if(updating)
                Logger.INFO( "Movie .nfo already exits. It will be over-written: "+ movieNFO);

            List<String> lines = new ArrayList<String>();
            
            String movieSet = null;//check for a set
                        
            if(video.getSubfolder() != null)
            {
                if(valid(video.getSubfolder().getMovieSet()))
                {
                    movieSet = video.getSubfolder().getMovieSet();
                    if(!movieSet.trim().equals(movieSet))
                    {
                        Logger.INFO( "Not adding <set> tag to .nfo because the movie set either starts or ends with a space. "
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
                Logger.INFO( "Successfully "+(updating ? "updated" : "created")+" .nfo at: "+ movieNFO);
                if(updating) return false; else return true;//only return true for new .nfo's
            }
            else
            {
                Logger.WARN( "Failed to create .nfo at: "+ movieNFO+". Cannot manually archive this video: "+ archivedFile);
                return false;
            }
        }
        else if(video.isMusicVideo())
        {
            if(!valid(video.getTitle()))
            {
                Logger.WARN( "Cannot manually archive .nfo because no title is available for music video: "+ archivedFile);
                return false;
            }
            if(!valid(video.getArtist()))
            {
                Logger.WARN( "Cannot manuall archive .nfo because no artist is available for music video: "+ archivedFile);
                return false;
            }
            File musicVideoNFO = new File(archivedFile.getPath().substring(0,archivedFile.getPath().lastIndexOf("."))+".nfo");
            boolean updating = musicVideoNFO.exists();
            if(updating)
                Logger.INFO( "Music Video .nfo already exits. It will be over-written: "+ musicVideoNFO);

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
                Logger.INFO( "Successfully "+(updating ? "updated" : "created")+" .nfo at: "+ musicVideoNFO);
                if(updating) return false; else return true;//only return true for new .nfo's
            }
            else
            {
                Logger.WARN( "Failed to create .nfo at: "+ musicVideoNFO+". Cannot manually archive this video: "+ archivedFile);
                return false;
            }
        }
    	//AngryCamel - 20120817 1620 - Added generic
        else if(video.isGeneric())
        {
            Logger.WARN( "Cannot generate .nfo files for generic videos.");
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
         
         try
         {                          
             Logger.INFO( "Will now update meta-data for videos in XBMC's library.");
             //update meta-data that's been queued
             Config.jsonRPCSender.addMetaDataFromQueue();

         }
         catch(Exception x)
         {
             Logger.ERROR( "General exception while updating XBMC database: "+ x,x);
         }
         
    }

    
   
    private void dropboxCleanUp()
    {
        //searches for strm files in dropbox that are no longer in the tracking database and deletes the files        
        setShortLogDesc("Clean Up");
        Logger.NOTICE( "Cleaning up dropbox...");
                      
        Collection<File> allVideoInDropbox = FileUtils.listFiles(new File(DROPBOX), new String[]{"strm"}, true);
        Logger.INFO( "Checking " + allVideoInDropbox.size()+ " archived strm videos in dropbox to make sure the archived video is still valid.");

        int deletedCount = 0;
        for(Iterator<File> it = allVideoInDropbox.iterator(); it.hasNext();)
        {
            //check if this file exists in the database, if not, delete it
            File strmFile = it.next();                           
            String path = strmFile.getPath();
            
            String getIdSQL = "SELECT id FROM ArchivedFiles WHERE lower(dropbox_location) = ?";
            
            int archivedFileId = archivedFilesDB.getSingleInt(getIdSQL, path.toLowerCase());
            if(archivedFileId == SQL_ERROR)
            {
                Logger.WARN( "Failed to determine archived id for file at: "+ path +". Will skip cleanup for this file. SQL used = "+ getIdSQL);
                continue;
            }
            
            boolean existsInDatabase = archivedFileId > -1;          
            if(!existsInDatabase)
            {
                Logger.INFO( "File found in dropbox that no longer exists in ArchivedFiles database");
                Logger.NOTICE( "Deleting old file at: "+ path);
                                    
                boolean deleted = tools.deleteStrmAndMetaFiles(strmFile);
                if(!deleted)
                    Logger.WARN( "Failed to delete old video. Will try again next time: "+ strmFile);
                else//successfully deleted
                    deletedCount++;                
            }            
        }
        Logger.NOTICE( "After removing "+deletedCount+" old videos from dropbox, number of videos is now: "+ (allVideoInDropbox.size()-deletedCount));
        Logger.NOTICE( "Done with dropbox clean-up");
         
        setShortLogDesc("");
    }
    
    private void fileExpiration()
    {
        setShortLogDesc("TidyUp");
        try
        {
            Collection<File> cachedXML = FileUtils.listFiles(new File(BASE_DIR+SEP+"XMLCache"), new String[]{"xml"}, false);
            int deleteCount = 0;
            for(File f : cachedXML)            
                if(f.delete()) deleteCount++;
            Logger.log(deleteCount>0?BTVLogLevel.INFO:BTVLogLevel.DEBUG, "Successfully cleared out " + deleteCount +" of "+ cachedXML.size() +" cached XML files.",null);
        }
        catch(Exception x)
        {
            Logger.WARN( "Failed to clean up XML cache: "+ x,x);
        }        
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
            Logger.NOTICE( "Cleaning dropbox of videos no longer used from source: \""+source.getFullName()+"\". Filecount from dropbox from this source is currently: "+ numberOfFiles);
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
                       Logger.DEBUG( "Skipping cleaning a video that was skipped because of already archived. Review this. Didn't expect this to happen.");                            
                    }
                }                
                
                if(!valid)
                {
                    setShortLogDesc("Missing");
                    Logger.INFO( "This archived video no longer exists in videos found from "+ source.getFullName()+". "
                            + "Will mark it as missing: "+ path + " (file last modified "+new Date(archivedStrmFile.lastModified())+")");
                    boolean shouldDelete = tools.markVideoAsMissing(path);
                    if(shouldDelete)
                    {
                        setShortLogDesc("Delete");
                        //delete from Database
                        String deleteSQL = "DELETE FROM ArchivedFiles WHERE dropbox_location = ?";//unique indx on dropbox_location
                        int rowsDeleted = Config.archivedFilesDB.executeMultipleUpdate(deleteSQL,path);
                        boolean deletedFromDB = rowsDeleted >=0;///as long as it wasn't a SQL error, it's no longer in the database
                        if(!deletedFromDB)
                        {
                            Logger.WARN( "Failed to delete this entry from the tracker database. Won't delete file until DB entry is removed. "
                                    + "Nothing was deleted using: "+ deleteSQL);
                            continue;
                        }
                        else Logger.INFO( "Successfully removed from ArchivedVideos db: "+archivedStrmFile);

                        //delete from File System
                        boolean deleted = tools.deleteStrmAndMetaFiles(archivedStrmFile);                        
                        if(deleted)
                        {
                            Logger.INFO( "Successfully deleted video file from disk: "+ archivedStrmFile);
                            numberOfFilesDeleted++;                                                                                                                

                            //also remove any queued meta data changes that might still exist
                            int numberDeleted = queuedChangesDB.executeMultipleUpdate("DELETE FROM QueuedChanges WHERE dropbox_location = ?", path);
                            if(numberDeleted > 0)
                                Logger.INFO( "Successfully removed "+ numberDeleted +" meta-data entry for the deleted source: "+ path);
                        }
                        else Logger.INFO( "Failed to delete video, will try again next time: "+ path);                                                
                    }
                    setShortLogDesc("Clean:"+source.getFullName());//back to normal
                }
            }
            Logger.NOTICE( "After cleaning dropbox, " +numberOfFilesDeleted +" old files were deleted for a new size of "+ (numberOfFiles-numberOfFilesDeleted) + " files");

            if(false)//DISABLING DELETING empty directories because keeping them will keep the fanart/thumbs/custom info that the user has saved about the show
                //incase the show gets added again later this meta-data will be saved
            {
                if(numberOfFilesDeleted > 0)
                {
                    Logger.INFO( "Now removing directories that have no videos inside of them");
                    Collection<File> folders = null;//TODO: recursively get folders below new File(DROPBOX)
                    Logger.INFO( "Found "+ folders.size() +" folders in dropbox.");
                    int dirsDeleted = 0;
                    for(File dir : folders)
                    {
                        if(!dir.exists())
                        {
                            dirsDeleted++;//this folder was recursively deleted in a FileUtils.deleteDirectory call
                            Logger.INFO( "Deleted empty directory: "+ dir);
                            continue;
                        }

                        if(!dir.isDirectory())
                        {
                            Logger.INFO( "This file is not a directory, skipping: "+ dir);
                            continue;
                        }

                        String[] validVideoExts = new String[]{"strm"};
                        if(FileUtils.listFiles(dir, validVideoExts, true).isEmpty())//recursively get all strm files
                        {
                            try
                            {
                                FileUtils.deleteDirectory(dir);//recursively deletes
                                dirsDeleted++;
                                Logger.INFO( "Deleted empty (contained no "+Arrays.toString(validVideoExts)+" files) directory: "+ dir);
                            }
                            catch(Exception x)
                            {
                                Logger.INFO( "Failed to delete empty directory, will try again next time: "+ dir,x);
                            }
                        }
                    }
                    Logger.log(dirsDeleted > 0 ? BTVLogLevel.NOTICE : BTVLogLevel.INFO, dirsDeleted +" empty directories were removed, new total = "+ (folders.size() -dirsDeleted) + " directories in dropbox",null);
                }
            }//end skipping empty dir cleanup
        }
        catch(Exception x)
        {
            Logger.ERROR( "Error while cleaning dropbox of videos no longer needed: "+x,x);
        }
    }                 
}