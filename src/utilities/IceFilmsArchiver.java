
package utilities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class IceFilmsArchiver implements Constants
{
    
    //store the videos from icefilms in a map so multi-part videos can work
    //String identifier for movies = FILE_TYPE + PARENT_PATH + TITLE (lowercase)
    //String identifier for episode = FILE_TYPE + PARENT_PATH +SERIES_NAME+ SEASON_EPISODE_NAMING + TITLE (lowercase)
    
    public static Map<String,XBMCFile> iceFilmVideos = new HashMap<String,XBMCFile>();

    public static boolean Archive(XBMCFile video)
    {
        String id;
        boolean parsed;
        if(video.isMovie())
        {
             parsed = movieParse(video);
             id = (video.getType()+video.getParentPath()+video.getTitle()).toLowerCase();
            
        }
        else if(video.isTvShow())
        {
            parsed =tvShowParse(video);
            id = (video.getType()+video.getParentPath()+video.getSeries()+video.getSeasonEpisodeNaming()+video.getTitle()).toLowerCase();
        }
        else
        {
            Config.log(INFO, "This IceFilms video does not have custom parsing available, will try standard parse: "+ video.getFullPathEscaped());
            return false;
        }

        if(parsed)
        {
            checkMultiPart(video,id);//track which video is original and which is duplicate/next part
            return true;//successfully parsed the movie
        }
        else
            return false;//failed to parse
    }

    public static boolean tvShowParse(XBMCFile video)
    {
        Config.log(DEBUG,"Attempting IceFilms TV Show parse");
        try
        {
            //folder structure looks like this for TV Shows
            /*
             .../
             /SeriesName (yyyy)         dirs[length-4]
             /Season X (yyyy)           dirs[length-3]
             /SxxExx Title              dirs[length-2]
             /[Episode File(s)]           dirs[length-1]
             * */

            //direct parent is the episode naming like: 1x01 Pilot
            String[] dirs = video.getFullPath().split(DELIM);            
            String series = dirs[dirs.length-4];
            if(!tools.valid(series))
            {
                Config.log(WARNING, "Series cannot be determined for IceFilms video: "+ video.getFullPathEscaped());
                return false;
            }
            video.setSeries(series);
            
            String episodeNaming = dirs[dirs.length-2];
            int xIndx = episodeNaming.toLowerCase().indexOf("x");
            int spaceIndx =episodeNaming.indexOf(" ");
            String strSeason = episodeNaming.substring(0,xIndx);
            String strEpisode =episodeNaming.substring(xIndx+1, spaceIndx);
            String title = episodeNaming.substring(spaceIndx+1,episodeNaming.length());
            if(!tools.isInt(strSeason) || !tools.isInt(strEpisode))
            {
                Config.log(WARNING, "Cannot determine season/episode naming from parent folder: \""+episodeNaming+"\" of IceFilms video: "+ video.getFullPathEscaped());
                return false;
            }
            video.setSeasonNumber(Integer.parseInt(strSeason));
            video.setEpisodeNumber(Integer.parseInt(strEpisode));

            if(!tools.valid(title))
                Config.log(INFO, "No title available for IceFilms tv episode: "+ video.getFullPathEscaped());
            else
                video.setTitle(title);

            return true;
        }
        catch(Exception x)
        {
            Config.log(WARNING, "Failed to parse IceFilms TV Show, will attempt default parse: "+x,x);
            return false;
        }
    }

    public static boolean movieParse(XBMCFile video)
    {
        Config.log(DEBUG,"Attempting IceFilms movie parse");
        List<String> skipFolders = new ArrayList<String>();
        skipFolders.add("(.*DVD.*|HD (720[p]?|1080[i,p]?))|HD");//for iceFilms skip folders
        String title = Archiver.getParentFolderWithSkips(video.getFullPath().split(DELIM),skipFolders);
        if(tools.valid(title))
        {
            //may contain a hd identifier, clean it out
            String hd = " *HD*";
            if(title.toUpperCase().endsWith(hd)) title = title.substring(0,title.toUpperCase().lastIndexOf(hd));
            video.setTitle(title);
            
            return true;
        }
        else
        {
            Config.log(WARNING, "Cannot determine name of movie from parent folder for: "+ video.getFullPathEscaped());
            return false;
        }
    }

    static private void checkMultiPart(XBMCFile video, String id)
    {
        //check if this is the first file for this video or a duplicate
        XBMCFile original = iceFilmVideos.get(id);
        if(original != null)
        {
            Config.log(DEBUG, "Duplicate IceFilms "+video.getType()+": "+ video.getFullPathEscaped());
            //this is a duplicate
            video.setAsDuplicateTo(original);
            video.setMultiFileVideo(true);

            //update the original with this video's file
            if(!original.isMultiFileVideo())
            {
                original.setMultiFileVideo(true);
                original.addDuplicateVideo(original);//always add self to the list
            }

            //add the file from this video too
            original.addDuplicateVideo(video);

            //sync the multi-files lists (so the .strm gets updated with all the parts)
            video.duplicateVideos = original.duplicateVideos;

            Config.log(DEBUG, "This video now has "+ original.duplicateVideos.size() +" multi-file parts associated with it: " + video.getFullPathEscaped());
        }
        else
        {
            Config.log(DEBUG, "New IceFilms "+video.getType()+": "+ video.getFullPathEscaped());
            //first time this videos identifier has been used, add it to the map incase future vidoes matchit
            iceFilmVideos.put(id, video);
        }
    }

}
