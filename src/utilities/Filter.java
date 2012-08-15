package utilities;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class Filter
{
    public static boolean FilterMatch(String path, int runtime, Map<String, List<String>> filters)
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
				//AngryCamel - 20120805 2351
				//  <runtime> - Matches if the runtime of the file fits the criteria specified in seconds along with the
				//   relational operator value. The format is "<relational_operator>|<runtime_seconds>". Posible relational
				//   operators are: EQ:Equal to, GT:Greater than, LT:Less than, NE:Not equal to, GE:Greater than or equal to,
				//   LE:Less than or equal to. Matches only on files and not directories.
				// Example: 
				// <!-- (Recursive) Modern Marvels Episodes over 20 minutes long -->
				// <subfolder name="History Channel/Modern Marvels" type="episodes" >
				// 		<filter>
				//			<runtime>GT|1200</runtime>				
				//		</filter>
				// </subfolder>	
                else if(type.equalsIgnoreCase(Constants.RUNTIME))
                {
					String[] splitFilterStr;
					int runtimeFilter = 0;
					String operator = "";
					
					splitFilterStr = filterString.toLowerCase().split("|");
					if(splitFilterStr.length < 2)
						return false;//filter string format invalid
					
					operator = splitFilterStr[0];
					
					try{
						runtimeFilter = Integer.parseInt(splitFilterStr[1]);
					}catch (NumberFormatException e){
						return false;//filter string format invalid
					}
					
					if(operator.equals("EQ"))
					{
						//Handle Equal To check here
						if(runtime == runtimeFilter)
						{
							return true;
						}
						else
						{
							return false;
						}
					}
					else if(operator.equals("GT"))
					{
						//Handle Greater Than check here
						if(runtime > runtimeFilter)
						{
							return true;
						}
						else
						{
							return false;
						}
					}
					else if(operator.equals("LT"))
					{
						//Handle Less Than check here
						if(runtime < runtimeFilter)
						{
							return true;
						}
						else
						{
							return false;
						}
					}
					else if(operator.equals("NE"))
					{
						//Handle Not Equal To check here
						if(runtime != runtimeFilter)
						{
							return true;
						}
						else
						{
							return false;
						}
					}
					else if(operator.equals("GE"))
					{
						//Handle Greater than or equal to check here
						if(runtime >= runtimeFilter)
						{
							return true;
						}
						else
						{
							return false;
						}
					}
					else if(operator.equals("LE"))
					{
						//Handle Less than or equal to check here
						if(runtime <= runtimeFilter)
						{
							return true;
						}
						else
						{
							return false;
						}
					}
					else
					{
						return false;//unknown relational operator
					}
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
