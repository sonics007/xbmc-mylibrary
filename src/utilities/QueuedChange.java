
package utilities;

import xbmcdb.db.tools.VideoType;


public class QueuedChange
{
    private int id;
    private String dropboxLocation, typeOfVideo, typeOfMetaData, value;
    public QueuedChange(int id, String dropboxLocation, String typeOfVideo, String typeOfMetaData, String value)
    {
        this.id=id;
        this.dropboxLocation = dropboxLocation;
        this.typeOfMetaData = typeOfMetaData;
        this.typeOfVideo = typeOfVideo;
        this.value = value;
    }
    public int getId()
    {
        return id;
    }
    public String getDropboxLocation()
    {
        return dropboxLocation;
    }
    public String getTypeOfVideo_depricated()
    {
        return typeOfVideo;
    }
    
    public VideoType getProperVideoType(){
        return tools.getProperVideoType(typeOfVideo);
    }    
            
    public MetaDataType getTypeOfMetaData()
    {
        return MetaDataType.valueOf(typeOfMetaData);
    }
    public String getValue()
    {
        return value;
    }
}