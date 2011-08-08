package utilities;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.apache.commons.io.FileUtils;

public class Archiver implements Runnable, Constants
{

    public static void main(String[] args) throws IOException
    {
        //\\onyx\data\compressed\StreamingVideos\TV Shows\Cops.-.s23e15\Season.23\S23E15 - Dazed & Confused #3.strm
        //PlayOn/Hulu/Popular/Popular Episodes/Cops - s23e15: Cops: Dazed & Confused #3
        //PlayOn/Netflix/Instant Queue/Alphabetical/2/24: Season 1/01: 12:00 Midnight-1:00 A.M
        //Netflix/Instant Queue/Alphabetical/M/MythBusters/Collection 1/02: Barrel of Bricks
        new Config(MY_LIBRARY).loadConfig();
        Source src = new Source("testing","testing");
        Archiver a = new Archiver(src);

        String name = "PlayOn/Netflix/Instant Queue/Alphabetical/M/MythBusters/Collection 1/02: Barrel of Bricks";
        Config.LOGGING_LEVEL = DEBUG;
        log(INFO,"TESTING: "+name);
        XBMCFile video = new XBMCFile(name.replace("/", DELIM));
        video.setType(TV_SHOW);
        
        src.setCustomParser("playon");
        Subfolder subf = new Subfolder(src, "testing");//to avoid NPE's, instantite dummy objects for the lookup.
        a.subf = subf;
        video.setSubfolder(subf);
        //String sxxExx = findSeasonEpisodeNumbers(video);
        //log(INFO, "SxxExx = "+ sxxExx);
        //addTVMetaDataFromSxxExx(video, sxxExx);
        a.addMetadata(video);
        log(INFO, "Title = "+ video.getTitle()+", Series = "+video.getSeries() +", SeasonEpisode = "+ video.getSeasonEpisodeNaming());

    }
    private Thread t;
    private boolean STOP, CAN_STOP=false, IS_STOPPED = false;
    BlockingQueue<XBMCFile> files;
    private Subfolder subf;
    public static List<Pattern> seasonEpisodePatterns = new ArrayList<Pattern>();

    //need to track totals for vidoes skipped because already archived  and vidoes archived successfully
    //track across all subfolders, so these are static variables
    public static Map<File,String> allVideosSkippedBecauseAlreadyArchived = new LinkedHashMap<File,String>();
    public static Map<File,String> allFilesArchived = new HashMap<File,String>();

    public static Map<File, XBMCFile> allVideosArchived = new LinkedHashMap<File, XBMCFile>();//must keep this linked

    //only track this subfolder (not-static)
    public Map<File,String> videosSkippedBecauseAlreadyArchived = new LinkedHashMap<File,String>();
    public Map<File,String> filesArchived = new HashMap<File,String>();

    public int tvSuccess = 0, tvFail = 0, musicVideoSuccess = 0, musicVideoFail = 0, movieSuccess = 0, movieFail = 0,archiveSuccess = 0, archiveFail = 0, archiveSkip =0, newArchivedCount = 0, updatedCount = 0;    
    public static int globaltvSuccess = 0, globaltvFail = 0, globalmusicVideoSuccess = 0, globalmusicVideoFail = 0, globalmovieSuccess = 0, globalmovieFail = 0, globalarchiveSuccess = 0, globalarchiveFail = 0, globalarchiveSkip =0, globalnewArchivedCount = 0, globalupdatedCount = 0;
    Source source;
    Set<File> filesArchivedFromThisSource;
    public Archiver(Source source)
    {
        this.source = source;
        if(!"manualarchive".equalsIgnoreCase(source.getName()))
            filesArchivedFromThisSource = tools.getFilesArchivedBySource(source.getName());
        STOP = false;
        //init patterns
        //ORDER MATTERS FOR THESE PATTERNS. DO NOT CHANGE INDEX IN THE LIST
        seasonEpisodePatterns.add(Pattern.compile("S[0-9]+E[0-9]+", Pattern.CASE_INSENSITIVE));//s12e2
        seasonEpisodePatterns.add(Pattern.compile("[0-9]+x[0-9]+", Pattern.CASE_INSENSITIVE));//12x2
        t = new Thread(this);
    }

    public void start(Subfolder subf, BlockingQueue<XBMCFile> files)
    {
        this.files = files;
        this.subf = subf;
        
        t.start();
    }

    public void canStop()
    {
        CAN_STOP = true;
    }
    public void stop()
    {
        STOP = true;
    }    
    public boolean isStopped()
    {
        return IS_STOPPED;
    }
    public void run() 
    {
        try
        {
            setShortLogDesc("Archiving");
            while(!STOP)
            {
                XBMCFile video = null;
                try
                {
                     video = files.poll(1000, TimeUnit.MILLISECONDS);
                }
                catch(InterruptedException x)
                {
                    Config.log(ERROR, "Interrupted while archiving vidoes from queue. Cannot continue.",x);
                    STOP = true;
                    break;
                }

                if(video != null)
                {
                    //we have a video to archive
                    archiveVideo(video);
                }
                else//timed out while polling...
                {
                    if(CAN_STOP)//if can stop, it means that the queue is not being fed anymore. so we are at the end
                    {
                        STOP = true;
                        break;
                    }
                    //else just continue to poll until stop() or canStop() is called
                }
            }
            
            summarize(subf.getFullNameClean(), 
                    tvSuccess, tvFail,
                    movieSuccess, movieFail,
                    musicVideoSuccess, musicVideoFail,
                    newArchivedCount, updatedCount, archiveSuccess, archiveSkip, archiveFail);

        }
        catch(Exception x)
        {            
            log(ERROR, "General error in archiving thread for subfolder named "+ subf.getFullName() +": "+ x,x);
        }
        finally
        {
            IS_STOPPED = true;
        }
    }

    private static void summarize(String subfName, int tvSuccess, int tvFail, int movieSuccess, int movieFail, 
            int musicVideoSuccess, int musicVideoFail, int newArchivedCount, int updatedCount, int archiveSuccess,
            int archiveSkip, int archiveFail)
    {
        //try{subfName = subfName.substring(0, subfName.indexOf("/"));}catch(Exception ignored){}//try to trim to only the source name
        setShortLogDesc("Summary:"+subfName);
        log(NOTICE, "----------------------Archiving Summary for " + subfName+"----------------------");
        log(NOTICE, "TV Success: "+ tvSuccess+", TV Fail: "+ tvFail);
        log(NOTICE, "Movie Success: "+ movieSuccess+", Movie Fail: "+ movieFail);
        log(NOTICE, "Music Video Success: "+ musicVideoSuccess+", Music Video Fail: "+ musicVideoFail);
        log(NOTICE, "New videos archived: "+ newArchivedCount +", existing videos updated: "+ updatedCount);
        log(NOTICE,"Overall: Success: "+ archiveSuccess+", Skip: "+ archiveSkip +", Fail: "+ archiveFail);
        setShortLogDesc("");

        //track global counts
        if(!subfName.equals(GLOBAL_SUMMARY_NAME))//dont add global to global
        {
            globalarchiveFail += archiveFail;
            globalarchiveSkip += archiveSkip;
            globalarchiveSuccess += archiveSuccess;
            globalnewArchivedCount += newArchivedCount;
            globalupdatedCount += updatedCount;
            globalmovieFail += movieFail;
            globalmovieSuccess += movieSuccess;
            globalmusicVideoFail += musicVideoFail;
            globalmusicVideoSuccess += musicVideoSuccess;
            globaltvFail += tvFail;
            globaltvSuccess += tvSuccess;
        }
    }

    final static String GLOBAL_SUMMARY_NAME = "---Overall---";
    public static void globalSummary()
    {
        summarize(GLOBAL_SUMMARY_NAME, globaltvSuccess, globaltvFail, globalmovieSuccess, globalmovieFail, globalmusicVideoSuccess, globalmusicVideoFail, globalnewArchivedCount, globalupdatedCount, globalarchiveSuccess, globalarchiveSkip, globalarchiveFail);
    }

    public void archiveVideo(XBMCFile video)
    {
                        
        //if its not a duplicate video,check if we can add more (if it can be a dup, need to continue and determine if it is a dup)
        if(!subf.canContainMultiPartVideos())
        {
            if(!subf.canAddAnotherVideo())
            {
                archiveSkip++;
                return;
            }
        }
        log(DEBUG,"Next: "+video.getFullPathEscaped());
        
        video.setType(subf.getType());        
        addMetadata(video);

        //we now know if it's a dup video or not
        if(!video.isDuplicate())
        {
            if(!subf.canAddAnotherVideo())
            {
                archiveSkip++;
                return;
            }
        }
        //log(INFO, video.getFullPathEscaped()+" Duplicate ? "+ video.isDuplicate());

        //if it doesnt have valid meta data yet, try TVDB lookup / manual archiving
        if(!video.hasValidMetaData())
        {
            if(video.isTvShow())
            {
                if(valid(video.getSeries()) && valid(video.getTitle()))//just cant find sXXeXX numbers, try tvdb lookup based on title/series
                {
                    if(!video.hasBeenLookedUpOnTVDB())
                    {
                        
                        log(INFO, "Since this video has a valid series and title, will attempt to lookup Seasion/Episode numbers on TheTVDB.com.");
                        TVDB.lookupTVShow(video);
                    }

                    if(!video.hasValidMetaData())//still no valid season/episode numbers after TVDB lookup
                    {
                        if(Config.MANUAL_ARCHIVING_ENABLED)
                        {
                            log(INFO, "Archiving as a special (Season zero) episode since TVDB lookup failed....");
                            //save as a special
                            video.setSeasonNumber(0);
                            video.setEpisodeNumber(getNextSpecialEpisiodeNumber(video));
                        }
                        else
                            log(INFO,"TV Episode will fail to be archived because manual archiving is disabled and the meta-data cannot be parsed or found on the TheTVDB.com: "+ video.getFullPathEscaped());
                    }

                }
            }
            else//movie or music video
            {
                //nowhere else to lookup meta-data for these. they will be skipped
            }
        }

        
        if(video.hasValidMetaData())
        {
            //check for max limits (max video was checked at beginning of this method and does not need to be checked again here)
            boolean withinLimits = true;//default for non-tv types
            if(video.isTvShow())
            {
                //check series limit
                withinLimits = subf.canAddAnotherSeries(video.getSeries());
            }            

            if(withinLimits)
            {
                boolean hasBeenDownloaded = isDownloaded(video);//skip it if it's already been downloaded to local
                if(!hasBeenDownloaded)
                {
                    //check if this file has already been archived
                    boolean originalWasSkippedBecauseAlreadyArchived = video.isDuplicate() && video.getOriginalVideo().skippedBecauseAlreadyArchived(); //this file is a duplicate, and the original was skipped becasue of already archived
                    boolean alreadyArchived = originalWasSkippedBecauseAlreadyArchived //if the original was skipped, also skip dups
                            || (!video.isDuplicate() && isAlreadyArchived(video));//not a duplicate, do the normal isAlreadyArchived check
                    if(!alreadyArchived)
                    {
                        boolean archived = archiveFileInDropbox(video);                                                
                        increaseArchiveCount(video,archived);//increases count (either success of fail) per video type and for overall count                        
                    }
                    else//already been archived
                    {
                        video.setSkippedBecauseAlreadyArchived(true);
                        if(!video.isDuplicate())//only log the original skip
                        {
                            archiveSkip++;
                            setShortLogDesc("Arvhive:Skip");
                            log(DEBUG, "SKIP: Not archiving this video because it has already been archived by another source");
                            setShortLogDesc("Archiving");
                        }
                    }
                }
                else//this video has been downloaded
                {
                    //treat downloaded vidoes as successfully archived (updated) videos
                    if(!video.isDuplicate())
                    {
                        increaseArchiveCount(video,true);
                        setShortLogDesc("Arvhive:Downloaded");
                        log(INFO, "DOWNLOADED: Not archiving this video as a .strm because it "
                                + "has been downloaded locally: "+ video.getFullPathEscaped() 
                                + " (at: "+ getDroboxDestNoExt(video)+".downloaded)");
                        setShortLogDesc("Archiving");
                    }
                }
            }
            else
            {
                if(!video.isDuplicate())
                {
                    archiveSkip++;
                    setShortLogDesc("Arvhive:Skip");
                    log(INFO, "SKIP: Not archiving because max limit has been reached: "
                        + (video.isTvShow() ? "max series="+(subf.getMaxSeries()<0 ? "unlimited":subf.getMaxSeries())+", series archived="+subf.getNumberOfSeries() +", ":"")
                        +"max_videos="+(subf.getMaxVideos()<0 ? "unlimited":subf.getMaxVideos())+", videos_archived="+subf.getNumberOfVideos());
                    setShortLogDesc("Archiving");
                }
            }
        }
        else//file does not have valid meta data
        {
            archiveFail++;
            log(WARNING, "Cannot be archived: series="+video.getSeries()+", title="+video.getTitle()+", season="+video.getSeasonNumber()+", episode="+video.getEpisodeNumber()
                    +"\n" + video.getFullPathEscaped());
        }

        if(!subf.canContainMultiPartVideos() && !subf.canAddAnotherVideo())
            log(NOTICE,"The rest of the videos in this subfolder will be skipped because max video count of "+ subf.getMaxVideos() +" has been reached.");

    }

    private void increaseArchiveCount(XBMCFile video, boolean success)
    {        
        //overall count
        if(success)archiveSuccess++; else archiveFail++;
        
        //do archive success/fail counts per video type
        if(video.isTvShow()) if(success) tvSuccess++; else tvFail++;
        else if(video.isMovie()) if(success) movieSuccess++; else movieFail++;
        else if(video.isMusicVideo()) if(success) musicVideoSuccess++; else musicVideoFail++;

        if(success && !video.isDuplicate())
        {
            subf.addVideo(video);//increase total video count for this subf (also increases series count if video is tv show);
        }
    }

    public void addMetadata(XBMCFile video)
    {
        /*
        if("manualarchive".equalsIgnoreCase(source.getName()))
        {
            //parse based on the already archived file
            File archivedFile = new File(video.getFinalLocation());
            String fileName = archivedFile.getName();
            video.setFileLabel(fileName);
            if(video.isTvShow())
            {
                String SxxExx = findSeasonEpisodeNumbers(video);
                if(!tools.valid(SxxExx))
                {
                    Config.log(WARNING, "Cannot get meta data because the TV Show does not have a valid SxxExx in its name: "+ fileName);
                    return;
                }
                int sIndx = SxxExx.toLowerCase().indexOf("s");
                int eIndx = SxxExx.toLowerCase().indexOf("e");
                int seasonNum = Integer.parseInt(SxxExx.substring(sIndx+1, eIndx));
                int episodeNum= Integer.parseInt(SxxExx.substring(eIndx+1, SxxExx.length()));
                video.setSeasonNumber(seasonNum);
                video.setEpisodeNumber(episodeNum);
                String prefix = SxxExx +" - ";
                String title = fileName.substring(fileName.indexOf(prefix)+prefix.length(),fileName.length()).trim();
                video.setTitle(title);
                String series = archivedFile.getParentFile().getParent();//2 parents above is series
                video.setSeries(series);
            }
            else if video.isMovie()
            {

            }
        }
        */
        String fullPath = video.getFullPathEscaped();
        
        //handle the custom parsing        
        boolean checkForCustomLookups = !"manualarchive".equalsIgnoreCase(source.getName());
        //log(INFO,"checkForCustomLookups = "+checkForCustomLookups +", source.getName() = "+source.getName()+", parser = "+ subf.getSource().getCustomParser());
        if(checkForCustomLookups)
        {
            //log(INFO, "Is PlayOn = " + "playon".equalsIgnoreCase(subf.getSource().getCustomParser()));
            if("playon".equalsIgnoreCase(subf.getSource().getCustomParser()))
            {
                boolean isNetflix = (fullPath.toLowerCase().contains("netflix/"));
                boolean isHulu = (fullPath.toLowerCase().contains("hulu/"));
                boolean isCBS = (fullPath.toLowerCase().contains("cbs/"));
                boolean isComedyCentral = (fullPath.toLowerCase().contains("comedy central/"));

                boolean customLookupSuccess = false;
                if(isNetflix)
                {
                    customLookupSuccess = PlayOnArchiver.doNetflix(video);
                }
                else if(isHulu || isCBS)//these use similiar patterns, so parse the same
                {
                    customLookupSuccess = PlayOnArchiver.doHuluCBS(video,isHulu,isCBS);
                }
                else if(isComedyCentral)
                {
                    customLookupSuccess = PlayOnArchiver.doComedyCentral(video);
                }
                else
                {
                    log(DEBUG, "No custom PlayOn parsing is availble for this video, trying default parsing for: "+video.getFullPathEscaped());
                }
                
                if(!customLookupSuccess)
                {
                    log(INFO, "Custom PlayOn parsing was unsuccessful, attempting default parsing for this video: "+ video.getFullPathEscaped());
                    defaultParse(video);
                }
            }//end playon customs
            else if(subf.getSource().getPath().toLowerCase().startsWith("plugin://plugin.video.icefilms") || "icefilms".equalsIgnoreCase(subf.getSource().getCustomParser()))
            {
                //ice films custom parsing
                IceFilmsArchiver.Archive(video);

            }
            else //default handling of file, not a known type
            {
                defaultParse(video);
            }
        }
        else//no custom loookups
        {
            defaultParse(video);
        }

        //check for force_tvdb="true"
        if(video.isTvShow() && !video.hasBeenLookedUpOnTVDB() && video.getSubfolder().forceTVDBLookup())
        {
            log(INFO, "This video is set to forced to be looked up on TVDB.com. Looking up now....");
            log(INFO, "TVDB lookup success = " + TVDB.lookupTVShow(video));
        }
    }


    private void defaultParse(XBMCFile video)
    {
        if(video.isMusicVideo())
        {
            //must be in "Artist - Title" format
            try
            {
                String label = video.getFileLabel();
                if(valid(label))
                {                    
                    final String splitter = " - ";
                    int splitIndex = label.indexOf(splitter);
                    if(splitIndex != -1)
                    {
                        String artist = label.substring(0, splitIndex);
                        String title = label.substring(splitIndex+splitter.length(), label.length());
                        
                        //clean out featuring artists, etc.
                        MusicVideoScraper.cleanMusicVideoLabel(artist);
                        MusicVideoScraper.cleanMusicVideoLabel(title);
                        
                        video.setArtist(artist);
                        video.setTitle(title);
                    }
                    else throw new Exception("Not in \"Artist - Title\" format.");
                }
                else throw new Exception("No file label is available.");
            }
            catch(Exception x)
            {
                log(WARNING, "Cannot get Artist/Title from music video: \""+video.getFileLabel()+"\" ("+video.getFullPathEscaped()+"): "+ x.getMessage());
                musicVideoFail++;
            }
        }
        else if(video.isTvShow())
        {
            log(DEBUG, "Attempting default parsing of TV Show...");
            if(addTVMetaData(video))
                log(DEBUG,"Successfully got TV informationg using default method. This show will be able to be archived if max limits have not been exceeded.");
        }
        else if(video.isMovie())
        {
            log(DEBUG, "Attempting to parse movie title using default settings. Setting movie title to \""+video.getFileLabel()+"\"");
            video.setTitle(video.getFileLabel());
        }
        else//not yet supported
        {
            setShortLogDesc("Archive:Skip");
            archiveSkip++;
            log(INFO, "SKIPPING: This video source type was not specified (type=\""+video.getType()+"\") or is not yet supported: "+ video.getFullPathEscaped());
            setShortLogDesc("Archiving");
            return;
        }
    }

    public boolean archiveFileInDropbox(XBMCFile video)
    {
        String destNoExt = getDroboxDestNoExt(video);
        String extToSave;

        
        //initially archive as .mpg to force XBMC to add it to its library. it will later be converted to a .strm once it's in the library
        File strm = new File(destNoExt+".strm");
        if(strm.exists())
            extToSave = ".strm";//this file has already been converted from .mpg to .strm. Just need to update the .strm file now
        else//this file has not yet been converted to a .strm, create/update the .mpg
            extToSave = ".mpg";


        String fullFinalPath = destNoExt+extToSave;
        video.setFinalLocation(fullFinalPath);
        File whereFile = new File(fullFinalPath);
        boolean updating = whereFile.exists();
        
        boolean duplicateUpdate = video.isDuplicate() && updating;//for duplicates, allow updating the current file because this dup may will add another multi-part to the file
        boolean regularUpdate = !video.isDuplicate();
        boolean created;
        if(regularUpdate || duplicateUpdate) created = tools.createInternetShortcut(whereFile, video.getFileList());//getFileList() will return multiple files for multi-part videos
        else
        {
            //we arecreating a new file from a duplicate. don't allow this, this means we have reached the max file limit, but this file was allowd to be processed because its a dup
            return true;//not an error
        }
        if(duplicateUpdate)
        {
            return true;//don't need to update the rest of the meta-data (it was done when the original was archived)
        }
        
        if(created)
        {
            allVideosArchived.put(whereFile, video);

            //queue the meta-data changes (append/prepend/movie set)
            //if the change already exists in the queue, it wil be skipped.
            //if the value has changed (and it's still in the queue), it will be updated
            //movie set
            queueMetaDataChange(video, Constants.MOVIE_SET, video.getSubfolder().getMovieSet());

            //prefix
            queueMetaDataChange(video, Constants.PREFIX, video.getSubfolder().getPrefix());

            //suffix
            queueMetaDataChange(video, Constants.SUFFIX, video.getSubfolder().getSuffix());                
            
            if(updating)
            {
                updatedCount++;
                setShortLogDesc("Archive:Update");
            }
            else
            {
                newArchivedCount++;
                setShortLogDesc("Archive:New");
            }
            
            log(updating ? INFO : INFO, (!updating ?"Archived" : "Updated") +" video at: "+ whereFile +" ("+video.getFullPathEscaped()+")");
            setShortLogDesc("Arvhiving");
            trackArchivedFiles(whereFile.getPath(), video);

            //check for scraping/nfo generation
            if(video.isMusicVideo() && Config.SCRAPE_MUSIC_VIDEOS)
            {
                MusicVideoScraper.scrape(whereFile);
            }
        }
        else log(WARNING, "Failed to "+(updating ?"Archive" : "Update")+" video at:" + whereFile+ "("+video.getFullPathEscaped()+")");

        return created;
    }

    private void trackArchivedFiles(String fullFinalPath, XBMCFile video)
    {
        
        //updates/adds the entry for the file at fullFinalPath
        tools.trackArchivedFile(source.getName(),fullFinalPath,video);//track in tracker file

        //track in global lists
        String originalPath = video.getFullPath();
        allFilesArchived.put(new File(fullFinalPath), originalPath);//track globally
        filesArchived.put(new File(fullFinalPath), originalPath);//track this subfolder
    }

    public static String getDroboxDestNoExt(XBMCFile file)
    {
        return getDroboxDestNoExt(Config.DROPBOX, file);//default dropbox
    }
    
    public static String getDroboxDestNoExt(String dropbox, XBMCFile file)
    {
        //determine new location, make sure directory structure is there

        if(file.isTvShow())
        {
            //create the directory structure, if needed
            File tvShowDir = new File(dropbox+SEP+"TV Shows");
            if(!tvShowDir.isDirectory())
            {
                log(INFO, "Creating base TV Shows directory at: " + tvShowDir);
                tvShowDir.mkdir();
            }

            File seriesDir = new File(tvShowDir+SEP+tools.spacesToDots(tools.safeFileName(file.getSeries())));//safe name replaces spaces with periods
            if(!seriesDir.isDirectory())
            {
                log(DEBUG, "Creating series directory at " + seriesDir);
                seriesDir.mkdir();
            }

            File seasonDir = new File(seriesDir +SEP+ "Season." + file.getSeasonNumber());
            if(!seasonDir.isDirectory())
            {
                log(DEBUG, "Creating season directory at " + seasonDir);
                seasonDir.mkdir();
            }

            //final file location
            String baseDestination = seasonDir +SEP+file.getSeasonEpisodeNaming();
            if(valid(file.getTitle())) baseDestination += " - "+ tools.safeFileName(file.getTitle());

            return baseDestination;
        }
        else if(file.isMovie())
        {
             File movieDir = new File(dropbox + SEP+"Movies");
            if(!movieDir.isDirectory())
            {
                log(INFO, "Creating base Movies directory at: " + movieDir);
                movieDir.mkdir();
            }
             String yearStr = (file.hasYear() ? " ("+file.getYear()+")" : "");

             boolean seperateFolderPerMovie = false;
             if(seperateFolderPerMovie)
             {
                 movieDir = new File(dropbox + SEP+"Movies"+SEP+ tools.safeFileName(file.getTitle() + yearStr));
                 if(!movieDir.isDirectory())
                {
                    log(INFO, "Creating \""+file.getTitle()+"\" Movie directory at: " + movieDir);
                    movieDir.mkdir();
                }
            }

            String baseDestination = movieDir +SEP+ tools.safeFileName(file.getTitle()+yearStr);
            return baseDestination;//remove illegal filename chars
        }
        else if(file.isMusicVideo())
        {
            File musicVideoDir = new File(dropbox +SEP+"Music Videos");
            if(!musicVideoDir.isDirectory())
            {
                log(INFO, "Creating base Music Videos directory at: " + musicVideoDir);
                musicVideoDir.mkdir();
            }
            return musicVideoDir+SEP+ tools.safeFileName(file.getArtist() + " - "+ file.getTitle());
        }
        else
        {
             log(ERROR, "Unknown video type: \""+ file.getType()+"\"");
             return null;
        }
    }
   
    public void queueMetaDataChange(XBMCFile file, String typeOfMetaData, String value)
    {
        if(!file.isMovie() && typeOfMetaData.equals(MOVIE_SET))
        {
            log(DEBUG, "Movie Set not allowed for " + typeOfMetaData+", Skipping.");
            return;
        }
        if(file.isMovie() && (typeOfMetaData.equals(PREFIX) || typeOfMetaData.equals(SUFFIX) ))
        {
            log(DEBUG, "Prefix/Suffix not allowed for movie type (Only Movie Set is allowed). Skipping.");
                return;
        }
                
        String dropboxFileLocation = tools.convertToStrm(file.getFinalLocation());//always stored as a .strm in the db
        boolean success = tools.addMetaDataChangeToDatabase(file, typeOfMetaData, value);
        if(!success)
            log(WARNING, "Could not queue meta-data change: type="+typeOfMetaData+", value="+value+", for file: "+dropboxFileLocation);
        else
            log(DEBUG, "Successfuly queued meta-data change for type="+typeOfMetaData+", value="+value+", for file: "+dropboxFileLocation);
    }

    public boolean isDownloaded(XBMCFile file)
    {
        String destNoExt = getDroboxDestNoExt(file);
        File dropboxLocation = new File(destNoExt+".downloaded");
        return dropboxLocation.exists() && dropboxLocation.isFile();
    }
    /*
     * Checks if the file has been already archived by ANY Archiver (not just the current one).
     */
    public boolean isAlreadyArchived(XBMCFile file)
    {                
        //check if this final path for this video has already been archived in this run
        String destNoExt = getDroboxDestNoExt(file);
        File dropboxLocation = new File(destNoExt+".strm");
        String videoThatWasAlreadyArchivedAtThisLocation = allFilesArchived.get(dropboxLocation);
        if(videoThatWasAlreadyArchivedAtThisLocation == null)
        {
            dropboxLocation = new File(destNoExt+".mpg");
            videoThatWasAlreadyArchivedAtThisLocation = allFilesArchived.get(dropboxLocation);
        }

        if(videoThatWasAlreadyArchivedAtThisLocation != null)
        {
            //these videos must be tracked so we know not to delete them when cleaning dropbox
            allVideosSkippedBecauseAlreadyArchived.put(dropboxLocation, file.getFullPathEscaped());//track globally
            videosSkippedBecauseAlreadyArchived.put(dropboxLocation, file.getFullPathEscaped());//track fr this subfolder
            log(INFO, "This video ("+file.getFullPathEscaped()+") was already archived at \""+dropboxLocation+"\" by video from ("+Config.escapePath(videoThatWasAlreadyArchivedAtThisLocation)+")");
            return true;
        }
        else
            return false;

        /*
         * Not using this because it was skipping episodes it shouldn't be, leading to un-updated files that won't play. TODO: review if needed

        //determine if a TV Show with same Season/Episode is already archived (with a different file name, which means it was archived from a different source)
        if(file.isTvShow())
        {
            //look for SxxExx match
            String SxxExx = file.getSeasonEpisodeNaming();
            File dirInDropbox = new File(destNoExt+".tmp").getParentFile();

            try
            {
                Collection<File> tvShows = FileUtils.listFiles(dirInDropbox, new String[] {"strm"}, true);//get all .strm TV Shows (recursive)
                log(DEBUG, "Checking "+ tvShows.size() +" .strm files in "+ dirInDropbox +" for match on "+ SxxExx);
                for(File tvShow : tvShows)
                {
                    boolean match = tvShow.getName().toUpperCase().contains(SxxExx.toUpperCase());
                    //log(DEBUG, "Match = "+ match +" for " +tvShow.getName().toUpperCase() + " containing "+ SxxExx.toUpperCase());
                    if(match)
                    {
                        boolean updating = (tvShow.equals(new File(destNoExt+".strm")));
                        if(!updating)//this means that the same season/episode exists, under a different file name. We don't need to add another one as it would be a duplicate
                        {
                            log(INFO,"This TV Show is already archived (based on Season/Episode numbers) at: \""+tvShow+"\", will not archive again: "+file.getFullPathEscaped());
                            allVideosSkippedBecauseAlreadyArchived.put(dropboxLocation, file.getFullPathEscaped());//track globally
                            videosSkippedBecauseAlreadyArchived.put(dropboxLocation, file.getFullPathEscaped());//track fr this subfolder
                            return true;
                        }
                    }
                }
                return false;
            }
            catch(Exception x)
            {
                log(WARNING, "Cannot determine if this TV Show is alrady archived: "+ file.getFullPathEscaped(),x);
                return false;//assume its not alrady archived
            }
        }

        else if(file.isMovie() || file.isMusicVideo())
        {
            return false;//dont have a good identifier (like SxxExx) for movies/music vids, so alwasy allow these to be archived/updated
        }
        else
        {
            log(WARNING, "Unknown file type being checked for isFileAlreadyArchived(), defaulting to false");
            return false;
        }
         * */
    }

    public static String findSeasonEpisodeNumbers(XBMCFile video)
    {
        
        for(Pattern p : seasonEpisodePatterns)
        {
            Matcher m = p.matcher(video.getFileLabel());
            if(m.find())
            {
                String match = m.group();                
                return match;
            }
            
        }
        return null;/// looped all the way thru w/ no match
    }

    /*
     * Normalizes the season/episode matched string as "SxxExx"
      */
    public static String normalizeSeasaonEpisodeNaming(String SxxExx)
    {
        //normalize as sXXeXX
        int indx = 0;
        for(Pattern p : seasonEpisodePatterns)
        {
            Matcher m = p.matcher(SxxExx);
            if(m.find())
            {
                String match = m.group();
                switch(indx)
                {
                    case 0://is already normalized, the default
                         break;
                    case 1: //like 1x23
                        match = "s"+match.toLowerCase().replace("x", "e");
                        break;
                    default: //missed the pattern
                        Config.log(WARNING, "Uncaught matching pattern, this must be handled. Expect errors.");
                }
                return match;
            }
            indx++;
        }
        return null;
    }
    
     public boolean addTVMetaData(XBMCFile video)
    {
        String sxxExx = findSeasonEpisodeNumbers(video);
        if(tools.valid(sxxExx))
        {
            log(DEBUG, "Found SxxExx pattern ("+sxxExx+"), attempting to parse it.");
            return addTVMetaDataFromSxxExx(video, sxxExx);//dont need to send in sxxexx string because they have been set in the findSeasonEpisodeNumbers method
        }
        else//try secondary method, lookingup on TheTVDB.com
        {
            //check if the series and title are both in the file label
            if(getSeriesAndTitleFromFileLabel(video))
            {
                log(DEBUG, "Found series \""+video.getSeries() +"\", and title \""+video.getTitle()+"\", from file label \""+video.getFileLabel()+"\". "
                        + "Will use this info to look up on the TVDB.com");
                return TVDB.lookupTVShow(video);
            }
            else//assume that the file label is the episode title
            {
                video.setTitle(video.getFileLabel());
                log(DEBUG, "Assuming that the file label is the episode title: \""+video.getTitle()+"\", finding Series by looking at parent folder(s)");
                if(getSeriesFromParentFolder(video))
                {
                    log(DEBUG, "Series determined to be \""+video.getSeries()+"\". Will now lookup on TheTVDB to get SxxExx numbers...");
                    return TVDB.lookupTVShow(video);
                }
                else
                {
                    log(WARNING, "Could not get series from parent folder(s). Will not be able to archive this video because series name is required: "+ video.getFullPathEscaped());
                    return false;
                }
            }
        }
    }

    public static boolean addTVMetaDataFromSxxExx(XBMCFile video, String seasonEpisodeNaming)
    {
        //parse season/episode numbers        
        if(!valid(seasonEpisodeNaming)) 
        {
            Config.log(WARNING, "No Season/Episode pattern was found. Cannot continue for: "+video.getFullPathEscaped());
            return false;//no season episode string, or video does not have season/episode nubmers set
        }
        
        String SxxExx = normalizeSeasaonEpisodeNaming(seasonEpisodeNaming).toUpperCase().trim();
        try
        {
            int sIndex = SxxExx.indexOf("S");
            int eIndex = SxxExx.indexOf("E");
            int season = Integer.parseInt(SxxExx.substring(sIndex+1, eIndex));
            int episode  = Integer.parseInt(SxxExx.substring(eIndex+1, SxxExx.length()));
            video.setSeasonNumber(season);
            video.setEpisodeNumber(episode);
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Cannot parse season and episode numbers from: "+ seasonEpisodeNaming +" ("+SxxExx+"): "+ x);
            return false;
        }


        //now get series/title
        String[] titleSplitters = new String[]{" - ", ": ", ":", "-", " : ", " -- "};
        int seasonEpisodeIndex = video.getFileLabel().indexOf(seasonEpisodeNaming);        
        if(seasonEpisodeIndex == 0)//SxxExx must be at beginning of file for this method
        {
            //get the title which comes after the SxxExx and a splitter            
            for(String splitter : titleSplitters)
            {
                if(video.getFileLabel().contains(seasonEpisodeNaming + splitter))
                {
                    //set the title equal to whatever comes after the sXXeXX + splitter.
                    //so "5x9 - Teri Garr and the B-52s" becomes "Teri Garr and the B-52s"

                    String title = video.getFileLabel().substring((seasonEpisodeNaming + splitter).length(), video.getFileLabel().length()).trim();
                    video.setTitle(title);
                    break;
                }
            }
            if(!valid(video.getTitle()))
            {
                log(WARNING, "Title cannot be found, it was expected to be found after one of "+ Arrays.toString(titleSplitters)+ " in the file label \""+video.getFileLabel()+"\"");
                return false;
            }
            
            //get the series from the parent folder
            if(getSeriesFromParentFolder(video))
                return true;

        }
        else//SxxExx is not at start of file label
        {
            //assume it looks like: "The Morning After - 1x38 - Wed, Mar 9, 2011 (HD)"
            for(String splitter : titleSplitters)
            {
                if(video.getFileLabel().contains(splitter))
                {
                    
                    String[] parts = video.getFileLabel().split(splitter);
                    log(DEBUG, "Splitting \""+video.getFileLabel()+"\" with \""+splitter+"\" = "+ Arrays.toString(parts)+", length of "+ parts.length +", looking for "+ 3);
                    if(parts.length == 3)
                    {
                        String series = parts[0];
                        //clean the series, sometimes it can get the sXXeXX pattern in it if the label uses different splitters for different parts
                        //i.e: PlayOn/Hulu/Popular/Popular Episodes/Cops - s23e15: Cops: Dazed & Confused #3
                        //will yield a series of "Cops - s23e15" because it uses " - " for the first splitter and ": " for the second splitter
                        for(String s : titleSplitters)
                        {
                            series = series.replace(seasonEpisodeNaming + s,"");
                            series = series.replace(s+seasonEpisodeNaming,"");
                        }
                        series = series.replace(seasonEpisodeNaming, "").trim();
                        String title = parts[2];
                        video.setTitle(title);
                        video.setSeries(series);
                        return true;
                    }
                }
            }

        }
        return false;
    }

    /*
     * Get the series and title from labels like "Chuck - Pilot" or "Desperate Housewives: Pilot"
     */
    public boolean getSeriesAndTitleFromFileLabel(XBMCFile video)
    {
        String[] splitters = new String[] {" - ", ": "};
        for(String splitter : splitters)
        {
            if(video.getFileLabel().contains(splitter))
            {
                try
                {
                    String[] parts = video.getFileLabel().split(splitter);
                    video.setSeries(parts[0].trim());
                    video.setTitle(parts[1].trim());
                    return true;
                }
                catch(Exception x)
                {
                    log(INFO, "Could not get series/title from \""+ video.getFileLabel() +"\", splitting at \""+ splitter+"\": "+ x.getMessage());
                }
            }
        }
        return false;
    }

    public static boolean getSeriesFromParentFolder(XBMCFile video)
    {
        //the series title comess before this as a folder name
        List<String> skipFolders = new ArrayList<String>();
        
        final String optionalYear ="(\\([0-9]+\\)|\\[[0-9]+\\])?";//matches (nnnn) or [nnnn]
        skipFolders.add("(Full Episodes|Episodes|Clips|Seasons)");//literal skips
        skipFolders.add("((Season|Series|Set|Episodes|Collection) (\\([0-9]+\\)|[0-9]+))"+" ?"+optionalYear);//name + number + optional_space + optional_year

        String series = getParentFolderWithSkips(video.getFullPath().split(DELIM),skipFolders);
        if(tools.valid(series))
        {
            video.setSeries(series);
            return true;
        }        
        return false;
    }

    public static String getParentFolderWithSkips(String[] folders, List<String> regexSkipFolderPatterns)
    {        
        for(int i=folders.length-2; i>=0; i--)//.length minus two to skip the file name and start at the folder above it
        {
            boolean skipThisFolder = false;
            String folder = folders[i];
            for(String regex : regexSkipFolderPatterns)//check if the folder matches any of the regex excludes
            {
                Pattern skipPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
                Matcher m = skipPattern.matcher(folder);
                if(m.find())
                {
                    String match = m.group();
                    if(match.equals(folder))//only skip if a full match.
                    {
                        skipThisFolder = true;
                        break; //break from regex loop
                    }
                }
            }
            if(skipThisFolder) continue;
            //otherwise, this is the folder
            if(valid(folder))            
                return folder;//set to the first folder that isn't skipped            
            else return null;
        }
        return null;
    }

    public int getNextSpecialEpisiodeNumber(XBMCFile file)
    {
        String seasonZeroFolder = getDroboxDestNoExt(file);
        seasonZeroFolder = seasonZeroFolder.substring(0, seasonZeroFolder.lastIndexOf(SEP));
        Collection<File> files = FileUtils.listFiles(new File(seasonZeroFolder), null, false);
        int maxEpNum = 1;
        Pattern p = Pattern.compile("S[0-9]+E[0-9]+", Pattern.CASE_INSENSITIVE);
        for(File f : files)
        {
            if(!f.getName().toLowerCase().contains(".mpg")) continue;
            String name = f.getName().toUpperCase();
            Matcher m = p.matcher(name);
            boolean matching = m.find();
            if(matching)
            {
                String match = m.group();
                int epNum =  Integer.parseInt(match.substring(match.indexOf("E")+1, match.length()));
                maxEpNum = Math.max(epNum+1, maxEpNum);
            }
        }
        log(DEBUG, "New season zero episode number for " + seasonZeroFolder +" is "+ maxEpNum);
        return maxEpNum;
    }

    
    //Map over convenience methods
    private static String shortLogDesc = "";    
    private static void setShortLogDesc(String desc)
    {
        shortLogDesc = desc;
    }
    private static void log(int level, String s)
    {
        log(level,s,null); 
    }
    private static void log(int level, String s, Exception x)
    {
        if(level <= Config.LOGGING_LEVEL)
        {
            Config.log(level,s,shortLogDesc,x);
        }
    }
    public static boolean valid(String s)
    {
        return tools.valid(s);
    }
}