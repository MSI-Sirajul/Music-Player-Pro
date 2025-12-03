package com.my.music;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;

public class PlayerActivity extends Activity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    private MusicService musicSrv;
    private boolean musicBound = false;
    private TextView songTitle, songArtist;
    private ImageButton btnPlay, btnNext, btnPrev;
    private SeekBar seekBar;
    private ImageView albumArt;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        // UI Initialization
        songTitle = findViewById(R.id.txtSongName);
        songArtist = findViewById(R.id.txtArtistName);
        btnPlay = findViewById(R.id.btnPlay);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        seekBar = findViewById(R.id.seekBar);
        albumArt = findViewById(R.id.albumArt);

        // Click Listeners
        btnPlay.setOnClickListener(this);
        btnNext.setOnClickListener(this);
        btnPrev.setOnClickListener(this);
        seekBar.setOnSeekBarChangeListener(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent playIntent = new Intent(this, MusicService.class);
        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
        registerReceiver(updateReceiver, new IntentFilter(MusicService.BROADCAST_ACTION));
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(musicBound) unbindService(musicConnection);
        unregisterReceiver(updateReceiver);
        handler.removeCallbacks(updateSeekBar);
    }

    private ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicSrv = binder.getService();
            musicBound = true;
            updateUI();
            handler.post(updateSeekBar);
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
        if (musicSrv != null && musicBound) {
            Song s = musicSrv.getCurrentSong();
            if(s != null) {
                songTitle.setText(s.getTitle());
                songArtist.setText(s.getArtist());
                songTitle.setSelected(true); // Enable Marquee
                
                // Load Album Art from file natively
                loadAlbumArt(s.getPath());
            }
            
            // Toggle Play/Pause Icon
            if (musicSrv.isPng()) {
                btnPlay.setImageResource(R.drawable.ic_pause);
            } else {
                btnPlay.setImageResource(R.drawable.ic_play);
            }
            
            seekBar.setMax(musicSrv.getDur());
        }
    }

    // Native function to extract embedded image
    private void loadAlbumArt(String path) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path);
            byte[] data = mmr.getEmbeddedPicture();
            
            if (data != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                albumArt.setImageBitmap(bitmap);
            } else {
                // Fallback to default icon if no album art found
                albumArt.setImageResource(R.drawable.ic_notification);
            }
        } catch (Exception e) {
            albumArt.setImageResource(R.drawable.ic_notification);
        } finally {
            try {
                mmr.release(); // Free resources
            } catch (Exception e) {
                // Ignore release errors
            }
        }
    }

    private Runnable updateSeekBar = new Runnable() {
        @Override
        public void run() {
            if (musicSrv != null && musicBound && musicSrv.isPng()) {
                seekBar.setProgress(musicSrv.getPosn());
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onClick(View v) {
        if (!musicBound) return;
        int id = v.getId();
        if (id == R.id.btnPlay) {
            if (musicSrv.isPng()) {
                musicSrv.pausePlayer();
            } else {
                musicSrv.go();
            }
        } else if (id == R.id.btnNext) {
            musicSrv.playNext();
        } else if (id == R.id.btnPrev) {
            musicSrv.playPrev();
        }
        updateUI();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (musicBound && fromUser) {
            musicSrv.seek(progress);
        }
    }
    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
}