package utilities;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Filter
{
    public static boolean FilterMatch(String path, Map<String, List<String>> filters)
    {
        if(filters == null || filters.isEmpty()) return true;//no filters to exclude on

        for(Map.Entry<String,List<String>> entry : filters.entrySet())
        {
            String type = entry.getKey();
            if(type==null) continue;//skip
            List<String> filterStrings = entry.getValue();
            for(String filterString : filterStrings)
            {
                if(type.equalsIgnoreCase(Constants.REGEXP))
                {
                    Pattern p = Pattern.compile(filterString, Pattern.CASE_INSENSITIVE);
                    Matcher m = p.matcher(path);
                    if(!m.find())
                    {
                        return false;//this filter does not match, return false because all filters must match
                    }
                }
                else if(type.equalsIgnoreCase(Constants.CONTAINS))
                {
                    if(!path.toLowerCase().contains(filterString.toLowerCase()))
                        return false;//this path does not contains this string, return false because all filters must match
                }
                else
                {
                     Config.log(Config.WARNING, "Unknown filter type: \""+type+"\"");
                }
            }
        }
        return true;//looped through all and all matched
    }
}
