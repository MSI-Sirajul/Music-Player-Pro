package com.my.music;
import java.io.Serializable;

public class Song implements Serializable {
    private long id;
    private String title;
    private String artist;
    private String path;
    private long albumId; // New Field

    public Song(long id, String title, String artist, String path, long albumId) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.albumId = albumId;
    }

    public long getId() { return id; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getPath() { return path; }
    public long getAlbumId() { return albumId; }
}