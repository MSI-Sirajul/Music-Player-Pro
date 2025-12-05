package com.my.music;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.transition.TransitionManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class MainActivity extends Activity {

    // Data
    private ArrayList<Song> masterSongList;
    private ArrayList<Song> displayList;
    private ArrayList<String> folderList;
    private Map<String, ArrayList<Song>> folderMap;
    
    // UI
    private RelativeLayout headerLayout;
    private ListView songView;
    private ImageButton btnMenu, btnBackHeader;
    private EditText etSearch;
    private TextView tabTracks, tabFolders, tabFavorites, tvHeaderTitle;
    
    // Mini Player
    private View miniPlayerLayout;
    private TextView miniTitle, miniArtist;
    private ImageView miniArt;
    private ImageButton btnMiniPlay, btnMiniNext;
    private ProgressBar progressBar;

    // Logic
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound = false;
    private DatabaseHelper dbHelper;
    private CustomAdapter adapter;
    private SharedPreferences prefs;

    // State
    private int currentTab = 0; 
    private boolean isInsideFolder = false; 
    private String currentFolderName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        prefs = getSharedPreferences("MusicPrefs", MODE_PRIVATE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Init Data
        masterSongList = new ArrayList<>();
        displayList = new ArrayList<>();
        folderList = new ArrayList<>();
        folderMap = new HashMap<>();
        dbHelper = new DatabaseHelper(this);

        // Init Views
        headerLayout = findViewById(R.id.headerLayout);
        songView = findViewById(R.id.songListView);
        tvHeaderTitle = findViewById(R.id.tvHeaderTitle);
        etSearch = findViewById(R.id.etSearch);
        btnMenu = findViewById(R.id.btnMenu);
        
        // Header Icon (Only Back Button exists now)
        btnBackHeader = findViewById(R.id.btnBackHeader);
        
        progressBar = findViewById(R.id.progressBar);
        
        tabTracks = findViewById(R.id.tabTracks);
        tabFolders = findViewById(R.id.tabFolders);
        tabFavorites = findViewById(R.id.tabFavorites);
        
        miniPlayerLayout = findViewById(R.id.miniPlayerLayout);
        miniTitle = findViewById(R.id.mini_title);
        miniArtist = findViewById(R.id.mini_artist);
        miniArt = findViewById(R.id.mini_art);
        btnMiniPlay = findViewById(R.id.mini_play);
        btnMiniNext = findViewById(R.id.mini_next);

        setupClickListeners();
        checkPermissions();
        
        // Initial Header State
        tvHeaderTitle.setText("Music Player Pro");
        btnBackHeader.setVisibility(View.GONE); 
    }

    // ==========================================
    // ANIMATIONS
    // ==========================================
    private void animateTitle(final String newText) {
        tvHeaderTitle.animate()
            .alpha(0f)
            .translationX(20f)
            .setDuration(150)
            .withEndAction(new Runnable() {
                @Override
                public void run() {
                    tvHeaderTitle.setText(newText);
                    tvHeaderTitle.setTranslationX(-20f); 
                    tvHeaderTitle.animate()
                        .alpha(1f)
                        .translationX(0f)
                        .setDuration(150)
                        .start();
                }
            }).start();
    }

    // ==========================================
    // CUSTOM MENU
    // ==========================================
    private void showCustomMenu() {
        View menuView = LayoutInflater.from(this).inflate(R.layout.layout_popup_menu, null);

        final PopupWindow popup = new PopupWindow(
                menuView, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                true
        );
        
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); 
        popup.setElevation(20);
        popup.showAsDropDown(btnMenu, -20, 0); 

        menuView.findViewById(R.id.actionTheme).setOnClickListener(v -> {
            popup.dismiss();
            toggleTheme();
        });

        menuView.findViewById(R.id.actionShare).setOnClickListener(v -> {
            popup.dismiss();
            shareApp();
        });

        menuView.findViewById(R.id.actionAbout).setOnClickListener(v -> {
            popup.dismiss();
            showAboutDialog();
        });
    }

    // ==========================================
    // ABOUT DIALOG (CUSTOM) - UPDATED
    // ==========================================
    private void showAboutDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about);
        
        // Make background transparent for rounded corners
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        
        // Find Views
        ImageButton btnWeb = dialog.findViewById(R.id.btnWeb);
        ImageButton btnTelegram = dialog.findViewById(R.id.btnTelegram);
        ImageButton btnGithub = dialog.findViewById(R.id.btnGithub);
        Button btnClose = dialog.findViewById(R.id.btnCloseAbout);
        TextView tvVersion = dialog.findViewById(R.id.tvVersion);

        // Dynamic Version Name
        try {
            String versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            tvVersion.setText("Version " + versionName);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // 1. Website
        btnWeb.setOnClickListener(v -> openUrl("https://md-sirajul-islam.vercel.app/"));

        // 2. Telegram
        btnTelegram.setOnClickListener(v -> {
            String telegramUser = "msi_tech_updates";
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("tg://resolve?domain=" + telegramUser));
                startActivity(intent);
            } catch (Exception e) {
                openUrl("https://t.me/" + telegramUser);
            }
        });

        // 3. GitHub
        btnGithub.setOnClickListener(v -> openUrl("https://github.com/MSI-Sirajul"));

        // Close
        btnClose.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void openUrl(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            startActivity(intent);
        } catch (Exception e) {
            // No browser or app found
        }
    }

    private void toggleTheme() {
        boolean current = prefs.getBoolean("isDark", false);
        prefs.edit().putBoolean("isDark", !current).apply();
        UiModeManager uiManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            uiManager.setApplicationNightMode(current ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
        } else {
            recreate();
        }
    }

    private void shareApp() {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "Music Player Pro");
            i.putExtra(Intent.EXTRA_TEXT, "Listen to music with style! Download here.\n App-Link: https://github.com/MSI-Sirajul/Music-Player-Pro/");
            startActivity(Intent.createChooser(i, "Share via"));
        } catch(Exception e) { }
    }

    // ==========================================
    // NAVIGATION
    // ==========================================
    @Override
    public void onBackPressed() {
        if (isInsideFolder && currentTab == 1) {
            switchTab(1); // Go back to folder list
            return;
        }
        super.onBackPressed();
    }

    // ==========================================
    // UI SETUP
    // ==========================================
    private void setupClickListeners() {
        btnMenu.setOnClickListener(v -> showCustomMenu());
        
        // Header Back Button Logic
        btnBackHeader.setOnClickListener(v -> onBackPressed());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        tabTracks.setOnClickListener(v -> switchTab(0));
        tabFolders.setOnClickListener(v -> switchTab(1));
        tabFavorites.setOnClickListener(v -> switchTab(2));

        miniPlayerLayout.setOnClickListener(v -> {
            if (musicBound && musicSrv != null && musicSrv.getCurrentSong() != null) {
                startActivity(new Intent(MainActivity.this, PlayerActivity.class));
                overridePendingTransition(R.anim.slide_in_up, 0);
            }
        });
        
        btnMiniPlay.setOnClickListener(v -> {
            if (musicBound && musicSrv != null) {
                if (musicSrv.isPng()) musicSrv.pausePlayer();
                else musicSrv.go();
            }
        });

        btnMiniNext.setOnClickListener(v -> {
            if (musicBound && musicSrv != null) musicSrv.playNext();
        });
    }

    private void switchTab(int tab) {
        currentTab = tab;
        isInsideFolder = false;
        
        tabTracks.setSelected(tab == 0);
        tabFolders.setSelected(tab == 1);
        tabFavorites.setSelected(tab == 2);
        
        // HEADER RESET
        animateTitle("Music Player Pro"); 
        btnBackHeader.setVisibility(View.GONE); 
        
        etSearch.setText("");
        etSearch.setHint("Search song or folder...");

        displayList.clear();
        
        if (tab == 0) {
            displayList.addAll(masterSongList);
            setupAdapter();
        } else if (tab == 1) {
            setupFolderAdapter();
        } else if (tab == 2) {
            displayList.addAll(dbHelper.getAllFavorites());
            setupAdapter();
        }
    }

    private void filterList(String query) {
        displayList.clear();
        String lowerQuery = query.toLowerCase();
        
        ArrayList<Song> source;
        if(isInsideFolder && currentTab == 1) source = folderMap.get(currentFolderName);
        else if (currentTab == 2) source = dbHelper.getAllFavorites();
        else source = masterSongList;

        if (source != null) {
            if (query.isEmpty()) displayList.addAll(source);
            else {
                for (Song s : source) {
                    if (s.getTitle().toLowerCase().contains(lowerQuery) || 
                        s.getArtist().toLowerCase().contains(lowerQuery)) {
                        displayList.add(s);
                    }
                }
            }
        }
        if(adapter != null) adapter.notifyDataSetChanged();
    }

    private void setupAdapter() {
        adapter = new CustomAdapter(this, displayList);
        songView.setAdapter(adapter);
        
        songView.setOnItemClickListener((parent, view, position, id) -> {
            musicSrv.setList(new ArrayList<>(displayList));
            musicSrv.setSong(position);
            musicSrv.playSong();
            startActivity(new Intent(MainActivity.this, PlayerActivity.class));
            overridePendingTransition(R.anim.zoom_in, 0);
        });
    }

    private void setupFolderAdapter() {
        FolderAdapter folderAdapter = new FolderAdapter();
        songView.setAdapter(folderAdapter);
        
        songView.setOnItemClickListener((parent, view, position, id) -> {
            String folderName = folderList.get(position);
            currentFolderName = folderName;
            
            displayList.clear();
            displayList.addAll(folderMap.get(folderName));
            
            isInsideFolder = true;
            setupAdapter(); 
            
            // SHOW BACK BUTTON
            animateTitle(folderName);
            btnBackHeader.setVisibility(View.VISIBLE);
            
            etSearch.setHint("Search in " + folderName + "...");
        });
    }

    class CustomAdapter extends BaseAdapter {
        private ArrayList<Song> songs;
        private LayoutInflater inflater;
        public CustomAdapter(Context c, ArrayList<Song> theSongs) { songs = theSongs; inflater = LayoutInflater.from(c); }
        @Override public int getCount() { return songs.size(); }
        @Override public Object getItem(int arg0) { return null; }
        @Override public long getItemId(int arg0) { return 0; }
        @Override public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = inflater.inflate(R.layout.song_item, parent, false);
            
            TextView title = convertView.findViewById(R.id.songTitle);
            TextView artist = convertView.findViewById(R.id.songArtist);
            TextView folder = convertView.findViewById(R.id.songFolder);
            ImageView art = convertView.findViewById(R.id.imgThumbnail);
            ImageView playing = convertView.findViewById(R.id.playingIndicator);

            Song currSong = songs.get(position);
            title.setText(currSong.getTitle());
            artist.setText(currSong.getArtist());
            
            try {
                String parentPath = new File(currSong.getPath()).getParent();
                folder.setText(new File(parentPath).getName());
            } catch (Exception e) { folder.setText("Music"); }

            try {
                Uri artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), currSong.getAlbumId());
                art.setImageURI(artworkUri);
                if (art.getDrawable() == null) art.setImageResource(R.drawable.ic_current_play);
            } catch (Exception e) { art.setImageResource(R.drawable.ic_current_play); }

            if (musicBound && musicSrv.getCurrentSong() != null && musicSrv.getCurrentSong().getId() == currSong.getId()) {
                playing.setVisibility(View.VISIBLE);
                title.setTextColor(getResources().getColor(R.color.md_theme_primary));
            } else {
                playing.setVisibility(View.GONE);
                title.setTextColor(getResources().getColor(R.color.md_theme_onSurface));
            }
            return convertView;
        }
    }

    class FolderAdapter extends BaseAdapter {
        @Override public int getCount() { return folderList.size(); }
        @Override public Object getItem(int i) { return null; }
        @Override public long getItemId(int i) { return 0; }
        @Override public View getView(int i, View v, ViewGroup p) {
            if(v==null) v = getLayoutInflater().inflate(R.layout.song_item, p, false);
            TextView title = v.findViewById(R.id.songTitle);
            TextView subtitle = v.findViewById(R.id.songArtist);
            ImageView icon = v.findViewById(R.id.imgThumbnail);
            TextView folder = v.findViewById(R.id.songFolder);
            
            String folderName = folderList.get(i);
            title.setText(folderName);
            subtitle.setText(folderMap.get(folderName).size() + " songs");
            folder.setVisibility(View.GONE); 
            icon.setImageResource(R.drawable.ic_folder); 
            icon.setBackground(null);
            title.setTextColor(getResources().getColor(R.color.md_theme_onSurface));
            v.findViewById(R.id.playingIndicator).setVisibility(View.GONE);
            return v;
        }
    }

    // Service & Loading Logic
    private void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> perms = new ArrayList<>();
            if (Build.VERSION.SDK_INT >= 33) {
                if (checkSelfPermission("android.permission.READ_MEDIA_AUDIO") != PackageManager.PERMISSION_GRANTED) perms.add("android.permission.READ_MEDIA_AUDIO");
                if (checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED) perms.add("android.permission.POST_NOTIFICATIONS");
            } else {
                if (checkSelfPermission("android.permission.READ_EXTERNAL_STORAGE") != PackageManager.PERMISSION_GRANTED) perms.add("android.permission.READ_EXTERNAL_STORAGE");
            }
            if (Build.VERSION.SDK_INT >= 28) {
                if (checkSelfPermission("android.permission.FOREGROUND_SERVICE") != PackageManager.PERMISSION_GRANTED) perms.add("android.permission.FOREGROUND_SERVICE");
            }
            if (!perms.isEmpty()) requestPermissions(perms.toArray(new String[0]), 1);
            else loadSongsAsync();
        } else {
            loadSongsAsync();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) loadSongsAsync();
    }

    private void loadSongsAsync() {
        progressBar.setVisibility(View.VISIBLE);
        songView.setVisibility(View.GONE);
        new Thread(() -> {
            loadSongs();
            new Handler(Looper.getMainLooper()).post(() -> {
                progressBar.setVisibility(View.GONE);
                songView.setVisibility(View.VISIBLE);
                switchTab(0);
                if(musicBound) updateMiniPlayer();
            });
        }).start();
    }

    private void loadSongs() {
        masterSongList.clear(); folderMap.clear(); folderList.clear();
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, MediaStore.Audio.Media.TITLE + " ASC");
        if (musicCursor != null && musicCursor.moveToFirst()) {
            int titleCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int idCol = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int pathCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int albumCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            do {
                long thisId = musicCursor.getLong(idCol);
                String thisTitle = musicCursor.getString(titleCol);
                String thisArtist = musicCursor.getString(artistCol);
                String thisPath = musicCursor.getString(pathCol);
                long thisAlbumId = musicCursor.getLong(albumCol);
                Song newSong = new Song(thisId, thisTitle, thisArtist, thisPath, thisAlbumId);
                masterSongList.add(newSong);
                try {
                    String folderPath = new File(thisPath).getParent();
                    String folderName = new File(folderPath).getName();
                    if (!folderMap.containsKey(folderName)) {
                        folderMap.put(folderName, new ArrayList<>());
                        folderList.add(folderName);
                    }
                    folderMap.get(folderName).add(newSong);
                } catch (Exception e) {}
            } while (musicCursor.moveToNext());
            musicCursor.close();
        }
    }

    private ServiceConnection musicConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder)service;
            musicSrv = binder.getService();
            if(masterSongList.size() > 0 && musicSrv.getCurrentSong() == null) musicSrv.setList(new ArrayList<>(masterSongList));
            musicBound = true;
            updateMiniPlayer();
        }
        @Override public void onServiceDisconnected(ComponentName name) { musicBound = false; }
    };

    private BroadcastReceiver updateReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            updateMiniPlayer();
            if(adapter != null && !isInsideFolder) adapter.notifyDataSetChanged(); 
        }
    };

    private void updateMiniPlayer() {
        if (musicBound && musicSrv != null && musicSrv.getCurrentSong() != null) {
            miniPlayerLayout.setVisibility(View.VISIBLE);
            Song s = musicSrv.getCurrentSong();
            miniTitle.setText(s.getTitle());
            miniArtist.setText(s.getArtist());
            btnMiniPlay.setImageResource(musicSrv.isPng() ? R.drawable.ic_pause : R.drawable.ic_play);
            try {
                Uri artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.getAlbumId());
                miniArt.setImageURI(artworkUri);
                if (miniArt.getDrawable() == null) miniArt.setImageResource(R.drawable.ic_notification);
            } catch (Exception e) {}
        } else { miniPlayerLayout.setVisibility(View.GONE); }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent playIntent = new Intent(this, MusicService.class);
        bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(playIntent);
        else startService(playIntent);
        registerReceiver(updateReceiver, new IntentFilter(MusicService.BROADCAST_ACTION));
    }
    @Override
    protected void onResume() {
        super.onResume();
        if(currentTab == 2) switchTab(2);
        if(musicBound) updateMiniPlayer();
    }
    @Override
    protected void onDestroy() {
        if (musicBound) unbindService(musicConnection);
        unregisterReceiver(updateReceiver);
        super.onDestroy();
    }
}