package com.my.music;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import java.util.ArrayList;

public class MusicService extends Service implements MediaPlayer.OnCompletionListener {

    private MediaPlayer player;
    private ArrayList<Song> songs;
    private int songPosn;
    private final IBinder musicBind = new MusicBinder();
    private static final String CHANNEL_ID = "MusicChannel";
    
    // Broadcast Action
    public static final String BROADCAST_ACTION = "com.my.music.MUSIC_UPDATED";

    @Override
    public void onCreate() {
        super.onCreate();
        songPosn = 0;
        player = new MediaPlayer();
        initMusicPlayer();
    }

    public void initMusicPlayer() {
        player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        player.setOnCompletionListener(this);
    }

    public void setList(ArrayList<Song> theSongs) {
        songs = theSongs;
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return true;
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
        if (player.getCurrentPosition() > 0) {
            mp.reset();
            playNext();
        }
    }

    public void playSong() {
        player.reset();
        Song playSong = songs.get(songPosn);
        try {
            player.setDataSource(playSong.getPath());
            player.prepare();
            player.start();
            showNotification();
            sendUpdateBroadcast();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void setSong(int songIndex) {
        songPosn = songIndex;
    }

    public int getSongPosn() {
        return songPosn;
    }

    public int getPosn() {
        return player.getCurrentPosition();
    }

    public int getDur() {
        return player.getDuration();
    }

    public boolean isPng() {
        return player.isPlaying();
    }

    public void pausePlayer() {
        player.pause();
        showNotification();
        sendUpdateBroadcast();
    }

    public void seek(int posn) {
        player.seekTo(posn);
    }

    public void go() {
        player.start();
        showNotification();
        sendUpdateBroadcast();
    }

    public void playPrev() {
        songPosn--;
        if (songPosn < 0) songPosn = songs.size() - 1;
        playSong();
    }

    public void playNext() {
        songPosn++;
        if (songPosn >= songs.size()) songPosn = 0;
        playSong();
    }
    
    public Song getCurrentSong() {
        if(songs != null && songs.size() > 0) return songs.get(songPosn);
        return null;
    }

    private void sendUpdateBroadcast() {
        Intent intent = new Intent(BROADCAST_ACTION);
        sendBroadcast(intent);
    }

    private void showNotification() {
        Intent notIntent = new Intent(this, PlayerActivity.class);
        notIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendInt = PendingIntent.getActivity(this, 0,
                notIntent, PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Music Player", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Playing Music");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
            builder = new Notification.Builder(this, CHANNEL_ID);
        } else {
            builder = new Notification.Builder(this);
        }

        if (songs != null && songs.size() > 0) {
            Song s = songs.get(songPosn);
            builder.setContentIntent(pendInt)
                    .setSmallIcon(R.drawable.ic_notification) // Using your specific icon
                    .setTicker(s.getTitle())
                    .setOngoing(true)
                    .setContentTitle(s.getTitle())
                    .setContentText(s.getArtist());

            Notification not = builder.build();
            startForeground(1, not);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Keeps service running until explicitly stopped
        return START_STICKY; 
    }

    @Override
    public void onDestroy() {
        stopForeground(true);
        super.onDestroy();
    }
}