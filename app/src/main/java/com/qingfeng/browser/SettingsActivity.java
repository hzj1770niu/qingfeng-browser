package com.qingfeng.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.view.View;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Switch;

public class SettingsActivity extends Activity {

    private SharedPreferences cfg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ScrollView root = findViewById(R.id.settingsRoot);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        Ui.applyInsets(root);

        cfg = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);

        Switch autoPlay = findViewById(R.id.swAutoPlay);
        autoPlay.setChecked(cfg.getBoolean("autoplay", true));
        autoPlay.setOnCheckedChangeListener((v, c) -> cfg.edit().putBoolean("autoplay", c).apply());

        Switch takeover = findViewById(R.id.swTakeover);
        takeover.setChecked(cfg.getBoolean("takeover", true));
        takeover.setOnCheckedChangeListener((v, c) -> cfg.edit().putBoolean("takeover", c).apply());

        Switch keepScreen = findViewById(R.id.swKeepScreen);
        keepScreen.setChecked(cfg.getBoolean("keepScreen", true));
        keepScreen.setOnCheckedChangeListener((v, c) -> cfg.edit().putBoolean("keepScreen", c).apply());

        Switch pullRefresh = findViewById(R.id.swPullRefresh);
        pullRefresh.setChecked(cfg.getBoolean("pullRefresh", true));
        pullRefresh.setOnCheckedChangeListener((v, c) -> cfg.edit().putBoolean("pullRefresh", c).apply());

        Switch desktopUa = findViewById(R.id.swDesktopUa);
        desktopUa.setChecked(cfg.getBoolean("desktopUa", false));
        desktopUa.setOnCheckedChangeListener((v, c) -> cfg.edit().putBoolean("desktopUa", c).apply());

        final Button defaultBrowserBtn = findViewById(R.id.defaultBrowserBtn);
        defaultBrowserBtn.setOnClickListener(v -> askDefaultBrowser());
        if (MainActivity.get() != null && MainActivity.get().isDefaultBrowser()) {
            defaultBrowserBtn.setText("已是默认浏览器");
            defaultBrowserBtn.setContentDescription("已是系统默认浏览器");
        }

        final Button retentionBtn = findViewById(R.id.tabRetentionBtn);
        updateRetentionBtn(retentionBtn);
        retentionBtn.setOnClickListener(v -> {
            final String[] modes = {"keep", "day", "week", "month"};
            final String[] names = {"永不关闭（手动）", "一天后", "一周后", "一个月后"};
            String cur = cfg.getString("tabRetention", "keep");
            int checked = 0;
            for (int i = 0; i < modes.length; i++) {
                if (modes[i].equals(cur)) {
                    checked = i;
                }
            }
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("退出后关闭标签页")
                    .setSingleChoiceItems(names, checked, (d, which) -> {
                        cfg.edit().putString("tabRetention", modes[which]).apply();
                        updateRetentionBtn(retentionBtn);
                        retentionBtn.announceForAccessibility("已设置为" + names[which]);
                        d.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        final Button engineBtn = findViewById(R.id.engineBtn);
        updateEngineBtn(engineBtn);
        engineBtn.setOnClickListener(v -> {
            final String[] names = {"百度", "谷歌"};
            String cur = cfg.getString("searchEngine", "baidu");
            int checked = "google".equals(cur) ? 1 : 0;
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("选择搜索引擎")
                    .setSingleChoiceItems(names, checked, (d, which) -> {
                        cfg.edit().putString("searchEngine", which == 1 ? "google" : "baidu").apply();
                        updateEngineBtn(engineBtn);
                        engineBtn.announceForAccessibility("搜索引擎已改为" + names[which]);
                        d.dismiss();
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        Button clearBtn = findViewById(R.id.clearBtn);
        clearBtn.setOnClickListener(v -> new AlertDialog.Builder(this,
                android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("清除缓存和 Cookie")
                .setMessage("清理后需要重新登录网页账号，确定吗？")
                .setPositiveButton("清除", (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    WebView wv = new WebView(this);
                    wv.clearCache(true);
                    wv.destroy();
                    clearBtn.announceForAccessibility("缓存和 Cookie 已清除");
                })
                .setNegativeButton("取消", null)
                .show());

        Button done = findViewById(R.id.settingsDone);
        done.setOnClickListener(v -> finish());
    }
    private static final int REQ_ROLE_SETTINGS = 3002;

    private void askDefaultBrowser() {
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                android.app.role.RoleManager rm =
                        (android.app.role.RoleManager) getSystemService(android.app.role.RoleManager.class);
                if (rm != null && rm.isRoleAvailable(android.app.role.RoleManager.ROLE_BROWSER)) {
                    if (rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER)) {
                        announce("已经是默认浏览器了");
                        return;
                    }
                    startActivityForResult(
                            rm.createRequestRoleIntent(android.app.role.RoleManager.ROLE_BROWSER),
                            REQ_ROLE_SETTINGS);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        try {
            startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS));
            announce("请在系统设置里把默认浏览器选为清风浏览器");
        } catch (Exception e) {
            announce("请到系统设置里手动把默认浏览器设为本应用");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_ROLE_SETTINGS) {
            boolean held = false;
            if (Build.VERSION.SDK_INT >= 29) {
                try {
                    android.app.role.RoleManager rm =
                            (android.app.role.RoleManager) getSystemService(android.app.role.RoleManager.class);
                    held = rm != null && rm.isRoleHeld(android.app.role.RoleManager.ROLE_BROWSER);
                } catch (Exception ignored) {
                }
            }
            Button btn = findViewById(R.id.defaultBrowserBtn);
            if (held) {
                btn.setText("已是默认浏览器");
                btn.announceForAccessibility("已设为默认浏览器");
            } else {
                btn.announceForAccessibility("没有设置为默认浏览器");
            }
        }
    }

    private void announce(String s) {
        View root = findViewById(R.id.settingsRoot);
        if (root != null) {
            root.announceForAccessibility(s);
        }
    }

    private void updateRetentionBtn(Button btn) {
        String mode = cfg.getString("tabRetention", "keep");
        btn.setText("当前：" + MainActivity.retentionLabel(mode));
        btn.setContentDescription("退出后关闭标签页，当前" + MainActivity.retentionLabel(mode) + "，点击切换");
    }

    private void updateEngineBtn(Button btn) {
        String cur = cfg.getString("searchEngine", "baidu");
        boolean google = "google".equals(cur);
        btn.setText("当前搜索引擎：" + (google ? "谷歌" : "百度"));
        btn.setContentDescription("搜索引擎，当前" + (google ? "谷歌" : "百度") + "，点击切换");
    }
}
