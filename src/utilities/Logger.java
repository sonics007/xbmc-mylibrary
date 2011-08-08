package utilities;

import java.io.BufferedWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;


/*
 * Queued, threaded logger.
 */
public class Logger implements Runnable, Constants
{
    BlockingQueue<String> logQueue = new ArrayBlockingQueue<String>(1000);
    private boolean CAN_STOP = false;
    private boolean STOP = false;
    BufferedWriter currentLog, historicalLog;
    private int LINE_BUFFER_SIZE = 15;//accumulate this many lines before writing to file
    private int MAX_WAIT_SECONDS = 3;//if there are lines in the queue for x seconds, just write them already
    public Logger(BufferedWriter currentLog, BufferedWriter historicalLog)
    {
        this.currentLog = currentLog;
        this.historicalLog = historicalLog;
        Thread loggingThread = new Thread(this);
        loggingThread.start();
    }
    
    public void queueLine(String s)
    {        
        try
        {
            logQueue.put(s);
        }
        catch(Exception x)
        {
            System.out.println("Failed to queue for logging: "+ s);
            x.printStackTrace();
        }
    }

    /*
     * Once this is called, the Loggeer will write all remaining lines, then exit
     */
    public void canStop()
    {
        CAN_STOP = true;
    }

    long lastWrite = System.currentTimeMillis();
    public void run()
    {
        while(!STOP)
        {            
            List<String> lines = new ArrayList<String>();
            try
            {                
                while(lines.size()<LINE_BUFFER_SIZE)//accumulate x many lines before writing to file
                {                    
                    String line = logQueue.poll(1000, TimeUnit.MILLISECONDS);                                                            
                    if(line==null)
                    {
                        if(CAN_STOP)//if can stop, it means that the queue is not being fed anymore. so we are at the end
                        {
                            STOP = true;
                            break;
                        }                        
                    }
                    else
                    {
                        lines.add(line);                        
                    }

                    long now = System.currentTimeMillis();
                    long secondsSinceLastCheck = (now-lastWrite) / 1000;
                    if(secondsSinceLastCheck >= MAX_WAIT_SECONDS)
                    {
                        if(!lines.isEmpty())
                            break;//write the lines since we've waited enough time
                    }
                }
            }
            catch(InterruptedException x)
            {
                Config.log(WARNING, "Interrupted while logging lines from queue. Cannot continue.",x);
                canStop();
                break;
            }

            if(!lines.isEmpty())
            {
                StringBuilder allLines = new StringBuilder();
                for(String line : lines)                
                    allLines.append(line);                
                writeToLogFiles(allLines.toString());
            }
        }//end while !STOP
        close();        
    }

    private void writeToLogFiles(String s)
    {
        lastWrite = System.currentTimeMillis();
        try
        {
            if(currentLog != null)
            {
                currentLog.write(s);
                currentLog.flush();
            }
            
            if(historicalLog != null)
            {
                historicalLog.write(s);
                historicalLog.flush();
            }
        }
        catch(Exception x)
        {
            System.out.println("Failed not log: " + x);
            x.printStackTrace();
        }
    }

    private void close()
    {
        try
        {
            if(currentLog != null)
                currentLog.close();
        }catch(Exception ignored){}
        try
        {
            if(historicalLog != null)
                historicalLog.close();
        }catch(Exception ignored){}
    }
}