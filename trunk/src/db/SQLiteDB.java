
package db;

import java.sql.*;
import utilities.Config;


public class SQLiteDB extends Database
{
    public SQLiteDB(String DBPath)
    {
        super(SQL_LITE, DBPath);
    }
    
    public synchronized boolean hasColumn(String tablename, String columnName)
    {
        String sql = "PRAGMA table_info("+tablename+")";
        boolean hasColumn = false;
        Statement stmt = null;
        try
        {
            stmt = conn.createStatement();            
            ResultSet rs = stmt.executeQuery(sql);
            while(rs.next())
            {
                String nextColumnName = rs.getString("name");
                if(nextColumnName.equalsIgnoreCase(columnName))
                {
                    hasColumn =true;
                    break;
                }
            }
            rs.close();
            return hasColumn;
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to determine if table \""+tablename+"\" has column named \""+columnName+"\"",x);
            return false;
        }
        finally
        {
            if(stmt != null)
                try{stmt.close();}catch(Exception ignored){}
        }
    }
}
