/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

/**
 *
 * @author bvidovic
 */
public class JDownload
{

    String pathToFile, status;
    double percentComplete = 0.0;
    public JDownload(String pathToFile)
    {
        this.pathToFile = pathToFile;
    }
    public String getStatus()
    {
        return status;
    }
    public void setStatus(String status)
    {
        this.status = status;
    }
    public String getPathToDownloadedFile()
    {
        return pathToFile;
    }
    public void setPercentComplete(double percentComplete)
    {
        this.percentComplete = percentComplete;
    }
    public double percentCompelte()
    {
        return percentComplete;
    }

}
