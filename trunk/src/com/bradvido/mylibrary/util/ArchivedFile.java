package com.bradvido.mylibrary.util;

/**
 *
 * @author bvidovic
 */
public class ArchivedFile 
{
        public int id;
        public String sourceName;
        public String originalPath;
        public Long missingSince;
        public int missingCount;
        public String videoType;
        public String title;
        public String series;
        public String artist;
        public int episodeNumber;
        public int seasonNumber;
        public int year;
        public boolean isTvDbLookup;
                
        public ArchivedFile(int id, String sourceName, String originalPath, Long missingSince, 
                int missingCount, String videoType, String title, String series, String artist, 
                int episodeNumber, int seasonNumber, int year, boolean isTvDbLookup)
        {
            this.id = id;
            this.sourceName = sourceName;
            this.originalPath = originalPath;
            this.missingSince = missingSince;
            this.missingCount = missingCount;
            this.videoType = videoType;
            this.title = title;
            this.series = series;
            this.artist = artist;
            this.episodeNumber = episodeNumber;
            this.seasonNumber = seasonNumber;
            this.year = year;
            this.isTvDbLookup = isTvDbLookup;
        }
}
