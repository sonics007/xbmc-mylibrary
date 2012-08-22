package utilities;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.*;

public class XbmcJsonRpc implements Runnable, Constants
{
           
    boolean useHTTP=false, useRawTCP=true;
    int port = 9090;
    public void useHTTP()//curl.exe -i -X POST -d "{\"jsonrpc\": \"2.0\", \"method\": \"JSONRPC.Version\", \"id\": 1}" http://localhost:8080/jsonrpc
    {
        useHTTP = true;
        useRawTCP = false;
        port = Config.XBMC_SERVER_WEB_PORT; 
    }
    public void useRawTCP()//default
    {
        useRawTCP = true;
        useHTTP = false;
        port = 9090;
    }

    public Map<String,XBMCFile> getLibraryVideos()
    {
        return getLibraryVideos(false);//default not to include extras
    }
    
    /*
     * Gets all TV Episodes, Music Videos, and Movies
     * includeExtras - if true, things like seasons, movie sets, etc will be included. If false, only videos will be included
     */
    boolean PRINT_JSON = false;
    public Map<String,XBMCFile> getLibraryVideos(boolean includeExtras)
    {
        Config.log(INFO, "Querying JSON-RPC interface for all videos in library.");
        Map<String,XBMCFile> allVideoes = new HashMap<String,XBMCFile>();
        List<String> fileList = new ArrayList<String>();
        int movieCount = 0, episodeCount = 0, musicVideoCount =0;
        int movieSetCount = 0, tvShowCount = 0, seasonCount = 0;
        Map<String,Object> params = new HashMap<String,Object>();        
        //get movies
        try
        {
            params.put("properties", new String[]{"file","fanart","thumbnail"});
            JSONObject movies = callMethod("VideoLibrary.GetMovies", 1, params);//this also returns movies that exist inside of a set            
            if(movies != null && movies.has("result"))
            {
                JSONObject result = movies.getJSONObject("result");
                if(result != null && result.has("movies"))
                {
                    JSONArray moviesArray = result.getJSONArray("movies");
                    for(int i=0;i<moviesArray.length();i++)
                    {
                        JSONObject nextMovie = moviesArray.getJSONObject(i);
                        String fileLocation = nextMovie.getString("file");
                                                                                                 
                        fileList.add(fileLocation);
                        boolean replaced = allVideoes.put(fileLocation.toLowerCase(), getXBMCFile(FILE,nextMovie)) != null;
                        if(replaced)Config.log(DEBUG, "Found duplicate file in video library: "+ fileLocation);
                        else//only count unique videos
                        {                        
                            movieCount++;
                        }                        
                    }
                }
            }
            
            if(includeExtras)
            {
                Config.log(DEBUG, "Searching for movie sets");
                //check for movie sets (considered directories for our purposes
                params.clear();
                params.put("properties", new String[]{"title","fanart","thumbnail"});                
                JSONObject movieSets = callMethod("VideoLibrary.GetMovieSets", 1, params);                        

                if(movieSets != null && movieSets.has("result"))
                {
                    JSONObject result = movieSets.getJSONObject("result");
                    if(result != null && result.has("sets"))
                    {
                        JSONArray movieSetArray = result.getJSONArray("sets");
                        for(int i=0;i<movieSetArray.length();i++)
                        {

                            JSONObject nextMovieSet = movieSetArray.getJSONObject(i);
                            Config.log(DEBUG, "Found movieset: "+ nextMovieSet.getString("title") +". setid="+nextMovieSet.getString("setid"));
                            String fileLocation = "movieset://"+nextMovieSet.getString("setid");//my custom location (not xmbc protocol)                                                                                                 

                            boolean replaced = allVideoes.put(fileLocation.toLowerCase(), getXBMCFile(DIRECTORY,nextMovieSet)) != null;
                            if(replaced)Config.log(DEBUG, "Found duplicate movie set in video library: "+ fileLocation);
                            else//only count unique movie sets
                            {                        
                                movieSetCount++;
                            }

                        }
                    }
                }                                   
            
            }

            if(movieCount == 0)
                Config.log(WARNING, "No movies were found in XBMC's database....");
            else Config.log(DEBUG, "Found "+ movieCount +" movies in XBMC's database");
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get list of Movies in XBMC's library using JSON-RPC interface: "+ x,x);
        }

        //get TV Shows and seasons/episodes
        try
        {
            
            params.clear();
            params.put("properties", new String[]{"file", "fanart", "thumbnail"});            
            JSONObject series = callMethod("VideoLibrary.GetTVShows", 1, params);
            //Config.log(INFO,series.toString(4));
            if(series != null && series.has("result"))
            {
                JSONObject result = series.getJSONObject("result");
                if(result != null && result.has("tvshows"))
                {
                    JSONArray tvshows = result.getJSONArray("tvshows");
                    for(int t=0;t<tvshows.length();t++)
                    {
                        JSONObject nextTVShow = tvshows.getJSONObject(t);
                        Object tvshowid = nextTVShow.get("tvshowid");
                        //if including extras, add the show, otherwise we are just worried about the episode/file
                        if(includeExtras)
                        {
                            //Add the TV Show
                            //use the tvshowid+label as the identifier since the JSON-RPC iterface doesnt profide a file field in the tvshow object
                            String label = nextTVShow.getString("label");
                            boolean replaced = allVideoes.put(tvshowid+": "+label, getXBMCFile(DIRECTORY, nextTVShow)) != null;
                            if(replaced)Config.log(DEBUG, "Found duplicate tv show in video library: \""+ tvshowid+": "+label+"\"");
                            else tvShowCount++;

                            //Add the Seasons                            
                            
                            params.clear();
                            params.put("tvshowid", tvshowid);
                            params.put("properties", new String[]{"fanart","thumbnail","showtitle"});
                            JSONObject tvShowSeasons = callMethod("VideoLibrary.GetSeasons", 1, params);                            
                            if(tvShowSeasons != null && tvShowSeasons.has("result"))
                            {
                                JSONObject seasonsResult = tvShowSeasons.getJSONObject("result");
                                if(seasonsResult.has("seasons"))
                                {
                                    JSONArray seasons = seasonsResult.getJSONArray("seasons");
                                    for(int s=0;s<seasons.length();s++)
                                    {
                                        JSONObject season = seasons.getJSONObject(s);
                                        //use tvshowid + seasonLabel as identifier because there is no file
                                        String seasonLabel = season.getString("label");                                        
                                        replaced = allVideoes.put(tvshowid+": "+seasonLabel, getXBMCFile(DIRECTORY, season)) != null;
                                        if(replaced)Config.log(DEBUG, "Found duplicate tv season in video library: \""+ tvshowid+": "+seasonLabel+"\"");
                                        else seasonCount++;
                                    }
                                }
                            }
                        }//end including extras (Tv shows/seasons)

                        //add the episodes
                        params.clear();                        
                        params.put("tvshowid", tvshowid);//get all seasons by not specifying season param
                        params.put("properties", new String[] {"file","thumbnail","fanart"});
                        JSONObject episodes = callMethod("VideoLibrary.getEpisodes", 1, params);
                        //Config.log(INFO,episodes.toString(4));
                        if(episodes.has("result") && episodes.getJSONObject("result").has("episodes"))
                        {
                            JSONArray episodeArray = episodes.getJSONObject("result").getJSONArray("episodes");
                            for(int e=0;e<episodeArray.length();e++)
                            {
                                JSONObject nextEpisode = episodeArray.getJSONObject(e);
                                String fileLocation = nextEpisode.getString("file");
                                fileList.add(fileLocation);
                                boolean replaced = allVideoes.put(fileLocation.toLowerCase(), getXBMCFile(FILE,nextEpisode)) != null;
                                if(replaced)Config.log(DEBUG, "Found duplicate file in video library: "+ fileLocation);
                                else episodeCount++;
                            }
                        }
                        else Config.log(INFO,"No episodes found for TV Show: "+ nextTVShow.getString("label") +". ("+nextTVShow+")");
                    }
                }
                else Config.log(INFO, "No TV Shows found in XBMC's library for this series: "+ series);
            }
            else  Config.log(INFO, "No TV Series found in XBMC's library!");
            if(episodeCount == 0)
                Config.log(WARNING, "No TV Shows/Series/Episodes were found in XBMC's library...");
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get list of Episodes in XBMC's library using JSON-RPC interface: "+ x,x);
        }

        // get music videos
        try
        {
             params.clear();             
             params.put("properties", new String[]{"file","fanart","thumbnail","title"});
             JSONObject musicVideos = callMethod("VideoLibrary.GetMusicVideos", 1, params);
             JSONObject result = null;
             try{result =  musicVideos.getJSONObject("result");}catch(Exception x){result = null;};//see if music vidoes exists inthe library
             if(musicVideos != null && result != null && result.has("musicvideos"))
             {                 
                 JSONArray musicVidesArray = musicVideos.getJSONObject("result").getJSONArray("musicvideos");
                 for(int m=0;m<musicVidesArray.length();m++)
                 {
                    JSONObject nextMusicVideos = musicVidesArray.getJSONObject(m);
                    String fileLocation = nextMusicVideos.getString("file");
                    fileList.add(fileLocation);
                    boolean replaced = allVideoes.put(fileLocation.toLowerCase(), getXBMCFile(FILE,nextMusicVideos)) != null;
                    if(replaced)Config.log(DEBUG, "Found duplicate file in video library: "+ fileLocation);
                    else musicVideoCount++;
                 }
             }
             else
                 Config.log(INFO, "No music videos were found in XBMC's library.");
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get list of Music Videos in XBMC's library using JSON-RPC interface: "+ x,x);
        }

        String ifExtras = ")";
        if(includeExtras) ifExtras = ", "+movieSetCount +" movie sets, " + tvShowCount +" TV Shows, and "+seasonCount +" total seasons)";
        Config.log(INFO, "Found "+ allVideoes.size()+" unique files in video library ("+movieCount+" movies, "+episodeCount+" episodes, "+ musicVideoCount+" music videos"+ifExtras);
        return allVideoes;      
    }

    public Map<String,XBMCFile> getLibraryMusic()
    {
        return getLibraryMusic(false);//default not to include extras
    }

    /*
     * Gets all Music
     * includeExtras - if true, then artists and albums will be included. If false, only songs will be included
     */
    public Map<String,XBMCFile> getLibraryMusic(boolean includeExtras)
    {
        Map<String,XBMCFile> allMusic = new HashMap<String,XBMCFile>();
        int albumCount = 0, artistCount = 0, songCount =0;
        Map<String,Object> params = null;

        //get Artists
        if(includeExtras)
        try
        {
            
            params =new HashMap<String,Object>();            
            params.put("albumartistsonly", false);
            params.put("properties", new String[]{"fanart","thumbnail"});
            JSONObject allArtists = callMethod("AudioLibrary.GetArtists", 1, params);            
            if(allArtists.has("result") && !allArtists.isNull("result"))
            {
                JSONArray artists = allArtists.getJSONObject("result").getJSONArray("artists");
                for(int a=0;a<artists.length();a++)
                {
                    JSONObject nextArtist = artists.getJSONObject(a);
                    String label = nextArtist.getString("label");
                    String artistId = nextArtist.getString("artistid");
                    boolean replaced =  allMusic.put(artistId +": "+ label, getXBMCFile(DIRECTORY, nextArtist)) != null;
                    if(replaced)Config.log(DEBUG, "Found duplicate artist in music library: "+ artistId +": "+ label);
                    else artistCount++;
                }
            }
            else Config.log(INFO, "No artists were found in XBMC's database.");
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get Artists from Music library using JSON-RPC: "+x);
        }

        //get Albums
        if(includeExtras)
        try
        {            
            params.clear();
            params.put("properties", new String[]{"fanart","thumbnail"});            
            JSONObject allAlbums = callMethod("AudioLibrary.GetAlbums", 1, params);            
            if(allAlbums.has("result") && !allAlbums.isNull("result"))
            {
                JSONArray albums = allAlbums.getJSONObject("result").getJSONArray("albums");
                for(int a=0;a<albums.length();a++)
                {
                    JSONObject nextAlbum = albums.getJSONObject(a);
                    String label = nextAlbum.getString("label");
                    String albumId = nextAlbum.getString("albumid");
                    boolean replaced =  allMusic.put(albumId +": "+ label, getXBMCFile(DIRECTORY, nextAlbum)) != null;
                    if(replaced)Config.log(DEBUG, "Found duplicate album in music library: "+ albumId +": "+ label);
                    else albumCount++;
                }
            }
            else Config.log(INFO,"No Albums were found in XBMC's library.");
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get Albums from Music library using JSON-RPC: "+x);
        }

        //get Songs
        try
        {
            params = new HashMap<String,Object>();
            params.clear();
            params.put("properties", new String[]{"file","thumbnail","fanart"});
            JSONObject allSongs = callMethod("AudioLibrary.GetSongs", 1, params);            
            if(allSongs.has("result"))
            {
                JSONObject result = allSongs.getJSONObject("result");
                JSONArray songs = result.getJSONArray("songs");
                for(int a=0;a<songs.length();a++)
                {
                    JSONObject nextSong = songs.getJSONObject(a);
                    String file = nextSong.getString("file");
                    boolean replaced =  allMusic.put(file, getXBMCFile(FILE, nextSong)) != null;
                    if(replaced)Config.log(DEBUG, "Found duplicate song in music library: "+ file);
                    else songCount++;
                }
            }
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get Songs from Music library using JSON-RPC: "+x);
        }

        String ifExtras = ")";
        if(includeExtras) ifExtras = ", "+artistCount +" artists, and " + albumCount +" total albums)";
        Config.log(INFO, "Found "+ allMusic.size()+" unique files in audio library ("+songCount+" songs"+ifExtras);
        return allMusic;
    }
    
    public static XBMCFile getXBMCFile(String fileOrDir, JSONObject json)
    {
        try {
        	//AngryCamel - 20120806 2206
        	String runtimeStr = json.has("runtime") ? json.getString("runtime") : "";
        	int runtime = parseRuntime(runtimeStr);
        	
            XBMCFile xbmcFile = new XBMCFile(
                        fileOrDir,
                        json.has("fanart") ? json.getString("fanart") : null,
                        json.has("file") ? json.getString("file") : null, 
                        json.has("label") ? json.getString("label") : null,
                        json.has("thumbnail") ? json.getString("thumbnail") : null,
						runtime //AngryCamel - 20120806 2206
                    );
            return xbmcFile;
        }
        catch (JSONException x)
        {
            Config.log(ERROR, "Failed to get file info from json object: "+ json,x);
            return null;
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
            BlockingQueue<XBMCFile> subfolderDirectory = new ArrayBlockingQueue<XBMCFile>(1);
            subf.setRecursive(false);//temporarily, we just want to get the actual subf folder
            getFiles(subf,
                    rootDir,//actual path to the root dir
                    fullPathLabel,
                    FOLDERS_ONLY,//only want to ge the folder
                    subfolderDirectory//should only get the single dir in it
                    );
            subf.setRecursive(originalRecursive);            

            if(subfolderDirectory.isEmpty())
            {
                Config.log(ERROR, "No matching subfolder named \""+subf.getFullName()+"\" was found. Skipping");
                subf.getSource().setJSONRPCErrors(true);//TODO: this may be preventing clean-ups too often. Consider doing clean-ups on a per-subdirectory basis
                return null;
            }
            else if(subfolderDirectory.size() != 1)
                Config.log(WARNING, "Found unexpected number of subfolder directory matches: "+ subfolderDirectory.size()+". Will use first match.");
            
            return subfolderDirectory.take();
        }
        catch(Exception x)
        {
            subf.setRecursive(originalRecursive);
            Config.log(ERROR, "Error while trying to determine subfolder location: "+ x,x);
            subf.getSource().setJSONRPCErrors(true);
            return null;
        }        
    }

    /*
     * {"jsonrpc": "2.0", "method": "Files.GetDirectory", "params": {"directory":"plugin://plugin.video.hulu"}, "id": "1"}
     * {"jsonrpc": "2.0", "method": "Files.GetDirectory", "params": {"directory":"plugin://plugin://plugin.video.hulu/"}, "id": "1"}
     */
//{"jsonrpc": "2.0", "method": "Files.GetSources", "id": "1", "params": {"media":"video"}}
     
    public void getFiles(
                        Subfolder subf,//the subfolder object we are matching on (also controls recursion)
                        String dir,//the actual directory name (not the label)
                        String fullPathLabel,//the friendly label of the dir, seperated with DELIM
                        final String foldersOrFiles,//Contstant key which tells us what kind of data to return
                        BlockingQueue<XBMCFile> filesAndDirsFound//the list which all files/dirs will be added to                        
                        )
    {        
    	
        if(!subf.canAddAnotherVideo()) return;
        Config.log(DEBUG, "Now looking in:" + Config.escapePath(fullPathLabel) + (Config.LOGGING_LEVEL == DEBUG ?" (" + dir+")" :""));

        Map<String, Object> params = new HashMap<String,Object>();
        params.put("directory",dir);
        
        final String mediaType = "files";//files should return everything after fix here: http://forum.xbmc.org/showthread.php?t=114921
        params.put("media", mediaType);  
        
		//AngryCamel - 20120805 2351
		// -Added runtime for the runtime filter.
		// -You were referencing label (returned when title is specified in properties), thumbnail, and fanart when creating XBMCFile but
		//  they were not coming back in the JSON reponse, so I added those while I was at it.
        final String[] properties = {"runtime", "title", "thumbnail", "fanart"};//files should return everything after fix here: http://forum.xbmc.org/showthread.php?t=114921
        params.put("properties", properties);   
        
        /*Sort testing
         * 
        boolean sort = true;
        String sortOrder = "descending";
        if(sort)
        {
            params.put("sort","{\"method\":\"label\", \"order\": \""+sortOrder+"\"}");
        }
        */
        
        //PRINT_JSON=true;
        JSONObject jsonGetDirectory = callMethod("Files.GetDirectory", 1, params);                          
        
        if(jsonGetDirectory == null || !jsonGetDirectory.has("result"))
        {
            subf.getSource().setJSONRPCErrors(true);
            Config.log(ERROR, "Failed to get list of files from JSON-RPC, skipping this directory (and all sub-directories): "+ Config.escapePath(fullPathLabel) +" ("+dir+")");
        }
        else
        {
            try
            {
                JSONObject result = jsonGetDirectory.getJSONObject("result");
                //process files first, then directories later
                List<JSONObject> directories = new ArrayList<JSONObject>();//get dirs from the list so we can process them later
                List<JSONObject> files = new ArrayList<JSONObject>();//files
                if(result.has("files") && !result.isNull("files"))//as of JSON-RPC v3, both files and dirs are stored in this files[]. Distinguished by filetype attribute
                {
                    JSONArray fileArray = result.getJSONArray("files");
                    for(int i=0;i<fileArray.length();i++)
                    {
                        JSONObject fileOrDir = fileArray.getJSONObject(i);
                        boolean isDirectory = "directory".equalsIgnoreCase(fileOrDir.getString("filetype"));                        
                        //as on json-rpc v3, filetype is an attribute of the file object... filter our directories here
                        if(isDirectory) 
                           directories.add(fileOrDir); //this is a directory, will process dirs after all files
                        else
                            files.add(fileOrDir);                       
                    }
                    Config.log(DEBUG, "Found "+ files.size() +" files and "+ directories.size() +" directories in "+ Config.escapePath(fullPathLabel));
                }
                else Config.log(DEBUG, "No files or directories found for directory: " + fullPathLabel+" ("+dir+")");
                
                
                //find matching FILES
                //Subfolder matchingSubfolder = source.getMatchingSubfolder(fullPathLabel);//need to check if this really matches a configured subfolder
                boolean pathIsInSubfolder = subf.pathMatches(fullPathLabel);//we may just be at this point because digdeeper == true, and these files should be skipped in that case
                if(pathIsInSubfolder)//if this is a configured match, add the files
                {                                                                                                                                                                        
                    if(!files.isEmpty())
                    {
                        for(JSONObject file : files)
                        {
                        	//AngryCamel - 20120806 2206
                        	String runtimeStr = file.has("runtime") ? file.getString("runtime") : "";
                        	int runtime = parseRuntime(runtimeStr);
                        	
                        	XBMCFile xbmcFile = new XBMCFile(
                                    FILE,
                                    file.has("fanart") ? file.getString("fanart") : null,
                                    file.getString("file"), //required
                                    file.getString("label"), //required
                                    file.has("thumbnail") ? file.getString("thumbnail") : null,
                                    runtime, //AngryCamel - 20120806 2206
                                    fullPathLabel,
                                    subf);

                           boolean allowed = subf.isAllowedByFilters(xbmcFile.getFullPathEscaped(), xbmcFile.getRuntime()); //AngryCamel - 20120805 2351
                           if(!allowed) continue;

                           boolean excluded = subf.isExcluded(xbmcFile.getFullPathEscaped());
                           if(excluded) continue;

                            filesAndDirsFound.put(xbmcFile);//passed all tests
                        }
                    }                                       
                }

                
                if(!directories.isEmpty())
                {                    
                    for(int i=0;i<directories.size();i++)
                    {
                        JSONObject directory = directories.get(i);
                        
                        //Remove any '/' from label because it is a reserved character
                        String label = directory.getString("label");
                        String file = directory.getString("file");
                        if(tools.valid(label) && label.contains("/"))
                        {
                            Config.log(INFO, "This label contains a '/', will remove it because it is reserved: \""+label+"\"");
                        }
                        label = tools.cleanJSONLabel(label);

                        //DELIM is used as folder seperator
                        String fullDirectoryPath = fullPathLabel + DELIM + label;
                                                
                        //add directory to the list if it's requested
                        if(foldersOrFiles.equals(FOLDERS_ONLY) || foldersOrFiles.equals(FOLDERS_AND_FILES))
                        {
                            if(subf.pathMatches(fullDirectoryPath))//only add if this is an actual match
                            {
                                XBMCFile xbmcFile = new XBMCFile(
                                                DIRECTORY,
                                                directory.has("fanart") ? directory.getString("fanart") : null,
                                                file, //required
                                                label, //required
                                                directory.has("thumbnail") ? directory.getString("thumbnail") : null,
                                                -1, //AngryCamel - 20120805 2351
                                                fullPathLabel,
                                                subf);
                                filesAndDirsFound.put(xbmcFile);
                                Config.log(DEBUG, "Added directory to list: "+ xbmcFile.getFullPathEscaped());
                            }
                            else Config.log(DEBUG, "Not storing folder in list because the path ("+Config.escapePath(fullDirectoryPath)+") does not match the subfolder ("+subf.getFullName()+")");
                        } 
                        else Config.log(DEBUG, "Not storing folder in list because config param is set to \""+foldersOrFiles+"\"");
                                                
                        //check if we need to go recursively into this dir
                        //this checks if the subf is recursive and handles the search accordingly                        
                        if(!subf.isRecursive() && subf.pathMatchesExactly(fullDirectoryPath))//non-recursive and exact match found, do no need to dig any deeper
                        {
                            //exact match, and non-recursive. End here.
                            Config.log(DEBUG, "Ending search because exact match was found and subfolders are not requested.");
                            return;
                        }                        

                        pathIsInSubfolder = subf.pathMatches(fullDirectoryPath);//we may just be at this point because digdeeper == true, and these files should be skipped in that case
                        boolean digDeeper =  subf.digDeeper(fullDirectoryPath);
                        
                        if(!digDeeper){//check if we have reached max series and end recursion if so (always allow recursion if we are just digging deeper)
                            boolean reachedMax = false;
                            if(subf.getMaxSeries() > 0 && subf.getNumberOfSeries() >= subf.getMaxSeries()) reachedMax = true;//maxed out on series, no need to recurse further
                            else if(!subf.canAddAnotherVideo()) reachedMax = true;//check max video count as wekk                            
                            
                            if(reachedMax){
                                Config.log(INFO, "Reached max (seriescount="+subf.getNumberOfSeries()+"/"+subf.getMaxSeries()+"), canAddAnotherVideo() ="+subf.canAddAnotherVideo()+", not recursing anymore past: " +fullPathLabel);
                                return;
                            }                            
                        }
                        
                        if(pathIsInSubfolder || digDeeper)//matching Subfolder or need to dig deeper, then go into this dir
                        {
                            boolean excluded;
                            if(digDeeper) excluded = false;//always allow digging deeper until we hit matched content
                            else
                            {
                                //not digging deeper, this is an actual folder match.
                                //make sure it's not excluded
                                excluded = subf.isExcluded(fullDirectoryPath);
                                //dont check if it matches a filter untill the full file name is resolved
                            }

                            if(!excluded)
                            {
                                getFiles(//recursive call for the dir
                                        subf,
                                        file,
                                        fullDirectoryPath,
                                        foldersOrFiles,
                                        filesAndDirsFound
                                        );
                            }
                        }
                        else Config.log(DEBUG, "Skipping because it does not match a Subfolder: "+ Config.escapePath(fullDirectoryPath));
                    }//end looping through directories
                }
            }
            catch(Exception x)
            {
                Config.log(ERROR, "Error while parsing json: "+ x,x);
                subf.getSource().setJSONRPCErrors(true);
            }
        }//end if valid json returned
    }
    
    //AngryCamel - 20120806 214700
    //  -Added runtime parsing to detect the format and if necessary translate to a number of minutes as an integer
    public static int parseRuntime(String runtimeStr)
    {
    	if(runtimeStr.equals(""))
	    	return 0;
    	
    	int runTime = 0;
    	
    	//HH:MM:SS Pattern matches any of the following:
    	//39:10, 31:46, 1:39:58, 9:13, 69:58:06
    	String hhmmssPattern = "(\\d*):?(\\d*)?:([0-5][0-9])";  
    	
    	// Compile and use regular expression
    	Pattern pattern = Pattern.compile(hhmmssPattern);
    	Matcher matcher = pattern.matcher(runtimeStr);
    	boolean matchFound = matcher.find();
    	if (matchFound) {
    		int hours = 0, mins = 0, secs = 0;

    		/*
        	String groupStr = "";
        	for (int i=0; i<=matcher.groupCount(); i++) {
                groupStr += " Group("+i+"): "+matcher.group(i);
            }
        	Config.log(Config.DEBUG, "Match:"+ groupStr);
        	*/
        	
    		if(matcher.groupCount()==3)
    		{
    	    	//For patterns without an hour segment, the minute will go into group 1 and the seconds into group 3. Group 2 will be empty
    	    	//For patterns with an hour segment (total of 4), the hour will go into group 1, minute into group 2, and the seconds into group 3
    			if(matcher.group(2).length() < 1)
    			{
    				//This is a MM:SS match
    				//Config.log(Config.DEBUG, "Matched on MM:SS pattern: "+ runtimeStr);
    				
    				//Parse the minutes
    	        	if(matcher.group(1).length()>0)
    	        	{
						try{
							mins = Integer.parseInt(matcher.group(1));
						}catch (NumberFormatException e){}
	    	        }
    	        	//Config.log(Config.DEBUG, "   Mins: "+ mins);
    				
    				//Parse the seconds
    	        	if(matcher.group(3).length()>0)
    	        	{
						try{
							secs = Integer.parseInt(matcher.group(3));
						}catch (NumberFormatException e){}
	    	        }
    	        	//Config.log(Config.DEBUG, "   Secs: "+ secs);
    			}
    			else
    			{
    				//This is a HH:MM:SS match
    				//Config.log(Config.DEBUG, "Matched on HH:MM:SS pattern: "+ runtimeStr);
    				
    				//Parse the hours
    	        	if(matcher.group(1).length()>0)
    	        	{
						try{
							hours = Integer.parseInt(matcher.group(1));
						}catch (NumberFormatException e){}
	    	        }
    	        	//Config.log(Config.DEBUG, "   Hours: "+ hours);
    	        	
    				//Parse the minutes
    	        	if(matcher.group(2).length()>0)
    	        	{
						try{
							mins = Integer.parseInt(matcher.group(2));
						}catch (NumberFormatException e){}
	    	        }
    	        	//Config.log(Config.DEBUG, "   Mins: "+ mins);
    				
    				//Parse the seconds
    	        	if(matcher.group(3).length()>0)
    	        	{
						try{
							secs = Integer.parseInt(matcher.group(3));
						}catch (NumberFormatException e){}
	    	        }
    	        	//Config.log(Config.DEBUG, "   Secs: "+ secs);
    			}
    		}
    	    //Now add it all up
    	    runTime = (60*60*hours) + (60*mins) + secs;
    	}
    	else
    	{
    		//Format did not match HH:MM:SS format; try to parse as int
    		//Config.log(Config.DEBUG, "Runtime format has no pattern (parsing as int): "+ runtimeStr);
			try{
				runTime = Integer.parseInt(runtimeStr);
			}catch (NumberFormatException e){}
    	}

    	//Config.log(Config.DEBUG, "Parsed " + runtimeStr + " to " + runTime + " mins");
        return runTime;
    }

 
    
    Socket jsonRPCSocket = null;
    private long lastRead = 0, tcpInit = 0;
    private boolean socketTimedOut = false;    
    

    String XBMC_SERVER;
    public XbmcJsonRpc(String XBMC_SERVER)
    {      
        this.XBMC_SERVER = XBMC_SERVER;
        if(Config.USE_HTTP)
            useHTTP();
        else useRawTCP();
    }
    public boolean ping()
    {
        JSONObject result = callMethod("JSONRPC.Ping", 1, null);
        if(result == null) return false;
        
        try
        {
            String pong = result.getString("result");
            if(pong.equalsIgnoreCase("pong"))
                return true;
            else return false;
        }
        catch(Exception x)
        {
            Config.log(WARNING, "JSON-RPC is not connected, result from JSONRPC.Ping command=\r\n"+result.toString());
            return false;
        }


    }

    boolean waitingForTimeout = false;
    public void run()
    {
        waitingForTimeout = true;
        while(true)
        {
            if(jsonRPCSocket != null)
            {                
                if(lastRead != 0)//wait until it's initialized
                {
                    long msAgo = (System.currentTimeMillis()-10) - lastRead; //adjust by 10 ms to account for time getting from clock
                    if(msAgo > tcpReadTimeout)
                    {
                        Config.log(DEBUG,"No more data received in the last "+msAgo+" milliseconds, closing JSON-RPC socket.");
                        closeTCPSocket();
                        break;
                    }
                }
                else//not yet initialized, check for init timeout
                {
                    long msAgo = ((System.currentTimeMillis()-10) - tcpInit);
                    if(msAgo > tcpInitializeTimeout)
                    {
                        Config.log(WARNING, "No data was received from XBMC after waiting for "+(msAgo/1000)+" seconds. Cancelling the request.");
                        closeTCPSocket();
                        break;
                    }
                }
            }

            try
            {
                Thread.sleep(50);
            }
            catch(InterruptedException x){
                Config.log(INFO, "Sleeping failed (this is unexpected): " +x,x);
            }
        }
        lastRead = 0;
        waitingForTimeout = false;
    }
    private void closeTCPSocket()
    {
        try
        {
            if(!jsonRPCSocket.isClosed())
            {
                socketTimedOut = true;
                //if(in != null) in.close();
                jsonRPCSocket.close();
                //Config.log(DEBUG, "Socket closed successfully");
            }
        }
        catch(Exception x)
        {
            Config.log(INFO, "Error closing socket: " + x ,x);
        }
    }

    long tcpReadTimeout = Config.JSON_RPC_RESPONSE_TIMEOUT_MS;
    long tcpInitializeTimeout = 1000 * Config.JSON_RPC_TIMEOUT_SECONDS;//give XBMC x seconds to start returning data, then cancel the call
    public JSONObject callMethod(String method, int id, Map<String,Object> params)
    {
        for(int i=0;i<3;i++)
        {
            switch(i)
            {
                case 0: tcpReadTimeout = Config.JSON_RPC_RESPONSE_TIMEOUT_MS; break;
                case 1: tcpReadTimeout = Config.JSON_RPC_RESPONSE_TIMEOUT_MS + 400; break;
                case 2: tcpReadTimeout = Config.JSON_RPC_RESPONSE_TIMEOUT_MS + 1400; break;
            }
            
            JSONObject json = callMethodWithRetry(method, id, params);
            if(json != null)
            {
                if(i > 0)
                    Config.log(INFO, "JSON was successfully retrieved after attempt #"+(i+1));
                return json;
            }
            else 
            {
                Config.log(WARNING, "JSON Returned was not valid (attempt " + (i + 1) + " of 3)" + (i == 2 ? "Ending retries, JSON is not valid" : ", will try again... "));
                if(i < 2)//if will try again, sleep a little bit
                {
                    try{Thread.sleep((i+1) * 2500);}catch(Exception x){}
                }
            }
        }        
        return null;//reached end of tired and no valid json was returned
    }

    private JSONObject callMethodWithRetry(String method, int id, Map<String,Object> params)
    {
        String cmd = "{\"jsonrpc\": \"2.0\", ";
               cmd += "\"method\": \""+method+"\"";
                if(params != null && !params.isEmpty())
                {
                    cmd += ", \"params\": {";
                        for(Map.Entry<String,Object> entry : params.entrySet())
                        {
                            String key = entry.getKey();
                            Object value = entry.getValue();
                            cmd += tools.jsonKeyValue(key, value)+", ";
                        }
                        cmd = cmd.substring(0, cmd.length()-", ".length());//trimm off the extra ", "
                        cmd += "}";//end params
                }
                cmd += ", \"id\": \""+id+"\"}";
        
        Config.log(DEBUG, "Connecting to JSON-RPC and sending command: " + cmd);
        
        StringBuilder response = new StringBuilder();
        if(useHTTP)
        {
            //HTTP POST
            //CURL example: curl.exe -i -X POST -d "{\"jsonrpc\": \"2.0\", \"method\": \"JSONRPC.Version\", \"id\": 1}" http://localhost:8080/jsonrpc                        
            
            String server = XBMC_SERVER+":"+Config.JSON_RPC_WEBSERVER_PORT+"/jsonrpc";
            if(!server.toLowerCase().startsWith("http"))
                server = "http://"+server;            
            try{
                String strResponse = tools.post(server, cmd);
                if(!tools.valid(strResponse)) throw new Exception("No reponse or invalid response code.");
                response = new StringBuilder(strResponse);//save the response
                
            }catch(Exception x){
                Config.log(ERROR,"Failed to POST to " + server,x);
            }
        }

        //Config.log(INFO, "JSON-RPC Command = " + cmd);                
        if(useRawTCP)
        {
            try//send the command to the server
            {                
                jsonRPCSocket = new Socket(XBMC_SERVER, port);
                PrintWriter out = new PrintWriter(jsonRPCSocket.getOutputStream());                
                out.print(cmd);
                out.flush();                
                tcpInit = System.currentTimeMillis();
            }
            catch(Exception x)
            {
                Config.log(ERROR, "Failed to call " + method +" using XBMC's JSON-RPC interface: "+x,x);
                return null;
            }

            try//read the result
            {
                Config.log(DEBUG, "Reading response from XBMC");
                BufferedReader in = new BufferedReader(new InputStreamReader(jsonRPCSocket.getInputStream()));                
                Thread timeout = new Thread(this);
                timeout.start();
                while(true)
                {
                    char c;
                    try
                    {                        
                        c = (char) in.read();
                        lastRead = System.currentTimeMillis();
                        if(c == -1) break;
                    }
                    catch(SocketException x)
                    {
                        if(!socketTimedOut)//expect an error on timeout
                            Config.log(ERROR, "Exception in JSON-RPC interface: "+ x,x);
                        break;
                    }
                    response.append(c);
                    //if(response.length() % 1000 == 0) Config.log(DEBUG, "Response length = "+ response.length() + response.substring(response.length()-500, response.length()));
                }
                if(!socketTimedOut && jsonRPCSocket != null && !jsonRPCSocket.isClosed()) jsonRPCSocket.close();                
                while(waitingForTimeout){}

            }
            catch(Exception x)
            {
                Config.log(WARNING, "Error while reading response (TCP "+port+") from XBMC. Command="+cmd,x);
                return null;
            }
        }

        if(PRINT_JSON)
            Config.log(INFO, "Response from "+cmd+" \r\n"+response);//{   "id" : "1",   "jsonrpc" : "2.0",   "result" : "OK"}
        
        try
        {
            JSONObject obj = new JSONObject(response.toString().trim());
            
            return obj;
        }
        catch(Exception x)
        {
            Config.log(WARNING, "The response from XBMC is not a valid JSON string:"+LINE_BRK+response);
            return null;
        }
    }
}