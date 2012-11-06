package utilities;

import java.io.File;
import java.util.*;

public class Source
{

    final String name;//name should not be changed after the constructor. It is used in the equals comparison
    String path;
    List<Subfolder> subfolders = new ArrayList<Subfolder>();

    Map<File,String> videosArchivedFromThisSource = new LinkedHashMap();
    Map<File,String> vidoesSkippedBecauseAlreadyArchived = new LinkedHashMap();

    boolean jsonRPCErrors = false;
    private String customParser = "";
    public Source(String name, String path)
    {
        this.name = name;
        this.path = path;
    }
        public void setCustomParser(String parser)
    {
        this.customParser = parser;
    }
    public String getCustomParser()
    {
        return customParser;
    }
    public void setJSONRPCErrors(boolean b)
    {
        this.jsonRPCErrors = b;
    }
    public boolean hadJSONRPCErrors()
    {
        return jsonRPCErrors;
    }
    public String getFullName()//todo: eliminate when cleanup is complete (same as getname)
    {
        return name;
    }
    public String getName()
    {
        return name;
    }
    public String getPath()
    {
        return path;
    }
    public void setPath(String p)
    {
        this.path = p;
    }

    public void trackArchivedVideos(Map<File,String> videosArchivedFromThisSource, Map<File,String> vidoesSkippedBecauseAlreadyArchived)
    {
        this.videosArchivedFromThisSource.putAll(videosArchivedFromThisSource);
        this.vidoesSkippedBecauseAlreadyArchived.putAll(vidoesSkippedBecauseAlreadyArchived);
    }
    public Map<File,String> getVideosArchivedFromThisSource()
    {
        return videosArchivedFromThisSource;
    }
    public Map<File,String> getVideosSkippedBecauseAlreadyArchived()
    {
        return vidoesSkippedBecauseAlreadyArchived;
    }        
    public void addSubfolder(Subfolder sf)
    {
        subfolders.add(sf);
    }
    public List<Subfolder> getSubfolders()
    {
        return subfolders;
    }

    @Override
    public boolean equals(Object o)
    {
        if(o==null) return false;//dont allow null equals
        if(!(o instanceof Source)) return false;//Not a source object

        //if name's are equal (case-insensitive), then the sources are equal.
        //need unique names for tracking purposed int he db        
        return getName().equalsIgnoreCase(((Source)o).getName());
    }

    @Override
    public int hashCode() 
    {
        //based on case-insensitive name
        String nameLc =  this.name == null ? null : this.name.toLowerCase();
        int hash = 5;
        hash = 23 * hash + (nameLc != null ? nameLc.hashCode() : 0);        
        return hash;
    }

}