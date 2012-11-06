package db;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import utilities.Config;
import utilities.Constants;
import utilities.Param;
import static utilities.Constants.*;

public class Database
{    
    Connection conn = null;
    boolean connectionClosed;    
    int secondsBeforeConnTimeout;    
    int PORT;
    final int MAX_SECONDS_FOR_STATEMENT_EXECUTION = 20;//after this many seconds, the stmt will be forced closed
    String MYSQL_UN, MYSQL_PW, DATABASE_NAME, SQL_SERVER;
    boolean isMySQL, isSQLite;

    public Database(String type, String dbPath)
    {//sqlite constructor only needs db path
        this(type,dbPath,null,null,null,-1);
    }
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
            Logger.ERROR( "Error instantiating DBConnection: "+x,x);
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
            Logger.ERROR("Cannot determine if Database is connected: "+x,x);
            return false;
        }
    }    
     

    private void setSQLLiteConnection()
    {        
        String connectionPath = "jdbc:sqlite:"+DATABASE_NAME;
        try
        {
            Class.forName("org.sqlite.JDBC");//initialize the class
            conn = DriverManager.getConnection(connectionPath);
            if(conn == null) throw new Exception("No connection...");            
        }
        catch(Exception x)
        {            
            Logger.ERROR("Failed to get SQL Lite DB Connection to : "+ connectionPath,x);
            conn = null;
        }                
    }

    private void setMySQLConnection()
    {        
        //get a MySQL standard connection
        try
        {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            String s = "jdbc:mysql://"+SQL_SERVER+":"+PORT+"/"+DATABASE_NAME+"?user="+MYSQL_UN+"&password="+MYSQL_PW;
            Logger.DEBUG( "Setting mysql connections using: "+ s);
            conn = DriverManager.getConnection(s);
        }
        catch (Exception ex)
        {
            Logger.ERROR( "MYSQL connection exception: " + ex.getMessage(),ex);
            conn = null;
        }        
    }

    private PreparedStatement prepared_statement = null;//single statement allowed
    private boolean statementAvail = true;//init
    long getStatementStart=0;
    public synchronized PreparedStatement getStatement(String preparedStmtSql)
    {
                                       
        final long sleepTime = 10;//check every x ms
        getStatementStart = System.currentTimeMillis();
        while(true)
        {            
            if(conn == null || connectionClosed){
                Logger.ERROR("Connection has been closed and a statement cannot be created! Expect DB errors.");
                return null;
            }

            //if(!isMySQL)
            {
                if(!statementAvail)//check if the statement has been released, but closeStatement was never called (i.e. an error occured)
                {
                    statementAvail = (prepared_statement == null);
                    if(!statementAvail) try{statementAvail = prepared_statement.isClosed();}catch(Exception ignored){}
                }
            }
            
            if(statementAvail/* || isMySQL*/)//mysql can use concurrent statements, SQLite is limited to 1 at a time
            {
                try
                {                    
                    prepared_statement = conn.prepareStatement(preparedStmtSql);
                    return prepared_statement;                                        
                }
                catch(Exception x)
                {
                    Logger.ERROR( "Failed to create SQL statement: "+ x,x);
                    return null;
                }
            }
            try{Thread.sleep(sleepTime);}catch(Exception x){}//wait for statement to be released
            long secondWaited = (System.currentTimeMillis() - getStatementStart) / 1000;
            if(secondWaited > MAX_SECONDS_FOR_STATEMENT_EXECUTION)
            {
                Logger.WARN( "Have waited " + (secondWaited) +" seconds for statement to finish. Will force it closed now.");
                closeStatement();
            }
        }
    }
    public synchronized void  closeStatement()
    {        
        try
        {
            if(prepared_statement != null) prepared_statement.close();
            prepared_statement = null;
        }
        catch(Exception x)
        {
            //ignore if it fails to close
            Logger.WARN( "Failed to close statement properly. This may lead to DB inconsistencies...",x);
        }
        finally
        {
            statementAvail = true;
        }
    }
    
    public void setParams(List<Param> params, PreparedStatement stmt)
    {
        if(params == null || params.isEmpty()){            
            return;
        }///nothign to set
        int i = 1;
        try
        {
            for(Param p : params)
            {
                switch(p.type)
                {
                    case INT:
                        stmt.setInt(i, p.param == null ? null : ((Integer)p.param)); break;
                    case STRING:
                        stmt.setString(i, p.param == null ? null : ((String)p.param)); break;
                    case DOUBLE:
                        stmt.setDouble(i, p.param == null ? null : ((Double)p.param)); break;
                    case TIMESTAMP:
                        stmt.setTimestamp(i, p.param == null ? null : ((java.sql.Timestamp)p.param)); break;
                    default: 
                    {
                        Logger.ERROR( "Unknown param type: "+p.type+" for param: "+p.param+". Cannot set parameter");
                        stmt.setObject(i, p.param);//nulls may not work here depending on database
                    }
                }
                //Logger.DEBUG( "Set param "+ i +" to "+ p.param);
                i++;
            }
        }
        catch(Exception x)
        {
            Logger.ERROR( "Failed to set parameters for PreparedStatement: "+ x,x);            
        }
    }
    
    public ResultSet executeQuery(String preparedStmtSql, List<Param> params)
    {
        PreparedStatement stmt = (PreparedStatement) getStatement(preparedStmtSql);
        setParams(params, stmt);
        try
        {
            return stmt.executeQuery();//must be closed externally
        }
        catch(Exception x)
        {
            Logger.ERROR( "Failed to executeQuery for prepared statement: "+ x,x);
            return null;
        }
    }

    public synchronized List<String> getStringList(String sql, List<Param> params)
    {
        ArrayList list = new ArrayList<String>();
        try
        {
            PreparedStatement stmt = (PreparedStatement) getStatement(sql);
            setParams(params, stmt);
            ResultSet rs = stmt.executeQuery();
            while(rs.next()) list.add(rs.getString(1));
            rs.close();            
        }
        catch(Exception x)
        {
            Logger.ERROR( "Could not execute query: " + sql, x);
        }
        finally
        {
            closeStatement();
        }
        return list;
    }

    public synchronized String getSingleString(String sql, List<Param> params)
    {
        String str = null;
        try
        {            
            PreparedStatement stmt = (PreparedStatement) getStatement(sql);
            setParams(params, stmt);
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) str = rs.getString(1);
            rs.close();            
        }
        catch(Exception x)
        {
            Logger.ERROR( "Could not execute query: " + sql, x);
        }
        finally
        {
            closeStatement();
        }
        return str;
    }
    public synchronized int getSingleInt(String sql, List<Param> params)
    {
        int id = -1;//default is nothing if found from query
        try
        {  
            PreparedStatement stmt = (PreparedStatement) getStatement(sql);
            setParams(params, stmt);
            ResultSet rs = stmt.executeQuery();
            if(rs.next()) id = rs.getInt(1);
            rs.close();            
            return id;
        }
        catch(Exception x)
        {
            Logger.ERROR( "Could not execute query: " + sql, x);
            return SQL_ERROR;
        }
        finally
        {
            closeStatement();
        }
    }
    
    public synchronized Long getSingleTimestamp(String sql, List<Param> params)
    {
        Long timestamp = null;//default null is nothing if found in sql query
        try
        {            
            PreparedStatement stmt = (PreparedStatement) getStatement(sql);
            setParams(params, stmt);
            ResultSet rs = stmt.executeQuery();
            if(rs.next())
            {
                java.sql.Timestamp sqlTimestamp = rs.getTimestamp(1);
                if(sqlTimestamp != null )timestamp = sqlTimestamp.getTime();
            }
            rs.close();            
        }
        catch(Exception x)
        {
            Logger.ERROR( "Could not execute query: " + sql, x);
            return (long) SQL_ERROR;
        }
        finally
        {
            closeStatement();
        }
        return timestamp;
    }

    public synchronized int executeMultipleUpdate(String sql, List<Param> params)
    {
        int rowsUpdated = 0;//dfault # rows updated
        try
        {
            //Config.log(Config.DEBUG, sql);
            PreparedStatement stmt = (PreparedStatement) getStatement(sql);
            setParams(params, stmt);
            rowsUpdated = stmt.executeUpdate();
        }
        catch(Exception x)
        {
            Logger.ERROR( "Cannot execute update (rows updated = " + rowsUpdated+"): " + sql,x);
            rowsUpdated = SQL_ERROR;
        }
        finally
        {
            closeStatement();
        }
         return rowsUpdated;
    }

    public synchronized boolean executeSingleUpdate(String sql,List<Param> params)
    {
        boolean success = false;//dfault
        int rowsUpdated = 0;
        try
        {
            PreparedStatement stmt = (PreparedStatement) getStatement(sql);
            setParams(params, stmt);
            rowsUpdated = stmt.executeUpdate();
            if(rowsUpdated == 1)
            {
                success = true;
            }            
        }
        catch(Exception x)
        {
            Logger.ERROR( "Cannot execute update (rows updated = " + rowsUpdated+"): " + sql,x);
            //TODO: return an error identifier
        }
        finally
        {
            closeStatement();
        }

        return success;
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
                Logger.DEBUG( "Error closing DB connection: " + t);                
            }
            finally
            {
                conn = null;
                connectionClosed = true;
            }
        }
    }
}