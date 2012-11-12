
package utilities;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.jdom.Document;
import org.jdom.Element;

import static utilities.Constants.*;
import static btv.tools.BTVTools.*;

public class TVDB
{      
    
    public static boolean lookupTVShow(MyLibraryFile video)
    {
        video.setHasBeenLookedUpOnTVDB(true);
        boolean lookupWasSuccessful = false;

        //determine if this has already been looked up successfully and skip the query
        if(video.getSubfolder()==null || !video.getSubfolder().forceTVDBLookup())
        {//tvdb lookup is NOT forced true
            Logger.DEBUG( "Checking if this video has already been looked up on TVDB based on original path of: "+ video.getFullPathEscaped());
            MyLibraryFile previouslyLookedupVideo = tools.getVideoFromOriginalLocation(video.getFullPath());//not escaped                        
            
            if(previouslyLookedupVideo==null || !previouslyLookedupVideo.hasBeenLookedUpOnTVDB())
            {
                Logger.INFO( "This video has not been succesfully looked up on the TVDB before, will attempt lookup now.");
            }
            else
            {
                if(previouslyLookedupVideo.hasValidMetaData())
                {
                    Logger.INFO( "This video has previously been successfully looked up on the TVDB, will use saved meta-data instead of querying TVDB.com");
                    //copy over all data
                    //make sure we keep the original sufolder and don't use the dummy subfolder
                    Subfolder subf = video.getSubfolder();
                    MyLibraryFile.copyVideoMetaData(previouslyLookedupVideo, video);
                    video.setSubfolder(subf);
                    return true;
                }
                else
                    Logger.INFO( "This video has been queried on the TVDB before, but it was unsuccessful. Will try again now: "+ video.getFullPathEscaped());
            }
        }
        else
            Logger.DEBUG( "Force TVDB lookup is true, skipping DB data check and continuing with TVDB lookup.");

        String seriesTitle = video.getSeries();
        String episodeTitle = video.getTitle();
        String originalAirDate = video.getOriginalAirDate();

        boolean canLookupSeriedId = valid(seriesTitle) || video.isTVDBIdOverridden();
        if(!canLookupSeriedId)
        {
            Logger.WARN( "Cannot lookup TV series because series name is unknown: "+video.getFullPathEscaped());
            return false;
        }

        boolean canLookupEpisode = valid(episodeTitle) || valid(originalAirDate);
        if(!canLookupEpisode)
        {
            Logger.WARN( "Cannot lookup TV episode because neither episode title nor original air date are known: "+video.getFullPathEscaped());
            return false;
        }
                
        String tvdbURL = null;
        List<String> seriesIds = new ArrayList<String>();
        try//tvdb lookup
        {
            int maxSeries = 3;
            if(!video.isTVDBIdOverridden())
            {
                tvdbURL = "http://www.thetvdb.com/api/GetSeries.php?seriesname="+java.net.URLEncoder.encode(seriesTitle,"UTF-8");
                Logger.DEBUG( "Attempting to get series IDs (max of "+maxSeries+") based on seriesname of '"+seriesTitle+"', url = " + tvdbURL);
                java.net.URL URL = new java.net.URL(tvdbURL);
                Document xml = tools.getXMLFromURL(URL);
                List<Element> children = xml.getRootElement().getChildren();
                int seriesCount = 0;
                for(Element series : children)
                {
                    seriesCount++;
                    String seriesId = series.getChildText("seriesid");
                    seriesIds.add(seriesId);
                    String seriesName =  series.getChildText("SeriesName");
                    Logger.DEBUG( "Adding Series #"+seriesCount+" found from thetvdb: seriesName= \"" + seriesName + "\" id = \"" + seriesId+"\"");
                    if(seriesCount == maxSeries) break;
                }
                if(seriesIds.isEmpty())
                {
                     Logger.WARN( "Ending lookup. No series could be found by querying TheTVDB: "+tvdbURL +". Will try to archive this video again later.");                     
                     return false;//unsuccessful lookup
                }
            }
            else
            {
                seriesIds.add(video.getTVDBId());
                Logger.INFO( "TheTVDB series ID is already known, no need to look it up. Using ID of: \"" + video.getTVDBId()+"\"");
            }

            boolean originalAirDateIsAvailable = valid(tools.normalize(video.getOriginalAirDate()));
            if(!originalAirDateIsAvailable) Logger.DEBUG( "Original air date is not known, will not try to match on it.");
            boolean episodeTitleIsAvailable = valid(tools.normalize(video.getTitle()));
            if(!episodeTitleIsAvailable) Logger.DEBUG( "No episode title is available, will not try to match on it.");

            if(!originalAirDateIsAvailable && !episodeTitleIsAvailable)
            {
                Logger.WARN( "Neither title nor original air date are available. Need at least 1 to lookup on thetvdb.com. File = "+video.getFullPathEscaped() +".");
                return false;
            }

            for(String seriesId : seriesIds)
            {
                //get episode info by orig air date and title
                tvdbURL = "http://www.thetvdb.com/api/" + Config.TVDB_API_KEY + "/series/"+seriesId+"/all/en.xml";

                Logger.INFO( "Attempting to find matching episode with"
                        +(originalAirDateIsAvailable ? " original air date = \""+tools.normalize(originalAirDate)+"\"" : "")
                        +(episodeTitleIsAvailable ? " title = \""+tools.normalize(episodeTitle)+"\"" : "")
                        +" from thetvdb, url = " + tvdbURL.replace(Config.TVDB_API_KEY, Config.TVDB_API_KEY_OBSCURED));

                java.net.URL URL = new java.net.URL(tvdbURL);
                Document xml = tools.getXMLFromURL(URL);

                //TODO: implement
                //if(xml != null && xml.getRootElement() != null)
                //  addTVDBQuery(video.getFullPathToOriginalRecording(),System.currentTimeMillis());//track query time to prevent overly-frequent queries

                List <Element> episodes = xml.getRootElement().getChildren("Episode");

                //if both can not be matched on, will use a single match if it exists. single match means 1 and only 1 episode matched on 1 of the criteria
                boolean singleMatch = false;
                boolean hasTVDBImage = false;
                String criteriaUsedForSingleMatch = null;
                String singleMatchTVDBFullInfo = null;
                Element singleMatchEpisode = null;
                                
                Logger.DEBUG( "Found "+ episodes.size() +" episodes for series id "+ seriesId +". Will look for match now...");
                if(episodes == null || episodes.isEmpty())
                {
                    Logger.WARN( "No episodes found on TheTVDB for series "+ seriesTitle + " ("+seriesId+"). "
                        + "You may need to add the series/episodes on TheTVDB.com, or manually provide the correct TVDB id in the config file.");
                    return false;
                }
                else//look for matching episode
                for(Iterator<Element> i = episodes.iterator(); i.hasNext();)
                {
                    boolean isFuzzyMatch = false;//dfault
                    boolean titleMatch = false;//dfault
                    boolean dateMatch = false;//dfault
                    Element episode = i.next();
                    String tvdbFirstAired = episode.getChildText("FirstAired");
                    String tvdbEpisodeTitle = episode.getChildText("EpisodeName");
                    String tvdbSeasonNumber = episode.getChildText("SeasonNumber");
                    String tvdbEpisodeNumber = episode.getChildText("EpisodeNumber");
                    hasTVDBImage = valid(episode.getChildText("filename"));//<filename> stores the path to the .jpg image for the episode

                    String tvdbFullInfo = " Season " + tvdbSeasonNumber + ", Episode " + tvdbEpisodeNumber + ", Titled \"" + tvdbEpisodeTitle +"\", "
                            + "first aired on " + tvdbFirstAired+", has episode image: "+ hasTVDBImage;

                    if(originalAirDateIsAvailable)
                    {
                        if(tools.normalize(video.getOriginalAirDate()).equalsIgnoreCase(tools.normalize(tvdbFirstAired)))
                        {
                            dateMatch = true;
                            Logger.DEBUG( "DATE MATCH: " + tvdbFullInfo);

                            if(!singleMatch)
                            {
                                singleMatch = true;
                                criteriaUsedForSingleMatch = "Original Air Date";
                                singleMatchTVDBFullInfo = tvdbFullInfo;
                                singleMatchEpisode = episode;
                            }
                            else//single match was already found, this is another match, means not a single match anymore
                            {
                                singleMatch = false;//a second (or greater) match was found. There is no longer a single episode match
                            }
                        }
                        else Logger.DEBUG( "NO DATE MATCH: \"" + tools.normalize(video.getOriginalAirDate()) + "\" != \"" + tools.normalize(tvdbFirstAired)+"\"");
                    }
                    else//no orig air date avail, dont need to find a match for it
                    {
                        dateMatch = true;
                    }

                    if(episodeTitleIsAvailable)
                    {
                        boolean exactMatch =tools.normalize(episodeTitle).equalsIgnoreCase(tools.normalize(tvdbEpisodeTitle));
                        
                        boolean fuzzyMatch = false;
                        if(!exactMatch) fuzzyMatch = tools.fuzzyTitleMatch(episodeTitle, tvdbEpisodeTitle, 15);//allow 15 percent discrepency

                        if(exactMatch || fuzzyMatch)
                        {
                            if(fuzzyMatch) isFuzzyMatch = true;
                            titleMatch = true;
                            Logger.DEBUG( (fuzzyMatch ? "FUZZY " : "") + "TITLE MATCH: " + tvdbFullInfo);
                            if(!singleMatch)
                            {
                                singleMatch = true;
                                criteriaUsedForSingleMatch = "Episode Title";
                                singleMatchTVDBFullInfo = tvdbFullInfo;
                                singleMatchEpisode = episode;
                            }
                            else//single match was already found, this is another match, means not a single match anymore
                            {
                                singleMatch = false;//a second (or greater) match was found. There is no longer a single episode match
                            }
                        }                       
                    }
                    else//no title is available, dont need to find a match for it
                    {
                        titleMatch = true;
                    }

                    if(titleMatch && dateMatch)
                    {
                        Logger.INFO( "Title and date match, saving data for this match...");
                        //add the season/episode numbers
                        if(!video.addTVDBSeriesEpisodeNumbers(tvdbSeasonNumber, tvdbEpisodeNumber))
                        {
                            Logger.ERROR( "Found a match on thetvdb.com for "+ video.getFullPathEscaped() +", "
                                + "but the season and episode numbers are invalid (\""+tvdbSeasonNumber+"\", \""+tvdbEpisodeNumber+"\"). "
                                + "Skipping...");
                            return false;
                        }

                        if(!valid(episodeTitle))
                        {
                            if(valid(tvdbEpisodeTitle))
                            {
                                Logger.DEBUG( "Setting video title to title from the TVDB: \""+tvdbEpisodeTitle+"\"");
                                video.setTitle(tvdbEpisodeTitle);
                            }
                        }
                        lookupWasSuccessful = true;
                        Logger.INFO( "SUCCESSFUL LOOKUP" +(isFuzzyMatch ? " (fuzzy match)" : " (exact match)")+": Found matching episdode on thetvdb: "+tvdbFullInfo);

                        if(tvdbSeasonNumber.equals("0") || isFuzzyMatch)
                        {
                            if(isFuzzyMatch)
                                Logger.DEBUG( "Since this was a fuzzy match, will continue to search for exact matches and only use this match if no exact matches exist.");
                            else
                                Logger.DEBUG( "Since the season number is zero, will continue to look for matching episodes, and use a 'regular' season matching episode if it exists. "
                                    + "Otherwise will fall back to this 'special' episode.");
                        }
                        else
                            break;//end loop
                    }
                }//end looking for matching episode

                if(!lookupWasSuccessful)//if there was no traditional match, check for single criteria match
                {                                        
                    if(singleMatch)
                    {
                        Logger.INFO( "Could not match on all criteria, but a single match was found based on " + criteriaUsedForSingleMatch +". "
                                + "Will use episode info: " + singleMatchTVDBFullInfo);
                        String seasonNum = singleMatchEpisode.getChildText("SeasonNumber");
                        String episodeNum = singleMatchEpisode.getChildText("EpisodeNumber");
                        if(!video.addTVDBSeriesEpisodeNumbers(seasonNum, episodeNum))
                        {
                             Logger.ERROR( "Found a multi-part match on thetvdb.com for "+ video.getFullPathEscaped() +", "
                                    + "but the season and episode numbers are invalid (\""+seasonNum+"\", \""+episodeNum+"\"). "
                                    + "Skipping and trying again later.");
                                return false;
                        }
                        lookupWasSuccessful = true;
                    }
                    else
                    {
                        Logger.INFO( "NOT FOUND: Could not find any episode that matched "+(originalAirDateIsAvailable ? "original air date ("+video.getOriginalAirDate()+"), " :"") +
                                (episodeTitleIsAvailable ? " episode title ("+video.getTitle()+")":"")+" for TVDB series id " +seriesId + "."
                                + " For video at: " + video.getFullPathEscaped());
                        lookupWasSuccessful = false;
                    }
                    
                }//end if lookup was unsuccessful and check for multi episode / single parameter match                

                if(lookupWasSuccessful)
                {
                    video.setTVDBId(seriesId);
                    //video.setHasTVDBImage(hasTVDBImage);
                    return lookupWasSuccessful;
                }                
            }//end looping through series ids
            return lookupWasSuccessful;//looped through all series ids
        }
        catch(Exception x)
        {
            Logger.ERROR( "Failed to get episode information from the TVDB for " + video.getFullPathEscaped() +", URL = "+ tvdbURL,x);
            return false;
        }
    }
}