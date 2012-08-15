package utilities;

import java.util.*;


public class Subfolder implements Constants
{
    boolean recursive = false, forceTVDB=false, regexName = false;;
    String type = AUTO_TYPE,movie_set, prefix, suffix;
    String name;
    int maxSeries =-1, maxVideos =-1;
    Set<String> series = new HashSet<String>();
    int numberofVideos = 0;
    public Map<String,List<String>> excludes = new LinkedHashMap<String,List<String>>();
    public Map<String,List<String>> filters = new LinkedHashMap<String,List<String>>();
    Source source;
    String regexMatchingName = null;//if the name is a regex, this will be set to what the regex matches
    boolean canContainMultiPartVideos = false;
    
    //Downloading support has been removed
    //private boolean download = false;//download instead of stream
    private int level_deep;
    String compression;
    
    public Subfolder(Source source, String name)
    {
        this.name = name;
        this.source = source;
    }

    public void setLevelDeep(int level)
    {
        this.level_deep = level;
    }
    public int getLevelDeep()
    {
        return level_deep;
    }
    /*
    public void setDownload(boolean download)
    {
        this.download =download;
    }     
     
    public boolean download()
    {
        return download;
    }    
    public void setCompression(String compression)
    {
        this.compression = compression;
    }
    public String getCompression()
    {
        return compression;
    }
    */     
    
    public boolean canContainMultiPartVideos()
    {
        return canContainMultiPartVideos;
    }
    public void setCanContainMultiPartVideos(boolean canContainMultiPartVideos)
    {
        this.canContainMultiPartVideos =canContainMultiPartVideos;
    }
    
    public Source getSource()
    {
        return source;
    }
    public void setRegexName(boolean regexName)
    {
        this.regexName = regexName;
    }
    public void setRegexMatchingName(String name)
    {
        this.regexMatchingName = name;
    }
    public boolean isRegexName()
    {
        return regexName;
    }
    public void setForceTVDB(boolean forceTVDB)
    {
        this.forceTVDB =forceTVDB;
    }
    public boolean forceTVDBLookup()
    {
        return forceTVDB;
    }

    
    public boolean canAddAnotherVideo()
    {
        boolean canAddAnotherEpisode = 
                    getMaxVideos() <0//max not configured
                    || getNumberOfVideos() < getMaxVideos();
                       
        return canAddAnotherEpisode;
    }
    public boolean canAddAnotherSeries(String seriesName)
    {
         boolean canAddAnotherSeries =
                getMaxSeries() < 0//max not configures
                || seriesIsAlreadyAdded(seriesName)//if series already exists, its alwasy allowed
                || getNumberOfSeries() < getMaxSeries();

         return canAddAnotherSeries;
    }
    public boolean seriesIsAlreadyAdded(String seriesName)
    {
        return series.contains(seriesName);
    }
    public void addSeries(String seriesName)
    {
        series.add(seriesName);
    }
    public void addVideo(XBMCFile video)
    {
        numberofVideos++;
        if(video.isTvShow()) addSeries(video.getSeries());//only increments if series is new
    }
    public int getNumberOfSeries()
    {
        return series.size();
    }
    public int getNumberOfVideos()
    {
        return numberofVideos; 
    }
    public boolean shouldExclude()
    {
        return !excludes.isEmpty() || !Exclude.globalExcludes.isEmpty();
    }
    public boolean shouldFilter()
    {
        return !filters.isEmpty();
    }
    public void addExclude(String type, String value)
    {
        List<String> values = excludes.get(type);
        if(values == null) values = new ArrayList<String>();
        values.add(value);
        excludes.put(type, values);
    }
    public void addFitler(String type, String value)
    {
        List<String> values = filters.get(type);
        if(values == null) values = new ArrayList<String>();
        values.add(value);
        filters.put(type, values);
    }
    public void setMovieSet(String movieSet)
    {
        this.movie_set = movieSet;
    }
    public String getMovieSet()
    {
        return movie_set;
    }
    public void setPrefix(String prefix)
    {
        this.prefix = prefix;
    }
    public String getPrefix()
    {
        return prefix;
    }
    public void setSuffix(String suffix)
    {
        this.suffix = suffix;
    }
    public String getSuffix()
    {
        return suffix;
    }
    public void setMaxSeries(int max_shows)
    {
        this.maxSeries = max_shows;
    }
    public int getMaxSeries()
    {
        return maxSeries;
    }
    public void setMaxVideos(int max_videos)
    {
        this.maxVideos = max_videos;
    }
    public int getMaxVideos()
    {
        return maxVideos;
    }
    
    public void setType(String type)
    {
        this.type = type;
    }
    public void setRecursive(boolean recursive)
    {
        this.recursive = recursive;
    }

    /*
     * includes parent source name
     */
    public String getFullName()
    {
        return source.getName()+"/"+getName();
    }
    public String getFullNameClean()
    {
        return source.getName()+"/"+getCleanName();
    }
    /*
     * Name of the subfolder only, no parent name is included
     */
    public String getName()
    {
        return name;
    }
    /*
     * Returns the name that the regex matched if regex was used
     */
    public String getCleanName()
    {
        if(isRegexName() && regexMatchingName != null)
            return regexMatchingName;
        else
            return name;
    }
    public String getType()
    {
        return type;
    }
    public boolean isRecursive()
    {
        return recursive;
    }

    /*
     * Checks if thie path is inside the subfolder. Allowes for x levels deep inside subfolder if recursive is true.
     * Otherwise, path must be an exact match of the subfolder
     */
    public boolean pathMatches(String folderPath)
    {
        folderPath = Config.escapePath(folderPath);
        if(isRecursive())
        {
            boolean match;
            String nameToUse;
            if(isRegexName())//get the regex matchign string and use that to compare .startsWith
               nameToUse = tools.getRegexMatch(getFullName(), folderPath);
            else//regular .startsWith using the name
                nameToUse = getFullName();
            
            //check if the folder path starts with the name of this subfolder, in which case, a recursive match is true. the folder path is below or at the same level as this subfolder
            match =  nameToUse != null && folderPath.toLowerCase().startsWith(nameToUse.toLowerCase());
            Config.log(DEBUG,(isRegexName() ? "Regex" :"Regular")+" recursive match ? "+ match +": "
                                +folderPath.toLowerCase() + " ["+(!match ? "does not start with" : "starts with")+"] "
                                + (nameToUse==null ? getFullName() : nameToUse).toLowerCase());
            return match;
        }
        else//not recursive, only exact matches are allowed
        {            
            return pathMatchesExactly(folderPath);
        }
    }

    public boolean pathMatchesExactly(String folderPath)
    {
        folderPath = Config.escapePath(folderPath);
        boolean exactMatch;
        String nameToUse;
        if(isRegexName())        
            nameToUse = tools.getRegexMatch(getFullName(), folderPath);//get matching regex string for the name
        else
            nameToUse = getFullName();//normal name

        exactMatch = nameToUse != null && nameToUse.equalsIgnoreCase(folderPath);//case insensitive
        
        Config.log(DEBUG,"Exact match = " + exactMatch +" for "+ (isRegexName() ? "regex" : "regular")+" match. "
                + "Checked if Subfolder: \""+(nameToUse==null ? getFullName() : nameToUse)+"\" "+(isRegexName() ? "matches" : "=") +" \""+folderPath+"\"");
        
        return exactMatch;
    }

    /*
     * Check if we need to dig deeper in order to get into the subfolder. Means this path is an ancestor of the subfolder
     */
    public boolean digDeeper(String folderPath)
    {
        folderPath = Config.escapePath(folderPath);
        {
            boolean match =false;
            String regexMatch = null;
            if(isRegexName())
            {
                
                //get seperate folders
                String[] folders = getFullName().split("/");
                for(int i=folders.length-1;i>=0;i--)
                {
                    String nameToCheck = "";
                    for(int z = i; z>=0; z--)//start with full name and move up, checking each folder for a regex starts with folderpath
                        nameToCheck = folders[z] + (nameToCheck.isEmpty() ? "" :"/"+nameToCheck);                    

                    boolean regexMatches = tools.regexMatch(nameToCheck, folderPath);
                    //Config.log(DEBUG,"DigDeeper Regex match ? "+ regexMatches +": \""+nameToCheck + "\" ["+(regexMatches ? "matches" : "does not match")+"] \""+folderPath+"\"");
                    if(regexMatches)
                    {
                        regexMatch = tools.getRegexMatch(nameToCheck, folderPath);
                        match = regexMatch.toLowerCase().startsWith(folderPath.toLowerCase());
                        break;
                    }
                    else
                    {
                        regexMatch =  "<no regex match on " + nameToCheck+">";
                        match = false;//no regex match
                    }
                }
            }
            else//regular, non-regex match
            {
                match = getFullName().toLowerCase().startsWith(folderPath.toLowerCase());
                //Config.log(DEBUG,"DigDeeper ? "+ match +": "+getFullName().toLowerCase() + " ["+(!match ? "does not start" : "starts")+" with] "+folderPath.toLowerCase());
            }

            
            if(match)
            {
                //Config.log(DEBUG, "Dig deeper=true for Subfolder: "+ getFullName() + ", current level = "+ folderPath);
                return true;//sourceName is a folder deeper in the source (recursive match)
            }
        }
        return false;//no match
    }

       /*
     * Checks the path against the filters.
     * Returns true if it is allowed by ALL filters or filter matching is not used
     * Returns false if this path should be skipped
     */
	//AngryCamel - 20120805 2351
    // public boolean isAllowedByFilters(String path)
	public boolean isAllowedByFilters(String path, int runtime)
    {
        path = Config.escapePath(path);
       //check against filters
       boolean shouldFilter = shouldFilter();
       boolean filterMatch = true;//default
       if(shouldFilter)
           filterMatch = Filter.FilterMatch(path, runtime, filters); //AngryCamel - 20120805 2351
        if(!filterMatch)
        {
            Config.log(DEBUG, "Skipping this path because it doesn't match any filters: "+ path);
            return false;
        }

       return true;//all checks passed
    }

    public boolean isExcluded(String path)
    {
        path = Config.escapePath(path);
       //check against excludes
       boolean shouldExclude = shouldExclude();     
       boolean exclude = false;//default
       if(shouldExclude)
           exclude = Exclude.exclude(path, excludes);//also checks global excludes
        if(exclude)
        {
            Config.log(DEBUG, "Skipping this path because it matches an exclude: "+ path);
            return true;
        }
       return false;//all checks passed, not excluded
    }

}
