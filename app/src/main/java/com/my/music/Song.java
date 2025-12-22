package com.my.music;
import java.io.Serializable;

public class Song implements Serializable {
    private long id;
    private String title;
    private String artist;
    private String path;
    private long albumId;
    private long duration;
    private long size;
    private long dateAdded;
    private String album; // নতুন ফিল্ড যোগ করা হয়েছে

    // পুরানো কন্সট্রাক্টর (backward compatibility)
    public Song(long id, String title, String artist, String path, long albumId, long duration, long size, long dateAdded) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.albumId = albumId;
        this.duration = duration;
        this.size = size;
        this.dateAdded = dateAdded;
        this.album = ""; // ডিফল্ট ভ্যালু
    }

    // নতুন কন্সট্রাক্টর album সহ
    public Song(long id, String title, String artist, String path, long albumId, long duration, long size, long dateAdded, String album) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.path = path;
        this.albumId = albumId;
        this.duration = duration;
        this.size = size;
        this.dateAdded = dateAdded;
        this.album = album;
    }

    // ডিফল্ট কন্সট্রাক্টর (নতুন এক্সটার্নাল ফাইল হ্যান্ডলিং এর জন্য)
    public Song() {
        // ডিফল্ট কন্সট্রাক্টর
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
    public String getAlbum() { return album; }

    // Setters (নতুন এক্সটার্নাল ফাইল হ্যান্ডলিং এর জন্য)
    public void setId(long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setArtist(String artist) { this.artist = artist; }
    public void setPath(String path) { this.path = path; }
    public void setAlbumId(long albumId) { this.albumId = albumId; }
    public void setDuration(long duration) { this.duration = duration; }
    public void setSize(long size) { this.size = size; }
    public void setDateAdded(long dateAdded) { this.dateAdded = dateAdded; }
    public void setAlbum(String album) { this.album = album; }
}