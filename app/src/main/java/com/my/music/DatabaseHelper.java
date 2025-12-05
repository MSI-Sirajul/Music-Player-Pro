package com.my.music;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "MusicDB";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE_FAVORITES = "favorites";
    private static final String KEY_ID = "id"; // Song ID from MediaStore
    private static final String KEY_TITLE = "title";
    private static final String KEY_ARTIST = "artist";
    private static final String KEY_PATH = "path";
    private static final String KEY_ALBUM_ID = "album_id";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        String createTable = "CREATE TABLE " + TABLE_FAVORITES + "("
                + KEY_ID + " LONG PRIMARY KEY,"
                + KEY_TITLE + " TEXT,"
                + KEY_ARTIST + " TEXT,"
                + KEY_PATH + " TEXT,"
                + KEY_ALBUM_ID + " LONG" + ")";
        db.execSQL(createTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_FAVORITES);
        onCreate(db);
    }

    // Add Song to Favorites
    public void addFavorite(Song song) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put(KEY_ID, song.getId());
        values.put(KEY_TITLE, song.getTitle());
        values.put(KEY_ARTIST, song.getArtist());
        values.put(KEY_PATH, song.getPath());
        values.put(KEY_ALBUM_ID, song.getAlbumId());
        
        // Insert with conflict strategy (ignore if already exists)
        db.insertWithOnConflict(TABLE_FAVORITES, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        db.close();
    }

    // Remove Song from Favorites
    public void removeFavorite(long songId) {
        SQLiteDatabase db = this.getWritableDatabase();
        db.delete(TABLE_FAVORITES, KEY_ID + "=?", new String[]{String.valueOf(songId)});
        db.close();
    }

    // Check if song is Favorite
    public boolean isFavorite(long songId) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursor = db.query(TABLE_FAVORITES, new String[]{KEY_ID}, KEY_ID + "=?",
                new String[]{String.valueOf(songId)}, null, null, null, null);
        boolean exists = (cursor.getCount() > 0);
        cursor.close();
        db.close();
        return exists;
    }

    // Get All Favorites
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
                
                favList.add(new Song(id, title, artist, path, albumId));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return favList;
    }
}