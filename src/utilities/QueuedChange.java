
package utilities;


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
    public String getTypeOfVideo()
    {
        return typeOfVideo;
    }
    public String getTypeOfMetaData()
    {
        return typeOfMetaData;
    }
    public String getValue()
    {
        return value;
    }
}
