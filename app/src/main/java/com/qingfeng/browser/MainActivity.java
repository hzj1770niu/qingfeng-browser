package com.qingfeng.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.ConsoleMessage;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends Activity {

    static final String PREFS = "cfg";
    static final String UA_MOBILE =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/142.0.0.0 Mobile Safari/537.36";
    static final String UA_DESKTOP =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/142.0.0.0 Safari/537.36";
    static final String HOME_URL = "about:blank";
    static final String SEARCH_BAIDU = "https://m.baidu.com/s?word=";
    static final String SEARCH_GOOGLE = "https://www.google.com/search?q=";
    private static final int REQ_PLAYER = 1001;
    private static final int REQ_ROLE = 3001;

    private static WeakReference<MainActivity> SELF;

    static class Tab {
        WebView web;
        String url = "";
        String title = "";
        final LinkedHashMap<String, String> media = new LinkedHashMap<>();
    }

    final List<Tab> tabs = new ArrayList<>();
    int currentIndex = -1;

    LinearLayout root;
    LinearLayout topBar;
    LinearLayout bottomBar;
    FrameLayout webHost;
    FrameLayout fsLayer;
    View customView;
    WebChromeClient.CustomViewCallback customViewCallback;
    EditText urlBar;
    Button goBtn;
    Button backBtn;
    Button fwdBtn;
    Button reloadBtn;
    Button tabBtn;
    Button bookmarkBtn;
    Button menuBtn;
    Button playerBtn;
    Button restoreUiBtn;
    boolean uiHidden = false;

    final Handler ui = new Handler(Looper.getMainLooper());
    SharedPreferences cfg;
    String hookJs = "";
    String desktopJs = "";
    String resumeJs = "";
    String pauseJs = "";
    String downloadJs = "";
    final java.util.Set<String> autoOpened = new java.util.HashSet<>();
    boolean autoOpenedThisPage = false;
    final java.util.HashMap<String, Long> autoSuppress = new java.util.HashMap<>();
    String pendUrl;
    String pendUa;
    String pendCd;
    String pendMime;
    String lastAnnouncedTitle = "";
    long lastPlayTakeoverAt = 0;

    static MainActivity get() {
        return SELF == null ? null : SELF.get();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SELF = new WeakReference<>(this);
        cfg = getSharedPreferences(PREFS, MODE_PRIVATE);
        setContentView(R.layout.activity_main);
        hookJs = readAsset("hook.js");
        desktopJs = readAsset("desktop_ua.js");
        resumeJs = readAsset("resume.js");
        pauseJs = readAsset("pause.js");
        downloadJs = readAsset("download_hook.js");

        root = findViewById(R.id.root);
        topBar = findViewById(R.id.topBar);
        bottomBar = findViewById(R.id.bottomBar);
        webHost = findViewById(R.id.webHost);
        urlBar = findViewById(R.id.urlBar);
        goBtn = findViewById(R.id.goBtn);
        backBtn = findViewById(R.id.backBtn);
        fwdBtn = findViewById(R.id.fwdBtn);
        reloadBtn = findViewById(R.id.reloadBtn);
        tabBtn = findViewById(R.id.tabBtn);
        bookmarkBtn = findViewById(R.id.bookmarkBtn);
        menuBtn = findViewById(R.id.menuBtn);
        playerBtn = findViewById(R.id.playerBtn);
        restoreUiBtn = findViewById(R.id.restoreUiBtn);
        restoreUiBtn.setVisibility(View.GONE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        Ui.applyInsets(root);

        // 点一下地址栏就把已填好的网址全选，按一次删除即可清空后重新输入
        urlBar.setOnClickListener(v -> selectAllUrl());
        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                ui.postDelayed(this::selectAllUrl, 150);
            }
        });

        goBtn.setOnClickListener(v -> navigateFromBar());

        // 像 Safari 一样：点一下地址栏就全选，按删除键直接清空
        urlBar.setOnClickListener(v -> urlBar.selectAll());
        urlBar.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                urlBar.postDelayed(urlBar::selectAll, 60);
            }
        });
        urlBar.setTextIsSelectable(true);
        urlBar.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                navigateFromBar();
                return true;
            }
            return false;
        });

        backBtn.setOnClickListener(v -> {
            Tab t = currentTab();
            if (t == null) {
                return;
            }
            if (t.web.canGoBack()) {
                t.web.goBack();
            } else {
                backBtn.announceForAccessibility("已经是第一页了");
            }
        });

        fwdBtn.setOnClickListener(v -> {
            Tab t = currentTab();
            if (t == null) {
                return;
            }
            if (t.web.canGoForward()) {
                t.web.goForward();
            } else {
                fwdBtn.announceForAccessibility("已经是最后一页了");
            }
        });

        reloadBtn.setOnClickListener(v -> {
            Tab t = currentTab();
            if (t != null) {
                t.web.reload();
                reloadBtn.announceForAccessibility("正在刷新");
            }
        });

        bookmarkBtn.setOnClickListener(v -> startActivity(new Intent(this, BookmarkActivity.class)));
        bookmarkBtn.setOnLongClickListener(v -> {
            addBookmarkDialog();
            return true;
        });

        tabBtn.setOnClickListener(v -> openTabs());
        tabBtn.setOnLongClickListener(v -> {
            closeAllDialog();
            return true;
        });

        menuBtn.setOnClickListener(v -> showMenu());
        menuBtn.setOnLongClickListener(v -> {
            setUiHidden(true);
            return true;
        });
        playerBtn.setOnClickListener(v -> showMediaList());

        boolean openedFromLink = handleViewIntent(getIntent());
        if (!openedFromLink) {
            if (!restoreTabs()) {
                String start = cfg.getString("lastUrl", null);
                createTab(start != null && !start.isEmpty() && MediaUtil.isHttp(start)
                        ? start : HOME_URL, true);
            }
        }
    }

    // ---------------- 标签页 ----------------

    private Tab currentTab() {
        if (currentIndex < 0 || currentIndex >= tabs.size()) {
            return null;
        }
        return tabs.get(currentIndex);
    }

    int tabCount() {
        return tabs.size();
    }

    String tabTitle(int i) {
        if (i < 0 || i >= tabs.size()) {
            return "";
        }
        Tab t = tabs.get(i);
        String title = t.title;
        if (title == null || title.trim().isEmpty()) {
            title = t.url == null || t.url.isEmpty() ? "新标签页" : t.url;
        }
        return title;
    }

    String tabUrl(int i) {
        if (i < 0 || i >= tabs.size()) {
            return "";
        }
        return tabs.get(i).url;
    }

    boolean isCurrentTab(int i) {
        return i == currentIndex;
    }

    void selectTabByIndex(int i) {
        selectTab(i);
    }

    void newTabFromUi(String url) {
        createTab(url == null || url.isEmpty() ? HOME_URL : url, true);
    }

    private Tab createTab(String url, boolean select) {
        Tab t = new Tab();
        t.web = buildWebView(t);
        tabs.add(t);
        if (select) {
            selectTab(tabs.size() - 1);
        }
        if (url != null && !url.isEmpty()) {
            t.web.loadUrl(url);
        }
        updateUiForTab();
        return t;
    }

    private void selectTab(int idx) {
        if (idx < 0 || idx >= tabs.size()) {
            return;
        }
        Tab t = tabs.get(idx);
        for (int i = webHost.getChildCount() - 1; i >= 0; i--) {
            if (webHost.getChildAt(i) instanceof WebView) {
                webHost.removeViewAt(i);
            }
        }
        webHost.addView(t.web, 0, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        currentIndex = idx;
        updateUiForTab();
        t.web.requestFocus();
        tabBtn.announceForAccessibility("已切换到：" + tabTitle(idx));
    }

    void closeTab(int idx) {
        if (idx < 0 || idx >= tabs.size()) {
            return;
        }
        Tab t = tabs.remove(idx);
        try {
            webHost.removeView(t.web);
            t.web.stopLoading();
            t.web.destroy();
        } catch (Exception ignored) {
        }
        if (tabs.isEmpty()) {
            currentIndex = -1;
            createTab(HOME_URL, true);
            return;
        }
        currentIndex = Math.min(currentIndex, tabs.size() - 1);
        selectTab(currentIndex);
    }

    void closeAllTabs() {
        for (Tab t : tabs) {
            try {
                webHost.removeView(t.web);
                t.web.stopLoading();
                t.web.destroy();
            } catch (Exception ignored) {
            }
        }
        tabs.clear();
        currentIndex = -1;
        createTab(HOME_URL, true);
        announce("已关闭全部标签页");
    }

    private void closeAllDialog() {
        int n = tabs.size();
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("关闭所有标签页")
                .setMessage("当前共有 " + n + " 个标签页，关闭后会新建一个空白页。")
                .setPositiveButton("关闭全部", (d, w) -> closeAllTabs())
                .setNegativeButton("取消", null)
                .show();
    }

    private void openTabs() {
        startActivity(new Intent(this, TabActivity.class));
    }

    // ---------------- WebView ----------------

    private String prefsUa() {
        return cfg.getBoolean("desktopUa", false) ? UA_DESKTOP : UA_MOBILE;
    }

    private WebView buildWebView(final Tab tab) {
        PullWebView w = new PullWebView(this);
        w.setPullRefreshEnabled(cfg.getBoolean("pullRefresh", true));
        w.setListener(() -> {
            Tab t = currentTab();
            if (t != null) {
                t.web.reload();
                announce("下拉刷新，正在重新加载");
            }
        });
        w.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        w.setBackgroundColor(Color.BLACK);
        w.setFocusable(true);
        w.setFocusableInTouchMode(true);

        WebSettings s = w.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(!cfg.getBoolean("autoplay", true));
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(!cfg.getBoolean("desktopUa", false));
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(false);
        s.setAllowFileAccess(false);
        s.setUserAgentString(prefsUa());

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);

        w.setDownloadListener((url, ua, cd, mime, len) -> onDownloadRequest(url, ua, cd, mime));
        w.addJavascriptInterface(new JsDownloader(), "QfDL");
        w.setWebViewClient(new BrowserClient(tab));
        w.setWebChromeClient(new BrowserChrome(tab));
        return w;
    }

    private void inject(WebView w) {
        if (w == null) {
            return;
        }
        try {
            if (cfg.getBoolean("desktopUa", false) && desktopJs != null && !desktopJs.isEmpty()) {
                w.evaluateJavascript(desktopJs, null);
            }
            if (hookJs != null && !hookJs.isEmpty()) {
                w.evaluateJavascript(hookJs, null);
            }
            if (downloadJs != null && !downloadJs.isEmpty()) {
                w.evaluateJavascript(downloadJs, null);
            }
        } catch (Exception ignored) {
        }
    }

    private class BrowserClient extends WebViewClient {
        private final Tab tab;

        BrowserClient(Tab t) {
            this.tab = t;
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            String scheme = u.getScheme();
            if ("http".equals(scheme) || "https".equals(scheme) || "about".equals(scheme)) {
                return false;
            }
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, u));
            } catch (Exception e) {
                announce("链接无法打开");
            }
            return true;
        }

        @Override
        public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            try {
                if (!request.isForMainFrame()) {
                    String u = request.getUrl().toString();
                    if (MediaUtil.isMedia(u)) {
                        final String fu = u;
                        ui.post(() -> addMedia(tab, fu));
                    }
                }
            } catch (Exception ignored) {
            }
            return null;
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            tab.url = url;
            if (tab == currentTab()) {
                autoOpened.clear();
                autoOpenedThisPage = false;
            }
            if (tab == currentTab()) {
                urlBar.setText(url);
            }
            // 尽早挂上探测钩子
            final WebView w = view;
            inject(w);
            ui.postDelayed(() -> inject(w), 500);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            tab.url = url;
            if (!view.getTitle().equals(tab.title)) {
                tab.title = view.getTitle();
            }
            inject(view);
            Store.addHistory(MainActivity.this, tab.title, url);
            if (tab == currentTab()) {
                updateUiForTab();
                String title = tabTitle(currentIndex);
                if (title != null && !title.equals(lastAnnouncedTitle)) {
                    lastAnnouncedTitle = title;
                    announce("页面已加载：" + title);
                }
            }
        }

        @Override
        public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
            if (request != null && request.isForMainFrame()) {
                announce("网页加载失败，可点击刷新重试");
            }
        }
    }

    private class BrowserChrome extends WebChromeClient {
        private final Tab tab;

        BrowserChrome(Tab t) {
            this.tab = t;
        }

        @Override
        public boolean onConsoleMessage(ConsoleMessage cm) {
            String msg = cm.message();
            if (msg == null) {
                return true;
            }
            if (msg.startsWith("A11Y_MEDIA|")) {
                String[] parts = msg.split("\\|", 3);
                if (parts.length >= 3) {
                    final String url = parts[2];
                    ui.post(() -> {
                        if (MediaUtil.isMedia(url)) {
                            addMedia(tab, url);
                        }
                    });
                }
            } else if (msg.startsWith("A11Y_PLAY|")) {
                final String url = msg.substring("A11Y_PLAY|".length());
                ui.post(() -> onPagePlay(tab, url));
            }
            return true;
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            showCustomView(view, callback);
        }

        @Override
        public void onHideCustomView() {
            hideCustomView();
        }
    }

    // ---------------- 视频探测与播放 ----------------

    private void onPagePlay(Tab tab, String url) {
        if (!MediaUtil.isMedia(url)) {
            return;
        }
        addMedia(tab, url);
    }

    /** 强制接管：抓到真实视频地址就直接开内置播放器，不再等用户手动点 */
    private void maybeAutoPlay(Tab tab, String url) {
        if (!cfg.getBoolean("takeover", true)) {
            return;
        }
        if (tab == null || tab != currentTab()) {
            return;
        }
        if (isFallbackHost(url)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = autoSuppress.get(url);
        if (until != null && now < until) {
            return;
        }
        if (autoOpenedThisPage || autoOpened.contains(url)) {
            return;
        }
        autoOpenedThisPage = true;
        if (now - lastPlayTakeoverAt < 800) {
            return;
        }
        autoOpened.add(url);
        lastPlayTakeoverAt = now;
        final String u = url;
        ui.postDelayed(() -> {
            if (tab != currentTab() || isFinishing()) {
                return;
            }
            openPlayer(u, tab);
        }, 700);
    }

    private void addMedia(Tab tab, String url) {
        if (url == null || !MediaUtil.isMedia(url)) {
            return;
        }
        boolean added = tab.media.put(url, MediaUtil.kindOf(url)) == null;
        if (!added) {
            return;
        }
        if (tab.media.size() > 40) {
            String oldest = tab.media.keySet().iterator().next();
            tab.media.remove(oldest);
        }
        if (tab == currentTab()) {
            refreshPlayerBar();
        }
        maybeAutoPlay(tab, url);
    }

    String currentTabUrl() {
        Tab t = currentTab();
        return t == null ? "" : (t.url == null ? "" : t.url);
    }

    String currentTabTitle() {
        Tab t = currentTab();
        if (t == null) {
            return "";
        }
        if (t.title != null && !t.title.trim().isEmpty()) {
            return t.title;
        }
        return t.url == null ? "" : t.url;
    }

    void openUrlFromUi(String url) {
        Tab t = currentTab();
        if (t == null || url == null) {
            return;
        }
        autoOpenedThisPage = false;
        lastAnnouncedTitle = "";
        t.web.loadUrl(url);
    }

    /** 长按“书签”按钮：像 Safari 一样，直接把当前页面存成书签，标题可改 */
    private void addBookmarkDialog() {
        String url = currentTabUrl();
        if (!MediaUtil.isHttp(url)) {
            announce("当前为空白页，没有可收藏的网址");
            return;
        }
        final EditText input = new EditText(this);
        input.setText(currentTabTitle());
        input.setSelectAllOnFocus(true);
        input.setTextColor(Color.WHITE);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setMinHeight(Ui.dp(this, 56));
        FrameLayout wrap = new FrameLayout(this);
        int pad = Ui.dp(this, 14);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("添加书签")
                .setMessage(url)
                .setView(wrap)
                .setPositiveButton("保存", (d, w) -> {
                    String name = input.getText().toString().trim();
                    boolean ok = Store.addBookmark(this, name, url);
                    announce(ok ? "已加入书签：" + name : "当前页面已在书签中");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private boolean isFallbackHost(String url) {
        String host = MediaUtil.hostOf(url);
        if (host.isEmpty()) {
            return false;
        }
        for (String h : cfg.getStringSet("fallbackHosts", new java.util.HashSet<>())) {
            if (host.equals(h) || host.endsWith("." + h)) {
                return true;
            }
        }
        return false;
    }

    private void refreshPlayerBar() {
        Tab t = currentTab();
        int n = t == null ? 0 : t.media.size();
        if (n == 0) {
            playerBtn.setVisibility(View.GONE);
        } else {
            playerBtn.setVisibility(View.VISIBLE);
            playerBtn.setText("内置播放器（已检测到 " + n + " 个）");
            playerBtn.setContentDescription("内置播放器，已检测到 " + n + " 个视频，点击选择");
        }
    }

    private void showMediaList() {
        Tab t = currentTab();
        if (t == null) {
            return;
        }
        if (t.media.isEmpty()) {
            announce("当前页面未检测到视频，请先在网页中播放，稍后再试");
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("还没检测到视频")
                    .setMessage("网页视频要先在页面里点一次播放，浏览器才能抓到真实地址。"
                            + "也可以手动粘贴一个 m3u8 或 mp4 链接直接播放。")
                    .setPositiveButton("手动输入链接", (d, w) -> manualUrlDialog())
                    .setNegativeButton("知道了", null)
                    .show();
            return;
        }
        final List<String> urls = new ArrayList<>(t.media.keySet());
        String[] labels = new String[urls.size() + 2];
        for (int i = 0; i < urls.size(); i++) {
            labels[i] = "第" + (i + 1) + "个：" + MediaUtil.label(urls.get(i));
        }
        labels[urls.size()] = "手动输入视频链接…";
        labels[urls.size() + 1] = "改用网页自带播放器（加密或会员内容选这个）";
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("选择要播放的视频")
                .setItems(labels, (d, which) -> {
                    if (which < urls.size()) {
                        openPlayer(urls.get(which), t);
                    } else if (which == urls.size()) {
                        manualUrlDialog();
                    } else {
                        useWebPlayer(t);
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 加密（DRM）或需要会员授权的内容：交回网页自带播放器 */
    private void useWebPlayer(Tab t) {
        if (t == null) {
            return;
        }
        String host = MediaUtil.hostOf(t.url);
        if (!host.isEmpty()) {
            java.util.Set<String> set = new java.util.HashSet<>(
                    cfg.getStringSet("fallbackHosts", new java.util.HashSet<>()));
            set.add(host);
            cfg.edit().putStringSet("fallbackHosts", set).apply();
        }
        if (!resumeJs.isEmpty()) {
            t.web.evaluateJavascript(resumeJs, null);
        }
        announce("已切换为网页播放器；加密内容请在网页中播放，本站点后续不再自动接管");
    }

    private void manualUrlDialog() {
        EditText input = new EditText(this);
        input.setHint("粘贴 m3u8 / mp4 等视频地址");
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.GRAY);
        input.setMinHeight(Ui.dp(this, 56));
        FrameLayout wrap = new FrameLayout(this);
        int pad = Ui.dp(this, 14);
        wrap.setPadding(pad, pad, pad, pad);
        wrap.addView(input, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT));
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("手动输入视频链接")
                .setView(wrap)
                .setPositiveButton("播放", (d, w) -> {
                    String u = input.getText().toString().trim();
                    if (MediaUtil.isHttp(u)) {
                        openPlayer(u, currentTab());
                    } else {
                        announce("地址格式不正确，需以 http 或 https 开头");
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void openPlayer(String url, Tab tab) {
        if (tab != null && !pauseJs.isEmpty()) {
            try {
                tab.web.evaluateJavascript(pauseJs, null);
            } catch (Exception ignored) {
            }
        }
        Intent it = new Intent(this, PlayerActivity.class);
        it.putExtra(PlayerActivity.EXTRA_URL, url);
        it.putExtra(PlayerActivity.EXTRA_REFERER, tab == null ? "" : tab.url);
        it.putExtra(PlayerActivity.EXTRA_TITLE, tab == null ? "" : tab.title);
        startActivityForResult(it, REQ_PLAYER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE) {
            announce(isDefaultBrowser()
                    ? "已设为默认浏览器，以后点网页链接会直接用清风浏览器打开"
                    : "没有设置为默认浏览器");
        }
        if (requestCode == REQ_PLAYER && data != null) {
            String back = data.getStringExtra("backUrl");
            if (back != null) {
                // 用户刚关闭这个视频，60 秒内不要重复强制弹播放器
                autoSuppress.put(back, System.currentTimeMillis() + 60000L);
            }
        }
    }

    // ---------------- 网页内全屏视频 ----------------

    private void showCustomView(View view, WebChromeClient.CustomViewCallback callback) {
        if (customView != null) {
            callback.onCustomViewHidden();
            return;
        }
        if (fsLayer == null) {
            fsLayer = new FrameLayout(this);
            fsLayer.setBackgroundColor(Color.BLACK);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            root.addView(fsLayer, lp);
        }
        customView = view;
        customViewCallback = callback;
        fsLayer.removeAllViews();
        fsLayer.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        fsLayer.setVisibility(View.VISIBLE);
        webHost.setVisibility(View.GONE);
        topBar.setVisibility(View.GONE);
        bottomBar.setVisibility(View.GONE);
        Ui.setImmersive(this, true);
        announce("已进入网页全屏，按返回键可退出全屏");
    }

    private void hideCustomView() {
        if (customView == null) {
            return;
        }
        if (fsLayer != null) {
            fsLayer.removeAllViews();
            fsLayer.setVisibility(View.GONE);
        }
        customView = null;
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
        webHost.setVisibility(View.VISIBLE);
        topBar.setVisibility(View.VISIBLE);
        bottomBar.setVisibility(View.VISIBLE);
        Ui.setImmersive(this, false);
        announce("已退出全屏");
    }

    private boolean isWebFullscreen() {
        return customView != null;
    }

    // ---------------- 外部链接 / 默认浏览器 ----------------

    private boolean handleViewIntent(Intent intent) {
        if (intent == null) {
            return false;
        }
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) && intent.getData() != null) {
            String url = intent.getData().toString();
            if (MediaUtil.isHttp(url)) {
                Tab cur = currentTab();
                if (cur == null) {
                    createTab(url, true);
                } else if (cur.url == null || cur.url.isEmpty() || "about:blank".equals(cur.url)) {
                    cur.web.loadUrl(url);
                } else {
                    createTab(url, true);
                }
                announce("已打开收到的链接");
                return true;
            }
        }
        if (Intent.ACTION_WEB_SEARCH.equals(action)) {
            String q = intent.getStringExtra(android.app.SearchManager.QUERY);
            if (q != null && !q.isEmpty()) {
                Tab cur = currentTab();
                if (cur == null) {
                    createTab(searchUrl(q), true);
                } else {
                    cur.web.loadUrl(searchUrl(q));
                }
                return true;
            }
        }
        return false;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleViewIntent(intent);
    }

    /** 申请系统"默认浏览器"角色（Android 10 及以上） */
    void requestDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm =
                        (android.app.role.RoleManager) getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                    if (rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                        announce("当前已是默认浏览器");
                        return;
                    }
                    startActivityForResult(
                            rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER),
                            REQ_ROLE);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            announce("请在系统设置中将默认浏览器选为“清风浏览器”");
        } catch (Exception e) {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
            } catch (Exception ignored) {
            }
            announce("请到系统设置中将默认浏览器设为本应用");
        }
    }

    boolean isDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm =
                        (android.app.role.RoleManager) getSystemService(android.app.role.RoleManager.class);
                return rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER);
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    // ---------------- 标签页保留 ----------------

    private long retentionWindowMs() {
        String mode = cfg.getString("tabRetention", "keep");
        if ("day".equals(mode)) {
            return 86400000L;
        }
        if ("week".equals(mode)) {
            return 7L * 86400000L;
        }
        if ("month".equals(mode)) {
            return 30L * 86400000L;
        }
        return -1L; // 永不关闭
    }

    static String retentionLabel(String mode) {
        if ("day".equals(mode)) {
            return "一天后";
        }
        if ("week".equals(mode)) {
            return "一周后";
        }
        if ("month".equals(mode)) {
            return "一个月后";
        }
        return "永不关闭";
    }

    private String retentionHint() {
        String mode = cfg.getString("tabRetention", "keep");
        if ("keep".equals(mode)) {
            return "标签页会一直保留，下次打开原样恢复。";
        }
        return "标签页会在" + retentionLabel(mode) + "自动关闭，下次打开不会恢复。";
    }

    private void saveTabs() {
        try {
            JSONArray a = new JSONArray();
            for (Tab t : tabs) {
                String u = t.url;
                if (u == null || u.isEmpty()) {
                    continue;
                }
                JSONObject o = new JSONObject();
                o.put("url", u);
                o.put("title", t.title == null ? "" : t.title);
                a.put(o);
            }
            cfg.edit()
                    .putString("openTabs", a.toString())
                    .putLong("tabsSavedAt", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) {
        }
    }

    private boolean restoreTabs() {
        long window = retentionWindowMs();
        long savedAt = cfg.getLong("tabsSavedAt", 0L);
        if (window > 0 && savedAt > 0 && System.currentTimeMillis() - savedAt > window) {
            cfg.edit().remove("openTabs").apply();
            return false;
        }
        String json = cfg.getString("openTabs", "");
        if (json.isEmpty()) {
            return false;
        }
        int n = 0;
        try {
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length() && n < 12; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                String u = o.optString("url", "");
                if (!MediaUtil.isHttp(u) && !u.startsWith("about:")) {
                    continue;
                }
                createTab(u, n == 0);
                n++;
            }
        } catch (Exception e) {
            return false;
        }
        if (n == 0) {
            return false;
        }
        selectTab(0);
        if (n > 1) {
            final int total = n;
            ui.postDelayed(() -> announce("已恢复 " + total + " 个标签页"), 800);
        }
        return true;
    }

    /** 退出浏览器入口 */
    private void confirmExit() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("退出浏览器")
                .setMessage(retentionHint())
                .setPositiveButton("退出", (d, w) -> finish())
                .setNegativeButton("取消", null)
                .show();
    }

    // ---------------- 隐藏控件 ----------------

    private void setUiHidden(boolean hidden) {
        uiHidden = hidden;
        topBar.setVisibility(hidden ? View.GONE : View.VISIBLE);
        bottomBar.setVisibility(hidden ? View.GONE : View.VISIBLE);
        restoreUiBtn.setVisibility(View.GONE);
        if (hidden) {
            announce("控件已隐藏，下拉网页可刷新，按返回键或全面屏手势可恢复");
        } else {
            announce("控件已恢复");
        }
    }

    // ---------------- 菜单 ----------------

    private void showMenu() {
        boolean desktop = cfg.getBoolean("desktopUa", false);
        String[] items = {
                "新建标签页",
                "标签页列表",
                "书签与历史",
                "下载管理",
                "关闭所有标签页",
                desktop ? "切换回手机版网页" : "切换为电脑版网页",
                uiHidden ? "显示控件" : "隐藏控件（全屏阅读）",
                "使用说明",
                "设置",
                "设为默认浏览器",
                "刷新当前页",
                "退出浏览器"
        };
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("菜单")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            createTab(HOME_URL, true);
                            break;
                        case 1:
                            openTabs();
                            break;
                        case 2:
                            startActivity(new Intent(this, BookmarkActivity.class));
                            break;
                        case 3:
                            startActivity(new Intent(this, DownloadActivity.class));
                            break;
                        case 4:
                            closeAllDialog();
                            break;
                        case 5:
                            toggleDesktopMode();
                            break;
                        case 6:
                            setUiHidden(!uiHidden);
                            break;
                        case 7:
                            startActivity(new Intent(this, HelpActivity.class));
                            break;
                        case 8:
                            startActivity(new Intent(this, SettingsActivity.class));
                            break;
                        case 9:
                            requestDefaultBrowser();
                            break;
                        case 11:
                            confirmExit();
                            break;
                        default:
                            Tab t = currentTab();
                            if (t != null) {
                                t.web.reload();
                            }
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 电脑版 / 手机版切换：改 UA + 注入 navigator 覆写，然后原地重新加载 */
    private void toggleDesktopMode() {
        boolean on = !cfg.getBoolean("desktopUa", false);
        cfg.edit().putBoolean("desktopUa", on).apply();
        String ua = prefsUa();
        for (Tab t : tabs) {
            try {
                t.web.getSettings().setUserAgentString(ua);
                t.web.getSettings().setLoadWithOverviewMode(!on);
                t.web.getSettings().setUseWideViewPort(true);
            } catch (Exception ignored) {
            }
        }
        Tab t = currentTab();
        if (t != null && MediaUtil.isHttp(t.url)) {
            lastAnnouncedTitle = "";
            t.web.loadUrl(t.url);
        }
        announce(on ? "已切换为电脑版网页，网站会认为是 Windows 上的最新版 Chrome，正在重新加载"
                : "已切换回手机版网页，正在重新加载");
    }

    // ---------------- 地址栏 ----------------

    private void selectAllUrl() {
        try {
            urlBar.selectAll();
        } catch (Throwable ignored) {
        }
    }

    private void navigateFromBar() {
        String raw = urlBar.getText().toString().trim();
        if (raw.isEmpty()) {
            return;
        }
        Tab t = currentTab();
        if (t == null) {
            return;
        }
        String target;
        if (raw.startsWith("http://") || raw.startsWith("https://") || raw.startsWith("about:")) {
            target = raw;
        } else if (!raw.contains(" ") && raw.matches("[A-Za-z0-9\\-_.]+(\\.([A-Za-z0-9\\-]+))+(/.*)?"
                + "|localhost(:[0-9]+)?(/.*)?")) {
            target = "https://" + raw;
        } else {
            target = searchUrl(raw);
        }
        urlBar.clearFocus();
        hideKeyboard();
        lastAnnouncedTitle = "";
        t.web.loadUrl(target);
        announce("正在打开");
    }

    private String searchUrl(String q) {
        String eng = cfg.getString("searchEngine", "baidu");
        if ("google".equals(eng)) {
            return SEARCH_GOOGLE + Uri.encode(q);
        }
        return SEARCH_BAIDU + Uri.encode(q);
    }

    // ---------------- 下载 ----------------

    private void onDownloadRequest(String url, String userAgent, String cd, String mime) {
        if (url == null || url.isEmpty()) {
            return;
        }
        if (url.startsWith("blob:") || url.startsWith("data:")) {
            return; // 交给页面里的 JS 钩子处理
        }
        if (!MediaUtil.isHttp(url)) {
            announce("链接无法下载");
            return;
        }
        if (url.toLowerCase(java.util.Locale.US).contains(".m3u8")) {
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("这是流媒体播放列表")
                    .setMessage("m3u8 是分片播放列表，不能直接存成视频文件。可以直接用内置播放器观看。")
                    .setPositiveButton("用内置播放器播放", (d, w) -> openPlayer(url, currentTab()))
                    .setNegativeButton("取消", null)
                    .show();
            return;
        }
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            pendUrl = url;
            pendUa = userAgent;
            pendCd = cd;
            pendMime = mime;
            requestPermissions(
                    new String[]{android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 2001);
            return;
        }
        startDownload(url, userAgent, cd, mime);
    }

    private void startDownload(String url, String userAgent, String cd, String mime) {
        Tab t = currentTab();
        String pageUrl = t == null ? "" : t.url;
        long id = Downloads.enqueue(this, url, userAgent, cd, mime, pageUrl);
        if (id > 0) {
            announce("开始下载，文件将保存至 Download 文件夹");
        } else {
            announce("下载启动失败");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 2001) {
            if (grantResults.length > 0
                    && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                startDownload(pendUrl, pendUa, pendCd, pendMime);
            } else {
                announce("未授予存储权限，文件无法保存至 Download 文件夹");
            }
            pendUrl = pendUa = pendCd = pendMime = null;
        }
    }

    /** 网页里 JS 生成的 blob 文件走这里落盘 */
    private class JsDownloader {
        @android.webkit.JavascriptInterface
        public void saveBase64(String name, String mime, String base64) {
            final String n = name;
            final String m = mime;
            final String b = base64;
            new Thread(() -> {
                String saved = Downloads.saveBase64(MainActivity.this, n, m, b);
                ui.post(() -> announce(saved == null
                        ? "这个文件保存失败"
                        : "文件已保存到 Download 文件夹：" + saved));
            }).start();
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(urlBar.getWindowToken(), 0);
        }
    }

    private void updateUiForTab() {
        Tab t = currentTab();
        if (t == null) {
            return;
        }
        urlBar.setText(t.url);
        backBtn.setEnabled(t.web.canGoBack());
        fwdBtn.setEnabled(t.web.canGoForward());
        String label = "标签页 " + tabs.size();
        tabBtn.setText(label);
        tabBtn.setContentDescription("标签页，共 " + tabs.size() + " 个，长按可关闭全部");
        refreshPlayerBar();
    }

    // ---------------- 生命周期 ----------------

    @Override
    protected void onResume() {
        super.onResume();
        boolean autoPlay = cfg.getBoolean("autoplay", true);
        String ua = prefsUa();
        for (Tab t : tabs) {
            try {
                t.web.getSettings().setMediaPlaybackRequiresUserGesture(!autoPlay);
                if (t.web instanceof PullWebView) {
                    ((PullWebView) t.web).setPullRefreshEnabled(
                            cfg.getBoolean("pullRefresh", true));
                }
                if (!ua.equals(t.web.getSettings().getUserAgentString())) {
                    t.web.getSettings().setUserAgentString(ua);
                }
            } catch (Exception ignored) {
            }
        }
        updateUiForTab();
        if (tabs.isEmpty()) {
            createTab(HOME_URL, true);
        }

        String pend = cfg.getString("pendingAnnounce", "");
        if (!pend.isEmpty()) {
            cfg.edit().remove("pendingAnnounce").apply();
            if ("fallback".equals(pend)) {
                Tab cur = currentTab();
                if (cur != null && !resumeJs.isEmpty()) {
                    cur.web.evaluateJavascript(resumeJs, null);
                }
                ui.postDelayed(() -> announce(
                        "这是加密或需要会员授权的内容，已切回网页自带播放器，请直接在网页里播放"), 500);
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        Tab t = currentTab();
        if (t != null) {
            cfg.edit().putString("lastUrl", t.url).apply();
        }
        saveTabs();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        for (Tab t : tabs) {
            try {
                t.web.destroy();
            } catch (Exception ignored) {
            }
        }
        tabs.clear();
        if (SELF != null && SELF.get() == this) {
            SELF = null;
        }
    }

    @Override
    public void onBackPressed() {
        if (uiHidden) {
            setUiHidden(false);
            return;
        }
        if (isWebFullscreen()) {
            hideCustomView();
            return;
        }
        Tab t = currentTab();
        if (t != null && t.web.canGoBack()) {
            t.web.goBack();
            return;
        }
        if (tabs.size() > 1) {
            closeTab(currentIndex);
            return;
        }
        confirmExit();
    }

    // ---------------- 工具 ----------------

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream bo = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int r;
            while ((r = in.read(buf)) > 0) {
                bo.write(buf, 0, r);
            }
            return bo.toString("UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private void announce(String s) {
        if (root != null) {
            root.announceForAccessibility(s);
        }
    }
}
