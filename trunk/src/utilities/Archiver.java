package utilities;

import btv.logger.BTVLogLevel;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

import org.apache.commons.io.FileUtils;


import static utilities.Constants.*;
import static btv.tools.BTVTools.*;

public class Archiver implements Runnable
{

    public static void main(String[] args) throws IOException
    {
        //\\onyx\data\compressed\StreamingVideos\TV Shows\Cops.-.s23e15\Season.23\S23E15 - Dazed & Confused #3.strm
        //PlayOn/Hulu/Popular/Popular Episodes/Cops - s23e15: Cops: Dazed & Confused #3
        //PlayOn/Netflix/Instant Queue/Alphabetical/2/24: Season 1/01: 12:00 Midnight-1:00 A.M
        //Netflix/Instant Queue/Alphabetical/M/MythBusters/Collection 1/02: Barrel of Bricks
        new Config().loadConfig();
        Source src = new Source("testing","testing");
        Archiver a = new Archiver(src);

        String name = "PlayOn/Netflix/Instant Queue/Alphabetical/M/MythBusters/Collection 1/02: Barrel of Bricks";
        Config.LOGGING_LEVEL = BTVLogLevel.DEBUG;
        Logger.INFO("TESTING: "+name);
        MyLibraryFile video = new MyLibraryFile(name.replace("/", xbmc.util.Constants.DELIM));
        video.setType(TV_SHOW);
        
        src.setCustomParser("playon");
        Subfolder subf = new Subfolder(src, "testing");//to avoid NPE's, instantite dummy objects for the lookup.
        a.subf = subf;
        video.setSubfolder(subf);
        //String sxxExx = findSeasonEpisodeNumbers(video);
        //Logger.INFO( "SxxExx = "+ sxxExx);
        //addTVMetaDataFromSxxExx(video, sxxExx);
        a.addMetadata(video);
        Logger.INFO( "Title = "+ video.getTitle()+", Series = "+video.getSeries() +", SeasonEpisode = "+ video.getSeasonEpisodeNaming());

    }
    private Thread t;
    private boolean STOP, CAN_STOP=false, IS_STOPPED = false;
    BlockingQueue<MyLibraryFile> files;
    private Subfolder subf;
    public static List<Pattern> seasonEpisodePatterns = new ArrayList<Pattern>();

    //need to track totals for vidoes skipped because already archived  and vidoes archived successfully
    //track across all subfolders, so these are static variables
    public static Map<File,String> allVideosSkippedBecauseAlreadyArchived = new LinkedHashMap<File,String>();
    public static Map<File,String> allFilesArchived = new HashMap<File,String>();

    public static Map<File, MyLibraryFile> allVideosArchived = new LinkedHashMap<File, MyLibraryFile>();//must keep this linked

    //only track this subfolder (not-static)
    public Map<File,String> videosSkippedBecauseAlreadyArchived = new LinkedHashMap<File,String>();
    public Map<File,String> filesArchived = new HashMap<File,String>();

    public int tvSuccess = 0, tvFail = 0, musicVideoSuccess = 0, musicVideoFail = 0, movieSuccess = 0, movieFail = 0, genericSuccess = 0, genericFail = 0,archiveSuccess = 0, archiveFail = 0, archiveSkip =0, newArchivedCount = 0, updatedCount = 0;    
    public static int globaltvSuccess = 0, globaltvFail = 0, globalmusicVideoSuccess = 0, globalmusicVideoFail = 0, globalgenericSuccess = 0, globalgenericFail = 0, globalmovieSuccess = 0, globalmovieFail = 0, globalarchiveSuccess = 0, globalarchiveFail = 0, globalarchiveSkip =0, globalnewArchivedCount = 0, globalupdatedCount = 0;
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

    public void start(Subfolder subf, BlockingQueue<MyLibraryFile> files)
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
    
    @Override
    public void run() 
    {
        try
        {
            Config.setShortLogDesc("Archiving");
            while(!STOP)
            {
                MyLibraryFile video = null;
                try
                {
                     video = files.poll(1000, TimeUnit.MILLISECONDS);
                }
                catch(InterruptedException x)
                {
                    Logger.ERROR( "Interrupted while archiving videos from queue. Cannot continue.",x);
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
                    genericSuccess, genericFail,
                    newArchivedCount, updatedCount, archiveSuccess, archiveSkip, archiveFail);

        }
        catch(Exception x)
        {            
            Logger.ERROR( "General error in archiving thread for subfolder named "+ subf.getFullName() +": "+ x,x);
        }
        finally
        {
            IS_STOPPED = true;
        }
    }

	//AngryCamel - 20120817 1620 - Added generic
    private static void summarize(String subfName, int tvSuccess, int tvFail, int movieSuccess, int movieFail, 
            int musicVideoSuccess, int musicVideoFail, int genericSuccess, int genericFail, int newArchivedCount, 
            int updatedCount, int archiveSuccess, int archiveSkip, int archiveFail)
    {
        //try{subfName = subfName.substring(0, subfName.indexOf("/"));}catch(Exception ignored){}//try to trim to only the source name
        Config.setShortLogDesc("Summary:"+subfName);
        Logger.NOTICE( "----------------------Archiving Summary for " + subfName+"----------------------");
        Logger.NOTICE( "TV Success: "+ tvSuccess+", TV Fail: "+ tvFail);
        Logger.NOTICE( "Movie Success: "+ movieSuccess+", Movie Fail: "+ movieFail);
        Logger.NOTICE( "Music Video Success: "+ musicVideoSuccess+", Music Video Fail: "+ musicVideoFail);
        
    	//AngryCamel - 20120817 1620 - Added generic
        Logger.NOTICE( "Generic Success: "+ genericSuccess+", Generic Fail: "+ genericFail);
        
        Logger.NOTICE( "New videos archived: "+ newArchivedCount +", existing videos updated: "+ updatedCount);
        Logger.NOTICE( "Overall: Success: "+ archiveSuccess+", Skip: "+ archiveSkip +", Fail: "+ archiveFail);
        Config.setShortLogDesc("");

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

        	//AngryCamel - 20120817 1620 - Added generic
            globalgenericFail += musicVideoFail;
            globalgenericSuccess += genericSuccess;
            
            globaltvFail += tvFail;
            globaltvSuccess += tvSuccess;
        }
    }

    final static String GLOBAL_SUMMARY_NAME = "---Overall---";
    public static void globalSummary()
    {
        summarize(GLOBAL_SUMMARY_NAME, globaltvSuccess, globaltvFail, globalmovieSuccess, globalmovieFail, globalmusicVideoSuccess, globalmusicVideoFail, 
        		globalgenericSuccess, globalgenericFail, globalnewArchivedCount, globalupdatedCount, globalarchiveSuccess, globalarchiveSkip, globalarchiveFail);
    }

    public void archiveVideo(MyLibraryFile video)
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
        Logger.DEBUG("Next: "+video.getFullPathEscaped());
        
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
        //Logger.INFO( video.getFullPathEscaped()+" Duplicate ? "+ video.isDuplicate());

        //if it doesnt have valid meta data yet, try TVDB lookup / manual archiving
        if(!video.hasValidMetaData())
        {
            if(video.isTvShow())
            {
                if(valid(video.getSeries()) && valid(video.getTitle()))//just cant find sXXeXX numbers, try tvdb lookup based on title/series
                {
                    if(!video.hasBeenLookedUpOnTVDB())
                    {
                        
                        Logger.INFO( "Since this video has a valid series and title, will attempt to lookup Seasion/Episode numbers on TheTVDB.com.");
                        TVDB.lookupTVShow(video);
                    }

                    if(!video.hasValidMetaData())//still no valid season/episode numbers after TVDB lookup
                    {
                        if(Config.MANUAL_ARCHIVING_ENABLED)
                        {
                            Logger.INFO( "Archiving as a special (Season zero) episode since TVDB lookup failed....");
                            //save as a special
                            video.setSeasonNumber(0);
                            video.setEpisodeNumber(getNextSpecialEpisiodeNumber(video));
                        }
                        else
                            Logger.INFO("TV Episode will fail to be archived because manual archiving is disabled and the meta-data cannot be parsed or found on the TheTVDB.com: "+ video.getFullPathEscaped());
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
            boolean withinLimits = video.isWithinLimits(subf);
            

            if(withinLimits)
            {                                                
                //check if this file has already been archived
                boolean originalWasSkippedBecauseAlreadyArchived = video.isDuplicate() 
                            && video.getOriginalVideo().skippedBecauseAlreadyArchived(); //this file is a duplicate, and the original was skipped becasue of already archived
                
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
                        Config.setShortLogDesc("Arvhive:Skip");
                        Logger.DEBUG( "SKIP: Not archiving this video because it has already been archived by another source");
                        Config.setShortLogDesc("Archiving");
                    }
                }                                
            }
            else
            {
                if(!video.isDuplicate())
                {
                    archiveSkip++;
                    Config.setShortLogDesc("Arvhive:Skip");
                    Logger.INFO( "SKIP: Not archiving because max limit has been reached: "
                        + (video.isTvShow() ? "max series="+(subf.getMaxSeries()<0 ? "unlimited":subf.getMaxSeries())+", series archived="+subf.getNumberOfSeries() +", ":"")
                        +"max_videos="+(subf.getMaxVideos()<0 ? "unlimited":subf.getMaxVideos())+", videos_archived="+subf.getNumberOfVideos());
                    Config.setShortLogDesc("Archiving");
                }
            }
        }
        else//file does not have valid meta data
        {
            archiveFail++;
            Logger.WARN( "Cannot be archived: series="+video.getSeries()+", title="+video.getTitle()+", season="+video.getSeasonNumber()+", episode="+video.getEpisodeNumber()
                    +LINE_BRK + video.getFullPathEscaped());
        }

        if(!subf.canContainMultiPartVideos() && !subf.canAddAnotherVideo())
            Logger.NOTICE("The rest of the videos in this subfolder will be skipped because max video count of "+ subf.getMaxVideos() +" has been reached.");

    }

    private void increaseArchiveCount(MyLibraryFile video, boolean success)
    {        
        //overall count
        if(success)archiveSuccess++; else archiveFail++;
        
        //do archive success/fail counts per video type
        if(video.isTvShow()) if(success) tvSuccess++; else tvFail++;
        else if(video.isMovie()) if(success) movieSuccess++; else movieFail++;
        else if(video.isMusicVideo()) if(success) musicVideoSuccess++; else musicVideoFail++;

    	//AngryCamel - 20120817 1620 - Added generic
        else if(video.isGeneric()) if(success) genericSuccess++; else genericFail++;

        if(success && !video.isDuplicate())
        {
            subf.addVideo(video);//increase total video count for this subf (also increases series count if video is tv show);
        }
    }

    public void addMetadata(MyLibraryFile video)
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
                    Logger.WARN( "Cannot get meta data because the TV Show does not have a valid SxxExx in its name: "+ fileName);
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
        //Logger.INFO("checkForCustomLookups = "+checkForCustomLookups +", source.getName() = "+source.getName()+", parser = "+ subf.getSource().getCustomParser());
        if(checkForCustomLookups)
        {
            //Logger.INFO( "Is PlayOn = " + "playon".equalsIgnoreCase(subf.getSource().getCustomParser()));
            if("playon".equalsIgnoreCase(subf.getSource().getCustomParser()))
            {
                boolean isNetflix = (fullPath.toLowerCase().contains("netflix/") || source.getName().toLowerCase().contains("netflix"));
                boolean isHulu = (fullPath.toLowerCase().contains("hulu/") || source.getName().toLowerCase().contains("hulu"));
                boolean isCBS = (fullPath.toLowerCase().contains("cbs/") || source.getName().toLowerCase().contains("cbs"));
                boolean isComedyCentral = (fullPath.toLowerCase().contains("comedy central/") || source.getName().toLowerCase().contains("comedycentral"));

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
                    Logger.DEBUG( "No custom PlayOn parsing is availble for this video, trying default parsing for: "+video.getFullPathEscaped());
                }
                
                if(!customLookupSuccess)
                {
                    Logger.INFO( "Custom PlayOn parsing was unsuccessful, attempting default parsing for this video: "+ video.getFullPathEscaped());
                    defaultParse(video);
                }
            }//end playon customs           
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
            Logger.INFO( "This video is set to forced to be looked up on TVDB.com. Looking up now....");
            Logger.INFO( "TVDB lookup success = " + TVDB.lookupTVShow(video));
        }
    }


    private void defaultParse(MyLibraryFile video)
    {
        if(video.isMusicVideo())
        {
            //must be in "Artist - Title" format
            try
            {
                String label = video.getFileLabel();
                if(valid(label))
                {                    
                    Logger.DEBUG( "Getting music video title from: "+ label);
                    final String splitter = " - ";
                    int splitIndex = label.indexOf(splitter);
                    if(splitIndex != -1)
                    {
                        String artist = label.substring(0, splitIndex);
                        String title = label.substring(splitIndex+splitter.length(), label.length());
                        
                        //clean out featuring artists, etc.
                        artist = MusicVideoScraper.cleanMusicVideoLabel(artist);
                        title = MusicVideoScraper.cleanMusicVideoLabel(title);
                        
                        Logger.DEBUG( "Artist="+artist+". Title="+title);
                        video.setArtist(artist);
                        video.setTitle(title);                        
                    }
                    else throw new Exception("Not in \"Artist - Title\" format.");
                }
                else throw new Exception("No file label is available.");
            }
            catch(Exception x)
            {
                Logger.WARN( "Cannot get Artist/Title from music video: \""+video.getFileLabel()+"\" ("+video.getFullPathEscaped()+"): "+ x.getMessage());
                musicVideoFail++;
            }
        }
        else if(video.isTvShow())
        {
            Logger.DEBUG( "Attempting default parsing of TV Show...");
            if(addTVMetaData(video))
                Logger.DEBUG("Successfully got TV informationg using default method. This show will be able to be archived if max limits have not been exceeded.");
        }
        else if(video.isMovie())
        {
            Logger.DEBUG( "Attempting to parse movie title using default settings. Setting movie title to \""+video.getFileLabel()+"\"");
            video.setTitle(video.getFileLabel());
        }
    	//AngryCamel - 20120817 1620 - Added generic
        else if(video.isGeneric())
        {
            Logger.DEBUG( "Attempting to parse generic video using default settings. Setting video title to \""+video.getFileLabel()+"\"");
            video.setTitle(video.getFileLabel());
        	//AngryCamel - 20120817 1620 - Added generic
        	applyCustomParser(video);
        }
        else//not yet supported
        {
            Config.setShortLogDesc("Archive:Skip");
            archiveSkip++;
            Logger.INFO( "SKIPPING: This video source type was not specified (type=\""+video.getType()+"\") or is not yet supported: "+ video.getFullPathEscaped());
            Config.setShortLogDesc("Archiving");
            return;
        }
    }

    public boolean archiveFileInDropbox(MyLibraryFile video)
    {
        final String destNoExt = getDroboxDestNoExt(video);
        final String extToSave = ".strm";
        final String fullFinalPath = destNoExt+extToSave;                        
        
        video.setFinalLocation(fullFinalPath);
        File whereFile = new File(fullFinalPath);
        boolean updating = whereFile.exists();
        
        boolean duplicateUpdate = video.isDuplicate() && updating;//for duplicates, allow updating the current file because this dup may will add another multi-part to the file
        boolean regularUpdate = !video.isDuplicate();
        StrmUpdateResult result;
        if(regularUpdate || duplicateUpdate) result = tools.createStrmFile(whereFile, updating, video.getFileList());//getFileList() will return multiple files for multi-part videos
        else
        {
            //we arecreating a new file from a duplicate. don't allow this, this means we have reached the max file limit, but this file was allowd to be processed because its a dup
            return true;//not an error
        }
        if(duplicateUpdate)
        {
            return true;//don't need to update the rest of the meta-data (it was done when the original was archived)
        }
        
        //if successful, queue changes
        if(result == StrmUpdateResult.CHANGED || result == StrmUpdateResult.SKIPPED_NO_CHANGE)
        {
            allVideosArchived.put(whereFile, video);

            //queue the meta-data changes (append/prepend/movie set/movie tags)
            //if the change already exists in the queue, it wil be skipped.
            //if the value has changed (and it's still in the queue), it will be updated
            
            //movie set
            queueMetaDataChange(video, MetaDataType.MOVIE_SET, video.getSubfolder().getMovieSet());
            
            //movie tags
            queueMetaDataChange(video, MetaDataType.MOVIE_TAGS, video.getSubfolder().getMovieTagsAsString());

            //prefix
            queueMetaDataChange(video, MetaDataType.PREFIX, video.getSubfolder().getPrefix());

            //suffix
            queueMetaDataChange(video, MetaDataType.SUFFIX, video.getSubfolder().getSuffix());                
            
            if(updating)
            {
                updatedCount++;
                String type = (result == StrmUpdateResult.CHANGED ? "Update" : "NoChange");
                Config.setShortLogDesc("Archive:"+type);
            }
            else//new file
            {
                newArchivedCount++;
                Config.setShortLogDesc("Archive:New");
            }
            
            Logger.INFO( whereFile +" ("+video.getFullPathEscaped()+")");
            Config.setShortLogDesc("Arvhiving");
            trackArchivedFiles(whereFile.getPath(), video);

            //check for scraping/nfo generation
            if(video.isMusicVideo() && Config.SCRAPE_MUSIC_VIDEOS)
            {
                MusicVideoScraper.scrape(whereFile);
            }
        }
        else Logger.WARN( "Failed to "+(updating ?"Archive" : "Update")+" video at:" + whereFile+ "("+video.getFullPathEscaped()+")");

        return result != StrmUpdateResult.ERROR;
    }

    private void trackArchivedFiles(String fullFinalPath, MyLibraryFile video)
    {
        
        //updates/adds the entry for the file at fullFinalPath
        tools.trackArchivedFile(source.getName(),fullFinalPath,video);//track in tracker file

        //track in global lists
        String originalPath = video.getFullPath();
        allFilesArchived.put(new File(fullFinalPath), originalPath);//track globally
        filesArchived.put(new File(fullFinalPath), originalPath);//track this subfolder
    }

    public static String getDroboxDestNoExt(MyLibraryFile file)
    {
        return getDropboxDestNoExt(Config.DROPBOX, file);//default dropbox
    }
    
    public static String getDropboxDestNoExt(String dropbox, MyLibraryFile file)
    {
        //determine new location, make sure directory structure is there

        if(file.isTvShow())
        {
            //create the directory structure, if needed
            File tvShowDir = new File(dropbox+SEP+"TV Shows");
            if(!tvShowDir.isDirectory())
            {
                Logger.INFO( "Creating base TV Shows directory at: " + tvShowDir);
                tvShowDir.mkdir();
            }

            File seriesDir = new File(tvShowDir+SEP+tools.spacesToDots(safeFileName(file.getSeries())));//safe name replaces spaces with periods
            if(!seriesDir.isDirectory())
            {
                Logger.DEBUG( "Creating series directory at " + seriesDir);
                seriesDir.mkdir();
            }

            File seasonDir = new File(seriesDir +SEP+ "Season." + file.getSeasonNumber());
            if(!seasonDir.isDirectory())
            {
                Logger.DEBUG( "Creating season directory at " + seasonDir);
                seasonDir.mkdir();
            }

            //final file location
            String baseDestination = seasonDir +SEP+file.getSeasonEpisodeNaming();
            if(valid(file.getTitle())) baseDestination += " - "+ safeFileName(file.getTitle());

            return baseDestination;
        }
        else if(file.isMovie())
        {
             File movieDir = new File(dropbox + SEP+"Movies");
            if(!movieDir.isDirectory())
            {
                Logger.INFO( "Creating base Movies directory at: " + movieDir);
                movieDir.mkdir();
            }
             String yearStr = (file.hasYear() ? " ("+file.getYear()+")" : "");

             boolean seperateFolderPerMovie = false;
             if(seperateFolderPerMovie)
             {
                 movieDir = new File(dropbox + SEP+"Movies"+SEP+ safeFileName(file.getTitle() + yearStr));
                 if(!movieDir.isDirectory())
                {
                    Logger.INFO( "Creating \""+file.getTitle()+"\" Movie directory at: " + movieDir);
                    movieDir.mkdir();
                }
            }

            String baseDestination = movieDir +SEP+ safeFileName(file.getTitle()+yearStr);
            return baseDestination;//remove illegal filename chars
        }
        else if(file.isMusicVideo())
        {
            File musicVideoDir = new File(dropbox +SEP+"Music Videos");
            if(!musicVideoDir.isDirectory())
            {
                Logger.INFO( "Creating base Music Videos directory at: " + musicVideoDir);
                musicVideoDir.mkdir();
            }
            return musicVideoDir+SEP+ safeFileName(file.getArtist() + " - "+ file.getTitle());
        }
    	//AngryCamel - 20120817 1620 - Added generic
        else if(file.isGeneric())
        {
            //create the directory structure, if needed
            File genericDir = new File(dropbox+SEP+"Generic");
            if(!genericDir.isDirectory())
            {
                Logger.INFO( "Creating base Generic directory at: " + genericDir);
                genericDir.mkdir();
            }

            File seriesDir = new File(genericDir+SEP+tools.spacesToDots(safeFileName(file.getSeries())));//safe name replaces spaces with periods
            if(!seriesDir.isDirectory())
            {
                Logger.DEBUG( "Creating series directory at " + seriesDir);
                seriesDir.mkdir();
            }

            //final file location
            String baseDestination = seriesDir +SEP+ safeFileName(file.getSeries());
            if(valid(file.getTitle())) baseDestination += " - "+ safeFileName(file.getTitle());

            return baseDestination;
        }
        else
        {
             Logger.ERROR( "Unknown video type: \""+ file.getType()+"\"");
             return null;
        }
    }
   
    public void queueMetaDataChange(MyLibraryFile file, MetaDataType typeOfMetaData, String value)
    {
        if(!file.isMovie() && (typeOfMetaData.isForMovieOnly()))
        {
            //Logger.DEBUG( "Movie Set/Tags not allowed for non-movie. Skipping: "+file.getFileLabel());
            return;
        }
                       
        String dropboxFileLocation = file.getFinalLocation();
        boolean success = tools.addMetaDataChangeToDatabase(file, typeOfMetaData, value);
        if(!success)
            Logger.WARN( "Could not queue meta-data change: type="+typeOfMetaData+", value="+value+", for file: "+dropboxFileLocation);
        else
            Logger.DEBUG( "Successfuly queued meta-data change for type="+typeOfMetaData+", value="+value+", for file: "+dropboxFileLocation);
    }
  
    
    Map<File,Collection<File>> episodesBySeasonDir = new HashMap<File,Collection<File>>();
    /*
     * Checks if the file has been already archived by ANY Archiver (not just the current one).
     */
    public boolean isAlreadyArchived(MyLibraryFile file)
    {                
        //check if this final path for this video has already been archived in this run
        String destNoExt = getDroboxDestNoExt(file);
        File dropboxLocation = new File(destNoExt+".strm");
        String videoThatWasAlreadyArchivedAtThisLocation = allFilesArchived.get(dropboxLocation);        

        if(videoThatWasAlreadyArchivedAtThisLocation != null)//found it
        {
            //these videos must be tracked so we know not to delete them when cleaning dropbox
            allVideosSkippedBecauseAlreadyArchived.put(dropboxLocation, file.getFullPathEscaped());//track globally
            videosSkippedBecauseAlreadyArchived.put(dropboxLocation, file.getFullPathEscaped());//track fr this subfolder
            Config.setShortLogDesc("Archive:Duplicate");
            Logger.INFO( "This video ("+file.getFullPathEscaped()+") was already archived at \""+dropboxLocation+"\" "
                    + "by video from ("+Config.escapePath(videoThatWasAlreadyArchivedAtThisLocation)+")");
            return true;
        }                         

        //determine if a TV Show with same Season/Episode is already archived (with a different file name, which means it was archived from a different source)
        if(file.isTvShow())
        {
            File seasonDir = dropboxLocation.getParentFile();
            Collection<File> episodes = episodesBySeasonDir.get(seasonDir);
            if(episodes == null)
            {
                //init listing of existing .strms in this season dir
                
                //get all .strm TV episodes in the Season.X dir
                if(seasonDir.isDirectory())
                {
                    episodes = FileUtils.listFiles(seasonDir, new String[] {"strm"}, false);
                    Logger.DEBUG( "Found "+ episodes.size() +" existing .strm TV Episodes in: "+seasonDir);
                    episodesBySeasonDir.put(seasonDir, episodes);
                }
                else return false;//not a duplicate; its directory doesn't even exist yet
            }
        
            //look for SxxExx match
            String SxxExx = file.getSeasonEpisodeNaming();            
            try
            {                
                Logger.DEBUG( "SxxExx DuplicateCheck for "+ episodes.size() +" .strm files in TV Shows directory for match on "+ SxxExx);
                for(File existingEpisode : episodes)
                {
                    boolean match = existingEpisode.getName().toUpperCase().contains(SxxExx.toUpperCase());
                    
                    if(match)
                    {
                        Logger.DEBUG( "SxxExx match found: \"" +existingEpisode.getName() + "\" contains \""+ SxxExx.toUpperCase()+"\"");
                        boolean sameFile = (existingEpisode.equals(dropboxLocation));
                        
                        if(sameFile)//this is the actual file we are checking... skip it
                            continue;
                        
                        //if got here, the same season/episode exists, under a different file name. We don't need to add another one as it would be a duplicate                        
                        Config.setShortLogDesc("SxEx Duplicate");                            
                        Logger.INFO("This TV Show is already archived (based on Season/Episode numbers) at: \""+existingEpisode+"\", will not archive again: "+file.getFullPathEscaped());
                        allVideosSkippedBecauseAlreadyArchived.put(dropboxLocation, file.getFullPathEscaped());//track globally
                        videosSkippedBecauseAlreadyArchived.put(dropboxLocation, file.getFullPathEscaped());//track fr this subfolder
                        return true;                                                
                    }
                }
                return false;//no dup
            }
            catch(Exception x)
            {
                Logger.WARN( "Cannot determine if this TV Show is alrady archived: "+ file.getFullPathEscaped(),x);
                return false;//assume its not alrady archived
            }
        }
    	//AngryCamel - 20120817 1620 - Added generic
        else if(file.isMovie() || file.isMusicVideo() || file.isGeneric())
        {
            return false;//dont have a good identifier (like SxxExx) for movies/music/generic vids, so alwasy allow these to be archived/updated
        }
        else
        {
            Logger.WARN( "Unknown file type being checked for isFileAlreadyArchived(), defaulting to false");
            return false;
        }         
    }

    public static String findSeasonEpisodeNumbers(MyLibraryFile video)
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
                        Logger.WARN( "Uncaught matching pattern, this must be handled. Expect errors.");
                }
                return match;
            }
            indx++;
        }
        return null;
    }
    
     public boolean addTVMetaData(MyLibraryFile video)
    {
        String sxxExx = findSeasonEpisodeNumbers(video);
        if(valid(sxxExx))
        {
            Logger.DEBUG( "Found SxxExx pattern ("+sxxExx+"), attempting to parse it.");
            return addTVMetaDataFromSxxExx(video, sxxExx);//dont need to send in sxxexx string because they have been set in the findSeasonEpisodeNumbers method
        }
        else//try secondary method, lookingup on TheTVDB.com
        {
        	//AngryCamel - 20120817 1620 - Added generic
        	if(applyCustomParser(video))
        	{
        	    Logger.DEBUG( "Found series \""+video.getSeries() +"\", and title \""+video.getTitle()+"\", from file label \""+video.getFileLabel()+"\" using a custom parser. "
        	    	            + "Will use this info to look up on the TVDB.com");
        	    return TVDB.lookupTVShow(video);
        	}
        	
        	if(getSeriesAndTitleFromFileLabel(video)) //check if the series and title are both in the file label
            {
                Logger.DEBUG( "Found series \""+video.getSeries() +"\", and title \""+video.getTitle()+"\", from file label \""+video.getFileLabel()+"\". "
                        + "Will use this info to look up on the TVDB.com");
                return TVDB.lookupTVShow(video);
            }
            else //assume that the file label is the episode title
            {
                video.setTitle(video.getFileLabel());
                Logger.DEBUG( "Assuming that the file label is the episode title: \""+video.getTitle()+"\", finding Series by looking at parent folder(s)");
                if(getSeriesFromParentFolder(video))
                {
                    Logger.DEBUG( "Series determined to be \""+video.getSeries()+"\". Will now lookup on TheTVDB to get SxxExx numbers...");
                    return TVDB.lookupTVShow(video);
                }
                else
                {
                    Logger.WARN( "Could not get series from parent folder(s). Will not be able to archive this video because series name is required: "+ video.getFullPathEscaped());
                    return false;
                }
            }
        }
    }

  	//AngryCamel - 20120815 2246
  	//   If a custom parser is not present, then continue on to default parsing.
  	//   If it was present but doesn't find a match (it will return out if it does),
  	//     then continue on to default parsing.
  	//   Expected XML format of the config section for this is:
	//     <!--Parse the series name then the title of the episode -->
	//     <parser>
	//         <regexp>([\w\s*'-]*):([\w\s*'-]*)</regexp> <!-- ex: "Show Name: Title of the Episode" -->
	//     </parser>
	 public boolean applyCustomParser(MyLibraryFile video)
	 {

     	if(video.getSubfolder().shouldApplyParser())
     	{
             Logger.DEBUG( "Found custom series and title parser.");
     		//Info: Structure of parsers = {"regexp":["pattern1","pattern2"]}
             for(Map.Entry<String,List<String>> entry : video.getSubfolder().parsers.entrySet())
             {
                 String type = entry.getKey();
                 if(type==null) continue;//skip
                 List<String> parserStrings = entry.getValue();
                 for(String parserString : parserStrings)
                 {
                     if(type.equalsIgnoreCase(Constants.REGEXP))
                     {
                         Logger.DEBUG( "Custom series and title parser regex: "+parserString);
                         Pattern p = Pattern.compile(parserString, Pattern.CASE_INSENSITIVE);
                         Matcher m = p.matcher(video.getFileLabel());
                         if(m.find())
                         {
                         	if (m.groupCount() == 2)
                         	{
                         		//First group is assumed to be the series
                                 video.setSeries(m.group(1).trim());
                             	//Second group is assumed to be the title
                                 video.setTitle(m.group(2).trim());
                                 return true;
                         	}
                         }
                     }
                 }
			}
     	}
     	return false;
    }

    public static boolean addTVMetaDataFromSxxExx(MyLibraryFile video, String seasonEpisodeNaming)
    {
        //parse season/episode numbers        
        if(!valid(seasonEpisodeNaming)) 
        {
            Logger.WARN( "No Season/Episode pattern was found. Cannot continue for: "+video.getFullPathEscaped());
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
            Logger.WARN( "Cannot parse season and episode numbers from: "+ seasonEpisodeNaming +" ("+SxxExx+"): "+ x);
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
                Logger.WARN( "Title cannot be found, it was expected to be found after one of "+ Arrays.toString(titleSplitters)+ " in the file label \""+video.getFileLabel()+"\"");
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
                    Logger.DEBUG( "Splitting \""+video.getFileLabel()+"\" with \""+splitter+"\" = "+ Arrays.toString(parts)+", length of "+ parts.length +", looking for "+ 3);
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
    public boolean getSeriesAndTitleFromFileLabel(MyLibraryFile video)
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
                    Logger.INFO( "Could not get series/title from \""+ video.getFileLabel() +"\", splitting at \""+ splitter+"\": "+ x.getMessage());
                }
            }
        }
        return false;
    }

    public static boolean getSeriesFromParentFolder(MyLibraryFile video)
    {
        //the series title comess before this as a folder name
        List<String> skipFolders = new ArrayList<String>();//the must match the folder name COMPLETELY. Partial matches wont be skipped
        
        final String optionalYear ="(\\([0-9]+\\)|\\[[0-9]+\\])?";//matches (nnnn) or [nnnn]
        skipFolders.add("(Full Episodes|Episodes|Clips|Seasons)");//literal skips
        skipFolders.add("((Season|Series|Set|Episodes|Collection) (\\([0-9]+\\)|[0-9]+))"+" ?"+optionalYear);//name + number + optional_space + optional_year        
        skipFolders.add("S[0-9]+E[0-9]+ - .+");//to skip "S04E01 - Orientation" in this: Netflix/Instant Queue/H/Heroes/Heroes: Season 4/S04E01 - Orientation/S04E11 - Thanksgiving
        skipFolders.add(".+: Season [0-9]+");//to skip "Heroes: Season 4" this: Netflix/Instant Queue/H/Heroes/Heroes: Season 4/S04E01 - Orientation/S04E11 - Thanksgiving
        skipFolders.add("[0-9]+");//new format used by playon specified season number as single integer folder
        skipFolders.add("(Next|Previous) (Page|Section) \\(.+\\)");//HuluBlueCop/Subscriptions/The Office (HD)/Episodes (174)/Next Page (101-174 of 174)/6x12 - Secret Santa (HD)
        String series = getParentFolderWithSkips(video.getFullPath().split(xbmc.util.Constants.DELIM),skipFolders);
        if(valid(series))
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
            folder = tools.stripExtraLabels(folder);
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

    public int getNextSpecialEpisiodeNumber(MyLibraryFile file)
    {
        String seasonZeroFolder = getDroboxDestNoExt(file);
        seasonZeroFolder = seasonZeroFolder.substring(0, seasonZeroFolder.lastIndexOf(SEP));
        Collection<File> seasonZeroFiles = FileUtils.listFiles(new File(seasonZeroFolder), new String[]{"strm"}, false);
        int maxEpNum = 1;//init
        Pattern p = Pattern.compile("S[0-9]+E[0-9]+", Pattern.CASE_INSENSITIVE);
        for(File f : seasonZeroFiles)
        {            
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
        Logger.DEBUG( "New season zero episode number for " + seasonZeroFolder +" is "+ maxEpNum);
        return maxEpNum;
    }              
}