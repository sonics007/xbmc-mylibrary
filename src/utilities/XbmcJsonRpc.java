package utilities;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

public class XbmcJsonRpc implements Runnable, Constants
{
           
    boolean userCurl=false, useRawTCP=true;
    int port = 9090;
    public void useCurl()//curl.exe -i -X POST -d "{\"jsonrpc\": \"2.0\", \"method\": \"JSONRPC.Version\", \"id\": 1}" http://localhost:8080/jsonrpc
    {
        userCurl = true;
        useRawTCP = false;
        port = Config.XBMC_SERVER_WEB_PORT; 
    }
    public void useRawTCP()//default
    {
        useRawTCP = true;
        userCurl = false;
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
            JSONObject movies = callMethod("VideoLibrary.GetMovies", 1, params);            
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
                        
                        //check if its a movie set
                        boolean isMovieSet = tools.valid(fileLocation) && fileLocation.toLowerCase().startsWith("videodb://");                       
                        if(isMovieSet)//get all files in the movie set
                        {
                            String movieSetName =  nextMovie.getString("label");
                            Config.log(DEBUG, "This appears to be a movie set named \""+movieSetName+"\", getting all videos inside it at: \""+fileLocation+"\"");
                            params = new HashMap<String,Object>();
                            params.put("directory",fileLocation);
                            JSONObject json = callMethod("Files.GetDirectory", 1, params);
                            if(json.has("result") && json.getJSONObject("result").has("files"))
                            {
                                JSONArray files = json.getJSONObject("result").getJSONArray("files");
                                Config.log(DEBUG, "Found " + files.length() +" movies in movie set named \"" +movieSetName+"\"");
                                for(int f=0;f<files.length();f++)
                                {
                                    JSONObject movieInMovieSet = files.getJSONObject(f);
                                    String loc = movieInMovieSet.getString("file");
                                    fileList.add(loc);
                                    boolean replaced = allVideoes.put( loc.toLowerCase(), getXBMCFile(FILE,movieInMovieSet)) != null;
                                    if(replaced)Config.log(DEBUG, "Found duplicate file in video library: "+  loc);
                                    else movieCount++;//only count unique videos
                                }
                            }
                        }

                         //add if its not a movie set, or if it is an include extras is enabled
                        if(!isMovieSet || (isMovieSet && includeExtras))
                        {
                            fileList.add(fileLocation);
                            boolean replaced = allVideoes.put(fileLocation.toLowerCase(), getXBMCFile(isMovieSet ? DIRECTORY : FILE,nextMovie)) != null;
                            if(replaced)Config.log(DEBUG, "Found duplicate file in video library: "+ fileLocation);
                            else//only count unique videos
                            {
                                if(isMovieSet) movieSetCount++;
                                else movieCount++;
                            }
                        }
                    }
                }
            }
            if(movieCount == 0)
                Config.log(WARNING, "No movies were found in XBMC's database....");
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get list of Movies in XBMC's library using JSON-RPC interface: "+ x,x);
        }

        //get episodes
        try
        {
            JSONObject series = callMethod("VideoLibrary.GetTVShows", 1, null);
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
                        //if including extras, add the show
                        if(includeExtras)
                        {
                            //Add the TV Show
                            //use the tvshowid+label as the identifier since the JSON-RPC iterface doesnt profide a file field in the tvshow object
                            String label = nextTVShow.getString("label");
                            boolean replaced = allVideoes.put(tvshowid+": "+label, getXBMCFile(DIRECTORY, nextTVShow)) != null;
                            if(replaced)Config.log(DEBUG, "Found duplicate tv show in video library: \""+ tvshowid+": "+label+"\"");
                            else tvShowCount++;

                            //Add the Seasons
                            params = new HashMap<String,Object>();
                            params.put("tvshowid", tvshowid);
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
                        params = new HashMap<String,Object>();
                        params.put("tvshowid", tvshowid);//get all seasons by not specifying season param
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
                        else Config.log(INFO,"No episodes found for TV Show: "+ nextTVShow);
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
             JSONObject musicVideos = callMethod("VideoLibrary.GetMusicVideos", 1, null);
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
            params.put("genreid", -1);//-1 for all artists
            JSONObject allArtists = callMethod("AudioLibrary.GetArtists", 1, params);            
            if(allArtists.has("result"))
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
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get Artists from Music library using JSON-RPC: "+x);
        }

        //get Albums
        if(includeExtras)
        try
        {
            params = new HashMap<String,Object>();
            params.put("genreid", -1);//-1 for all artists
            params.put("artistid", -1);//-1 for all artists
            JSONObject allAlbums = callMethod("AudioLibrary.GetAlbums", 1, params);            
            if(allAlbums.has("result"))
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
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to get Albums from Music library using JSON-RPC: "+x);
        }

        //get Songs
        try
        {
            params = new HashMap<String,Object>();
            params.put("genreid", -1);//-1 for all genres
            params.put("artistid", -1);//-1 for all artists
            params.put("albumid", -1);//-1 for all albums
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
            XBMCFile xbmcFile = new XBMCFile(
                        fileOrDir,
                        json.has("fanart") ? json.getString("fanart") : null,
                        json.has("file") ? json.getString("file") : null,
                        json.has("label") ? json.getString("label") : null,
                        json.has("thumbnail") ? json.getString("thumbnail") : null
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
                        BlockingQueue<XBMCFile> files//the list which all files/dirs will be added to
                        )
    {        
        if(!subf.canAddAnotherVideo()) return;
        Config.log(DEBUG, "Now looking in:" + Config.escapePath(fullPathLabel) + (Config.LOGGING_LEVEL == DEBUG ?" (" + dir+")" :""));

        Map<String, Object> params = new HashMap<String,Object>();
        params.put("directory",dir);
        params.put("recursive", false);//recursive is not currently working (XBMC bug), so we do our own recursion in this method (better/faster for excludes anyway)
        
        JSONObject json = callMethod("Files.GetDirectory", 1, params);               
        
        if(json == null || !json.has("result"))
        {
            subf.getSource().setJSONRPCErrors(true);
            Config.log(ERROR, "Failed to get list of files from JSON-RPC, skipping this directory (and all sub-directories): "+ Config.escapePath(fullPathLabel) +" ("+dir+")");
        }
        else
        {
            try
            {
                JSONObject result = json.getJSONObject("result");
                //Subfolder matchingSubfolder = source.getMatchingSubfolder(fullPathLabel);//need to check if this really matches a configured subfolder
                boolean pathIsInSubfolder = subf.pathMatches(fullPathLabel);//we may just be at this point because digdeeper == true, and these files should be skipped in that case
                if(pathIsInSubfolder)//if this is a configured match, add the files
                {
                    if(foldersOrFiles.equals(FILES_ONLY) || foldersOrFiles.equals(FOLDERS_AND_FILES))
                    {
                        if(result.has("files"))
                        {
                            JSONArray fileArray = result.getJSONArray("files");
                            if(fileArray.length() > 0)
                            {
                                for(int i=0;i<fileArray.length();i++)
                                {
                                   JSONObject file = fileArray.getJSONObject(i);
                                   XBMCFile xbmcFile = new XBMCFile(
                                            FILE,
                                            file.has("fanart") ? file.getString("fanart") : null,
                                            file.getString("file"), //required
                                            file.getString("label"), //required
                                            file.has("thumbnail") ? file.getString("thumbnail") : null,
                                            fullPathLabel,
                                            subf);

                                   boolean allowed = subf.isAllowedByFilters(xbmcFile.getFullPathEscaped());
                                   if(!allowed) continue;

                                   boolean excluded = subf.isExcluded(xbmcFile.getFullPathEscaped());
                                   if(excluded) continue;

                                    files.put(xbmcFile);//passed all tests
                                }
                            }
                        }
                    }
                }

                if(result.has("directories"))
                {
                    JSONArray directories = result.getJSONArray("directories");
                    for(int i=0;i<directories.length();i++)
                    {
                        JSONObject direct = directories.getJSONObject(i);
                        
                        //Remove any '/' from label because it is a reserved character
                        String label = direct.getString("label");
                        String file = direct.getString("file");
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
                                                direct.has("fanart") ? direct.getString("fanart") : null,
                                                file, //required
                                                label, //required
                                                direct.has("thumbnail") ? direct.getString("thumbnail") : null,
                                                fullPathLabel,
                                                subf);
                                files.put(xbmcFile);
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
                                        files
                                        );
                            }
                        }
                        else Config.log(DEBUG, "Skipping because it does not match a Subfolder: "+ Config.escapePath(fullDirectoryPath));
                    }
                }
            }
            catch(Exception x)
            {
                Config.log(ERROR, "Error while parsing json: "+ x,x);
                subf.getSource().setJSONRPCErrors(true);
            }
        }//end if valid json returned
    }

 
    
    Socket jsonRPCSocket = null;
    private long lastRead = 0, tcpInit = 0;
    private boolean socketTimedOut = false;    
    

    String XBMC_SERVER;
    public XbmcJsonRpc(String XBMC_SERVER)
    {      
        this.XBMC_SERVER = XBMC_SERVER;
        if(Config.USE_CURL)
            useCurl();
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
                        cmd = cmd.substring(0, cmd.length()-2);//trimm off the extra ", "
                        cmd += "}";//end params
                }
                cmd += ", \"id\": \""+id+"\"}";
        
        if(userCurl)
        {
            //CURL example: curl.exe -i -X POST -d "{\"jsonrpc\": \"2.0\", \"method\": \"JSONRPC.Version\", \"id\": 1}" http://localhost:8080/jsonrpc
            cmd = cmd.replace("\"", "\\\"");//need to escape the quotes for the command line to interpret it correctly
            
            String auth = " ";
            if(tools.valid(Config.JSON_RPC_WEBSERVER_USERNAME) && tools.valid(Config.JSON_RPC_WEBSERVER_PASSWORD))
            {
                auth = " --basic -u "+Config.JSON_RPC_WEBSERVER_USERNAME+":"+Config.JSON_RPC_WEBSERVER_PASSWORD+" ";
            }
            cmd = "\""+Config.BASE_PROGRAM_DIR+SEP+"res"+SEP+"curl"+SEP+"curl.exe\" -i"+auth+"--max-time 30 --connect-timeout 15 --retry 2 --retry-delay 1  -X POST -d \""+cmd+"\" "+XBMC_SERVER+":"+Config.JSON_RPC_WEBSERVER_PORT+"/jsonrpc";
        }

        //Config.log(DEBUG, "JSON-RPC Command = " + cmd);
        StringBuilder response = new StringBuilder();
        String curlHeader = "";
        if(useRawTCP)
        {
            try//send the command to the server
            {
                Config.log(DEBUG, "Connecting to JSON-RPC at " + XBMC_SERVER +":"+port +" and sending command: " + cmd);
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
        else//use curl
        {
            try //use curl
            {
                final Process process = Runtime.getRuntime().exec(cmd);                
                InputStream is = process.getInputStream();
                InputStreamReader isr = new InputStreamReader(is);
                int timeoutSec = 15;
                BufferedReader br = new BufferedReader(isr);
                ReaderTimeout readerWithTimeout = new ReaderTimeout(br, timeoutSec);// second timeout                                

                /*Sample curl output:
                 HTTP/1.1 200 OK
                Content-Length: 80
                Content-Type: application/json
                Date: Fri, 18 Feb 2011 19:36:12 GMT
                                                        <------- [start recording here]
                {
                   "id" : 1,
                   "jsonrpc" : "2.0",
                   "result" : {
                      "version" : 2
                   }
                }
                 */
                                                
                boolean startRecording = false;
                for(String line : readerWithTimeout.getLines())
                {
                  
                    if(!startRecording)
                        if(line.trim().isEmpty())
                            startRecording = true;//skip the header. look for the blank line between header and content
                        else
                            curlHeader += line+LINE_BRK;
                    if(startRecording)
                        response.append(line).append(LINE_BRK);
                }                                              
            }
            catch (Exception x)
            {
                Config.log(ERROR, "Failed while executing curl command: "+ cmd,x);
            }
        }

        //Config.log(DEBUG, "Response = \r\n"+response);//{   "id" : "1",   "jsonrpc" : "2.0",   "result" : "OK"}
        try
        {
            JSONObject obj = new JSONObject(response.toString().trim());
            return obj;
        }
        catch(Exception x)
        {
            Config.log(WARNING, "The response from XBMC is not a valid JSON string:"+LINE_BRK+curlHeader+response);
            return null;
        }
    }
}