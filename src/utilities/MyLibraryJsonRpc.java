package utilities;

import java.util.*;
import java.util.concurrent.*;
import mylibrary.importer;
import xbmc.util.*;
import static xbmc.util.Constants.*;
import static xbmc.util.XbmcTools.*;
import xbmc.jsonrpc.XbmcJsonRpc;
import static utilities.Constants.*;

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
   
}