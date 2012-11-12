package utilities;

import java.util.concurrent.*;
import mylibrary.importer;
import btv.logger.BTVLogLevel;

import java.io.File;
import java.util.*;

import xbmc.db.XBMCVideoDbInterface;
import xbmcdb.db.tools.VideoType;
import xbmc.jsonrpc.XbmcJsonRpc;
import xbmc.util.*;

import static xbmc.util.Constants.*;
import static xbmc.util.XbmcTools.*;
import static utilities.Constants.*;
import static btv.tools.BTVTools.*;

public class MyLibraryJsonRpc extends XbmcJsonRpc
{
    
    public MyLibraryJsonRpc(String XBMC_SERVER_URL)
    {      
        super(XBMC_SERVER_URL);
    }
    
    /* Samples
     * {"jsonrpc": "2.0", "method": "Files.GetDirectory", "params": {"directory":"plugin://plugin.video.hulu"}, "id": "1"}
     * {"jsonrpc": "2.0", "method": "Files.GetDirectory", "params": {"directory":"plugin://plugin://plugin.video.hulu/"}, "id": "1"}
     * {"jsonrpc": "2.0", "method": "Files.GetSources", "id": "1", "params": {"media":"video"}}
     */
    
    
    /**
     * Get all files under the subfolder (within the subfolder's constraints)
     * @param subf
     * @param dir
     * @param fullPathLabel
     * @param filesFound 
     */
    public void getFilesInSubfolder(
                        Subfolder subf,//the subfolder object we are matching on (also controls recursion)
                        String dir,//the actual directory name (not the label)
                        String fullPathLabel,//the friendly label of the dir, seperated with DELIM                        
                        final String foldersOrFiles,//Contstant key which tells us what kind of data to return
                        BlockingQueue<MyLibraryFile> filesFound//the list which all files/dirs will be added to                        
                        )
    {        
        
        if(!importer.connectedToXbmc)
            return;//we've lost connection (maybe xbmc failed or was closed). End now.
        
        try
        {
            BlockingQueue<XBMCFile> filesAndDirsFounds = new ArrayBlockingQueue<XBMCFile>(20000);
            //get all folders and files (non-recursive), then go thru them to find matches and recurse if needed
            super.getFiles(dir, fullPathLabel, FOLDERS_AND_FILES, filesAndDirsFounds);

            List<XBMCFile> directories = new ArrayList<XBMCFile>();
            List<XBMCFile> files = new ArrayList<XBMCFile>();

            for(XBMCFile f : filesAndDirsFounds){
                if(f.isDirectory())
                    directories.add(f);
                else
                    files.add(f);
            }

            if(!subf.canAddAnotherVideo()) return;

                
            //find matching FILES
            //Subfolder matchingSubfolder = source.getMatchingSubfolder(fullPathLabel);//need to check if this really matches a configured subfolder
            boolean pathIsInSubfolder = subf.pathMatches(fullPathLabel);//we may just be at this point because digdeeper == true, and these files should be skipped in that case
            
            if(!FOLDERS_ONLY.equals(foldersOrFiles))
            {                
                if(pathIsInSubfolder)//if this is a configured match, add the files
                {                                                                                                                                                                        
                    if(!files.isEmpty())
                    {
                        for(XBMCFile file : files)
                        {                        	

                            //convert to MyLibraryFile
                                MyLibraryFile xbmcFile = new MyLibraryFile(
                                    FILE,
                                    file.getFanart(),
                                    file.getFile(),
                                    file.getFileLabel(),
                                    file.getThumbnail(),
                                    file.getRuntime(),
                                    fullPathLabel,
                                    subf);

                           boolean allowed = subf.isAllowedByFilters(xbmcFile.getFullPathEscaped(), xbmcFile.getRuntime()); //AngryCamel - 20120805 2351
                           if(!allowed) continue;

                           boolean excluded = subf.isExcluded(xbmcFile.getFullPathEscaped());
                           if(excluded) continue;

                            filesFound.put(xbmcFile);//passed all tests, add it in to the queue
                        }
                    }                                       
                }
            }

            if(!FILES_ONLY.equals(foldersOrFiles) || subf.isRecursive())
            {
                //now check directories to see if we have to dig deeper/recurse
                if(!directories.isEmpty())
                {                                        
                    for(int i=0;i<directories.size();i++)
                    {                        
                        XBMCFile directory = directories.get(i);                                                

                        //DELIM is used as folder seperator
                        String fullDirectoryPath = fullPathLabel + DELIM + directory.getFileLabel();

                        MyLibraryFile xbmcDir = new MyLibraryFile(
                                                DIRECTORY,
                                                directory.getFanart(),
                                                directory.getFile(),
                                                directory.getFileLabel(),
                                                directory.getThumbnail(),
                                                directory.getRuntime(),
                                                fullPathLabel, 
                                                subf
                                                );
                        

                        //check if we need to go recursively into this dir
                        
                        //this checks if the subf is recursive and handles the search accordingly                        
                        if(!subf.isRecursive() && subf.pathMatchesExactly(fullDirectoryPath))//non-recursive and exact match found, do no need to dig any deeper
                        {
                            //exact match, and non-recursive. End here.
                            Logger.DEBUG( "Ending search because exact match was found and subfolders are not requested.");
                            if(!FILES_ONLY.equals(foldersOrFiles))
                                filesFound.put(xbmcDir);//add and return this single dir
                            return;
                        }                        

                        pathIsInSubfolder = subf.pathMatches(fullDirectoryPath);//we may just be at this point because digdeeper == true, and these files should be skipped in that case
                        boolean digDeeper =  subf.digDeeper(fullDirectoryPath);

                        if(!digDeeper){//check if we have reached max series and end recursion if so (always allow recursion if we are just digging deeper)
                            boolean reachedMax = false;
                            if(subf.getMaxSeries() > 0 && subf.getNumberOfSeries() >= subf.getMaxSeries()) reachedMax = true;//maxed out on series, no need to recurse further
                            else if(!subf.canAddAnotherVideo()) reachedMax = true;//check max video count as wekk                            

                            if(reachedMax){
                                Logger.INFO( "Reached max (seriescount="+subf.getNumberOfSeries()+"/"+subf.getMaxSeries()+"), canAddAnotherVideo() ="+subf.canAddAnotherVideo()+", not recursing anymore past: " +fullPathLabel);
                                return;
                            }                            
                        }

                        if(pathIsInSubfolder || digDeeper)//matching Subfolder or need to dig deeper, then go into this dir
                        {
                            boolean excluded;
                            if(digDeeper) 
                                excluded = false;//always allow digging deeper until we hit matched content
                            else
                            {
                                //not digging deeper, this is an actual folder match.
                                //make sure it's not excluded
                                excluded = subf.isExcluded(fullDirectoryPath);
                                //dont check if it matches a filter untill the full file name is resolved
                            }

                            if(!excluded)
                            {                                
                                //add the dir to the collection unless we requested only files or are just digging deeper
                                if(!FILES_ONLY.equals(foldersOrFiles) && !digDeeper)
                                    filesFound.put(xbmcDir);
                                
                                getFilesInSubfolder(//recursive call for the dir
                                        subf,
                                        directory.getFile(),
                                        fullDirectoryPath,  
                                        foldersOrFiles,
                                        filesFound
                                        );
                            }
                        }
                        else 
                            Logger.DEBUG( "Skipping because it does not match a Subfolder: "+ escapePath(fullDirectoryPath));
                    }//end looping through directories
                }
            }
        }
        catch(Exception x)
        {
            Logger.ERROR( "Error while geting files in subfolder: "+ x,x);
            subf.getSource().setJSONRPCErrors(true);
        }
    }
    
    
    
    
    public XBMCFile getSubfolderDirectory(Subfolder subf)
    {
        //init label and path
        String fullPathLabel = subf.getSource().getName();
        String rootDir = subf.getSource().getPath();

        boolean originalRecursive = subf.isRecursive();
        try
        {
            BlockingQueue<MyLibraryFile> subfolderDirectory = new ArrayBlockingQueue<MyLibraryFile>(1);
            
            subf.setRecursive(false);
            this.getFilesInSubfolder(
                    subf,
                    rootDir,//actual path to the root dir
                    fullPathLabel,
                    FOLDERS_ONLY,//only want to ge the folder
                    subfolderDirectory//should only get the single dir in it
                    );
            subf.setRecursive(originalRecursive);

            if(subfolderDirectory.isEmpty())
            {
                Logger.ERROR( "No matching subfolder named \""+subf.getFullName()+"\" was found. Skipping");
                subf.getSource().setJSONRPCErrors(true);//TODO: this may be preventing clean-ups too often. Consider doing clean-ups on a per-subdirectory basis
                return null;
            }
            else if(subfolderDirectory.size() != 1)
                Logger.WARN( "Found unexpected number of subfolder directory matches: "+ subfolderDirectory.size()+". Will use first match.");
            
            XBMCFile f = subfolderDirectory.take();
            return f;
        }
        catch(Exception x)
        {            
            Logger.ERROR( "Error while trying to determine subfolder location: "+ x,x);
            subf.getSource().setJSONRPCErrors(true);
            subf.setRecursive(originalRecursive);
            return null;
        }        
    }
    
    
    /**
     * Reads all queued meta data changes and attempts to complete them by editing the XBMC Database directly.
     * TODO: Convert what we can to use JSON-RPC interface
     *      DONE: bradvido 11/12/2012. THis class uses JSON-RPC for everything. No XBMC db connection needed !_(::)_!
     */
    public void addMetaDataFromQueue()
    {
        Config.setShortLogDesc("Meta-Data");
        List<QueuedChange> queuedChanges = Config.queuedChangesDB.getQueuedChanges();
        Logger.NOTICE( "Attempting to integrate "+ queuedChanges.size() +" queued meta-data changes into XBMC's MySQL video library.");
        List<QueuedChange> changesToDelete = new ArrayList<QueuedChange>();//these are the changes that are no longer valid
        List<QueuedChange> changesCompleted = new ArrayList<QueuedChange>();//these are the changes that were succseefully implemented
        Set<String> strmsNotInLibrary = new HashSet<String>();
        for(QueuedChange qc : queuedChanges)
        {            
            try
            {                
                String archivedLocation = qc.getDropboxLocation();                
                File archivedStrmFile = new File(archivedLocation);//by the time it has gotten here, the strm file should be in the library
                if(strmsNotInLibrary.contains(archivedStrmFile.toString())) 
                    continue;//we've already determined this isn't in the libarary, no need to attempt more meta data changes
                
                if(!archivedStrmFile.exists())
                {//.strm doesnt exist in dropbox                   
                    changesToDelete.add(qc);
                    Logger.INFO( "REMOVE: This video no longer exists in the dropbox, so it is being removed from meta-data queue: "+ archivedStrmFile);
                    continue;
                }
                                
                //map the string type to the proper type
                VideoType videoType = qc.getProperVideoType();
                
                //make sure its a known video type
                if(videoType == null || !VideoType.isMember(videoType))
                {                                
                    Logger.WARN("REMOVE: Unknown video type: \""+ videoType+"\", will not update meta data");                            
                    changesToDelete.add(qc);
                    continue;
                }
                
                
                MetaDataType metaDataType = qc.getTypeOfMetaData();
                String value = qc.getValue();

                String xbmcPath = XBMCVideoDbInterface.getFullXBMCPath(archivedStrmFile);//the path including filename
                
                if (valid(Config.LINUX_SAMBA_PREFIX ))
                {
                    xbmcPath = utilities.Config.LINUX_SAMBA_PREFIX + xbmcPath;
                    Logger.DEBUG( "Using LINUX_SAMBA_PREFIX. Concatentated path: "+ xbmcPath);
                }

                //bradvido -- 11/12/2012
                //Using JSON-RPC instead of direct db interaction
                XbmcVideoLibraryFile xbmcVideo = getVideoFileDetails(xbmcPath);
                                
                boolean videoIsInXBMCsLibrary = xbmcVideo!=null && xbmcVideo.isInLibrary();
                if(videoIsInXBMCsLibrary)
                {
                    //this video file is in the database, update it                                        
                    int video_id = xbmcVideo.getLibraryId();                    

                    if(MetaDataType.MOVIE_SET == metaDataType)
                    {
                        String setName = value;
                        if(!valid(setName))
                        {
                            Logger.DEBUG( "OK: movie set name is empty, will not add to a movie set: "+ archivedStrmFile);
                            changesCompleted.add(qc);
                            continue;
                        }
                        
                        //this is the set that the movie currently exists in (if any)
                        XbmcMovie movie = getMovieDetails(video_id);
                        int currentMoviesSetId = movie.getSetId();
                        boolean movieCurrentlyBelongsToASet = currentMoviesSetId > 0;
                        
                        
                        //this is the set we want to add the movie to (if it already exists)
                        XbmcMovieSet movieSet = getMovieSetByName(setName);
                        int destinationSetId = movieSet == null ? -1 : movieSet.getSetid();
                        boolean destinationSetExists = destinationSetId > 0;
                        
                        boolean movieAlreadyExistsInDestinationSet = 
                                   movieCurrentlyBelongsToASet 
                                && destinationSetExists 
                                && destinationSetId == currentMoviesSetId;

                        
                        if(movieAlreadyExistsInDestinationSet)
                        {
                            Logger.DEBUG( "OK: This movie already exists in the set \""+setName+"\", skipping.");
                            changesCompleted.add(qc);
                            continue;
                        }
                                                
                        //in XBMC Frodo, movies can only belong to one set (favors using tags for many-to-many relationships)
                        //over-write the existing set
                        if(movieCurrentlyBelongsToASet)//movie already belongs to a different set than our destination set. Don't overwrite
                        {
                            String existingSetName = movie.getSetName();
                            Logger.WARN("This movie already belongs to a different set ("+existingSetName+", setid="+currentMoviesSetId+"), will overwrite with user-specified set: \""+setName+"\":"+archivedStrmFile);                                                                                            
                        }
                        
                        //set the movie set using json-rpc
                        boolean success = setMovieSet(video_id, setName);                        
                        if(success)
                        {
                            Logger.INFO( "SUCCESS: added movie (id="+video_id+") to movieset named \"" + value + "\": " + archivedStrmFile);
                            changesCompleted.add(qc);
                        }
                        else throw new Exception("SKIP: Failed to add to movie set \""+value+"\": "+archivedStrmFile+" using JSON-RPC");
                    }
                    else if(MetaDataType.MOVIE_TAGS == metaDataType)
                    {
                          String strDesiredTags = value;
                          XbmcMovie movie = getMovieDetails(video_id);
                          if(movie == null)
                          {
                              throw new Exception("SKIP: Tags cannot be determined. Could not get movie details for movie id: "+ video_id);
                          }
                        
                        //determine existing tags
                        String[] existingTags = movie.getTags();
                        if(existingTags != null)
                            Arrays.sort(existingTags);
                        Logger.DEBUG("Existing Tags = "+ Arrays.toString(existingTags));
                        
                        final String splitter = "|";  
                        String[] desiredTags = strDesiredTags.contains(splitter) ? splitLiteralDelim(strDesiredTags, splitter)
                                                                            : new String[]{strDesiredTags};                                                                                                                          
                                                
                        
                        if(desiredTags == null)
                        {
                            Logger.WARN("Found no value for desired tags, will set to none.");
                            desiredTags = new String[0];
                        }                        
                        Arrays.sort(desiredTags);//get alphabetical order so we can compare and ignore order                        
                        Logger.DEBUG("Desired Tags = "+ Arrays.toString(desiredTags));                        
                        
                        boolean tagsHaveChanged = 
                            existingTags == null  ? true
                                                  : !Arrays.equals(existingTags, desiredTags);
                         
                        if(!tagsHaveChanged){
                            Logger.INFO("OK: Tags have not changed. Existing = "+ Arrays.toString(existingTags)+", Desired = "+ Arrays.toString(desiredTags));
                            changesCompleted.add(qc);
                            continue;
                        }
                        
                        //this sets the tags to desiredTags (replacing/removing existing values)
                        boolean successfullySetTags = setMovieTags(video_id, desiredTags);                        
                        if(successfullySetTags)
                        {
                            Logger.INFO( "SUCCESS: set movie tags "+Arrays.toString(desiredTags) +" for movie (id="+video_id+"), previous tags = "+ Arrays.toString(existingTags)+": " + archivedStrmFile);                                
                            //completed changes for movie_tags
                            changesCompleted.add(qc);
                        }
                        else throw new Exception("SKIP: Failed to add movie tags "+Arrays.toString(desiredTags)+" using JSON-RPC");                            
                                                                                               
                    }
                    else if(metaDataType.isXFix())//prefix or suffix
                    {
                        
                        
                        String currentTitle = xbmcVideo.getTitle();
                        if(currentTitle == null)
                        {
                            Logger.WARN("Cannot determine existing label for this video. Will assume it needs to be updated.");
                            currentTitle = "";
                        }                        

                        boolean needsUpdate = false;
                        String newValue, xFix;
                        if(MetaDataType.PREFIX == metaDataType)
                        {
                            xFix = "prefix";                            
                            needsUpdate = !currentTitle.startsWith(value);
                            newValue = value + currentTitle;                                
                            
                        }
                        else// if(SUFFIX.equals(metaDataType))//suffix
                        {
                            xFix = "suffix";
                            needsUpdate = !currentTitle.endsWith(value);
                            newValue = currentTitle + value;
                        }
                        //else never gets here because of parent if(...)
                        
                        if(!valid(value))
                        {
                            Logger.DEBUG( "OK: "+xFix +" is empty, no need to change anything for: "+ archivedStrmFile);
                            changesCompleted.add(qc);
                            continue;
                        }                                                
                        
                        if(!needsUpdate)
                        {
                            Logger.INFO("OK: Not updating because this video already has the "+ xFix + "\""+value+"\" at "+archivedStrmFile);
                            changesCompleted.add(qc);
                            continue;
                        }                                                
                        
                        //do the update                
                        boolean success = setTitle(videoType, video_id, toAscii(newValue));//ascii'ing this to avoid problem with json-rpc rejecting special characters TODO: figure out charset problems
                        if(!success)
                            Logger.WARN( "Failed to add prefix/suffix with JSON-RPC. Will try again next time...");
                        else
                        {
                            Logger.INFO("SUCCESS: Added "+xFix +" of \""+value+"\" to title of video (id="+video_id+"): "+ archivedStrmFile + ". New Title = \""+newValue+"\"");
                            changesCompleted.add(qc);
                        }
                    }
                }
                else
                {
                    strmsNotInLibrary.add(archivedStrmFile.toString());
                    throw new Exception("SKIP: The video is not yet in XBMC's library: "+archivedStrmFile);                           
                }
            }
            catch(Exception x)
            {
                boolean isSkipException = (x.getMessage()+"").startsWith("SKIP: ");
                Logger.log(
                        isSkipException ? BTVLogLevel.INFO : BTVLogLevel.WARN, 
                        isSkipException ? "SKIPPING meta-data update: "+x.getMessage() : "Failed updating meta data: "+ x, 
                        isSkipException ? null : x);
            }
        }
        
        int changesDone = (changesCompleted.size() + changesToDelete.size());
        int changesRemaining = queuedChanges.size()-changesDone;
        int numChangesCompleted = queuedChanges.size()- changesRemaining;
        Logger.NOTICE( "Successfully completed "+numChangesCompleted+" queued metadata changes. There are now "+ changesRemaining +" changes left in the queue.");

        Logger.NOTICE( "Updating QueuedChanges database to reflect the "+changesCompleted.size()+" successful metatadata changes.");
        for(QueuedChange qc : changesCompleted)
        {
            //update the status as COMPLETED in the sqlite tracker
            boolean updated = Config.queuedChangesDB.executeSingleUpdate("UPDATE QueuedChanges SET status = ? WHERE id = ?",COMPLETED,qc.getId());
                    
            if(!updated)Logger.ERROR( "Could not update status as "+COMPLETED +" for queued change. This may result in duplicate meta-data for file: "+ qc.getDropboxLocation());
        }
        
        Logger.NOTICE( "Updating QueuedChanges database to reflect the "+changesToDelete.size()+" metatadata changes that are no longer needed.");
        //delete the ones that can be deleted
        for(QueuedChange qc : changesToDelete)
        {            
            boolean deleted = Config.queuedChangesDB.executeSingleUpdate("DELETE FROM QueuedChanges WHERE id = ?",(qc.getId()));
            if(!deleted)Logger.ERROR( "Could not delete metadata queue file in prep for re-write. This may result in duplicate entries for file: "+ qc.getDropboxLocation());
        }
        Logger.NOTICE( "Done updating metadata queued changed database.");
    }

}