package com.my.music;

import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.ContentUris;
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
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.LinearInterpolator;
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

    // Animation & Gestures
    private ObjectAnimator rotateAnimator; // Album Art Rotation
    private float x1, x2; // Swipe Detection
    private static final int MIN_DISTANCE = 150;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);

        StrictMode.VmPolicy.Builder builder = new StrictMode.VmPolicy.Builder();
        StrictMode.setVmPolicy(builder.build());

        dbHelper = new DatabaseHelper(this);

        // Bind Views
        songTitle = findViewById(R.id.txtSongName);
        songArtist = findViewById(R.id.txtArtistName);
        tvCurrentTime = findViewById(R.id.tvCurrentTime);
        tvTotalTime = findViewById(R.id.tvTotalTime);
        tvSongCount = findViewById(R.id.tvSongCount);
        
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

        // Initialize Rotation Animator
        initRotationAnimator();
        
        // Initialize Swipe Listener
        setupAlbumSwipe();

        // Listeners
        btnPlay.setOnClickListener(this);
        btnNext.setOnClickListener(this);
        btnPrev.setOnClickListener(this);
        btnFavorite.setOnClickListener(this);
        btnBack.setOnClickListener(this);
        btnMenu.setOnClickListener(this);
        btnRepeat.setOnClickListener(this);
        btnQueue.setOnClickListener(this);
        
        seekBar.setOnSeekBarChangeListener(this);
        
    }

    // ==========================================
    // ALBUM ART ANIMATION & SWIPE
    // ==========================================
    private void initRotationAnimator() {
        rotateAnimator = ObjectAnimator.ofFloat(albumArt, "rotation", 0f, 0f);
        rotateAnimator.setDuration(0); //  seconds per rotation (Slow & Smooth)
        rotateAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        rotateAnimator.setInterpolator(new LinearInterpolator());
    }

    // ভেরিয়েবল (ক্লাসের উপরে যোগ করুন যদি না থাকে)
    private float initialX;

    private void setupAlbumSwipe() {
        albumArt.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = event.getRawX();
                        // রোটেশন এনিমেশন সাময়িক বন্ধ রাখা ভালো সোয়াইপের সময়
                        if(rotateAnimator != null) rotateAnimator.pause(); 
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // Real-time movement
                        float deltaX = event.getRawX() - initialX;
                        v.setTranslationX(deltaX);
                        // আলফা কমিয়ে ফেড ইফেক্ট দেওয়া যেতে পারে (অপশনাল)
                        v.setAlpha(1 - Math.abs(deltaX) / (v.getWidth() * 1.5f));
                        return true;

                    case MotionEvent.ACTION_UP:
                        float moved = event.getRawX() - initialX;
                        float threshold = v.getWidth() / 3;

                        if (Math.abs(moved) > threshold) {
                            // Complete Swipe
                            float exitX = (moved > 0) ? v.getWidth() + 100 : -(v.getWidth() + 100);
                            v.animate().translationX(exitX).alpha(0).setDuration(200).withEndAction(() -> {
                                if (musicBound && musicSrv != null) {
                                    if (moved > 0) musicSrv.playPrev();
                                    else musicSrv.playNext();
                                    updateUI();
                                    
                                    // Reset Position for Next Song Animation
                                    v.setTranslationX(moved > 0 ? -v.getWidth() : v.getWidth());
                                    v.animate().translationX(0).alpha(1).setDuration(300).start();
                                }
                            }).start();
                        } else {
                            // Bounce Back (Swipe Cancelled)
                            v.animate().translationX(0).alpha(1).setDuration(200).start();
                            if(musicSrv != null && musicSrv.isPng() && rotateAnimator != null) {
                                rotateAnimator.resume();
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }

    // ==========================================
    // QUEUE DIALOG
    // ==========================================
    private void showQueueDialog() {
        if (!musicBound || musicSrv == null) return;

        final Dialog dialog = new Dialog(this, R.style.BottomDialogTheme);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_queue);
        
        Window window = dialog.getWindow();
        if (window != null) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            window.setDimAmount(0.0f);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
            WindowManager.LayoutParams params = window.getAttributes();
            int displayWidth = getResources().getDisplayMetrics().widthPixels;
            int displayHeight = getResources().getDisplayMetrics().heightPixels;
            
            int marginWidth = (int) (40 * getResources().getDisplayMetrics().density); 
            params.width = displayWidth - marginWidth; 
            params.height = (int) (displayHeight * 0.55);
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.y = (int) (50 * getResources().getDisplayMetrics().density); 

            window.setAttributes(params);
        }

        ListView listView = dialog.findViewById(R.id.queueListView);
        Button btnClose = dialog.findViewById(R.id.btnCloseQueue);
        ImageButton btnScroll = dialog.findViewById(R.id.btnScrollToCurrent);

        final ArrayList<Song> queueList = musicSrv.getSongs();
        
        BaseAdapter queueAdapter = new BaseAdapter() {
            @Override public int getCount() { return queueList != null ? queueList.size() : 0; }
            @Override public Object getItem(int i) { return null; }
            @Override public long getItemId(int i) { return 0; }
            @Override public View getView(int i, View v, ViewGroup p) {
                if (v == null) v = LayoutInflater.from(PlayerActivity.this).inflate(R.layout.item_queue, p, false);
                
                TextView serial = v.findViewById(R.id.qSerial);
                TextView title = v.findViewById(R.id.qTitle);
                TextView artist = v.findViewById(R.id.qArtist);
                ImageView img = v.findViewById(R.id.qArt);
                ImageView playing = v.findViewById(R.id.qPlaying);
                
                Song s = queueList.get(i);
                serial.setText(String.format(Locale.getDefault(), "%02d", i + 1));
                title.setText(s.getTitle());
                artist.setText(s.getArtist());
                
                try {
                    Uri artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.getAlbumId());
                    img.setImageURI(artworkUri);
                    if (img.getDrawable() == null) img.setImageResource(R.drawable.ic_notification);
                } catch (Exception e) { img.setImageResource(R.drawable.ic_notification); }
                
                if (i == musicSrv.getSongPosn()) {
                    title.setTextColor(0xFF4CAF50); 
                    serial.setTextColor(0xFF4CAF50);
                    playing.setVisibility(View.VISIBLE);
                } else {
                    title.setTextColor(getResources().getColor(R.color.md_theme_onSurface));
                    serial.setTextColor(Color.parseColor("#808080"));
                    playing.setVisibility(View.GONE);
                }
                return v;
            }
        };
        
        listView.setAdapter(queueAdapter);
        listView.setSelection(musicSrv.getSongPosn()); 
        
        btnScroll.setOnClickListener(v -> listView.setSelection(musicSrv.getSongPosn()));
        
        listView.setOnItemClickListener((parent, view, position, id) -> {
            musicSrv.setSong(position);
            musicSrv.playSong();
            updateUI();
            dialog.dismiss();
        });

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    // ==========================================
    // MENU & RINGTONE
    // ==========================================
    private void showPlayerMenu() {
        View menuView = LayoutInflater.from(this).inflate(R.layout.layout_player_menu, null);
        View btnEq = menuView.findViewById(R.id.actionEqualizer);
        
        final PopupWindow popup = new PopupWindow(
                menuView, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                true
        );
        
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); 
        popup.setElevation(20);
        popup.showAsDropDown(btnMenu, -20, 0);

        menuView.findViewById(R.id.actionShare).setOnClickListener(v -> {
            popup.dismiss();
            shareSong();
        });

        menuView.findViewById(R.id.actionRingtone).setOnClickListener(v -> {
            popup.dismiss();
            checkRingtonePermission();
        });
    }
    // ==========================================
    // SMART EQUALIZER LOGIC
    // ==========================================
    private void openEqualizer() {
        int sessionId = 0; // Global Session
        if (musicBound && musicSrv != null) {
            // যদি সার্ভিসে getAudioSessionId() মেথড থাকে তবে সেটি ব্যবহার করা ভালো, 
            // না থাকলে 0 (Global) দিয়ে কাজ চলবে।
        }

        try {
            // 1. স্ট্যান্ডার্ড অ্যান্ড্রয়েড ইনটেন্ট তৈরি
            Intent intent = new Intent(android.media.audiofx.AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL);
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_AUDIO_SESSION, sessionId);
            intent.putExtra(android.media.audiofx.AudioEffect.EXTRA_CONTENT_TYPE, android.media.audiofx.AudioEffect.CONTENT_TYPE_MUSIC);
            intent.putExtra("android.media.extra.PACKAGE_NAME", getPackageName());

            // 2. ব্র্যান্ড অনুযায়ী নির্দিষ্ট প্যাকেজ টার্গেট করা
            String manufacturer = Build.MANUFACTURER.toLowerCase();

            if (manufacturer.contains("samsung")) {
                // Samsung SoundAlive
                Intent samsungIntent = new Intent(intent);
                samsungIntent.setPackage("com.sec.android.app.soundalive");
                try {
                    startActivityForResult(samsungIntent, 0);
                    return; // সফল হলে এখান থেকেই বের হয়ে যাবে
                } catch (Exception e) { 
                    // Samsung এর নির্দিষ্ট প্যাকেজ না পেলে নিচে ডিফল্ট ট্রাই করবে
                }
            } else if (manufacturer.contains("xiaomi") || manufacturer.contains("redmi")) {
                // Xiaomi/HyperOS সাধারণত ডিফল্ট ইনটেন্টেই তাদের 'Mi Sound Enhancer' ওপেন করে
                // তাই আলাদা প্যাকেজ সেট করার প্রয়োজন নেই, তবে প্রায়োরিটি চেক করা হলো
            } else if (manufacturer.contains("sony")) {
                Intent sonyIntent = new Intent(intent);
                sonyIntent.setPackage("com.sonyericsson.audioeffect");
                try {
                    startActivityForResult(sonyIntent, 0);
                    return;
                } catch (Exception e) { }
            }

            // 3. যদি নির্দিষ্ট ব্র্যান্ডের অ্যাপ না পাওয়া যায় বা অন্য ফোন হয় (Pixel/Generic)
            // তাহলে সিস্টেমের ডিফল্ট ইকুয়ালাইজার ওপেন করবে
            startActivityForResult(intent, 0);

        } catch (Exception e) {
            // 4. যদি কোনো ইকুয়ালাইজারই না থাকে
            Toast.makeText(this, "No Equalizer found on this device.", Toast.LENGTH_SHORT).show();
        }
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
        if (currentSong == null) return;
        try {
            File originalFile = new File(currentSong.getPath());
            if (!originalFile.exists()) return;

            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, currentSong.getTitle());
            values.put(MediaStore.MediaColumns.TITLE, currentSong.getTitle());
            values.put(MediaStore.MediaColumns.MIME_TYPE, "audio/mpeg"); 
            values.put(MediaStore.Audio.Media.IS_RINGTONE, true);
            values.put(MediaStore.Audio.Media.IS_NOTIFICATION, false);
            values.put(MediaStore.Audio.Media.IS_ALARM, false);
            values.put(MediaStore.Audio.Media.IS_MUSIC, false);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Ringtones/");
            }

            Uri newUri = getContentResolver().insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values);

            if (newUri != null) {
                java.io.InputStream is = new java.io.FileInputStream(originalFile);
                java.io.OutputStream os = getContentResolver().openOutputStream(newUri);
                if (os != null) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = is.read(buffer)) > 0) os.write(buffer, 0, len);
                    os.close();
                    is.close();
                    RingtoneManager.setActualDefaultRingtoneUri(this, RingtoneManager.TYPE_RINGTONE, newUri);
                    Toast.makeText(this, "Ringtone set successfully!", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "Could not create ringtone entry.", Toast.LENGTH_SHORT).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ==========================================
    // UI UPDATE & ANIMATION LOGIC
    // ==========================================
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
                
                // Animation Logic
                if (musicSrv.isPng()) {
                    btnPlay.setImageResource(R.drawable.ic_pause);
                    if (rotateAnimator != null && rotateAnimator.isPaused()) rotateAnimator.resume();
                    if (rotateAnimator != null && !rotateAnimator.isStarted()) rotateAnimator.start();
                } else {
                    btnPlay.setImageResource(R.drawable.ic_play);
                    if (rotateAnimator != null) rotateAnimator.pause();
                }
                
                int pos = musicSrv.getSongPosn() + 1;
                int total = musicSrv.getListSize();
                tvSongCount.setText(pos + " / " + total);
                
                int duration = musicSrv.getDur();
                seekBar.setMax(duration);
                tvTotalTime.setText(formatTime(duration));
            }
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