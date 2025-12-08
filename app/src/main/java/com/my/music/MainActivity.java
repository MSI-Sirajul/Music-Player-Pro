package com.my.music;

import android.Manifest;
import android.animation.ObjectAnimator;
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
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.LinearInterpolator;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Collections;
import java.util.Comparator;

public class MainActivity extends Activity {

    // Data
    private ArrayList<Song> masterSongList;
    private ArrayList<Song> displayList;
    private ArrayList<String> folderList;
    private Map<String, ArrayList<Song>> folderMap;
    // ... আগের ভেরিয়েবলগুলো ...
    
    // === NEW: CACHE LISTS ===
    private ArrayList<Song> cachedTracks = new ArrayList<>();
    private ArrayList<Song> cachedFavorites = new ArrayList<>();
    private ArrayList<Song> cachedHistory = new ArrayList<>();
    
    // UI
    private RelativeLayout headerLayout;
    private ListView songView;
    private ImageButton btnMenu, btnBackHeader;
    private EditText etSearch;
    private TextView tabTracks, tabFolders, tabFavorites, tabHistory, tvHeaderTitle;
    // Mini Player
    private View miniPlayerLayout;
    private TextView miniTitle, miniArtist;
    private ImageView miniArt;
    private ImageButton btnMiniPlay, btnMiniNext, btnMiniPrev;
    private ProgressBar progressBar;
    private LinearLayout miniTextLayout; 

    // Logic & Services
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound = false;
    private DatabaseHelper dbHelper;
    private CustomAdapter adapter;
    private SharedPreferences prefs;

    // Animation & Gestures
    private ObjectAnimator rotateAnimator; // For Circular Rotation
    private float x1, x2; // For Swipe Detection
    private static final int MIN_DISTANCE = 150;

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
        btnMiniPrev = findViewById(R.id.mini_prev);
        miniTextLayout = findViewById(R.id.mini_text_layout);
        // tab history include
        tabHistory = findViewById(R.id.tabHistory); 
        tabHistory.setOnClickListener(v -> switchTab(3)); // 3 = History Tab

        // Initialize Animator
        initRotationAnimator();

        setupClickListeners();
        setupMiniPlayerSwipe(); // Swipe Logic Added

        // Initial State
        tvHeaderTitle.setText("Music Player Pro");
        btnBackHeader.setVisibility(View.GONE); 
        
        checkPermissions();
    }

    // ==========================================
    // MINI PLAYER ANIMATION & SWIPE
    // ==========================================

    private void initRotationAnimator() {
        rotateAnimator = ObjectAnimator.ofFloat(miniArt, "rotation", 0f, 360f);
        rotateAnimator.setDuration(10000); // 10 seconds for full circle
        rotateAnimator.setRepeatCount(ObjectAnimator.INFINITE);
        rotateAnimator.setInterpolator(new LinearInterpolator());
    }

    // ভেরিয়েবল ডিক্লেয়ারেশন (ক্লাসের উপরে যদি না থাকে)
    private float dX, startX;
    
    private void setupMiniPlayerSwipe() {
        miniPlayerLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getRawX();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        // টাচ পুরো মিনি প্লেয়ারে হচ্ছে, কিন্তু নড়বে শুধু টেক্সট
                        float currentX = event.getRawX();
                        float displacement = currentX - startX;
                        
                        // শুধুমাত্র টেক্সট লেআউট মুভ হবে
                        miniTextLayout.setTranslationX(displacement);
                        
                        // অপশনাল: টেক্সট একটু ঝাপসা হবে সরানোর সময়
                        miniTextLayout.setAlpha(1 - Math.abs(displacement) / 500f);
                        return true;

                    case MotionEvent.ACTION_UP:
                        float endX = event.getRawX();
                        float diffX = endX - startX;
                        float threshold = v.getWidth() / 4; // অল্প টানলেই কাজ করবে

                        if (Math.abs(diffX) > threshold) {
                            // সোয়াইপ সফল হলে
                            float exitTo = (diffX > 0) ? v.getWidth() : -v.getWidth();
                            
                            // ১. টেক্সট স্লাইড করে বের করে দেওয়া
                            miniTextLayout.animate()
                                .translationX(exitTo)
                                .alpha(0f)
                                .setDuration(200)
                                .withEndAction(() -> {
                                    if (musicBound && musicSrv != null) {
                                        startMusicService();
                                        // ২. গান পরিবর্তন
                                        if (diffX > 0) musicSrv.playPrev();
                                        else musicSrv.playNext();
                                        
                                        // ৩. UI আপডেট (নতুন নাম সেট হবে)
                                        updateMiniPlayer(); 
                                        
                                        // ৪. টেক্সট উল্টো দিক থেকে আবার আসবে (Reset & Slide In)
                                        miniTextLayout.setTranslationX(diffX > 0 ? -v.getWidth() : v.getWidth());
                                        miniTextLayout.animate()
                                            .translationX(0)
                                            .alpha(1f)
                                            .setDuration(300)
                                            .start();
                                    }
                                }).start();
                        } else {
                            // সোয়াইপ না হলে বা ক্লিক হলে
                            if (Math.abs(diffX) < 10) {
                                if (musicBound && musicSrv != null && musicSrv.getCurrentSong() != null) {
                                    startActivity(new Intent(MainActivity.this, PlayerActivity.class));
                                    overridePendingTransition(R.anim.slide_in_up, 0);
                                }
                            }
                            // বাউন্স ব্যাক (আগের জায়গায় ফিরে আসবে)
                            miniTextLayout.animate().translationX(0).alpha(1f).setDuration(200).start();
                        }
                        return true;
                }
                return false;
            }
        });
    }
    // ==========================================
    // PERMISSIONS & LOADING
    // ==========================================
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
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadSongsAsync();
        } else {
            Toast.makeText(this, "Permission Required", Toast.LENGTH_SHORT).show();
        }
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
        
        // সব মেটাডেটা প্রজেকশন
        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED
        };

        Cursor musicCursor = musicResolver.query(musicUri, projection, null, null, null);

        if (musicCursor != null && musicCursor.moveToFirst()) {
            int idCol = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int titleCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int artistCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            int pathCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int albumCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID);
            int durCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DURATION);
            int sizeCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.SIZE);
            int dateCol = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED);

            do {
                long thisId = musicCursor.getLong(idCol);
                String thisTitle = musicCursor.getString(titleCol);
                String thisArtist = musicCursor.getString(artistCol);
                String thisPath = musicCursor.getString(pathCol);
                long thisAlbumId = musicCursor.getLong(albumCol);
                long thisDuration = musicCursor.getLong(durCol);
                long thisSize = musicCursor.getLong(sizeCol);
                long thisDate = musicCursor.getLong(dateCol);

                Song newSong = new Song(thisId, thisTitle, thisArtist, thisPath, thisAlbumId, thisDuration, thisSize, thisDate);
                masterSongList.add(newSong);

                // Folder Logic (Same as before)
                try {
                    String folderPath = new java.io.File(thisPath).getParent();
                    String folderName = new java.io.File(folderPath).getName();
                    if (!folderMap.containsKey(folderName)) {
                        folderMap.put(folderName, new ArrayList<>());
                        folderList.add(folderName);
                    }
                    folderMap.get(folderName).add(newSong);
                } catch (Exception e) {}

            } while (musicCursor.moveToNext());
            musicCursor.close();
        }
        
        // ডিফল্ট সর্টিং (Name A-Z) লোড হওয়ার পর
        sortList();
    }
    
    // ==========================================
    // UI INTERACTIONS
    // ==========================================
    private void setupClickListeners() {
        btnMenu.setOnClickListener(v -> showCustomMenu());
        btnBackHeader.setOnClickListener(v -> onBackPressed());

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterList(s.toString()); }
            @Override public void afterTextChanged(Editable s) {}
        });

        tabTracks.setOnClickListener(v -> switchTab(0));
        tabFolders.setOnClickListener(v -> switchTab(1));
        tabFavorites.setOnClickListener(v -> switchTab(2));

        // Note: miniPlayerLayout click is now handled inside setupMiniPlayerSwipe()
        btnMiniPrev.setOnClickListener(v -> {
            if (musicBound && musicSrv != null) {
                startMusicService();
                musicSrv.playPrev();
                updateMiniPlayer(); 
            }
        });
                
        btnMiniPlay.setOnClickListener(v -> {
            if (musicBound && musicSrv != null) {
                // প্লে করার আগে সার্ভিস স্টার্ট এনশিওর করা
                startMusicService();
                
                if (musicSrv.isPng()) musicSrv.pausePlayer();
                else musicSrv.go();
                updateMiniPlayer();
            }
        });

        btnMiniNext.setOnClickListener(v -> {
            if (musicBound && musicSrv != null) {
                startMusicService();
                musicSrv.playNext();
                updateMiniPlayer();
            }
        });
    }

    // ==========================================
    // ANIMATIONS & MENU
    // ==========================================
    private void animateTitle(final String newText) {
        tvHeaderTitle.animate()
            .alpha(0f).translationX(20f).setDuration(150)
            .withEndAction(() -> {
                tvHeaderTitle.setText(newText);
                tvHeaderTitle.setTranslationX(-20f); 
                tvHeaderTitle.animate().alpha(1f).translationX(0f).setDuration(150).start();
            }).start();
    }

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

        // Existing Actions
        menuView.findViewById(R.id.actionTheme).setOnClickListener(v -> { popup.dismiss(); toggleTheme(); });
        menuView.findViewById(R.id.actionShare).setOnClickListener(v -> { popup.dismiss(); shareApp(); });
        menuView.findViewById(R.id.actionAbout).setOnClickListener(v -> { popup.dismiss(); showAboutDialog(); });

        // === NEW ACTIONS ===
        // আপনার layout_popup_menu.xml এ এই আইডিগুলো থাকতে হবে
        View btnSort = menuView.findViewById(R.id.actionSort);
        View btnEq = menuView.findViewById(R.id.actionEqualizer);

        if(btnSort != null) {
            btnSort.setOnClickListener(v -> {
                popup.dismiss();
                showSortDialog();
            });
        }

        if(btnEq != null) {
            btnEq.setOnClickListener(v -> {
                popup.dismiss();
                openEqualizer();
            });
        }
    }

    // System Equalizer Opening Logic
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

    private void showAboutDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_about);
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        
        ImageButton btnWeb = dialog.findViewById(R.id.btnWeb);
        ImageButton btnTelegram = dialog.findViewById(R.id.btnTelegram);
        ImageButton btnGithub = dialog.findViewById(R.id.btnGithub);
        Button btnClose = dialog.findViewById(R.id.btnCloseAbout);
        TextView tvVersion = dialog.findViewById(R.id.tvVersion);
        
        // Device Info
        ((TextView)dialog.findViewById(R.id.infoBrand)).setText(Build.BRAND.toUpperCase());
        ((TextView)dialog.findViewById(R.id.infoModel)).setText(Build.MODEL);
        ((TextView)dialog.findViewById(R.id.infoAndroid)).setText(Build.VERSION.RELEASE);
        ((TextView)dialog.findViewById(R.id.infoBuild)).setText(Build.ID);			
        try {
            tvVersion.setText(getPackageManager().getPackageInfo(getPackageName(), 0).versionName);
        } catch (Exception e) {}

        btnWeb.setOnClickListener(v -> openUrl("https://msi-sirajul.github.io/Music-Player-Pro/"));
        btnTelegram.setOnClickListener(v -> openUrl("https://t.me/msi_tech_updates"));
        btnGithub.setOnClickListener(v -> openUrl("https://github.com/MSI-Sirajul/Music-Player-Pro"));
        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void openUrl(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); } catch (Exception e) {}
    }

    private void toggleTheme() {
        boolean current = prefs.getBoolean("isDark", false);
        prefs.edit().putBoolean("isDark", !current).apply();
        UiModeManager uiManager = (UiModeManager) getSystemService(Context.UI_MODE_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            uiManager.setApplicationNightMode(current ? UiModeManager.MODE_NIGHT_NO : UiModeManager.MODE_NIGHT_YES);
        } else { recreate(); }
    }

    private void shareApp() {
        try {
            Intent i = new Intent(Intent.ACTION_SEND);
            i.setType("text/plain");
            i.putExtra(Intent.EXTRA_SUBJECT, "Music Player");
            i.putExtra(Intent.EXTRA_TEXT, "The Purest Audio Experience...🥰\nNative Android Music Player built with Material Design 3 aesthetics.⚙️\nNo External Libraries.📚\nNo Ads. Just pure performance.!🎼\nDownload: https://msi-sirajul.github.io/Music-Player-Pro/ ");
            startActivity(Intent.createChooser(i, "Share via"));
        } catch(Exception e) { }
    }

    // ==========================================
    // NAVIGATION
    // ==========================================
    @Override
    public void onBackPressed() {
        if (isInsideFolder && currentTab == 1) {
            switchTab(1); 
            return;
        }
        super.onBackPressed();
    }

    private void switchTab(int tab) {
        currentTab = tab;
        isInsideFolder = false;
        
        // UI Updates
        tabTracks.setSelected(tab == 0);
        tabFolders.setSelected(tab == 1);
        tabFavorites.setSelected(tab == 2);
        tabHistory.setSelected(tab == 3);
        
        animateTitle("Music Player Pro"); 
        btnBackHeader.setVisibility(View.GONE); 
        etSearch.setText("");
        etSearch.setHint("Search song or folder...");

        // === INSTANT SWITCH LOGIC (CACHE CHECK) ===
        if (tab == 0 && !cachedTracks.isEmpty()) {
            displayList.clear();
            displayList.addAll(cachedTracks);
            setupAdapter();
            return; // ক্যাশ থেকে লোড হয়েছে, তাই রিটার্ন
        } 
        else if (tab == 2 && !cachedFavorites.isEmpty()) {
            displayList.clear();
            displayList.addAll(cachedFavorites);
            setupAdapter();
            return;
        }
        else if (tab == 3 && !cachedHistory.isEmpty()) {
            displayList.clear();
            displayList.addAll(cachedHistory);
            setupAdapter();
            return;
        }

        // ক্যাশ না থাকলে নতুন করে লোড হবে
        if (tab == 1) {
            displayList.clear();
            setupFolderAdapter();
        } else {
            loadDataAndSortAsync(tab, null);
        }
    }
    
    private void loadDataAndSortAsync(final int tab, final String targetFolder) {
        progressBar.setVisibility(View.VISIBLE);
        songView.setVisibility(View.GONE);

        new Thread(new Runnable() {
            @Override
            public void run() {
                final ArrayList<Song> tempList = new ArrayList<>();

                // 1. Data Fetching
                if (tab == 0) {
                    if (masterSongList.isEmpty()) loadSongs(); // মাস্টার লিস্ট খালি থাকলে লোড করুন
                    tempList.addAll(masterSongList);
                } else if (tab == 1 && targetFolder != null) {
                    ArrayList<Song> folderSongs = folderMap.get(targetFolder);
                    if (folderSongs != null) tempList.addAll(folderSongs);
                } else if (tab == 2) {
                    tempList.addAll(dbHelper.getAllFavorites());
                } else if (tab == 3) {
                    tempList.addAll(dbHelper.getHistory());
                }

                // 2. Sorting (History বাদে)
                if (tab != 3) {
                    int sortMode = prefs.getInt("sort_mode", 0);
                    Collections.sort(tempList, new Comparator<Song>() {
                        @Override
                        public int compare(Song s1, Song s2) {
                            switch (sortMode) {
                                case 0: return compareSmart(s1.getTitle(), s2.getTitle());
                                case 1: return s1.getArtist().compareToIgnoreCase(s2.getArtist());
                                case 2: return Long.compare(s2.getDateAdded(), s1.getDateAdded());
                                case 3: return Long.compare(s2.getSize(), s1.getSize());
                                case 4: return Long.compare(s2.getDuration(), s1.getDuration());
                                default: return 0;
                            }
                        }
                    });
                }

                // 3. Save to Cache
                if (tab == 0) {
                    cachedTracks.clear();
                    cachedTracks.addAll(tempList);
                } else if (tab == 2) {
                    cachedFavorites.clear();
                    cachedFavorites.addAll(tempList);
                } else if (tab == 3) {
                    cachedHistory.clear();
                    cachedHistory.addAll(tempList);
                }

                // 4. UI Update
                new Handler(Looper.getMainLooper()).post(new Runnable() {
                    @Override
                    public void run() {
                        displayList.clear();
                        displayList.addAll(tempList);
                        
                        if (tab == 1 && targetFolder == null) {
                            setupFolderAdapter();
                        } else {
                            setupAdapter();
                        }
                        
                        progressBar.setVisibility(View.GONE);
                        songView.setVisibility(View.VISIBLE);
                    }
                });
            }
        }).start();
    }

    // স্মার্ট সর্টিং হেল্পার (ব্যাকগ্রাউন্ডে ব্যবহারের জন্য)
    private int compareSmart(String str1, String str2) {
        boolean isLetter1 = Character.isLetter(str1.charAt(0));
        boolean isLetter2 = Character.isLetter(str2.charAt(0));
        if (isLetter1 && !isLetter2) return -1;
        else if (!isLetter1 && isLetter2) return 1;
        else return str1.compareToIgnoreCase(str2);
    }
    
    private void filterList(String query) {
        displayList.clear();
        String lowerQuery = query.toLowerCase();
        
        ArrayList<Song> source;
        if(isInsideFolder && currentTab == 1) {
             source = folderMap.get(currentFolderName);
        } else if (currentTab == 2) {
             source = dbHelper.getAllFavorites();
        } else if (currentTab == 3) {
             // === HISTORY SEARCH SOURCE ===
             source = dbHelper.getHistory();
        } else {
             source = masterSongList;
        }

        if (source != null) {
            if (query.isEmpty()) {
                displayList.addAll(source);
            } else {
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

    // ==========================================
    // ADAPTERS
    // ==========================================
    private void setupAdapter() {
        adapter = new CustomAdapter(this, displayList);
        songView.setAdapter(adapter);
        songView.setOnItemClickListener((parent, view, position, id) -> {
            startMusicService();
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
            isInsideFolder = true; // ফ্ল্যাগ সেট করা হলো
            
            // ১. হেডার আপডেট (এনিমেশন ও ব্যাক বাটন)
            animateTitle(folderName);
            btnBackHeader.setVisibility(View.VISIBLE);
            etSearch.setHint("Search in " + folderName + "...");

            // ২. লজিক ফিক্স: Async মেথড কল করা হচ্ছে
            // এটি ফোল্ডারের গান লোড করবে, সর্ট করবে এবং সং লিস্ট দেখাবে
            loadDataAndSortAsync(1, folderName);
        });
    }
    
    class CustomAdapter extends BaseAdapter {
        private ArrayList<Song> songs;
        private LayoutInflater inflater;
        
        // Date Formatter
        private java.text.SimpleDateFormat dateSdf = new java.text.SimpleDateFormat("dd:MM:yyyy", Locale.getDefault());

        public CustomAdapter(Context c, ArrayList<Song> theSongs) { 
            songs = theSongs; 
            inflater = LayoutInflater.from(c); 
        }

        @Override public int getCount() { return songs.size(); }
        @Override public Object getItem(int arg0) { return null; }
        @Override public long getItemId(int arg0) { return 0; }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) convertView = inflater.inflate(R.layout.song_item, parent, false);
            
            TextView serial = convertView.findViewById(R.id.songSerial);
            TextView title = convertView.findViewById(R.id.songTitle);
            TextView artist = convertView.findViewById(R.id.songArtist);
            TextView folder = convertView.findViewById(R.id.songFolder);
            TextView extraInfo = convertView.findViewById(R.id.songExtraInfo); // New View
            ImageView art = convertView.findViewById(R.id.imgThumbnail);
            ImageView playing = convertView.findViewById(R.id.playingIndicator);

            Song currSong = songs.get(position);
            
            // 1. Serial
            serial.setText(String.format(Locale.getDefault(), "%02d", position + 1));
            
            // 2. Title & Artist
            title.setText(currSong.getTitle());
            artist.setText(currSong.getArtist());
            
            // 3. Folder Name
            try {
                String parentPath = new File(currSong.getPath()).getParent();
                folder.setText(new File(parentPath).getName());
            } catch (Exception e) { folder.setText("Music"); }

            // 4. EXTRA INFO LOGIC (Duration • Size • Date)
            // Duration (mm:ss)
            long dur = currSong.getDuration();
            String durationStr = String.format(Locale.getDefault(), "%02d:%02d", 
                (dur / 1000) / 60, (dur / 1000) % 60);

            // Size (MB)
            double sizeMb = currSong.getSize() / (1024.0 * 1024.0);
            String sizeStr = String.format(Locale.getDefault(), "%.1fMB", sizeMb);

            // Date (dd:MM:yyyy) - DateAdded is in seconds, convert to millis
            String dateStr = dateSdf.format(new java.util.Date(currSong.getDateAdded() * 1000));

            // Set Combined Text
            extraInfo.setText(" • " + durationStr + " • " + sizeStr + " • " + dateStr);

            // 5. Album Art
            try {
                Uri artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), currSong.getAlbumId());
                art.setImageURI(artworkUri);
                if (art.getDrawable() == null) art.setImageResource(R.drawable.ic_notification);
            } catch (Exception e) { art.setImageResource(R.drawable.ic_notification); }

            // 6. Highlight Playing Song
            if (musicBound && musicSrv.getCurrentSong() != null && musicSrv.getCurrentSong().getId() == currSong.getId()) {
                playing.setVisibility(View.VISIBLE);
                title.setTextColor(getResources().getColor(R.color.md_theme_primary));
                serial.setTextColor(getResources().getColor(R.color.md_theme_primary));
            } else {
                playing.setVisibility(View.GONE);
                title.setTextColor(getResources().getColor(R.color.md_theme_onSurface));
                serial.setTextColor(Color.parseColor("#808080"));
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
            
            TextView serial = v.findViewById(R.id.songSerial);
            TextView title = v.findViewById(R.id.songTitle);
            TextView subtitle = v.findViewById(R.id.songArtist);
            ImageView icon = v.findViewById(R.id.imgThumbnail);
            TextView folder = v.findViewById(R.id.songFolder);
            
            serial.setText(String.format(Locale.getDefault(), "%02d", i + 1));
            String folderName = folderList.get(i);
            title.setText(folderName);
            subtitle.setText(folderMap.get(folderName).size() + " songs");
            folder.setVisibility(View.GONE); 
            icon.setImageResource(R.drawable.ic_folder); 
            icon.setBackground(null);
            
            title.setTextColor(getResources().getColor(R.color.md_theme_onSurface));
            serial.setTextColor(Color.parseColor("#808080"));
            v.findViewById(R.id.playingIndicator).setVisibility(View.GONE);
            return v;
        }
    }

    // ==========================================
    // SERVICE CONNECTION & MINI PLAYER
    // ==========================================
    private ServiceConnection musicConnection = new ServiceConnection(){
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicService.MusicBinder binder = (MusicService.MusicBinder)service;
            musicSrv = binder.getService();
            
            // ১. লিস্ট সেট করুন (এটি সবসময় প্রথমে করতে হবে)
            if(masterSongList.size() > 0) {
                musicSrv.setList(new ArrayList<>(masterSongList));
            }
            
            musicBound = true;

            // === CRASH FIX & LOGIC UPDATE ===
            // যদি গান প্লে না হতে থাকে (অর্থাৎ অ্যাপ নতুন চালু হয়েছে বা রিস্টার্ট হয়েছে)
            // তবেই আমরা আগের গানটি লোড করব।
            if (!musicSrv.isPng()) {
                SharedPreferences prefs = getSharedPreferences("MusicPrefs", MODE_PRIVATE);
                long lastId = prefs.getLong("last_song_id", -1);
                
                if (lastId != -1 && masterSongList != null) {
                    for (int i = 0; i < masterSongList.size(); i++) {
                        if (masterSongList.get(i).getId() == lastId) {
                            musicSrv.setSong(i); // পজিশন সেট
                            musicSrv.prepareCurrent(); // প্লেয়ার রেডি (বাজবে না)
                            break;
                        }
                    }
                }
            }
            // ======================================

            updateMiniPlayer();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
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
            
            if (musicSrv.isPng()) {
                btnMiniPlay.setImageResource(R.drawable.ic_pause);
                if (rotateAnimator != null && rotateAnimator.isPaused()) rotateAnimator.resume();
                if (rotateAnimator != null && !rotateAnimator.isStarted()) rotateAnimator.start();
            } else {
                btnMiniPlay.setImageResource(R.drawable.ic_play);
                if (rotateAnimator != null) rotateAnimator.pause();
            }
            
            try {
                Uri artworkUri = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), s.getAlbumId());
                miniArt.setImageURI(artworkUri);
                if (miniArt.getDrawable() == null) miniArt.setImageResource(R.drawable.ic_notification);
            } catch (Exception e) { miniArt.setImageResource(R.drawable.ic_notification); }
        } else {
            miniPlayerLayout.setVisibility(View.GONE);
        }
    }
    // ==========================================
    // SORTING LOGIC (PERSISTENT)
    // ==========================================
    private static final String PREF_SORT = "sort_mode"; 

    private void showSortDialog() {
        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_sort);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }

        // Views
        View sortName = dialog.findViewById(R.id.sortName);
        View sortArtist = dialog.findViewById(R.id.sortArtist);
        View sortDate = dialog.findViewById(R.id.sortDate);
        View sortSize = dialog.findViewById(R.id.sortSize);
        View sortDuration = dialog.findViewById(R.id.sortDuration);

        // Click Listeners (Save & Sort)
        sortName.setOnClickListener(v -> saveAndSort(0, dialog));
        sortArtist.setOnClickListener(v -> saveAndSort(1, dialog));
        sortDate.setOnClickListener(v -> saveAndSort(2, dialog));
        sortSize.setOnClickListener(v -> saveAndSort(3, dialog));
        sortDuration.setOnClickListener(v -> saveAndSort(4, dialog));

        dialog.show();
    }

    // Helper method to save preference and sort
    private void saveAndSort(int mode, Dialog dialog) {
        prefs.edit().putInt("sort_mode", mode).apply();
        
        // সর্ট চেঞ্জ হলে পুরনো ক্যাশ মুছে ফেলতে হবে
        cachedTracks.clear();
        cachedFavorites.clear();
        // History সাধারণত সর্ট হয় না, তাই মুছলাম না
        
        sortList();
        dialog.dismiss();
    }
    
// সর্টিং মেনু থেকে যখন কল হবে, তখন এটি রান হবে
    private void sortList() {
        // আমরা নিজে এখানে সর্ট করব না, বরং Async মেথডকে কল করব
        // যাতে লোডিং দেখায় এবং ল্যাগ না করে
        if (isInsideFolder) {
            loadDataAndSortAsync(1, currentFolderName);
        } else {
            loadDataAndSortAsync(currentTab, null);
        }
    }
    
    // গান প্লে করার সময় এই মেথড কল হবে
    private void startMusicService() {
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
        }
        // এখন সার্ভিস স্টার্ট হবে এবং নোটিফিকেশন আসবে
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(playIntent);
        } else {
            startService(playIntent);
        }
    }
   
    @Override
    protected void onStart() {
        super.onStart();
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            // শুধুমাত্র Bind করা হচ্ছে, Start নয়। তাই নোটিফিকেশন আসবে না।
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
        }
        registerReceiver(updateReceiver, new IntentFilter(MusicService.BROADCAST_ACTION));
    }
    @Override
    protected void onResume() {
        super.onResume();
        
        if(currentTab == 2) { 
             cachedFavorites.clear(); // ফেভারিট ক্যাশ ক্লিয়ার
             switchTab(2); // রিফ্রেশ
        } else if (currentTab == 3) {
             cachedHistory.clear(); // হিস্ট্রি ক্যাশ ক্লিয়ার
             switchTab(3); // রিফ্রেশ
        }
        
        if(musicBound) updateMiniPlayer();
    }
    @Override
    protected void onDestroy() {
        if (musicBound) unbindService(musicConnection);
        unregisterReceiver(updateReceiver);
        super.onDestroy();
    }
}