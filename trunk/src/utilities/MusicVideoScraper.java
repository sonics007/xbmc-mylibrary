package utilities;

import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import org.apache.commons.io.FileUtils;
import org.jdom.Document;
import org.jdom.Element;
import static utilities.Constants.*;
import static btv.tools.BTVTools.*;
public class MusicVideoScraper
{
    public static void main(String[] args)
    {
        Config c = new Config();
        String folder = "C:"+SEP+"dropbox"+SEP+"Music Videos";
        if(args == null || args.length == 0)
        {
            Logger.WARN("No args specified, using default folder of: "+folder);
        }
        else
             folder = args[0];
        try
        {
            Random r = new Random();
            Iterator<File> it = FileUtils.iterateFiles(new File(folder), new String[]{"strm"}, false);
            while(it.hasNext())
            {
                File f = it.next();
                MusicVideoScraper.scrape(f);
                Logger.INFO( "So far: attempts="+attempts+", success="+success+"\tskip="+skip+"\tfail="+fail+"\tqueryCountThisRun="+apiQueryCount);
                int seconds = r.nextInt(14)+10;//average is ~17.28 seconds in order to stay @ 5,000 per day
                Logger.INFO( "Waiting "+ seconds +" seconds before next query to keep Yahoo happy...");
                try
                {
                    Thread.sleep(seconds * 1000*0);
                }
                catch(Exception x){}
                //if(attempts == 5) break;
            }

        }
        catch(Exception x)
        {
            Logger.ERROR( "General exception: "+ x,x);
        }
        finally
        {
            c.end();
        }
    }

    final static String APP_ID = "DEc7T1nV34Eo1sPk8oYcPr7KosJynButoR2cMDp5QhzRbhTE9B_eeKz6x_dpzVCUwr4mokbUqJMgx1H39rarXnVcEJeFDEY-";
    final static String API_NAME = "YahooMusicApi";
    final static int MAX_QUERIES_24HR = 1500;

    static int attempts=0, success = 0, fail=0, skip=0, apiQueryCount = 0;
    static private int getQueryCountInPast24Hours()
    {       
        int queryCount = 0;
        String sql = "SELECT count(id) FROM APIQueries WHERE api_name = ? AND query_time > ?";
        PreparedStatement prep = null;
        try
        {
            prep = Config.scraperDB.getStatement(sql);
            prep.setString(1, API_NAME);
            prep.setTimestamp(2, new java.sql.Timestamp(System.currentTimeMillis() - ONE_DAY));//24 hours ago
            ResultSet rs = prep.executeQuery();
            if(rs.next())
                queryCount = rs.getInt(1);
        }
        catch(Exception x)
        {
            Logger.ERROR( "Failed to get query count using: "+ sql,x);
        }
        finally
        {
            Config.scraperDB.closeStatement();
            return queryCount;
        }
    }
    
    private static void trackApiQuery(String urlQueried)
    {                
        String sql = "INSERT INTO APIQueries (query_time, api_name, query_url) "
                + "VALUES(?, ?, ?)";        
        
        PreparedStatement prep = null;
        try
        {
            prep = Config.scraperDB.getStatement(sql);
            
            prep.setTimestamp(1, new java.sql.Timestamp(System.currentTimeMillis()));//last_query is right now                                
            prep.setString(2, API_NAME);//api_name
            prep.setString(3, urlQueried);//query_url
        
            int rowsUpdated = prep.executeUpdate();
            if(rowsUpdated != 1)
                throw new Exception(rowsUpdated+" rows were updated");                
        }
        catch(Exception x)
        {
            Logger.WARN("Failed to track API query for scraper "+ API_NAME+": "+ x,x);
        }
        finally
        {
            Config.scraperDB.closeStatement();            
        }
    }
    
    private static boolean createNFO(Document xml, File nfoFile)
    {
        //Config.setShortLogDesc("CreateNFO");
        try
        {                                                
            try
            {
                Element root = xml.getRootElement();
                List<Element> videos = root.getChildren("Video");
                if(videos.isEmpty())
                {
                    Logger.WARN("In XML from API, no <Video> elements found under <Videos>, cannot create .nfo.");
                    fail++;
                    return false;
                }
                Element video = videos.get(0);//use the first match

                //get needed data
                String artist = video.getChild("Artist").getAttributeValue("name");
                String title  = video.getAttributeValue("title");
                String year = video.getAttributeValue("copyrightYear");
                String strSeconds = video.getAttributeValue("duration");
                String runtime = null;
                if(valid(strSeconds))
                {
                    try
                    {
                        int secs = Integer.parseInt(strSeconds);
                        int min = secs / 60;
                        int sec = secs % 60;
                        runtime = min+":"+sec;
                    }
                    catch(Exception x)
                    {
                        Logger.INFO( "Failed to get runtime min:sec from: "+ strSeconds);
                    }
                }
                String genre = null;
                List<Element> categories = video.getChildren("Category");
                if(categories != null)
                for(Element e : categories)
                {
                    if("Genre".equalsIgnoreCase(e.getAttributeValue("type")))
                    {
                        genre = e.getAttributeValue("name");
                        break;//use the first "genre" category found
                    }
                }

                String studio = video.getAttributeValue("label");

                List<String> lines = new ArrayList<String>();
                lines.add("<musicvideo>");
                    //required:
                    if(valid(title)) lines.add("<title>"+title+"</title>");
                    else
                    {
                        Logger.WARN("Title is not available, cannot continue.");
                        fail++;
                        return false;
                    }
                    if(valid(artist)) lines.add("<artist>"+artist+"</artist>");
                    else
                    {
                        Logger.WARN("artist is not available, cannot continue.");
                        fail++;
                        return false;
                    }

                    //optional:
                    if(valid(genre)) lines.add("<genre>"+genre+"</genre>");
                    if(valid(runtime)) lines.add("<runtime>"+runtime+"</runtime>");
                    if(valid(year)) lines.add("<year>"+year+"</year>");
                    if(valid(studio)) lines.add("<studio>"+studio+"</studio>");
                lines.add("</musicvideo>");

                boolean written = tools.writeToFile(nfoFile, lines, true);
                if(written)
                {
                    Logger.INFO( "Created .nfo at: "+ nfoFile);                    
                    
                    //get the image
                    File imageFile = new File(nfoFile.getPath().substring(0, nfoFile.getPath().lastIndexOf("."))+".tbn");
                    if(imageFile.exists())
                    {
                        Logger.INFO( "Image already exists, skipping downloading image: "+ imageFile);
                    }
                    else
                    {
                        String imageUrl = null;
                        int largestSize = 0;
                        List<Element> images = video.getChildren("Image");
                        for(Element image : images)
                        {
                            try
                            {
                                int size = Integer.parseInt(image.getAttributeValue("size"));
                                if(size > largestSize)
                                {
                                    largestSize = size;
                                    imageUrl = image.getAttributeValue("url");
                                }
                            }
                            catch(Exception x)
                            {
                                Logger.WARN("Failed to get image info: "+x,x);
                            }
                        }

                        if(valid(imageUrl))
                        {
                            boolean saved = saveImage(imageUrl, imageFile);
                            if(saved)Logger.INFO( "Saved image to: "+ imageFile +" from "+ imageUrl);
                        }
                    }

                    return true;
                }
                else//failed to write .nfo
                {
                    Logger.ERROR( "Failed to write .nfo at: "+ nfoFile);
                    fail++;
                    return false;
                }
                
            }
            catch(Exception x)
            {
                Logger.WARN("Error while parsing XML. Cannot create .nfo: "+ x);
                fail++;
                return false;
            }
        }
        catch(Exception x)
        {
            Logger.ERROR( "General error while creating .nfo: "+x,x);
            return false;
        }
    }

    public static boolean scrape(File musicVideoFile)
    {
        attempts++;

        //Config.setShortLogDesc("CheckLimit");
        int past24hrQueryCount = getQueryCountInPast24Hours();
        if(past24hrQueryCount >= MAX_QUERIES_24HR)
        {
            Logger.NOTICE( "Disabling music video pre-scraper because "+ past24hrQueryCount+" queries have been executed for the "+ API_NAME +" API in the past 24 hours, the max allowed is: "+MAX_QUERIES_24HR);
            Config.SCRAPE_MUSIC_VIDEOS = false;
            return false;
        }
        Logger.DEBUG( "Queries in past 24 hours for "+ API_NAME +" api = "+ past24hrQueryCount +", max allowed = "+ MAX_QUERIES_24HR +", remaining = "+ (MAX_QUERIES_24HR-past24hrQueryCount));


        //Config.setShortLogDesc("GetXML");
        try
        {
            if(!musicVideoFile.exists() || !musicVideoFile.isFile())
            {
                Logger.WARN("Music video file does not exist. Cannot scrape: "+ musicVideoFile);
                fail++;
                return false;
            }

            File nfoFile = new File(musicVideoFile.getPath().substring(0, musicVideoFile.getPath().lastIndexOf("."))+".nfo");            
            if(nfoFile.exists())
            {
                Logger.DEBUG( "NFO File already exists, skipping scrape for: "+ nfoFile);
                skip++;
                return false;
            }

            Logger.INFO( "Scraping info for music video: "+ musicVideoFile);

            String artist = null, title = null;
            String name = musicVideoFile.getName();
            final String split = " - ";
            try
            {
                name = name.substring(0, name.lastIndexOf("."));//trim the ext
                int splitIndx = name.indexOf(split);
                if(splitIndx == -1) throw new Exception("Name does not contain \""+split+"\"");
                artist = name.substring(0, splitIndx).trim();
                title = name.substring(splitIndx+split.length(), name.length()).trim();

                artist = cleanMusicVideoLabel(artist);
                title = cleanMusicVideoLabel(title);
            }
            catch(Exception x)
            {
                Logger.WARN("Failed to parse file name: "+ name+". Error: "+x);
                fail++;
                return false;
            }

            //look up based on artist - title
            String url = "http://us.music.yahooapis.com/video/v1/list/search/all/"
                    + URLEncoder.encode(artist + " " + title, "UTF-8")
                    + "?appid="+APP_ID
                    + "&response=artists,images,categories";

            String checkSQl = "SELECT query_time FROM APIQueries "
                    + "WHERE query_url = ? "
                    + "AND api_name = ?";
            
            Long lastqueried = Config.scraperDB.getSingleTimestamp(checkSQl,url,API_NAME);
            
            
            if(lastqueried != null)
            {
                if(lastqueried.longValue() == SQL_ERROR)
                {
                    Logger.WARN("Cannot determine when the last query for this url was. Allowing new query: "+url);
                }
                else
                {
                    long oneDayAgo = System.currentTimeMillis() - ONE_DAY;
                    if(lastqueried > oneDayAgo)
                    {
                        Logger.INFO( "This query was perormed in the last 24 hours ("+(Config.log_sdf.format(new Date(lastqueried)))+"), skipping re-query until 24 hours has passed.");
                        skip++;
                        return false;
                    }
                }
            }


            trackApiQuery(url);
            apiQueryCount++;
            Document xml = tools.getXMLFromURL(new java.net.URL(url));
            
            if(xml == null)
            {
                Logger.WARN("Failed to retrieve XML from: "+url);
                fail++;
                return false;
            }
            if(xml.hasRootElement())
            {
                List<Element> matches = xml.getRootElement().getChildren("Video");
                if(matches.isEmpty())
                {
                    Logger.INFO("No matching videos were found from query: "+ url);
                    fail++;
                    return false;
                }                
            }
            else
            {
                Logger.WARN("XML found at "+ url +" does not have a root element");
                fail++;
                return false;
            }

            boolean successful = createNFO(xml,nfoFile);
            if(successful) success++;
            else fail++;

            return successful;            
        }
        catch(Exception x)
        {
            Logger.ERROR( "General error while parsing files: "+ x,x);
            return false;
        }
    }

    public static String cleanMusicVideoLabel(String source)
    {
        if(!valid(source)) return "";

        source = source.replace("&#39;", "'");//catch single quote replacement
        
        //trim off any featuring artists or other meta data
        String[] feats = new String[] {"feat", "ft", "featuring", "with", "remix"};
        String[] splitters = new String[] {"",":", "-", ".", "(", "["};
        for(String feat : feats)
        {
            for(String s : splitters)
            {
                //check if it ends with or begins with the suffix
                feat = (feat + s + " ").toLowerCase();
                if(source.toLowerCase().contains(feat))
                {
                    source = source.substring(0, source.toLowerCase().indexOf(feat));
                    return source.trim();
                }

                feat = (" " + s + feat).toLowerCase();
                if(source.toLowerCase().contains(feat))
                {
                    source = source.substring(0, source.toLowerCase().indexOf(feat));
                    return source.trim();
                }
            }
        }
        return source;//no change        
    }

    public static boolean saveImage(String imageUrl, File destinationFile)
    {
        try
        {
		URL url = new URL(imageUrl);
		InputStream is = url.openStream();
		OutputStream os = new FileOutputStream(destinationFile);

		byte[] b = new byte[2048];
		int length;

		while ((length = is.read(b)) != -1) {
			os.write(b, 0, length);
		}

		is.close();
		os.close();
                return true;
        }
        catch(Exception x)
        {
            Logger.WARN("Failed to save image: "+ imageUrl +" to "+ destinationFile,x);
            return false;
        }
    }
}