
package utilities;

import java.net.*;
import java.util.*;
import java.io.*;
import java.net.URL;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.output.Format;
import org.jdom.output.XMLOutputter;


public class JDownloaderInterface implements Constants
{

    boolean connected;//gets initialzed when loadConfig is called in constructor
    String host;
    Map<String,String> config;
    public JDownloaderInterface(String host)
    {
        if(!host.endsWith("/")) host += "/";
        this.host = host;
        loadConfig();

    }
    public boolean isConnected()
    {
        return connected;
    }
    public boolean startDownloads()
    {
        List<String> response = getRequest("action/start");
        String fullResponse = "";
        for(String s : response)
        {
            if(tools.valid(s))
            {
                if(s.toLowerCase().contains("downloads started"))
                return true;
            }
            fullResponse += s + LINE_BRK;
        }
        Config.log(WARNING, "JDownloader: Failed to start downloads. Full response = "+ LINE_BRK+fullResponse);
        return false;
    
    }
    public boolean queueURL(String url)
    {
        return queueURL(url, true);
    }
    private boolean queueURL(String url,boolean encode)
    {
        try
        {
            
            String urlToUse;
            if(encode)
            {
                Config.log(DEBUG, "URL before encode: "+ url);
                final String http = "http://";
                if(url.toLowerCase().startsWith("http://"))//don't encode the http:// part
                    urlToUse = http+URLEncoder.encode(url.substring(http.length(), url.length()), "UTF-8");
                else//encod the whole thing
                    urlToUse = URLEncoder.encode(url, "UTF-8");
                Config.log(DEBUG, "URL after  encode: "+ urlToUse);
            }
            else 
            {
                urlToUse = url.replace(" ", "+");//it thinks spaces mean multiple URL's, so replace them;
                Config.log(DEBUG, "Not encoding url: " + urlToUse);
            }
            
            List<String> response = getRequest("action/add/links/grabber0/start1/"+urlToUse);
            if(response != null && !response.isEmpty())
            {
                //get all of the response in a single line
                String fullResponse = "";
                for(String s:response)
                    fullResponse += s +" ";
                fullResponse = fullResponse.trim();

                
                if(fullResponse.toLowerCase().contains("link(s) added") &&
                        !fullResponse.toLowerCase().contains("link(s) added. ()"))
                {
                    //links were added and not empty, success
                    Config.log(INFO, "Successfully added link. Response = "+ fullResponse);
                    return true;
                }
                else
                {
                    if(encode)//if this attempt was encoded, attempt an unencoded URL
                    {
                        Config.log(WARNING, "Failed to add link with encoded url ("+urlToUse+"), will try un-encoded now.");
                        return queueURL(url, false);//use original url here
                    }
                    Config.log(WARNING, "Failed to add link ("+urlToUse+"), Response = "+ fullResponse);
                    return false;
                }
            }
            else
            {
                throw new Exception("Invalid response: \""+response+"\"");
            }
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to queue URL in JDownloader: "+x,x);
            return false;
        }
    }
    public Map<String, JPackage> getDownloadPackages()
    {
        try
        {
            String downloadDirectory = getConfig("DOWNLOAD_DIRECTORY");
            if(!tools.valid(downloadDirectory))
                throw new Exception("JDownloader download directory is not valid or cannot be found.");
            if(!downloadDirectory.endsWith(SEP))downloadDirectory+= SEP;
            String strUrl = host + "get/downloads/alllist";
            Config.log(DEBUG, "Getting list of current downloads from: "+ strUrl);
            
            Document xml = tools.getXMLFromURL(new URL(strUrl),false);//false to disable cache
            Element root = xml.getRootElement();

            
            /*
             * Sample XML
              <package package_ETA="~" package_linksinprogress="0" package_linkstotal="4" package_loaded="2.64 GB" package_name="ThthrGys" package_percent="100.00" package_size="2.64 GB" package_speed="0 B" package_todo="0 B">
                  <file file_hoster="megaupload.com" file_name="ThthrGys.01.avi" file_package="ThthrGys" file_percent="100.00" file_speed="0" file_status="" />
                  <file file_hoster="megaupload.com" file_name="ThthrGys.02.avi" file_package="ThthrGys" file_percent="100.00" file_speed="0" file_status="" />
                  <file file_hoster="megaupload.com" file_name="ThthrGys.03.avi" file_package="ThthrGys" file_percent="100.00" file_speed="0" file_status="" />
                  <file file_hoster="megaupload.com" file_name="ThthrGys.04.avi" file_package="ThthrGys" file_percent="100.00" file_speed="0" file_status="" />
              </package>
             */
            Map<String, JPackage> jpackages = new LinkedHashMap<String, JPackage>();
            List<Element> packageElements = root.getChildren("package");
            for(Element packageElem : packageElements)
            {

                String packageName = packageElem.getAttributeValue("package_name");
                JPackage jpackage = new JPackage(packageName);
                double percentComplete = 0.0;
                try{percentComplete = Double.parseDouble(packageElem.getAttributeValue("package_percent"));}
                catch(Exception x){Config.log(WARNING, "Cannot determine percent complete for JDownloader package \""+packageName+"\". Will default to zero. "+ x);}
                jpackage.setPercentComplete(percentComplete);
                
                List<Element> fileElems = packageElem.getChildren("file");
                for(Element fileElem : fileElems)
                {
                    String fileName = fileElem.getAttributeValue("file_name");
                    String status = fileElem.getAttributeValue("file_status");
                    String fullPath = downloadDirectory+fileName;
                    JDownload jdownload = new JDownload(fullPath);
                    jdownload.setStatus(status == null ? "" : status);
                    percentComplete = 0.0;
                    try{percentComplete = Double.parseDouble(fileElem.getAttributeValue("file_percent"));}
                    catch(Exception x){Config.log(WARNING, "Cannot determine percent complete for JDownloader file \""+fullPath+"\". Will default to zero. "+ x);}
                    jdownload.setPercentComplete(percentComplete);
                    jpackage.addDownload(jdownload);                    
                }

                jpackages.put(jpackage.getId(),jpackage);
            }
            return jpackages;
        }
        catch (Exception x)
        {
            Config.log(ERROR, "Failed to get download list from JDownloader: "+x,x);
            return null;
        }


    }
    private String getConfig(String key)
    {
        return config.get(key);
    }
    private void loadConfig()
    {
        config = new HashMap<String, String>();
        try
        {
            List<String> response = getRequest("get/config");
            if(response == null)
            {
                Config.log(ERROR, "Cannot access JDownloader. Please check your configuration.");
                return;
            }
            final String splitter = " = ";
            for(String s : response)
            {
                if(s != null) s = s.replace("<pre>", "").replace("</pre>", "");//comes back a a pre, clean the tags out
                if(tools.valid(s))
                {
                    if(s.contains(splitter))
                    {
                        String[] parts = s.split(splitter);
                        if(parts.length == 2)
                            config.put(parts[0].trim(), parts[1].trim());
                        //else, skip because the param has no value, such as "HTTPSEND_PASS = "
                    }
                    else
                        Config.log(WARNING, "Invalid configuration parameter found for JDownloader: \""+ s+"\"");
                }
            }
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to get config information from JDownloader. Will not be able to use JDownloader...",x);
            config = null;
        }
        finally
        {
            connected = (config != null && !config.isEmpty());
        }

    }

    public List<String> getRequest(String command)
    {
        List<String> lines = new ArrayList<String>();
        String strUrl = host+command;
        try
        {
            if(command.startsWith("/")) command = command.substring(1, command.length());
            URL url = new URL(strUrl);
            Config.log(DEBUG, "Calling: "+ url);
            URLConnection urlConn = url.openConnection();
            BufferedReader in = new BufferedReader(new InputStreamReader(urlConn.getInputStream()));
            while(true)
            {
                String line = in.readLine();
                if(line == null)break;
                lines.add(line);
            }
            in.close();
            return lines;
        }
        catch(Exception x)
        {
            Config.log(ERROR, "Failed to get response from JDownloader at: "+ strUrl,x);
            return null;
        }

    }
}