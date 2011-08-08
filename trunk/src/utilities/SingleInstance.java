package utilities;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.UnknownHostException;

public class SingleInstance implements Constants
{    
    public static boolean isSingleInstance()
    {
        try
        {
            InetAddress localhost = InetAddress.getByAddress(new byte[] {127, 0, 0, 1});
            Config.SINGLE_INSTANCE_SOCKET = new ServerSocket(Config.SINGLE_INSTANCE_PORT,10,localhost);
            Config.log(INFO, "Single instance = true");
            return true;
        }
        catch(UnknownHostException x)
        {
            Config.log(ERROR, "Error checking for single instance. Will allow program to run.",x);
            return true;
        }
        catch(IOException x)
        {
            Config.log(ERROR, "This program is already running. Only one instance is allowed to run at a time. (port "+Config.SINGLE_INSTANCE_PORT+" is already bound): "+ x,x);
            return false;
        }
    }
}