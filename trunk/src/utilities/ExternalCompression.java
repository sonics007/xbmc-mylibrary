
package utilities;


public class ExternalCompression implements Constants, Runnable
{
    String command;
    String cmdFile;
    
    Thread compressionStarter;
   public ExternalCompression(String cmdFile, String command)
   {
        this.cmdFile = cmdFile;
        this.command = command;
        compressionStarter = new Thread(this);        
   }
   
   public void start()
   {
       compressionStarter.start();
   }

    public void run()
    {
        Config. log(Config.DEBUG, "External compression thread started...");
        try
        {
            Config. log(Config.INFO, "Executing .cmd file: \"" + cmdFile+"\"");
            Config. log(Config.INFO, "Executing command: "+command);

            ProcessBuilder pb = new ProcessBuilder("\""+cmdFile+"\"");
            pb.redirectErrorStream();
            java.lang.Process pr = pb.start();//dont wait for anything
            
        }
         catch(Exception e)
         {
            Config. log(Config.ERROR, "External compression command failed. Please check your command: " + command,e);
         }

         Config. log(Config.INFO, "External compression has been started.");
    }
}
