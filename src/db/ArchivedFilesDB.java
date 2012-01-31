/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package db;
import java.io.File;
import java.sql.*;
import java.util.List;
import utilities.ArchivedFile;
import utilities.Config;
import utilities.Param;
import utilities.Source;
import utilities.Subfolder;
import utilities.XBMCFile;
import utilities.tools;
/**
 *
 * @author bvidovic
 */
public class ArchivedFilesDB extends SQLiteDB
{
    public ArchivedFilesDB(String dbPath)
    {
        super(dbPath);
    }
    
    public ArchivedFile getArchivedFileByLocation(String dropboxLocation)
    {
    //check if this file already exists in the tracker (unique index on dropbox_location)
        String sql = 
                "SELECT id, source_name, original_path, missing_since, missing_count, video_type, title, series, artist, episode_number, season_number, year, is_tvdb_lookup "
                + "FROM ArchivedFiles "
                + "WHERE dropbox_location = ?";

        int id =-1, MissingCount =-1, episodeNumber = -1, seasonNumber=-1, year=-1;
        String sourceName=null, originalPath=null, videoType=null, title=null, series=null, artist=null;
        java.sql.Timestamp MissingSince =null;
        boolean isTVDBLookup = false;
        ArchivedFile archivedFile = null;
        try
        {            
            PreparedStatement stmt = getStatement(sql);
            stmt.setString(1, dropboxLocation);
            ResultSet rs = stmt.executeQuery();
            if(rs.next())
            {
                id = rs.getInt("id");
                sourceName = rs.getString("source_name");
                originalPath = rs.getString("original_path");
                MissingSince = rs.getTimestamp("missing_since");                
                MissingCount = rs.getInt("missing_count");
                //the data about the video:
                videoType = rs.getString("video_type");
                title = rs.getString("title");
                series = rs.getString("series");
                artist = rs.getString("artist");
                episodeNumber = rs.getInt("episode_number"); if(rs.wasNull()) episodeNumber = -1;
                seasonNumber = rs.getInt("season_number"); if(rs.wasNull()) seasonNumber = -1;
                year = rs.getInt("year"); if(rs.wasNull()) year = -1;
                isTVDBLookup = rs.getInt("is_tvdb_lookup") == 1;//1=true, 0=false
                archivedFile = new ArchivedFile(id, sourceName, originalPath, MissingSince==null ? null : MissingSince.getTime(), 
                        MissingCount, videoType, title, series, artist, episodeNumber, seasonNumber, year, isTVDBLookup);
            }            
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to query for archived file at: "+ dropboxLocation,x);
            return null;
        }
        finally
        {
            closeStatement();            
        }
        return archivedFile;
    }
    
    public XBMCFile getVideoWithMetaDataFromDB(String preparedStmtSQL, List<Param> params)
    {
        XBMCFile video = null;
        try
        {
            PreparedStatement stmt = getStatement(preparedStmtSQL);
            setParams(params,stmt);
            
            ResultSet rs = stmt.executeQuery();

            if(rs.next())
            {
                video = new XBMCFile(rs.getString("original_path"));
                video.setType(rs.getString("video_type"));
                video.setTitle("title");
                video.setHasBeenLookedUpOnTVDB(rs.getInt("is_tvdb_lookup") == 1);
                
                if(video.isTvShow())
                {
                    video.setSeries(rs.getString("series"));
                    video.setSeasonNumber(rs.getInt("season_number"));
                    video.setEpisodeNumber(rs.getInt("episode_number"));
                }
                else if(video.isMovie())
                {
                    video.setYear(rs.getInt("year")); if(rs.wasNull()) video.setYear(-1);//year is optional
                }
                else if(video.isMusicVideo())
                {
                    video.setArtist(rs.getString("artist"));
                }
                else
                {
                    Config.log(WARNING, "Video type (TV/Movie/Music Video) cannot be determined using: "+preparedStmtSQL);
                    return null;
                }
                String dropboxLocation = rs.getString("dropbox_location");//always a .strm out of the database                                
                video.setFinalLocation(dropboxLocation);                                
                   
               Subfolder subf = new Subfolder(new Source("ManualArchive","ManualArchive"), "ManualArchive");//to avoid NPE's, instantiate dummy objects. Can be replaced with real objects
               video.setSubfolder(subf);
            }
            else
            {
                Config.log(WARNING, "No video found in the database using SQL: "+preparedStmtSQL);
                return null;
            }
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to get meta data from database for using SQL: "+ preparedStmtSQL,x);
            return null;
        }
        finally
        {
            closeStatement();
            return video;
        }
    }
    
    public Long getDateArchived(String archivedFile)
    {               
        String sql = "SELECT date_archived "
                + "FROM ArchivedFiles "
                + "WHERE dropbox_location = ?";
        Long dateArchived = getSingleTimestamp(sql,tools.params(archivedFile));

        if(dateArchived != null)
        {
            Config.log(DEBUG, "Found date archived: "+ new Date(dateArchived)+" for archived file: "+ archivedFile);
            return dateArchived;
        }
        else
        {
            File f = new File(archivedFile);
            if(f.exists())
            {
                Config.log(WARNING, "Could not get date archived for file \""+archivedFile+"\". (SQL = "+ sql+"). Will use last modified date of "+ new Date(f.lastModified()));
                return f.lastModified();
            }
            else
            {
                Config.log(ERROR, "Cannot find date archive for file \""+archivedFile+"\" and the file no longer exits.");
                return null;
            }
        }
    }
}
