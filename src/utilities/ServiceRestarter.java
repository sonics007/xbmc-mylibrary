

package utilities;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class ServiceRestarter implements Constants
{

    String serviceName;
    public ServiceRestarter(String serviceName)
    {
        this.serviceName = serviceName;
    }
    public boolean restart()
    {
        String cmd = "net stop \""+ serviceName+"\"";
        List<String> lines = execute(cmd);
        boolean successfulStop = false;
        if(lines != null)
        for(String line : lines)
        {           
            line = line.trim();
            if(line.toLowerCase().contains(" service was stopped successfully."))
            {
                successfulStop = true;
                Config.log(INFO, "Successfully stopped service: "+ serviceName);
                break;
            }
            if(line.toLowerCase().contains(" service is not started."))
            {
                successfulStop = true;
                Config.log(INFO, "The service isn't currently running. Will start it: "+ serviceName);
                break;
            }
            
        }
        if(!successfulStop)
        {
            Config.log(WARNING, "The service could not be successfully stopped. Will not attempt to start it. Output from command ("+cmd+") = " + lines);
            return false;
        }

        cmd = "net start \""+serviceName+"\"";
        lines = execute(cmd);
        boolean successfulStart = false;
        if(lines != null)
        for(String line : lines)
        {
            if(tools.valid(line))
            {
                line = line.trim();
                if(line.toLowerCase().contains(" service was started successfully."))
                {
                    successfulStart = true;
                    Config.log(INFO, "Successfully started service: "+ serviceName);
                    break;
                }
            }
        }
        if(!successfulStart)        
            Config.log(WARNING, "The service could not be successfully started. Output from command ("+cmd+") = "+ lines);
        else
        {
            int minuteWait = 5;
            Config.log(INFO, "Waiting "+minuteWait+" minutes for service to start completely and for PlayOn to populate all content...");
            try{Thread.sleep(minuteWait * 60 * 1000);}catch(Exception ignored){}//give service a chance to warm up
        }        
        
        return successfulStart;
    }


    private List<String> execute(String command)
    {
        try
        {
            List<String> lines = new ArrayList<String>();
            Process process = Runtime.getRuntime().exec(command);
            InputStream is = process.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);
            String line;
            while ((line = br.readLine()) != null) {
              if(tools.valid(line))
                lines.add(line);
            }
            return lines;
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed while executing: "+ command,x);
            return null;
        }
    }
}
