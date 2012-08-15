

package utilities;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.*;

public class PlayOnArchiver implements Constants
{

    public static boolean doComedyCentral(XBMCFile video)
    {
        //parse dailyshow/colbert report dates from label. Looks like "February 15, 2011 - January Jones"
        String fullPath = video.getFullPathEscaped();
        boolean dailyShow = fullPath.toLowerCase().contains("/the daily show with jon stewart/");
        boolean colbert = fullPath.toLowerCase().contains("/the colbert report/");
        boolean southpark = fullPath.toLowerCase().contains("/south park/");
        if(dailyShow || colbert)
        {
            if(dailyShow)
            {
                video.setTVDBId("71256");
                video.setSeries("The Daily Show with Jon Stewart");
            }
            if(colbert)
            {
                video.setTVDBId("79274");
                video.setSeries("The Colbert Report");
            }
            if(!video.getFileLabel().contains(" - "))
            {
                Config.log(WARNING, "Cannot parse Daily Show/Colbert Report episode because ' - ' was not found in the title");
                return false;
            }

            SimpleDateFormat dailyShowSDF = new SimpleDateFormat("MMMM dd, yyyy");
            String[] labelParts = video.getFileLabel().split(" - ");
            String textDate = labelParts[0];
            String title = labelParts[1];
            video.setTitle(title);
            Date airDate = null;
            try
            {
                airDate = dailyShowSDF.parse(textDate);
                video.setOriginalAirDate(tools.toTVDBAiredDate(airDate));
                Config.log(DEBUG, "Original Air date = "+ video.getOriginalAirDate());
            }
            catch(Exception x)
            {
                Config.log(WARNING, "The PlayOn Comedy Central Daily Show/Colbert Report episode named \""+video.getFileLabel()+"\" cannot be looked up "
                        + "because a date could be parsed from the title using date format: "+ dailyShowSDF.toPattern(),x);
            }
            boolean successfulLookup = TVDB.lookupTVShow(video);
            if(!successfulLookup)
                Config.log(WARNING, "TVDB lookup failed for Daily Show/Cobert report episode: "+ video.getFullPathEscaped());
            return successfulLookup;
        }
        else if(southpark)
        {
            //like: /South Park/Season 02/s02e13 - Cow Days
            Pattern seasonEpisodePattern = Pattern.compile("^s[0-9]+e[0-9]+ - ", Pattern.CASE_INSENSITIVE);
            Matcher seasonMatcher = seasonEpisodePattern.matcher(video.getFileLabel().trim());
            if(seasonMatcher.find())
            {
                try
                {
                    video.setType(TV_SHOW);
                    String match = seasonMatcher.group();
                    video.setSeasonNumber(Integer.parseInt(match.toLowerCase().substring(match.indexOf("s")+1, match.indexOf("e"))));
                    video.setEpisodeNumber(Integer.parseInt(match.toLowerCase().substring(match.indexOf("e")+1, match.indexOf(" - "))));
                    video.setTitle(video.getFileLabel().substring(video.getFileLabel().indexOf(match)+match.length(), video.getFileLabel().length()));
                    video.setSeries("South Park");
                    return true;
                }
                catch(Exception x)
                {
                    Config.log(WARNING, "Failed to parse PlayOn Comedy Central South park episode: "+ video.getFullPathEscaped(),x);
                    return false;
                }
            }
            else
            {
                Config.log(INFO, "Cannot find SxxExx pattern in PlayOn Comedy Central SouthPark filename, cannot archive: " + video.getFileLabel());
                return false;
            }
        }
        else//unknown type
        {
            Config.log(INFO, "This PlayOn Comedy Central video does not have custom parsing available, will try standard parse: "+ video.getFullPathEscaped());
            return false;
        }
    }
    
    public static boolean doHuluCBS(XBMCFile video, boolean isHulu, boolean isCBS)
    {
        String matchingPattern = null;
        if(!video.knownType() || video.isTvShow())
        {
            Pattern seasonEpisodePattern = Pattern.compile("s[0-9]+e[0-9]+", Pattern.CASE_INSENSITIVE);
            Matcher seasonEpisodeMatcher = seasonEpisodePattern.matcher(video.getFullPath());
            boolean match = seasonEpisodeMatcher.find();
            if(match)
            {
                matchingPattern = seasonEpisodeMatcher.group();
                video.setType(TV_SHOW);
            }
            else if(!video.isTvShow())//dont over-ride config param
                video.setType(MOVIE);
        }

        boolean success;
        if(video.isTvShow())
            success = addHuluOrCBSTvEpisodeMetaData(video, matchingPattern);
        else if(video.isMovie())
        {
            video.setTitle(video.getFileLabel());
            success = tools.valid(video.getTitle());
        }
        else//do hulu/cbs have music videos?
        {
            Config.log(WARNING, "Cannot Archive: Type of content cannot be auto-determined for Hulu video: "+ video.getFullPathEscaped());
            success = false;
        }

        Config.log(DEBUG, (isCBS ? "CBS": "Hulu")+": success="+success+"; "+ (video.isTvShow() ? "TV: series="+video.getSeries()+"; title="+video.getTitle()+", "
                + "season="+video.getSeasonNumber()+", episode="+video.getEpisodeNumber() : "Movie: "+ video.getTitle())+" --- "+ video.getFullPathEscaped());
        return success;
    }
    
    private static boolean addHuluOrCBSTvEpisodeMetaData(XBMCFile video, String seasonEpisodeNaming)
    {
        //parse season/episode numbers
        try
        {
            //get the series and title
            boolean normalMethodSuccess = Archiver.addTVMetaDataFromSxxExx(video, seasonEpisodeNaming);
            if(!normalMethodSuccess)
            {
                //This means the series title comes before this in the fileLabel
                //looks like: "Pretty Little Liars - s1e1 - Pilot" or "Pretty Little Liars - s1e1: Pilot"
                String[] splitters = new String[] {" - "+seasonEpisodeNaming+ " - ", " - "+seasonEpisodeNaming+ ": "};
                for(String matchOn : splitters)
                {
                    int matchIndex = video.getFileLabel().indexOf(matchOn);
                    if(matchIndex != -1)
                    {
                        video.setSeries(video.getFileLabel().substring(0, matchIndex));
                        //get the title
                        video.setTitle(video.getFileLabel().substring(matchIndex+matchOn.length(), video.getFileLabel().length()));
                        break;
                    }
                }

            }

            if(!tools.valid(video.getSeries()))
            {
                Config.log(WARNING, "Series cannot be found from Hulu/CBS path: "+ video.getFullPathEscaped());
                return false;
            }

            //get the title (not required for scraping
            if(!tools.valid(video.getTitle()))
            {
                Config.log(INFO, "Title cannot be parsed for this video, it will be set to the file name: \""+ video.getFileLabel()+"\"");
                video.setTitle(video.getFileLabel());
            }
            return true;
        }
        catch(Exception x)
        {
            Config.log(WARNING, "This Hulu/CBS video was thought to be a TV show, but the season/episode numbers and/or title could not be parsed: "+ video.getFullPathEscaped(),x);
            return false;
        }
    }

    public static boolean doNetflix(XBMCFile video)
    {
        //pattern one, matches most normal TV season/episode patterns for netflix
        Pattern seasonEpisodePattern = Pattern.compile("(season|series|vol\\.) [0-9]+/[0-9][0-9]:",Pattern.CASE_INSENSITIVE);//Netflix/New Arrivals/New TV to watch instantly/A-B/3rd Rock from the Sun: Season 3/08: A Friend in Dick
        Matcher seasonEpisodeMatcher = seasonEpisodePattern.matcher(video.getFullPathEscaped());
        
        //another pattern to find when no season/series word is specified        
        Pattern seasonEpisodePattern2 = Pattern.compile("/[0-9]+/[0-9][0-9]:");//Netflix/Instant Queue/Alphabetical/B/Blue's Clues/5/28: Our Neighborhood Festival
        Matcher seasonEpisodeMatcher2 = seasonEpisodePattern2.matcher(video.getFullPathEscaped());

        //pattern that matches absolutely numbered TV shows (no season)
        Pattern absoluteEpisodePattern = Pattern.compile("^[0-9][0-9]:");//Netflix/Instant Queue/Alphabetical/B/The Blue Planet: Tidal Seas/01: Tidal Seas
        Matcher absoluteEpisodeMatcher = absoluteEpisodePattern.matcher(video.getFileLabel());//only chck the file name because we are using the ^ start of string regex identifier

        if(!video.knownType() || video.isTvShow())
        {
            //get season and episode numbers
            try
            {
                if(seasonEpisodeMatcher.find())//looks like "/30 Rock Season 1/1: Pilot"
                {
                    video.setType(TV_SHOW);
                    String match = seasonEpisodeMatcher.group();// "season 1/02:"
                    int seasonNum = Integer.parseInt(match.substring(match.indexOf(" ")+1, match.indexOf("/")));
                    int episodeNum = Integer.parseInt(match.substring(match.indexOf("/")+1, match.indexOf(":")));
                    video.setSeasonNumber(seasonNum);
                    video.setEpisodeNumber(episodeNum);
                }
                else if(seasonEpisodeMatcher2.find())
                {
                    video.setType(TV_SHOW);
                    String match = seasonEpisodeMatcher2.group();// "/5/28:"
                    String[] parts = (match.substring(1, match.indexOf(":"))).split("/"); // "5/28"
                    int seasonNum = Integer.parseInt(parts[0]);
                    int episodeNum = Integer.parseInt(parts[1]);
                    video.setSeasonNumber(seasonNum);
                    video.setEpisodeNumber(episodeNum);                    
                }
                else if (absoluteEpisodeMatcher.find())
                {
                    //catch something like this, which is really TV, but has no season number
                    Config.log(INFO, "This appears to be a TV episode with no Season info. Will attempt TVDB lookup, and if it fails, default to season zero: "+ video.getFullPathEscaped());
                    video.setType(TV_SHOW);
                    addNetflixTVSeriesAndTitle(video);//need series/title before lookup
                    if(!TVDB.lookupTVShow(video))
                    {
                        video.setSeasonNumber(0);
                        //TODO: generate info for this automatically since it will not be scraped successfully
                        String match = absoluteEpisodeMatcher.group();// "01:"
                        video.setEpisodeNumber(Integer.parseInt(match.substring(0,match.indexOf(":"))));//parse the number from something like "01:"
                    }
                }
                else
                {
                    Config.log(DEBUG, "This netflix source does not match any known TV patterns. assuming it is a movie: "+ video.getFullPathEscaped());
                    video.setType(MOVIE);
                }
            }
            catch(Exception x)
            {
                Config.log(WARNING, "Cannot parse season/episode numbers for netflix TV source: "+video.getFullPathEscaped(),x);
            }
        }
        
        //netflix doesn't have music videos afaik
        
        Config.log(DEBUG, "Found PlayOn Netflix: "+ (video.isTvShow() ? "TV   ":"Movie")+": "+ video.getFullPathEscaped());

        if(video.isTvShow())
            return addNetflixTVSeriesAndTitle(video);//parse netflix info from label/path
        else if(video.isMovie())
        {
            video.setTitle(video.getFileLabel());//just use the label as the title
            return tools.valid(video.getFileLabel());
        }
        else
        {
            Config.log(WARNING, "Cannot Arvhive Playon Netflix video: Type of content cannot be auto-determined for: "+ video.getFullPathEscaped());
            return false;
        }
    }
    private static boolean addNetflixTVSeriesAndTitle(XBMCFile video)
    {
        
        try
        {
            //find series and title
            String series = null, title = null;
            try//this method must be tried first (even though it matches less files); try using the filename spit at ":"... catches things line Bob the Builder: Call in the Crew
            {

                if(video.getFileLabel().contains(": "))//somethign like /Bob the Builder: Call in the Crew
                {
                    String[] sa = video.getFileLabel().split(": ");
                    if(sa.length != 2) throw new Exception("File label contains more than one ':'. Cannot parse.");
                    series = sa[0];
                    title = sa[1].trim();
                    boolean is24 = (series.trim().equals("24") || video.getFullPathEscaped().contains("/24/"))
                            && (title.toLowerCase().contains("a.m") || title.toLowerCase().contains("p.m"));                    
                    if(!is24)//exclude 24 becuase the series should be an integer
                    {
                        series = "24";
                        if(tools.isInt(series))//this is actually the episode number, not the series.
                        {
                            series = null;
                            throw new Exception("Incorrect series (Integer found as series name)");
                        }
                    }
                }
                else throw new Exception("No colon in file label, try secondary method");
            }
            catch(Exception x)//try secondary method, getting series as the parent folder and the file label being the title
            {
                title = video.getFileLabel();

                //try looking in the folder(s) above this show for the series
                if(!Archiver.getSeriesFromParentFolder(video))
                {
                    //didn't work try secondary method
                    String[] serieses = video.getFullPath().split(DELIM);
                    series = serieses[serieses.length-2];//the folder above the file name
                }
                else
                {
                    series = video.getSeries();//series was  set in getSeriesFromParentFolder(). get it back here for further checks
                }
                
                if(series.contains(":"))//catches folders named like so: /Busytown Mysteries: Series 2/
                        series = series.substring(0, series.indexOf(":"));

                if(title.contains(":"))
                    title = title.substring(title.indexOf(":")+1,title.length());
            }

            if(!tools.valid(series)) throw new Exception("Series cannot be parsed...");
            video.setSeries(series.trim());

            if(!tools.valid(title)) throw new Exception("Title cannot be parsed...");
            video.setTitle(title.trim());

            return true;//successfully got the series and title
            //log(INFO, "Netflix episode meta data: series="+file.getSeries()+"; title="+file.getTitle()+", season="+file.getSeasonNumber()+", episode="+file.getEpidoseNumber());
        }
        catch(Exception x)
        {
            if(!tools.valid(video.getSeries()) || !tools.valid(video.getTitle()))
            {
                Config.log(WARNING, "Cannot parse, and cannot lookup Netflix TV show (series="+video.getSeries()+", title="+video.getTitle()+")"+LINE_BRK+ video.getFullPathEscaped() +LINE_BRK+ x.getMessage());
                return false;
            }
           else
            {
                Config.log(INFO, "Cannot parse, but can lookup Netflix TV show (series="+video.getSeries()+", title="+video.getTitle()+")"+LINE_BRK + video.getFullPathEscaped() + LINE_BRK+ x.getMessage());
                Config.log(INFO, "Attempting to find metadata on TheTVDB.com");
                return TVDB.lookupTVShow(video);
            }
        }
    }
}