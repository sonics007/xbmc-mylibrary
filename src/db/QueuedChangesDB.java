package db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import utilities.Config;
import utilities.Constants;
import utilities.QueuedChange;
import static utilities.Constants.*;

/**
 *
 * @author bvidovic
 */
public class QueuedChangesDB extends SQLiteDB
{    
    
    public QueuedChangesDB(String dbPath)
    {
        super(dbPath);
    }
    
    public List<QueuedChange> getQueuedChanges()
    {
        List<QueuedChange> changes = new ArrayList<QueuedChange>();
        String sql = "SELECT id, dropbox_location, video_type, meta_data_type, value "
                + "FROM QueuedChanges "
                + "WHERE status = ?";
        try
        {
            
            PreparedStatement stmt = getStatement(sql);
            stmt.setString(1, QUEUED);
            
            ResultSet rs = stmt.executeQuery();
            while(rs.next())
            {
                changes.add(new QueuedChange(
                        rs.getInt("id"), 
                        rs.getString("dropbox_location"), 
                        rs.getString("video_type"), 
                        rs.getString("meta_data_type"), 
                        rs.getString("value")));
            }
            rs.close();             
        }
        catch(Exception x)
        {
            Logger.ERROR( "Failed to get queued meta data changed from SQLite DB: "+x,x);
        }
        finally
        {
            closeStatement();
        }
        return changes;
    }
    
}
