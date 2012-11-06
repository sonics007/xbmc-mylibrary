
package utilities;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utilities.Constants.*;

public class Exclude
{
    public static void main(String[] xxx)
    {
        Map<String,List<String>> testFilters = new LinkedHashMap<String,List<String>>();
        List<String> excludeStrings = new ArrayList<String>();

        excludeStrings.add("Christian");
        excludeStrings.add("Blues");
        excludeStrings.add("Latino");
        excludeStrings.add("Jazz");
        testFilters.put("contains", excludeStrings);

        boolean exclude = shouldExclude("Vevo/Top 10's/Top 10 Christian & Gospel All Time", testFilters);
        System.out.println("Should exclude ? "+exclude);
    }
    public static Map<String,List<String>> globalExcludes = new LinkedHashMap<String,List<String>>();

    public static void addGlobalExclude(String type, String excludeString)
    {
        List<String> list = globalExcludes.get(type);
        if(list == null) list = new ArrayList<String>();
        list.add(excludeString);
        globalExcludes.put(type, list);
    }

    public static boolean exclude(String path, Map<String,List<String>> excludeFilters)
    {
        //check against excludes passed in, and global excludes
        if(shouldExclude(path, excludeFilters))//test subfolder excludes
            return true;
        return shouldExclude(path, globalExcludes);///test global
    }

    private static boolean shouldExclude(String path, Map<String,List<String>> excludeFilters)
    {
        if(excludeFilters == null || excludeFilters.isEmpty()) return false;//no filters to exclude on
        
        for(Map.Entry<String,List<String>> entry : excludeFilters.entrySet())
        {
            String type = entry.getKey();
            if(!tools.valid(type)) continue;
            List<String> excludeStrings = entry.getValue();
            for(String excludeString : excludeStrings)
            {
                if(type.equalsIgnoreCase(Constants.REGEXP))
                {
                    Pattern p = Pattern.compile(excludeString, Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(path);
                    boolean match = m.find();
                    //Config.log(Config.DEBUG, "Exclude ? "+ match +" \""+path+"\" "+(!match ? "does not match" : "matchs")+" regexp \""+excludeString+"\"");
                    if(match)
                    {
                        return true;//path matches this regex
                    }
                }
                else if(type.equalsIgnoreCase(Constants.CONTAINS))
                {

                    boolean match = path.toLowerCase().contains(excludeString.toLowerCase());
                    //Config.log(Config.DEBUG, "Exclude ? "+ match +" \""+path+"\" "+(!match ? "does not contain" : "contains")+"  \""+excludeString+"\"");
                    if(match)
                        return true;//path contains this string
                }
                else
                {
                     Logger.WARN( "Unknown exclude type: \""+type+"\"");
                }
            }
        }
        return false;//looped through all w/o a match
    }
    

}
