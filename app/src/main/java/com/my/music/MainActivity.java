package com.my.music;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import java.util.ArrayList;

public class MainActivity extends Activity {

    private ArrayList<Song> songList;
    private ListView songView;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound = false;
    private SongAdapter songAdt;
    private ImageButton btnNowPlaying;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        songView = findViewById(R.id.songListView);
        btnNowPlaying = findViewById(R.id.btnNowPlaying);
        songList = new ArrayList<>();

        // Header Play Button Logic
        btnNowPlaying.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(musicBound) {
                    startActivity(new Intent(MainActivity.this, PlayerActivity.class));
                }
            }
        });

        checkPermissions();
    }

    // Permission Handling
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String perm = (Build.VERSION.SDK_INT >= 33) ? "android.permission.READ_MEDIA_AUDIO" : Manifest.permission.READ_EXTERNAL_STORAGE;
            
            if (checkSelfPermission(perm) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{perm, Manifest.permission.FOREGROUND_SERVICE}, 1);
            } else {
                loadSongs();
            }
        } else {
            loadSongs();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongs();
        }
    }

    private void loadSongs() {
        getSongList();
        songAdt = new SongAdapter(this, songList);
        songView.setAdapter(songAdt);

        songView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                musicSrv.setSong(position);
                musicSrv.playSong();
                // Click করলে সাথে সাথে প্লেয়ার স্ক্রিনে চলে যাবে
                startActivity(new Intent(MainActivity.this, PlayerActivity.class));
            }
        });
    }

    // Service Connection
    private ServiceConnection musicConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder)service;
            musicSrv = binder.getService();
            musicSrv.setList(songList);
            musicBound = true;
            updateUI();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            updateUI();
        }
    };

    private void updateUI() {
        if(songAdt != null) songAdt.notifyDataSetChanged();
        
        if(musicBound && musicSrv.getCurrentSong() != null) {
            btnNowPlaying.setVisibility(View.VISIBLE);
        } else {
            btnNowPlaying.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
        registerReceiver(updateReceiver, new IntentFilter(MusicService.BROADCAST_ACTION));
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if(songAdt != null) songAdt.notifyDataSetChanged();
    }

    @Override
    protected void onDestroy() {
        if (musicBound) unbindService(musicConnection);
        unregisterReceiver(updateReceiver);
        super.onDestroy();
    }

    // Song List Retrieval
    public void getSongList() {
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int dataColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int albumIdColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID); // Album ID for Artwork

            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                String thisPath = musicCursor.getString(dataColumn);
                long thisAlbumId = musicCursor.getLong(albumIdColumn);

                songList.add(new Song(thisId, thisTitle, thisArtist, thisPath, thisAlbumId));
            } while (musicCursor.moveToNext());
            musicCursor.close();
        }
    }

    // Custom Adapter
    class SongAdapter extends BaseAdapter {
        private ArrayList<Song> songs;
        private LayoutInflater songInf;

        public SongAdapter(Context c, ArrayList<Song> theSongs) {
            songs = theSongs;
            songInf = LayoutInflater.from(c);
        }

        @Override
        public int getCount() { return songs.size(); }
        @Override
        public Object getItem(int arg0) { return null; }
        @Override
        public long getItemId(int arg0) { return 0; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = songInf.inflate(R.layout.song_item, parent, false);
            }
            
            TextView songView = convertView.findViewById(R.id.songTitle);
            TextView artistView = convertView.findViewById(R.id.songArtist);
            ImageView playingIndicator = convertView.findViewById(R.id.playingIndicator);
            ImageView imgThumbnail = convertView.findViewById(R.id.imgThumbnail);
            
            Song currSong = songs.get(position);
            songView.setText(currSong.getTitle());
            artistView.setText(currSong.getArtist());
            
            // Native Album Art Loading for List (Efficient)
            try {
                Uri artworkUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"), currSong.getAlbumId());
                imgThumbnail.setImageURI(artworkUri);
                
                // Check if image set failed (defaults to null drawable sometimes), reset to default icon
                if (imgThumbnail.getDrawable() == null) {
                    imgThumbnail.setImageResource(R.drawable.ic_notification);
                }
            } catch (Exception e) {
                imgThumbnail.setImageResource(R.drawable.ic_notification);
            }

            // Highlight Logic (Using Resource Colors)
            if(musicBound && musicSrv.getSongPosn() == position) {
                playingIndicator.setVisibility(View.VISIBLE);
                // Active Color (Primary)
                songView.setTextColor(getResources().getColor(R.color.md_theme_primary)); 
                artistView.setTextColor(getResources().getColor(R.color.md_theme_primary));
            } else {
                playingIndicator.setVisibility(View.GONE);
                // Default Color (OnSurface)
                songView.setTextColor(getResources().getColor(R.color.md_theme_onSurface));
                artistView.setTextColor(getResources().getColor(R.color.md_theme_onSurfaceVariant));
            }
            
            return convertView;
        }
    }
}