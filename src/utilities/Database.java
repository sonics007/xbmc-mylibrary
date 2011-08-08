package utilities;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database implements Constants
{    
    Connection conn = null;
    boolean connectionClosed;    
    int secondsBeforeConnTimeout;    
    int PORT;
    final int MAX_SECONDS_FOR_STATEMENT_EXECUTION = 20;//after this many seconds, the stmt will be forced closed
    String MYSQL_UN, MYSQL_PW, DATABASE_NAME, SQL_SERVER;
    boolean isMySQL, isSQLite;

    public Database(String type, String schemaOrDBPath, String host, String un, String pw, int port)
    {
        try
        {
            isMySQL = MYSQL.equals(type);
            isSQLite = SQL_LITE.equals(type);
            this.MYSQL_UN = un;
            this.MYSQL_PW = pw;
            this.SQL_SERVER = host;
            this.DATABASE_NAME = schemaOrDBPath;//for MySQL, the schema name, for SQL Lite, the path to the .db file
            this.PORT = port;
            secondsBeforeConnTimeout = SECOND_BEFORE_DB_CONNECTION_FORCE_CLOSED;//X SEC TIMEOUT            

            if(type.equals(MYSQL))
                setMySQLConnection();
            else
                setSQLLiteConnection();
            connectionClosed = false;//init                        
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Error instantiating DBConnection: "+x,x);
        }
    }
    public boolean isMySQL()
    {
        return isMySQL;
    }
    public boolean isSQLite()
    {
        return isSQLite;
    }

    public boolean isConnected()
    {
        try
        {
            return conn != null && !conn.isClosed() && !connectionClosed;
        }
        catch(java.sql.SQLException x)
        {
            Config.log(ERROR,"Cannot determine if Database is connected: "+x,x);
            return false;
        }
    }

    /*
    public Connection getConnection()
    {     
        return conn;
    }
     * */
     

    private void setSQLLiteConnection()
    {
        SQLLiteConnection sqlLiteConn = new SQLLiteConnection(DATABASE_NAME);
        conn = sqlLiteConn.getConnection();
    }

    private void setMySQLConnection()
    {        
        //get a MySQL standard connection
        try
        {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            String s = "jdbc:mysql://"+SQL_SERVER+":"+PORT+"/"+DATABASE_NAME+"?user="+MYSQL_UN+"&password="+MYSQL_PW;
            Config.log(DEBUG, "Setting mysql connections using: "+ s);
            conn = DriverManager.getConnection(s);
        }
        catch (Exception ex)
        {
            Config.log(ERROR, "MYSQL connection exception: " + ex.getMessage(),ex);
            conn = null;
        }        
    }

    private Statement stmt = null;//single statement allowed
    private boolean statementAvail = true;//init
    public synchronized Statement getStatement()
    {
        return getStatement(STATEMENT, null);//regular statement
    }
    long getStatementStart=0;
    public synchronized Statement getStatement(int type, String sql)
    {               
        final long sleepTime = 10;//check every x ms
        getStatementStart = System.currentTimeMillis();
        while(true)
        {            
            if(conn == null || connectionClosed){
                Config.log(ERROR,"Connection has been closed and a statement cannot be created! Expect DB errors.");
                return null;
            }

            //if(!isMySQL)
            {
                if(!statementAvail)//check if the statement has been released, but closeStatement was never called (i.e. an error occured)
                {
                    statementAvail = (stmt == null);
                    if(!statementAvail) try{statementAvail = stmt.isClosed();}catch(Exception ignored){}
                }
            }
            
            if(statementAvail/* || isMySQL*/)//mysql can use concurrent statements, SQLite is limited to 1 at a time
            {
                try
                {
                    if(type == STATEMENT) stmt = conn.createStatement();
                    else if(type == PREPARED_STATEMENT) stmt = conn.prepareStatement(sql);
                    return stmt;
                }
                catch(Exception x)
                {
                    Config.log(ERROR, "Failed to create SQL statement: "+ x,x);
                    return null;
                }
            }
            try{Thread.sleep(sleepTime);}catch(Exception x){}//wait for statement to be released
            long secondWaited = (System.currentTimeMillis() - getStatementStart) / 1000;
            if(secondWaited > MAX_SECONDS_FOR_STATEMENT_EXECUTION)
            {
                Config.log(WARNING, "Have waited " + (secondWaited) +" seconds for statement to finish. Will force it closed now.");
                closeStatement();
            }
        }
    }
    public synchronized void  closeStatement()
    {        
        try
        {
            if(stmt != null && !stmt.isClosed()) stmt.close();
            stmt = null;
        }
        catch(Throwable t)
        {
            //ignore if it fails to close
            //Config.log(WARNING, "Failed to close statement properly. This may lead to DB inconsistencies...");
        }
        finally
        {
            statementAvail = true;
        }
    }

    public synchronized List<String> getStringList(String sql)
    {
        ArrayList list = new ArrayList<String>();
        try
        {
            ResultSet rs = getStatement().executeQuery(sql);
            while(rs.next()) list.add(rs.getString(1));
            rs.close();
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR, "Could not execute query: " + sql, x);
        }
        finally
        {
            closeStatement();
        }
        return list;
    }

    public synchronized String getSingleString(String sql)
    {
        String str = null;
        try
        {            
            ResultSet rs = getStatement().executeQuery(sql);
            if(rs.next()) str = rs.getString(1);
            rs.close();            
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR, "Could not execute query: " + sql, x);
        }
        finally
        {
            closeStatement();
        }
        return str;
    }
    public synchronized int getSingleInt(String sql)
    {
        int id = -1;//default is nothing if found from query
        try
        {            
            ResultSet rs = getStatement().executeQuery(sql);
            if(rs.next()) id = rs.getInt(1);
            rs.close();            
            return id;
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR, "Could not execute query: " + sql, x);
            return SQL_ERROR;
        }
        finally
        {
            closeStatement();
        }
    }
    
    public synchronized Long getSingleTimestamp(String sql)
    {
        Long timestamp = null;//default null is nothing if found in sql query
        try
        {            
            ResultSet rs = getStatement().executeQuery(sql);
            if(rs.next())
            {
                java.sql.Timestamp sqlTimestamp = rs.getTimestamp(1);
                if(sqlTimestamp != null )timestamp = sqlTimestamp.getTime();
            }
            rs.close();            
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR, "Could not execute query: " + sql, x);
            return (long) SQL_ERROR;
        }
        finally
        {
            closeStatement();
        }
        return timestamp;
    }

    public synchronized int executeMultipleUpdate(String sql)
    {
        int rowsUpdated = 0;//dfault # rows updated
        try
        {
            //Config.log(Config.DEBUG, sql);
            rowsUpdated = getStatement().executeUpdate(sql);
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR, "Cannot execute update (rows updated = " + rowsUpdated+"): " + sql,x);
            rowsUpdated = SQL_ERROR;
        }
        finally
        {
            closeStatement();
        }
         return rowsUpdated;
    }

    public synchronized boolean executeSingleUpdate(String sql)
    {
        boolean success = false;//dfault
        int rowsUpdated = 0;
        try
        {
            //Config.log(Config.DEBUG, sql);
            rowsUpdated = getStatement().executeUpdate(sql);
            if(rowsUpdated == 1)
            {
                success = true;
            }            
        }
        catch(Exception x)
        {
            Config.log(Config.ERROR, "Cannot execute update (rows updated = " + rowsUpdated+"): " + sql,x);
            //TODO: return an error identifier
        }
        finally
        {
            closeStatement();
        }

        return success;
    }

    public synchronized boolean hasColumn(String tablename, String columnName)
    {
        String sql = "PRAGMA table_info("+tablename+")";
        boolean hasColumn = false;
        try
        {
            ResultSet rs = getStatement().executeQuery(sql);
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
            closeStatement();
        }
    }
    public void close()
    {
        if(conn != null)
        {
            try
            {
                if(!conn.isClosed()) conn.close();                            
            }
            catch(Throwable t)
            {                
                Config.log(DEBUG, "Error closing DB connection: " + t);                
            }
            finally
            {
                conn = null;
                connectionClosed = true;
            }
        }
    }
}