package utilities;

import java.sql.*;
public class SQLLiteConnection implements Constants
{
     
    Connection conn = null;
    public SQLLiteConnection(String dbPath)
    {
        String connectionPath = "jdbc:sqlite:"+dbPath;
        try
        {
            Class.forName("org.sqlite.JDBC");//initialize the class
            conn = DriverManager.getConnection(connectionPath);
            if(conn == null) throw new Exception("No connection...");            
        }
        catch(Exception x)
        {            
            Config.log(ERROR,"Failed to get SQL Lite DB Connection to : "+ connectionPath,x);
            conn = null;
        }        
    }
    
    public Connection getConnection()
    {
        return conn;
    }

    public static void main(String[] args)
    {
        SQLLiteConnection db = new SQLLiteConnection("C:/test.db");
        db.test();
    }

    private void test()
    {
        try
        {

            Statement stmt = conn.createStatement();
            stmt.executeUpdate("drop table if exists people;");
            stmt.executeUpdate("create table people (name timestamp, occupation);");
            PreparedStatement prep = conn.prepareStatement("insert into people values (?, ?);");
            prep.setString(1, "Gandhi");
            prep.setString(2, "politics");
            prep.addBatch();
            Date d = new Date(System.currentTimeMillis());
            prep.setTimestamp(1,new java.sql.Timestamp(d.getTime()));


            prep.setString(2, "computers");
            prep.addBatch();
            prep.setString(1, "Wittgenstein");
            prep.setString(2, "smartypants");
            prep.addBatch();            
            prep.executeBatch();            
            ResultSet rs = stmt.executeQuery("select name || occupation from people;");
            while (rs.next())
            {
                System.out.println("name = " + rs.getString(1));
                //System.out.println("job = " + rs.getString("occupation"));
            }
            rs.close();
            conn.close();
        }
        catch (Exception ex)
        {
            ex.printStackTrace();
        }
    }
}