package com.my.music;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaMetadataRetriever;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.StrictMode;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.Locale;

public class PlayerActivity extends Activity implements View.OnClickListener, SeekBar.OnSeekBarChangeListener {

    // Components
    private MusicService musicSrv;
    private boolean musicBound = false;
    private DatabaseHelper dbHelper;
    private Song currentSong;
    private Handler handler = new Handler();
    
    // UI Elements
    private TextView songTitle, songArtist, tvCurrentTime, tvTotalTime, tvSongCount, tvFooter;
    private ImageButton btnPlay, btnNext, btnPrev, btnFavorite, btnBack, btnMenu, btnRepeat, btnQueue;
    private SeekBar seekBar;
    private ImageView albumArt;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        dbHelper = new DatabaseHelper(this);

        songTitle = findViewById(R.id.txtSongName);
        songArtist = findViewById(R.id.txtArtistName);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvSongCount = findViewById(R.id.tvSongCount);
        tvFooter = findViewById(R.id.tvFooter);
        
        btnPlay = findViewById(R.id.btnPlay);
        btnNext = findViewById(R.id.btnNext);
        btnPrev = findViewById(R.id.btnPrev);
        btnFavorite = findViewById(R.id.btnFavorite);
        btnBack = findViewById(R.id.btnBack);
        btnMenu = findViewById(R.id.btnMenu);
        btnRepeat = findViewById(R.id.btnRepeat);
        btnQueue = findViewById(R.id.btnQueue);
        
        seekBar = findViewById(R.id.seekBar);
        albumArt = findViewById(R.id.albumArt);

        btnPlay.setOnClickListener(this);
        btnNext.setOnClickListener(this);
        btnPrev.setOnClickListener(this);
        btnFavorite.setOnClickListener(this);
        btnBack.setOnClickListener(this);
        btnMenu.setOnClickListener(this);
        btnRepeat.setOnClickListener(this);
        btnQueue.setOnClickListener(this);
        
        seekBar.setOnSeekBarChangeListener(this);
        
        setupFooterText();
    }

    private void setupFooterText() {
        String text = "Music Player Pro Design by @MSI-Sirajul";
        SpannableString spannable = new SpannableString(text);
        
        int start = text.indexOf("@MSI-Sirajul");
        int end = start + "@MSI-Sirajul".length();
        spannable.setSpan(new ForegroundColorSpan(0xFF4CAF50), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        
        tvFooter.setText(spannable);
        tvFooter.setTextColor(getResources().getColor(R.color.footer_text_base)); 
    }

    private void showQueueDialog() {
        if (!musicBound || musicSrv == null) return;

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_queue);
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);

        ListView listView = dialog.findViewById(R.id.queueListView);
        Button btnClose = dialog.findViewById(R.id.btnCloseQueue);

        final ArrayList<Song> queueList = musicSrv.getSongs();
        
        BaseAdapter queueAdapter = new BaseAdapter() {
            @Override public int getCount() { return queueList != null ? queueList.size() : 0; }
            @Override public Object getItem(int i) { return null; }
            @Override public long getItemId(int i) { return 0; }
            @Override public View getView(int i, View v, ViewGroup p) {
                if (v == null) v = LayoutInflater.from(PlayerActivity.this).inflate(R.layout.song_item, p, false);
                
                TextView title = v.findViewById(R.id.songTitle);
                TextView artist = v.findViewById(R.id.songArtist);
                TextView folder = v.findViewById(R.id.songFolder);
                ImageView img = v.findViewById(R.id.imgThumbnail);
                ImageView playing = v.findViewById(R.id.playingIndicator);
                
                Song s = queueList.get(i);
                title.setText(s.getTitle());
                artist.setText(s.getArtist());
                folder.setVisibility(View.GONE); 
                img.setVisibility(View.GONE); 
                
                if (i == musicSrv.getSongPosn()) {
                    title.setTextColor(0xFF4CAF50);
                    playing.setVisibility(View.VISIBLE);
                } else {
                    title.setTextColor(getResources().getColor(R.color.md_theme_onSurface));
                    playing.setVisibility(View.GONE);
                }
                return v;
            }
        };
        
        listView.setAdapter(queueAdapter);
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            musicSrv.setSong(position);
            musicSrv.playSong();
            updateUI();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // Custom Menu for Player
    private void showPlayerMenu() {
        View menuView = LayoutInflater.from(this).inflate(R.layout.layout_player_menu, null);
        
        final PopupWindow popup = new PopupWindow(
                menuView, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                true
        );
        
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); 
        popup.setElevation(20);
        popup.showAsDropDown(btnMenu, -10, 0);

        // Handle Clicks Manually
        menuView.findViewById(R.id.actionShare).setOnClickListener(v -> {
            popup.dismiss();
            shareSong();
        });

        menuView.findViewById(R.id.actionRingtone).setOnClickListener(v -> {
            popup.dismiss();
            checkRingtonePermission();
        });
    }

    private void shareSong() {
        try {
            File file = new File(currentSong.getPath());
            if(file.exists()) {
                Intent share = new Intent(Intent.ACTION_SEND);
                share.setType("audio/*");
                share.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(file));
                startActivity(Intent.createChooser(share, "Share Audio"));
            }
        } catch (Exception e) {
            Toast.makeText(this, "Cannot share this file", Toast.LENGTH_SHORT).show();
        }
    }

    private void checkRingtonePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.System.canWrite(this)) {
                setRingtone();
            } else {
                Intent intent = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
                Toast.makeText(this, "Allow permission to set Ringtone", Toast.LENGTH_LONG).show();
            }
        } else {
            setRingtone();
        }
    }

    private void setRingtone() {
        try {
            File k = new File(currentSong.getPath());
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DATA, k.getAbsolutePath());
            values.put(MediaStore.MediaColumns.TITLE, currentSong.getTitle());
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mp3");
            values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
            values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
            values.put(MediaStore.Audio.Media.IS_ALARM, false);
            values.put(MediaStore.Audio.Media.IS_MUSIC, false);

            Uri uri = MediaStore.Audio.Media.getContentUriForPath(k.getAbsolutePath());
            getContentResolver().delete(uri, MediaStore.MediaColumns.DATA + "=\"" + k.getAbsolutePath() + "\"", null);
            Uri newUri = getContentResolver().insert(uri, values);
            RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, newUri);
            Toast.makeText(this, "Ringtone set successfully!", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to set ringtone.", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUI() {
        if (musicSrv != null && musicBound) {
            currentSong = musicSrv.getCurrentSong();
            
            if(currentSong != null) {
                songTitle.setText(currentSong.getTitle());
                songArtist.setText(currentSong.getArtist());
                songTitle.setSelected(true); 
                
                loadAlbumArt(currentSong.getPath());
                checkFavoriteStatus();
                updateRepeatIcon();
                
                int pos = musicSrv.getSongPosn() + 1;
                int total = musicSrv.getListSize();
                tvSongCount.setText(pos + " / " + total);
                
                int duration = musicSrv.getDur();
                seekBar.setMax(duration);
                tvTotalTime.setText(formatTime(duration));
            }
            btnPlay.setImageResource(musicSrv.isPng() ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }

    private void toggleRepeat() {
        if (!musicBound) return;
        Intent intent = new Intent(this, MusicService.class);
        intent.setAction(MusicService.ACTION_REPEAT);
        startService(intent);
    }

    private void updateRepeatIcon() {
        if (!musicBound) return;
        int mode = musicSrv.getRepeatMode();
        switch (mode) {
            case MusicService.REPEAT_ALL:
                btnRepeat.setImageResource(R.drawable.ic_repeat_all);
                btnRepeat.setAlpha(1.0f);
                break;
            case MusicService.REPEAT_ONE:
                btnRepeat.setImageResource(R.drawable.ic_repeat_one);
                btnRepeat.setAlpha(1.0f);
                break;
            case MusicService.REPEAT_OFF:
                btnRepeat.setImageResource(R.drawable.ic_repeat_off);
                btnRepeat.setAlpha(0.5f);
                break;
        }
    }

    private void toggleFavorite() {
        if(currentSong == null) return;
        if(dbHelper.isFavorite(currentSong.getId())) {
            dbHelper.removeFavorite(currentSong.getId());
            Toast.makeText(this, "Removed from Favorites", Toast.LENGTH_SHORT).show();
        } else {
            dbHelper.addFavorite(currentSong);
            Toast.makeText(this, "Added to Favorites", Toast.LENGTH_SHORT).show();
        }
        checkFavoriteStatus();
        sendBroadcast(new Intent(MusicService.BROADCAST_ACTION));
    }

    private void checkFavoriteStatus() {
        if(currentSong == null) return;
        boolean isFav = dbHelper.isFavorite(currentSong.getId());
        btnFavorite.setImageResource(isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_empty);
    }

    private void loadAlbumArt(String path) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path);
            byte[] data = mmr.getEmbeddedPicture();
            if (data != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                albumArt.setImageBitmap(bitmap);
            } else {
                albumArt.setImageResource(R.drawable.ic_notification);
            }
        } catch (Exception e) { albumArt.setImageResource(R.drawable.ic_notification); }
    }

    private String formatTime(int millis) {
        int seconds = (millis / 1000) % 60;
        int minutes = (millis / 1000) / 60;
        return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds);
    }

    private Runnable updateTimeTask = new Runnable() {
        @Override
        public void run() {
            if (musicSrv != null && musicBound) {
                if(musicSrv.isPng() || seekBar.getProgress() > 0) {
                    int currentPos = musicSrv.getPosn();
                    seekBar.setProgress(currentPos);
                    tvCurrentTime.setText(formatTime(currentPos));
                }
            }
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    public void onClick(View v) {
        if (!musicBound) return;
        int id = v.getId();
        if (id == R.id.btnPlay) {
            if (musicSrv.isPng()) musicSrv.pausePlayer();
            else musicSrv.go();
        } else if (id == R.id.btnNext) {
            musicSrv.playNext();
        } else if (id == R.id.btnPrev) {
            musicSrv.playPrev();
        } else if (id == R.id.btnFavorite) {
            toggleFavorite();
        } else if (id == R.id.btnRepeat) {
            toggleRepeat();
        } else if (id == R.id.btnQueue) {
            showQueueDialog();
        } else if (id == R.id.btnMenu) {
            showPlayerMenu();
        } else if (id == R.id.btnBack) {
            onBackPressed();
        }
        updateUI();
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
        if (musicBound && fromUser) {
            musicSrv.seek(progress);
            tvCurrentTime.setText(formatTime(progress));
        }
    }
    @Override public void onStartTrackingTouch(SeekBar seekBar) {}
    @Override public void onStopTrackingTouch(SeekBar seekBar) {}
    
    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, R.anim.slide_out_down);
    }

    private ServiceConnection musicConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder) service;
            musicSrv = binder.getService();
            musicBound = true;
            updateUI();
            handler.post(updateTimeTask); 
        }
        @Override public void onServiceDisconnected(ComponentName name) { musicBound = false; }
    };

    private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { updateUI(); }
    };

    @Override
    protected void onStart() {
        super.onStart();
        Intent playIntent = new Intent(this, MusicService.class);
        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
        registerReceiver(updateReceiver, new IntentFilter(MusicService.BROADCAST_ACTION));
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        if(musicBound) updateUI();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(musicBound) unbindService(musicConnection);
        unregisterReceiver(updateReceiver);
        handler.removeCallbacks(updateTimeTask);
    }
}