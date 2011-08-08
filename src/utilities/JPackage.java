/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package utilities;

import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author bvidovic
 */
public class JPackage {

    String packagename;
    List<JDownload> downloads = new ArrayList<JDownload>();
    double percentComplete = 0.0;

    public JPackage(String name)
    {
        this.packagename = name;
    }
    public void addDownload(JDownload download)
    {
        downloads.add(download);
    }
    public List<JDownload> getDownloads()
    {
        return downloads;
    }
     public void setPercentComplete(double percentComplete)
    {
        this.percentComplete = percentComplete;
    }
    public double percentCompelte()
    {
        return percentComplete;
    }
    public String getName()
    {
        return packagename;
    }
    public String getId()
    {
        String s = packagename;
        for(JDownload d : downloads)
        {
            s += Constants.DELIM + d.getPathToDownloadedFile();
        }
        return s;
    }
}
