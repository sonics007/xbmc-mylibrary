
package utilities;


public class Download
{
    int downloadId, archivedFileId;
    String status, archivedStrmLocation, compression;
    java.sql.Timestamp start;

    public Download(int downloadId, int archivedFileId, String status, java.sql.Timestamp start, String archivedStrmLocation, String compression)
    {
        this.downloadId=downloadId;
        this.archivedFileId=archivedFileId;
        this.status=status;
        this.start=start;
        this.archivedStrmLocation=archivedStrmLocation;
        this.compression = compression;
    }

    public int getDownloadID()
    {
        return downloadId;
    }
    public int getArchivedFileID()
    {
        return archivedFileId;
    }
    public String getStatus()
    {
        return status;
    }
    public Long getStartMs()
    {
        return start == null ? null : start.getTime();
    }
    public String getArchivedStreamLocation()
    {
        return archivedStrmLocation;
    }
    public String getCompression()
    {
        return compression;
    }
}
