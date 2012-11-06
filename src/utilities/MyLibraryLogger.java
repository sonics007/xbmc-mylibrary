/*
Copyright (C) 2012 Brady Vidovic

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package utilities;

import java.io.FileFilter;
import btv.logger.BTVLogLevel;
import btv.logger.BTVLogger;
import btv.logger.BTVLoggerOptions;
import btv.tools.BTVTools;
import java.io.File;
import org.apache.commons.io.FileUtils;
import static utilities.Constants.*;

/**
 * 
 * @author Brady Vidovic
 */
public class MyLibraryLogger extends BTVLogger{
    
    public MyLibraryLogger(Class klass, BTVLoggerOptions loggerOptions) 
    {
        super(klass,loggerOptions,btv.tools.BTVTools.getBaseDir(klass),Constants.PROGRAM_NAME);//default base dir
    }
    
    @Override
    final public synchronized void log(BTVLogLevel logLevelOfThisMessage, String message, Exception x)
    {
        //prepend short description to message
        if(!BTVTools.valid(Config.shortLogDesc))
            Config.shortLogDesc = " ";
                                                                
        Config.shortLogDesc = tools.tfl(Config.shortLogDesc, Config.SHORT_DESC_LENGTH);
        message = Config.shortLogDesc +" "+ message;
        
        //obscure tvdb id
        if(Config.OBSCURE_TVDB_KEY_IN_LOG && message.contains(Config.TVDB_API_KEY)) 
            message = message.replace(Config.TVDB_API_KEY, Config.TVDB_API_KEY_OBSCURED);   

        super.log(logLevelOfThisMessage, message, x);
    }
    
    @Override
    public void close(){
        Logger.INFO("Closing logs. Deleting expired logs and ALL historical DEBUG logs.");        
        super.close(false);//default close (no reset)
        
        //For MyLibrary, delete all DEBUG historical logs because they will get too large (only keep current one)
        try{
            File logDir  = new File(BASE_DIR.getAbsolutePath()+SEP+"logs");
            File[] debugLogs = logDir.listFiles(new FileFilter() {

                @Override
                public boolean accept(File f) {//only .log files
                    return f.isFile() && f.getAbsolutePath().toUpperCase().endsWith("DEBUG.LOG");
                }
            });
            Logger.DEBUG("Will delete "+ debugLogs.length +" debug logs found in logDir: "+ logDir);
            for(File f : debugLogs)
            {
                if(!f.delete())
                    Logger.WARN("Failed to delete DEBUG file: "+ f);
                else 
                    Logger.DEBUG("Deleted "+ f);
            }
            
        }catch(Exception x){
            Logger.ERROR("Error deleting old log files: "+x,x);
        }
    }        
}