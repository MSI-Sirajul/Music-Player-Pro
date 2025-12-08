package com.my.music;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "MusicDB";
    private static final int DATABASE_VERSION = 3; // Version Updated

    // Tables
    private static final String TABLE_FAVORITES = "favorites";
    private static final String TABLE_HISTORY = "history";

    // Common Columns
    private static final String KEY_ID = "id"; 
    private static final String KEY_TITLE = "title";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_PATH = "path";
    private static final String KEY_ALBUM_ID = "album_id";
    
    // New Columns for Sorting
    private static final String KEY_DURATION = "duration";
    private static final String KEY_SIZE = "size";
    private static final String KEY_DATE_ADDED = "date_added";
    
    // History specific
    private static final String KEY_TIMESTAMP = "timestamp"; 

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createTables(db);
    }

    private void createTables(SQLiteDatabase db) {
        String createFav = "CREATE TABLE " + TABLE_FAVORITES + "("
                + KEY_ID + " LONG PRIMARY KEY,"
                + KEY_TITLE + " TEXT,"
                + KEY_ARTIST + " TEXT,"
                + KEY_PATH + " TEXT,"
                + KEY_ALBUM_ID + " LONG,"
                + KEY_DURATION + " LONG,"
                + KEY_SIZE + " LONG,"
                + KEY_DATE_ADDED + " LONG" + ")";
        db.execSQL(createFav);

        String createHistory = "CREATE TABLE " + TABLE_HISTORY + "("
                + KEY_ID + " LONG PRIMARY KEY,"
                + KEY_TITLE + " TEXT,"
                + KEY_ARTIST + " TEXT,"
                + KEY_PATH + " TEXT,"
                + KEY_ALBUM_ID + " LONG,"
                + KEY_DURATION + " LONG,"
                + KEY_SIZE + " LONG,"
                + KEY_DATE_ADDED + " LONG,"
                + KEY_TIMESTAMP + " LONG" + ")";
        db.execSQL(createHistory);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // নতুন কলাম যুক্ত করার জন্য টেবিল রি-ক্রিয়েট করা হচ্ছে
        // (বিকল্প: ALTER TABLE ব্যবহার করা যায়, তবে এটি সহজ পদ্ধতি)
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_HISTORY);
        onCreate(db);
    }

    // ==========================================
    // FAVORITES METHODS
    // ==========================================
    public void addFavorite(Song song) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        
        values.put(KEY_ID, song.getId());
        values.put(KEY_TITLE, song.getTitle());
        values.put(KEY_ARTIST, song.getArtist());
        values.put(KEY_PATH, song.getPath());
        values.put(KEY_ALBUM_ID, song.getAlbumId());
        
        // Saving Sorting Info
        values.put(KEY_DURATION, song.getDuration());
        values.put(KEY_SIZE, song.getSize());
        values.put(KEY_DATE_ADDED, song.getDateAdded());

        db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
    }

    public void removeFavorite(long songId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_FAVORITES, KEY_ID + "=?", new String[]{String.valueOf(songId)});
        db.close();
    }

    public boolean isFavorite(long songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAVORITES, new String[]{KEY_ID}, KEY_ID + "=?",
                new String[]{String.valueOf(songId)}, null, null, null, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        db.close();
        return exists;
    }

    public ArrayList<Song> getAllFavorites() {
        ArrayList<Song> favList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_FAVORITES;
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ARTIST));
                String path = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PATH));
                long albumId = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ALBUM_ID));
                
                // Retrieving Sorting Info
                long duration = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DURATION));
                long size = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_SIZE));
                long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DATE_ADDED));

                // Passing actual values instead of 0
                favList.add(new Song(id, title, artist, path, albumId, duration, size, dateAdded));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return favList;
    }

    // ==========================================
    // HISTORY METHODS
    // ==========================================
    
    public void addHistory(Song song) {
        SQLiteDatabase db = this.getWritableDatabase();
        
        db.delete(TABLE_HISTORY, KEY_ID + "=?", new String[]{String.valueOf(song.getId())});

        ContentValues values = new ContentValues();
        values.put(KEY_ID, song.getId());
        values.put(KEY_TITLE, song.getTitle());
        values.put(KEY_ARTIST, song.getArtist());
        values.put(KEY_PATH, song.getPath());
        values.put(KEY_ALBUM_ID, song.getAlbumId());
        
        // Saving Sorting Info
        values.put(KEY_DURATION, song.getDuration());
        values.put(KEY_SIZE, song.getSize());
        values.put(KEY_DATE_ADDED, song.getDateAdded());
        
        values.put(KEY_TIMESTAMP, System.currentTimeMillis());

        db.insert(TABLE_HISTORY, null, values);
        db.close();
    }

    public ArrayList<Song> getHistory() {
        ArrayList<Song> historyList = new ArrayList<>();
        String selectQuery = "SELECT * FROM " + TABLE_HISTORY + " ORDER BY " + KEY_TIMESTAMP + " DESC";
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.rawQuery(selectQuery, null);

        if (cursor.moveToFirst()) {
            do {
                long id = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(KEY_TITLE));
                String artist = cursor.getString(cursor.getColumnIndexOrThrow(KEY_ARTIST));
                String path = cursor.getString(cursor.getColumnIndexOrThrow(KEY_PATH));
                long albumId = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_ALBUM_ID));
                
                // Retrieving Sorting Info
                long duration = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DURATION));
                long size = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_SIZE));
                long dateAdded = cursor.getLong(cursor.getColumnIndexOrThrow(KEY_DATE_ADDED));

                // Passing actual values
                historyList.add(new Song(id, title, artist, path, albumId, duration, size, dateAdded));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return historyList;
    }
}