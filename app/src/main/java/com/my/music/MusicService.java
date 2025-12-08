package com.my.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.v4.app.NotificationCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.widget.Toast;
import java.util.ArrayList;

public class MusicService extends Service implements 
        MediaPlayer.OnPreparedListener, 
        MediaPlayer.OnErrorListener, 
        MediaPlayer.OnCompletionListener,
        AudioManager.OnAudioFocusChangeListener {

    // Core Components
    private MediaPlayer player;
    private ArrayList<Song> songs;
    private int songPosn;
    private final IBinder musicBind = new MusicBinder();
    private AudioManager audioManager;
    private DatabaseHelper dbHelper;
    
    // MediaSession for Lockscreen & Native Controls
    private MediaSessionCompat mediaSession;
    
    // States
    private boolean resumeOnFocusGain = false;
    private boolean resumeAfterCall = false;
    
    // Repeat Modes
    public static final int REPEAT_ALL = 0;
    public static final int REPEAT_ONE = 1;
    public static final int REPEAT_OFF = 2;
    private int repeatMode = REPEAT_ALL; 
    
    private static final String CHANNEL_ID = "MusicChannel";
    private static final int NOTIFICATION_ID = 1;

    // Actions
    public static final String ACTION_PLAY = "com.my.music.ACTION_PLAY";
    public static final String ACTION_PAUSE = "com.my.music.ACTION_PAUSE";
    public static final String ACTION_NEXT = "com.my.music.ACTION_NEXT";
    public static final String ACTION_PREV = "com.my.music.ACTION_PREV";
    public static final String ACTION_CLOSE = "com.my.music.ACTION_CLOSE";
    public static final String ACTION_FAV = "com.my.music.ACTION_FAV";
    public static final String ACTION_REPEAT = "com.my.music.ACTION_REPEAT";
    public static final String BROADCAST_ACTION = "com.my.music.MUSIC_UPDATED";

    @Override
    public void onCreate() {
        super.onCreate();
        songPosn = 0;
        player = new MediaPlayer();
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        dbHelper = new DatabaseHelper(this);
        
        initMusicPlayer();
        initMediaSession();      // ১. মিডিয়া সেশন সেটআপ (লক স্ক্রিনের জন্য জরুরি)
        registerNoisyReceiver(); // ২. হেডফোন আনপ্লাগ রিসিভার
        initCallListener();      // ৩. কল হ্যান্ডলিং
        createNotificationChannel();
    }
    
    private void initMusicPlayer() {
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

  // 1. initMediaSession আপডেট করুন
    private void initMediaSession() {
        mediaSession = new MediaSessionCompat(this, "MusicService");
        mediaSession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() { go(); }
            @Override
            public void onPause() { pausePlayer(); }
            @Override
            public void onSkipToNext() { playNext(); }
            @Override
            public void onSkipToPrevious() { playPrev(); }
            @Override
            public void onStop() { 
                pausePlayer(); 
                stopForeground(true); 
            }
            @Override
            public void onSetRepeatMode(int mode) { toggleRepeat(); }
            
            // --- NEW: Seekbar Listener ---
            @Override
            public void onSeekTo(long pos) {
                seek((int) pos);
            }
        });
        
        mediaSession.setActive(true);
        mediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS | 
                              MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
    }

    // 2. updateMediaSessionState আপডেট করুন
    private void updateMediaSessionState() {
        if (mediaSession == null) return;

        int state = player.isPlaying() ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED;
        
        // --- NEW: ACTION_SEEK_TO যুক্ত করা হয়েছে ---
        mediaSession.setPlaybackState(new PlaybackStateCompat.Builder()
                .setState(state, player.getCurrentPosition(), 1.0f)
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE | 
                            PlaybackStateCompat.ACTION_SKIP_TO_NEXT | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                            PlaybackStateCompat.ACTION_SEEK_TO | PlaybackStateCompat.ACTION_STOP) // Seek permission added
                .build());

        Song song = getCurrentSong();
        if (song != null) {
            Bitmap art = getAlbumArt(song.getPath());
            if (art == null) {
                art = BitmapFactory.decodeResource(getResources(), R.drawable.ic_notification);
            }

            mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.getTitle())
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.getArtist())
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getDuration()) // Duration জরুরি
                    .build());
        }
    }
    // ==========================================
    // 2. HEADPHONE UNPLUG LISTENER
    // ==========================================
    private final BroadcastReceiver noisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (player != null && player.isPlaying()) {
                pausePlayer(); // হেডফোন খুললে গান পজ হবে
            }
        }
    };

    private void registerNoisyReceiver() {
        IntentFilter filter = new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);
        registerReceiver(noisyReceiver, filter);
    }

    // ==========================================
    // 3. CALL LISTENER (Auto Pause/Resume)
    // ==========================================
    private void initCallListener() {
        try {
            TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(new PhoneStateListener() {
                @Override
                public void onCallStateChanged(int state, String phoneNumber) {
                    switch (state) {
                        case TelephonyManager.CALL_STATE_RINGING:
                        case TelephonyManager.CALL_STATE_OFFHOOK:
                            if (player != null && player.isPlaying()) {
                                pausePlayer();
                                resumeAfterCall = true; // মনে রাখবে যে গান বাজছিল
                            }
                            break;
                        case TelephonyManager.CALL_STATE_IDLE:
                            if (resumeAfterCall) {
                                resumeAfterCall = false;
                                go(); // কল শেষ হলে আবার বাজবে
                            }
                            break;
                    }
                }
            }, PhoneStateListener.LISTEN_CALL_STATE);
        } catch (Exception e) { e.printStackTrace(); }
    }

    // ==========================================
    // SERVICE START & ANR FIX
    // ==========================================
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // ANR Fix: সার্ভিস চালুর সাথে সাথেই নোটিফিকেশন দেখানো
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForeground(NOTIFICATION_ID, buildNotification(false));
        }
        
        // হেডসেট বাটন ইভেন্ট হ্যান্ডল
        android.support.v4.media.session.MediaButtonReceiver.handleIntent(mediaSession, intent);

        if (intent != null && intent.getAction() != null) {
            switch (intent.getAction()) {
                case ACTION_PLAY: go(); break;
                case ACTION_PAUSE: pausePlayer(); break;
                case ACTION_NEXT: playNext(); break;
                case ACTION_PREV: playPrev(); break;
                case ACTION_FAV: toggleFavorite(); break;
                case ACTION_REPEAT: toggleRepeat(); break;
                case ACTION_CLOSE: 
                    pausePlayer();
                    stopForeground(true);
                    stopSelf();
                    break;
            }
        }
        return START_STICKY; 
    }

    // ==========================================
    // AUDIO FOCUS (Mic/Assistant Logic)
    // ==========================================
    @Override
    public void onAudioFocusChange(int focusChange) {
        if (player == null) return;
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_LOSS:
                if (player.isPlaying()) { pausePlayer(); resumeOnFocusGain = false; }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (player.isPlaying()) { 
                    player.pause(); 
                    updateNotification(false); 
                    updateMediaSessionState();
                    sendUpdateBroadcast(); 
                    resumeOnFocusGain = true; 
                }
                break;
            case AudioManager.AUDIOFOCUS_GAIN:
                if (resumeOnFocusGain && !player.isPlaying()) { 
                    player.start(); 
                    updateNotification(true); 
                    updateMediaSessionState();
                    sendUpdateBroadcast(); 
                }
                player.setVolume(1.0f, 1.0f);
                break;
        }
    }

    // ==========================================
    // PLAYBACK CONTROLS (Final Fix)
    // ==========================================

    // গান প্লে করার মেথড (ইউজার যখন ক্লিক করবে)
    public void playSong() {
        player.reset();
        resumeOnFocusGain = true; 
        
        if (songs != null && songPosn >= 0 && songPosn < songs.size()) {
            Song playSong = songs.get(songPosn);
            
            // Save Last Played Song
            dbHelper.addHistory(playSong);
            getSharedPreferences("MusicPrefs", MODE_PRIVATE)
                .edit()
                .putLong("last_song_id", playSong.getId())
                .apply();

            int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                try {
                    player.setDataSource(playSong.getPath());
                    player.prepareAsync();
                } catch (Exception e) { e.printStackTrace(); }
            }
        } else { songPosn = 0; }
    }

    // নতুন মেথড: অ্যাপ ওপেন হলে গান লোড হবে কিন্তু বাজবে না
    public void prepareCurrent() {
        player.reset();
        resumeOnFocusGain = false; // অটো প্লে বন্ধ থাকবে
        
        if (songs != null && songPosn >= 0 && songPosn < songs.size()) {
            Song playSong = songs.get(songPosn);
            try {
                player.setDataSource(playSong.getPath());
                player.prepareAsync(); 
                // onPrepared কল হবে, সেখান থেকে নোটিফিকেশন আপডেট হবে
            } catch (Exception e) { e.printStackTrace(); }
        }
    }
    
    @Override
    public void onPrepared(MediaPlayer mp) {
        // যদি resumeOnFocusGain সত্য হয় (playSong কল হলে), তবেই গান বাজবে
        if (resumeOnFocusGain) {
            mp.start();
        }
        
        // UI এবং Notification আপডেট করা জরুরি, এমনকি গান না বাজলেও
        updateNotification(mp.isPlaying()); 
        updateMediaSessionState(); 
        sendUpdateBroadcast();
    }
    
    @Override 
    public void onCompletion(MediaPlayer mp) { 
        if (player.getCurrentPosition() > 0) { 
            mp.reset(); 
            // Repeat Logic
            if (repeatMode == REPEAT_ONE) playSong();
            else if (repeatMode == REPEAT_ALL) playNext();
            else { // REPEAT_OFF
                if (songPosn < songs.size() - 1) playNext();
                else { 
                    pausePlayer();
                    seek(0);
                    updateNotification(false);
                    sendUpdateBroadcast();
                }
            }
        } 
    }
    
    @Override public boolean onError(MediaPlayer mp, int what, int extra) { mp.reset(); return false; }

    // Basic Controls
    public void pausePlayer() {
        if(player.isPlaying()) {
            player.pause();
            resumeOnFocusGain = false;
            updateNotification(false);
            updateMediaSessionState();
            sendUpdateBroadcast();
        }
    }
    public void seek(int posn) { 
        player.seekTo(posn); 
        updateNotification(isPng());
        updateMediaSessionState(); 
    }
    public void go() {
        resumeOnFocusGain = true;
        int result = audioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            player.start();
            updateNotification(true);
            updateMediaSessionState();
            sendUpdateBroadcast();
        }
    }
    public void playPrev() { 
        songPosn--; 
        if (songPosn < 0) songPosn = (songs != null && !songs.isEmpty()) ? songs.size() - 1 : 0; 
        playSong(); 
    }
    public void playNext() { 
        songPosn++; 
        if (songs != null && songPosn >= songs.size()) songPosn = 0; 
        playSong(); 
    }
    
    // Getters & Setters
    public Song getCurrentSong() { if (songs != null && songPosn >= 0 && songPosn < songs.size()) return songs.get(songPosn); return null; }
    public ArrayList<Song> getSongs() { return songs; }
    public void setSong(int songIndex) { songPosn = songIndex; }
    public int getPosn() { return player.getCurrentPosition(); }
    public int getDur() { return player.getDuration(); }
    public boolean isPng() { return player.isPlaying(); }
    public int getSongPosn() { return songPosn; } 
    public int getListSize() { return (songs != null) ? songs.size() : 0; } 
    public void setList(ArrayList<Song> theSongs) { songs = theSongs; }
    
    public class MusicBinder extends Binder { MusicService getService() { return MusicService.this; } }
    @Override public IBinder onBind(Intent intent) { return musicBind; }
    @Override public boolean onUnbind(Intent intent) { return true; }

    private void sendUpdateBroadcast() { sendBroadcast(new Intent(BROADCAST_ACTION)); }

    // Features
    private void toggleRepeat() {
        if (repeatMode == REPEAT_ALL) repeatMode = REPEAT_ONE;
        else if (repeatMode == REPEAT_ONE) repeatMode = REPEAT_OFF;
        else repeatMode = REPEAT_ALL;
        updateNotification(isPng());
        updateMediaSessionState();
        sendUpdateBroadcast();
        // Toast showing is better handled in Activity, Service just updates state
    }

    private void toggleFavorite() {
        Song currentSong = getCurrentSong();
        if (currentSong != null) {
            if (dbHelper.isFavorite(currentSong.getId())) dbHelper.removeFavorite(currentSong.getId());
            else dbHelper.addFavorite(currentSong);
            updateNotification(isPng());
            sendUpdateBroadcast();
        }
    }
    public int getRepeatMode() { return repeatMode; }

    // ==========================================
    // 4. NATIVE NOTIFICATION (MediaStyle)
    // ==========================================
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW);
                channel.setShowBadge(false);
                channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC); 
                nm.createNotificationChannel(channel);
            }
        }
    }

    private void updateNotification(boolean isPlaying) {
        Notification notif = buildNotification(isPlaying);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, notif);
    }

    private Notification buildNotification(boolean isPlaying) {
        Song song = getCurrentSong();
        String title = (song != null) ? song.getTitle() : "Music Player";
        String artist = (song != null) ? song.getArtist() : "Select a song";
        Bitmap art = (song != null) ? getAlbumArt(song.getPath()) : null;
        if (art == null) art = BitmapFactory.decodeResource(getResources(), R.drawable.ic_notification);

        // Intent Setup
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, flags);

        // Notification Builder
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(artist)
                .setLargeIcon(art)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW);

        // --- Action Buttons Logic (Updated) ---

        // 1. Repeat Action (Custom Icon: System has no default repeat icon)
        // Logic Optimized: 3-State Toggle
        int repeatResId;
        if (repeatMode == REPEAT_ONE) repeatResId = R.drawable.ic_repeat_one;
        else if (repeatMode == REPEAT_OFF) repeatResId = R.drawable.ic_repeat_off;
        else repeatResId = R.drawable.ic_repeat_all;
        builder.addAction(repeatResId, "Repeat", playbackAction(6));

        // 2. Previous Action (SYSTEM DEFAULT ICON)
        builder.addAction(android.R.drawable.ic_media_previous, "Prev", playbackAction(3));

        // 3. Play/Pause Action (SYSTEM DEFAULT ICONS)
        if (isPlaying) {
            builder.addAction(android.R.drawable.ic_media_pause, "Pause", playbackAction(1));
        } else {
            builder.addAction(android.R.drawable.ic_media_play, "Play", playbackAction(0));
        }

        // 4. Next Action (SYSTEM DEFAULT ICON)
        builder.addAction(android.R.drawable.ic_media_next, "Next", playbackAction(2));

        // 5. Favorite Action (Custom Icon: System has no heart icon)
        // Logic Optimized: Checks DB directly
        boolean isFav = (song != null) && dbHelper.isFavorite(song.getId());
        int favResId = isFav ? R.drawable.ic_heart_filled : R.drawable.ic_heart_empty;
        builder.addAction(favResId, "Fav", playbackAction(5));

        // MediaStyle Applied
        builder.setStyle(new android.support.v4.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken())
                // Compact View: Show Prev(1), Play(2), Next(3)
                .setShowActionsInCompactView(1, 2, 3)); 

        return builder.build();
    }
    
    /// album image
    private Bitmap getAlbumArt(String path) {
        MediaMetadataRetriever mmr = new MediaMetadataRetriever();
        try {
            mmr.setDataSource(path);
            byte[] data = mmr.getEmbeddedPicture();
            if (data != null) return BitmapFactory.decodeByteArray(data, 0, data.length);
        } catch (Exception e) { }
        return null;
    }

    private PendingIntent playbackAction(int actionNumber) {
        Intent playbackAction = new Intent(this, MusicService.class);
        switch (actionNumber) {
            case 0: playbackAction.setAction(ACTION_PLAY); break;
            case 1: playbackAction.setAction(ACTION_PAUSE); break;
            case 2: playbackAction.setAction(ACTION_NEXT); break;
            case 3: playbackAction.setAction(ACTION_PREV); break;
            case 4: playbackAction.setAction(ACTION_CLOSE); break;
            case 5: playbackAction.setAction(ACTION_FAV); break;
            case 6: playbackAction.setAction(ACTION_REPEAT); break;
        }
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, actionNumber, playbackAction, flags);
    }

    @Override public void onTaskRemoved(Intent rootIntent) { super.onTaskRemoved(rootIntent); }
    
    @Override public void onDestroy() {
        if (player != null) { player.release(); player = null; }
        if (mediaSession != null) { mediaSession.release(); }
        try { unregisterReceiver(noisyReceiver); } catch (Exception e) {}
        stopForeground(true);
        if (audioManager != null) audioManager.abandonAudioFocus(this);
        super.onDestroy();
    }
}