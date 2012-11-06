
package utilities;

import static utilities.Constants.*;
public class ExternalCompression implements Runnable
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
        Config. Logger.DEBUG( "External compression thread started...");
        try
        {
            Logger.INFO( "Executing .cmd file: \"" + cmdFile+"\"");
            Logger.INFO( "Executing command: "+command);

            ProcessBuilder pb = new ProcessBuilder("\""+cmdFile+"\"");
            pb.redirectErrorStream();
            java.lang.Process pr = pb.start();//dont wait for anything
            
        }
         catch(Exception e)
         {
            Logger.ERROR( "External compression command failed. Please check your command: " + command,e);
         }

         Logger.INFO( "External compression has been started.");
    }
}
