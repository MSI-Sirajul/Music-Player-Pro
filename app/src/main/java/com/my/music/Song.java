package com.my.music;
import java.io.Serializable;

public class Song implements Serializable {
    private long id;
    private String title;
    private String artist;
    private String path;
    private long albumId;
    // নতুন ফিল্ডগুলো যুক্ত করুন
    private long duration;
    private long size;
    private long dateAdded;

    public Song(long id, String title, String artist, String path, long albumId, long duration, long size, long dateAdded) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.albumId = albumId;
        this.duration = duration;
        this.size = size;
        this.dateAdded = dateAdded;
    }

    // Getters
    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getPath() { return path; }
    public long getAlbumId() { return albumId; }
    public long getDuration() { return duration; }
    public long getSize() { return size; }
    public long getDateAdded() { return dateAdded; }
}