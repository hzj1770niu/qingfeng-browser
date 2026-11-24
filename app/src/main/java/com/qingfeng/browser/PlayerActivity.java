package com.qingfeng.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.app.PictureInPictureParams;
import android.util.Rational;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.media3.common.AudioAttributes;
import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.MediaItem;
import androidx.media3.common.PlaybackException;
import androidx.media3.common.Player;
import androidx.media3.common.TrackGroup;
import androidx.media3.common.TrackSelectionOverride;
import androidx.media3.common.Tracks;
import androidx.media3.datasource.DataSource;
import androidx.media3.datasource.DefaultDataSource;
import androidx.media3.datasource.DefaultHttpDataSource;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.dash.DashMediaSource;
import androidx.media3.exoplayer.hls.HlsMediaSource;
import androidx.media3.exoplayer.source.MediaSource;
import androidx.media3.exoplayer.source.ProgressiveMediaSource;
import androidx.media3.ui.PlayerView;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;

public class PlayerActivity extends Activity {

    public static final String EXTRA_URL = "url";
    public static final String EXTRA_REFERER = "referer";
    public static final String EXTRA_TITLE = "title";

    private static final float[] SPEEDS = {0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f};

    private FrameLayout root;
    private PlayerView playerView;
    private ExoPlayer player;
    private SeekBar seekBar;
    private Button playBtn;
    private Button rewBtn;
    private Button ffBtn;
    private Button fullBtn;
    private Button speedBtn;
    private Button trackBtn;
    private Button hideBtn;
    private Button restoreBtn;
    private Button closeBtn;
    private TextView titleText;
    private TextView timeNow;
    private TextView timeTotal;
    private LinearLayout topPanel;
    private LinearLayout bottomPanel;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SharedPreferences cfg;
    private String url;
    private String referer;
    private String title;

    private int speedIndex = 2;
    private boolean userSeeking = false;
    private boolean immersive = false;
    private boolean uiHidden = false;
    private boolean firstReady = true;
    private GestureDetector gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_player);
        cfg = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

        root = findViewById(R.id.playerRoot);
        playerView = findViewById(R.id.playerView);
        seekBar = findViewById(R.id.seekBar);
        playBtn = findViewById(R.id.playBtn);
        rewBtn = findViewById(R.id.rewBtn);
        ffBtn = findViewById(R.id.ffBtn);
        fullBtn = findViewById(R.id.fullBtn);
        speedBtn = findViewById(R.id.speedBtn);
        trackBtn = findViewById(R.id.trackBtn);
        hideBtn = findViewById(R.id.hideBtn);
        restoreBtn = findViewById(R.id.restoreBtn);
        closeBtn = findViewById(R.id.closeBtn);
        titleText = findViewById(R.id.titleText);
        timeNow = findViewById(R.id.timeNow);
        timeTotal = findViewById(R.id.timeTotal);
        topPanel = findViewById(R.id.topPanel);
        bottomPanel = findViewById(R.id.bottomPanel);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        Ui.applyInsets(root);

        url = getIntent().getStringExtra(EXTRA_URL);
        referer = getIntent().getStringExtra(EXTRA_REFERER);
        title = getIntent().getStringExtra(EXTRA_TITLE);

        if (url == null || url.isEmpty()) {
            finish();
            return;
        }

        String label = title == null || title.trim().isEmpty() ? MediaUtil.hostOf(url) : title;
        titleText.setText("内置播放器：" + label);
        titleText.setContentDescription("正在播放，" + label);

        if (cfg.getBoolean("keepScreen", true)) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }

        playerView.setKeepScreenOn(true);
        buildPlayer();

        setupGesture();
        bindButtons();
        startTick();
    }

    private void setFullscreen(boolean on) {
        immersive = on;
        Ui.setImmersive(this, on);
        fullBtn.setText(on ? "退出全屏" : "全屏");
        fullBtn.setContentDescription(on ? "退出全屏，显示状态栏" : "全屏，隐藏状态栏");
    }

    /** 视频一开始播就自动铺满全屏 */
    private void autoFullscreen() {
        if (immersive) {
            return;
        }
        setFullscreen(true);
        setUiHidden(true);
    }

    private void buildPlayer() {
        if (player != null) {
            player.release();
            player = null;
        }

        DefaultHttpDataSource.Factory http = new DefaultHttpDataSource.Factory()
                .setUserAgent(MainActivity.UA_MOBILE)
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15000)
                .setReadTimeoutMs(20000);

        HashMap<String, String> headers = new HashMap<>();
        if (referer != null && MediaUtil.isHttp(referer)) {
            headers.put("Referer", referer);
            try {
                URI u = new URI(referer);
                if (u.getScheme() != null && u.getHost() != null) {
                    headers.put("Origin", u.getScheme() + "://" + u.getHost());
                }
            } catch (Exception ignored) {
            }
        }
        if (!headers.isEmpty()) {
            http.setDefaultRequestProperties(headers);
        }

        DataSource.Factory dsFactory = new DefaultDataSource.Factory(this, http);

        MediaItem item = MediaItem.fromUri(url);
        String lower = url.toLowerCase(Locale.US);
        MediaSource source;
        try {
            if (lower.contains("m3u8") || lower.contains("hls")) {
                source = new HlsMediaSource.Factory(dsFactory)
                        .setAllowChunklessPreparation(true)
                        .createMediaSource(item);
            } else if (lower.contains(".mpd") || lower.contains("dash")) {
                source = new DashMediaSource.Factory(dsFactory).createMediaSource(item);
            } else {
                source = new ProgressiveMediaSource.Factory(dsFactory).createMediaSource(item);
            }
        } catch (Exception e) {
            source = new ProgressiveMediaSource.Factory(dsFactory).createMediaSource(item);
        }

        player = new ExoPlayer.Builder(this)
                .setSeekBackIncrementMs(10000)
                .setSeekForwardIncrementMs(10000)
                .build();
        player.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setUsage(C.USAGE_MEDIA)
                .build(), true);

        player.addListener(new Player.Listener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_BUFFERING) {
                    announce("正在缓冲");
                } else if (state == Player.STATE_READY && firstReady) {
                    firstReady = false;
                    announce("视频已就绪，开始播放");
                    handler.postDelayed(PlayerActivity.this::autoFullscreen, 350);
                } else if (state == Player.STATE_ENDED) {
                    announce("播放结束");
                    updatePlayButton();
                    savePosition();
                }
                updateTimes();
                updatePlayButton();
            }

            @Override
            public void onIsPlayingChanged(boolean isPlaying) {
                updatePlayButton();
                if (isPlaying && firstReady) {
                    handler.postDelayed(PlayerActivity.this::autoFullscreen, 300);
                }
            }

            @Override
            public void onPlayerError(PlaybackException error) {
                showError(error);
            }
        });

        long startPos = cfg.getLong("pos:" + url, 0L);
        player.setMediaSource(source, startPos);
        player.setPlaybackSpeed(SPEEDS[speedIndex]);
        player.prepare();
        player.setPlayWhenReady(true);
        playerView.setPlayer(player);
    }

    private void bindButtons() {
        playBtn.setOnClickListener(v -> {
            if (player == null) {
                return;
            }
            togglePlayPause();
        });

        rewBtn.setOnClickListener(v -> {
            if (player == null) {
                return;
            }
            player.seekBack();
            announce("后退 10 秒，当前 " + MediaUtil.fmtTime(player.getCurrentPosition()));
        });
        rewBtn.setOnLongClickListener(v -> {
            if (player == null) {
                return true;
            }
            long target = Math.max(0, player.getCurrentPosition() - 30000);
            player.seekTo(target);
            announce("后退 30 秒，当前 " + MediaUtil.fmtTime(target));
            return true;
        });
        rewBtn.setContentDescription("后退10秒，长按后退30秒");

        ffBtn.setOnClickListener(v -> {
            if (player == null) {
                return;
            }
            player.seekForward();
            announce("前进 10 秒，当前 " + MediaUtil.fmtTime(player.getCurrentPosition()));
        });
        ffBtn.setOnLongClickListener(v -> {
            if (player == null) {
                return true;
            }
            long dur = player.getDuration();
            long base = player.getCurrentPosition() + 30000;
            long target = (dur == C.TIME_UNSET || dur <= 0) ? base : Math.min(dur, base);
            player.seekTo(target);
            announce("前进 30 秒，当前 " + MediaUtil.fmtTime(target));
            return true;
        });
        ffBtn.setContentDescription("前进10秒，长按前进30秒");

        seekBar.setKeyProgressIncrement(10000);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                if (fromUser && player != null) {
                    player.seekTo(progress);
                    timeNow.setText(MediaUtil.fmtTime(progress));
                    bar.setContentDescription("播放进度 " + MediaUtil.fmtTime(progress)
                            + "，共 " + MediaUtil.fmtTime(bar.getMax()));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
                userSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                userSeeking = false;
                announce("进度已调整到 " + MediaUtil.fmtTime(bar.getProgress()));
            }
        });

        fullBtn.setOnClickListener(v -> {
            setFullscreen(!immersive);
            announce(immersive ? "已进入全屏" : "已退出全屏");
        });

        speedBtn.setOnClickListener(v -> {
            speedIndex = (speedIndex + 1) % SPEEDS.length;
            float s = SPEEDS[speedIndex];
            if (player != null) {
                player.setPlaybackSpeed(s);
            }
            String txt = (s == Math.round(s)) ? ((int) s) + "×" : s + "×";
            speedBtn.setText("倍速 " + txt);
            speedBtn.setContentDescription("倍速，当前 " + txt + "，点击切换");
            announce("播放速度 " + txt);
        });
        speedBtn.setContentDescription("倍速，当前 1×，点击切换");

        trackBtn.setOnClickListener(v -> showTracksDialog());

        hideBtn.setOnClickListener(v -> setUiHidden(true));
        if (restoreBtn != null) {
            restoreBtn.setVisibility(View.GONE);
        }

        closeBtn.setOnClickListener(v -> finish());
    }

    private void setUiHidden(boolean hidden) {
        uiHidden = hidden;
        topPanel.setVisibility(hidden ? View.GONE : View.VISIBLE);
        bottomPanel.setVisibility(hidden ? View.GONE : View.VISIBLE);
        restoreBtn.setVisibility(hidden ? View.VISIBLE : View.GONE);
        announce(hidden ? "已隐藏控制界面，点右上角显示界面可恢复" : "控制界面已显示");
    }

    private void updatePlayButton() {
        if (player == null || playBtn == null) {
            return;
        }
        boolean playing = player.isPlaying();
        playBtn.setText(playing ? "暂停" : "播放");
        playBtn.setContentDescription(playing ? "暂停播放" : "开始播放");
    }

    private void updateTimes() {
        if (player == null || userSeeking) {
            return;
        }
        long pos = Math.max(0, player.getCurrentPosition());
        long dur = player.getDuration();
        if (dur == C.TIME_UNSET || dur < 0) {
            dur = 0;
        }
        if (dur > 0) {
            seekBar.setMax((int) Math.min(Integer.MAX_VALUE, dur));
            seekBar.setProgress((int) Math.min(Integer.MAX_VALUE, pos));
            seekBar.setEnabled(true);
        } else {
            seekBar.setEnabled(false);
        }
        timeNow.setText(MediaUtil.fmtTime(pos));
        timeTotal.setText(MediaUtil.fmtTime(dur));
        seekBar.setContentDescription("播放进度 " + MediaUtil.fmtTime(pos)
                + "，共 " + MediaUtil.fmtTime(dur) + "，可上下滑动调节");
    }

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateTimes();
            handler.postDelayed(this, 500);
        }
    };

    private void startTick() {
        handler.removeCallbacks(tick);
        handler.post(tick);
    }

    private void showTracksDialog() {
        if (player == null) {
            return;
        }
        final Tracks tracks = player.getCurrentTracks();
        final List<String> labels = new ArrayList<>();
        final List<TrackGroup> groups = new ArrayList<>();
        final List<Integer> indexes = new ArrayList<>();

        labels.add("自动选择（默认）");
        for (Tracks.Group g : tracks.getGroups()) {
            int type = g.getType();
            String typeName;
            if (type == C.TRACK_TYPE_AUDIO) {
                typeName = "音轨";
            } else if (type == C.TRACK_TYPE_TEXT) {
                typeName = "字幕";
            } else if (type == C.TRACK_TYPE_VIDEO) {
                typeName = "画面";
            } else {
                typeName = "轨道";
            }
            for (int i = 0; i < g.length; i++) {
                if (!g.isTrackSupported(i)) {
                    continue;
                }
                Format f = g.getTrackFormat(i);
                String lang = f.language == null ? "" : f.language;
                String name = f.label == null ? "" : f.label;
                String desc = typeName;
                if (!lang.isEmpty()) {
                    desc += " " + lang;
                }
                if (!name.isEmpty()) {
                    desc += " " + name;
                }
                desc += "，第" + (i + 1) + "条";
                labels.add(desc);
                groups.add(g.getMediaTrackGroup());
                indexes.add(i);
            }
        }

        if (labels.size() == 1) {
            announce("当前视频没有可切换的音轨或字幕");
            return;
        }

        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("选择音轨或字幕")
                .setItems(labels.toArray(new String[0]), (d, which) -> {
                    if (player == null) {
                        return;
                    }
                    if (which == 0) {
                        player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                                .buildUpon().clearOverrides().build());
                        announce("已恢复默认音轨与字幕");
                    } else {
                        int idx = which - 1;
                        player.setTrackSelectionParameters(player.getTrackSelectionParameters()
                                .buildUpon()
                                .setOverrideForType(new TrackSelectionOverride(groups.get(idx), indexes.get(idx)))
                                .build());
                        announce("已切换到 " + labels.get(which));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showError(PlaybackException error) {
        String msg = error.getMessage();
        if (msg == null) {
            msg = "错误代码 " + error.getErrorCodeName();
        }
        if (msg.length() > 300) {
            msg = msg.substring(0, 300);
        }
        announce("播放失败");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("播放失败")
                .setMessage("这可能是因为视频地址过期、需要登录，或者是加密（DRM）视频。\n\n详细信息："
                        + msg)
                .setPositiveButton("重试", (d, w) -> {
                    firstReady = true;
                    buildPlayer();
                })
                .setNeutralButton("改用网页播放", (d, w) -> fallbackToWeb())
                .setNegativeButton("关闭", (d, w) -> finish())
                .show();
    }

    /** 加密 / 需会员授权的内容交回网页自带播放器，并记住这个站点不再自动接管 */
    private void fallbackToWeb() {
        SharedPreferences p = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
        String host = MediaUtil.hostOf(referer != null && !referer.isEmpty() ? referer : url);
        if (!host.isEmpty()) {
            java.util.Set<String> set = new java.util.HashSet<>(
                    p.getStringSet("fallbackHosts", new java.util.HashSet<>()));
            set.add(host);
            p.edit().putStringSet("fallbackHosts", set).apply();
        }
        p.edit().putString("pendingAnnounce", "fallback").apply();
        savePosition();
        finish();
    }

    private void savePosition() {
        if (player == null || url == null) {
            return;
        }
        long pos = player.getCurrentPosition();
        cfg.edit().putLong("pos:" + url, pos).apply();
    }

    private void announce(String s) {
        if (root != null) {
            root.announceForAccessibility(s);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        savePosition();
    }

    private void setupGesture() {
        gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(android.view.MotionEvent e) {
                togglePlayPause();
                return true;
            }
        });
        playerView.setOnTouchListener((v, e) -> gestureDetector.onTouchEvent(e));
    }

    private void togglePlayPause() {
        if (player == null) {
            return;
        }
        if (player.isPlaying()) {
            player.pause();
            announce("已暂停");
        } else {
            player.play();
            announce("继续播放");
        }
        updatePlayButton();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (player != null) {
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                togglePlayPause();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                player.play();
                updatePlayButton();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                player.pause();
                updatePlayButton();
                return true;
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onUserLeaveHint() {
        enterPiP();
    }

    private void enterPiP() {
        if (Build.VERSION.SDK_INT < 26 || player == null) {
            return;
        }
        try {
            PictureInPictureParams.Builder b = new PictureInPictureParams.Builder();
            b.setAspectRatio(new Rational(16, 9));
            if (Build.VERSION.SDK_INT >= 31) {
                b.setAutoEnterEnabled(true);
            }
            enterPictureInPictureMode(b.build());
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(tick);
        savePosition();
        if (player != null) {
            player.release();
            player = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (uiHidden) {
            setUiHidden(false);
            return;
        }
        if (immersive) {
            setFullscreen(false);
            announce("已退出全屏");
            return;
        }
        savePosition();
        finish();
    }

    @Override
    public void finish() {
        try {
            Intent data = new Intent();
            data.putExtra("backUrl", url);
            setResult(RESULT_OK, data);
        } catch (Exception ignored) {
        }
        super.finish();
    }
}
