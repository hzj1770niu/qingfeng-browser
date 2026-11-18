package com.qingfeng.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/** 应用内更新：多线路拉版本信息，多线路下载，带进度条，下完直接弹安装 */
public class Updater {

    private static final String REPO = "hzj1770niu/qingfeng-browser";
    private static final String TAG_PREFIX = "v";

    private static final String[] META_URLS = {
            "https://cdn.jsdelivr.net/gh/" + REPO + "@main/version.json",
            "https://fastly.jsdelivr.net/gh/" + REPO + "@main/version.json",
            "https://raw.githubusercontent.com/" + REPO + "/main/version.json",
            "https://gh-proxy.com/https://raw.githubusercontent.com/" + REPO + "/main/version.json",
            "https://ghfast.top/https://raw.githubusercontent.com/" + REPO + "/main/version.json"
    };

    public interface Callback {
        void onResult(boolean hasNew, String version, String apkPath, String note, String error);
    }

    public static String currentVersion(Context c) {
        try {
            return c.getPackageManager().getPackageInfo(c.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.6";
        }
    }

    private static List<String> apkUrls(String version, String apkPath) {
        List<String> list = new ArrayList<>();
        String file = apkPath == null ? "" : apkPath;
        String name = file.contains("/") ? file.substring(file.lastIndexOf('/') + 1) : file;
        if (file.startsWith("apk/")) {
            String raw = "https://raw.githubusercontent.com/" + REPO + "/main/" + file;
            list.add("https://ghproxy.net/" + raw);
            list.add("https://gh-proxy.com/" + raw);
            list.add("https://ghfast.top/" + raw);
            list.add("https://cdn.jsdelivr.net/gh/" + REPO + "@main/" + file);
            list.add(raw);
        }
        String rel = "https://github.com/" + REPO + "/releases/download/" + TAG_PREFIX + version + "/" + name;
        list.add(rel);
        list.add("https://gh-proxy.com/" + rel);
        list.add("https://ghfast.top/" + rel);
        list.add("https://ghproxy.net/" + rel);
        return list;
    }

    /** 后台查版本 */
    public static void check(Context ctx, Callback cb) {
        new Thread(() -> {
            String version = null;
            String apkPath = null;
            String note = null;
            String err = null;
            for (String u : META_URLS) {
                try {
                    String body = getText(u);
                    if (body == null) {
                        continue;
                    }
                    JSONObject o = new JSONObject(body);
                    version = o.optString("version", null);
                    apkPath = o.optString("apk", null);
                    note = o.optString("notes", null);
                    if (version != null && !version.isEmpty()) {
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
            boolean newer = false;
            if (version != null) {
                newer = compare(version, currentVersion(ctx)) > 0;
            } else {
                err = "所有更新线路都连不上，检查网络后再试";
            }
            final boolean fNewer = newer;
            final String fv = version;
            final String fa = apkPath;
            final String fn = note;
            final String fe = err;
            new Handler(Looper.getMainLooper()).post(() -> cb.onResult(fNewer, fv, fa, fn, fe));
        }).start();
    }

    /** 弹出"发现新版本"对话框 */
    public static void promptUpdate(Activity a, String version, String apkPath, String note) {
        String cur = currentVersion(a);
        String msg = "当前 1." + cur.replace("1.", "") + "，最新 " + version
                + (note == null || note.isEmpty() ? "" : "\n\n" + note);
        new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("发现新版本 " + version)
                .setMessage(msg)
                .setPositiveButton("下载并安装", (d, w) -> download(a, version, apkPath))
                .setNegativeButton("以后再说", null)
                .show();
    }

    private static void download(Activity a, String version, String apkPath) {
        final LinearLayout box = new LinearLayout(a);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 30, 40, 10);
        final TextView tip = new TextView(a);
        tip.setText("正在连接下载线路…");
        tip.setTextSize(16);
        final ProgressBar bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        box.addView(tip);
        box.addView(bar, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        final AlertDialog dlg = new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("正在下载新版本 " + version)
                .setView(box)
                .setCancelable(true)
                .setNegativeButton("取消", (d, w) -> d.dismiss())
                .create();
        dlg.show();

        final boolean[] cancelled = {false};
        dlg.setOnDismissListener(d -> cancelled[0] = true);

        new Thread(() -> {
            File out = new File(a.getExternalFilesDir(null), "qingfeng-update-" + version + ".apk");
            String okUrl = null;
            String err = null;
            List<String> urls = apkUrls(version, apkPath);
            for (String u : urls) {
                if (cancelled[0]) {
                    break;
                }
                try {
                    if (fetch(a, u, out, tip, bar, cancelled)) {
                        okUrl = u;
                        break;
                    }
                } catch (Exception ignored) {
                }
            }
            final String fErr = okUrl == null ? (err == null ? "所有下载线路都失败了，可以在设置里复制链接手动下载" : err) : null;
            final File fOut = out;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (cancelled[0]) {
                    return;
                }
                try {
                    dlg.dismiss();
                } catch (Exception ignored) {
                }
                if (fErr != null) {
                    new AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                            .setTitle("下载失败")
                            .setMessage(fErr)
                            .setPositiveButton("知道了", null)
                            .show();
                    return;
                }
                install(a, fOut);
            });
        }).start();
    }

    private static boolean fetch(Activity a, String url, File out, TextView tip, ProgressBar bar, boolean[] cancelled) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 Android");
        try {
            int code = c.getResponseCode();
            if (code != 200) {
                return false;
            }
            long total = c.getContentLengthLong();
            InputStream in = c.getInputStream();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[8192];
            long done = 0;
            int lastPct = -1;
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancelled[0]) {
                    fos.close();
                    in.close();
                    return false;
                }
                fos.write(buf, 0, n);
                done += n;
                int pct = total > 0 ? (int) (done * 100 / total) : 0;
                if (pct != lastPct && pct % 5 == 0) {
                    lastPct = pct;
                    final int fp = pct;
                    final long fd = done;
                    final long ft = total;
                    a.runOnUiThread(() -> {
                        bar.setProgress(fp);
                        tip.setText("已下载 " + fp + "%（" + (fd / 1024 / 1024) + "MB / "
                                + (ft > 0 ? (ft / 1024 / 1024) + "MB" : "未知大小") + "）");
                    });
                }
            }
            fos.flush();
            fos.close();
            in.close();
            if (out.length() < 1024) {
                return false;
            }
            return true;
        } finally {
            c.disconnect();
        }
    }

    /** 拉起系统安装界面 */
    public static void install(Activity a, File apk) {
        if (Build.VERSION.SDK_INT >= 26) {
            boolean can = a.getPackageManager().canRequestPackageInstalls();
            if (!can) {
                View root = a.findViewById(android.R.id.content);
                if (root != null) {
                    root.announceForAccessibility("请先允许清风浏览器安装应用，返回后会自动继续安装");
                }
                try {
                    a.startActivity(new Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                            .setData(Uri.parse("package:" + a.getPackageName())));
                } catch (Exception ignored) {
                }
                return;
            }
        }
        Uri u = androidx.core.content.FileProvider.getUriForFile(
                a, a.getPackageName() + ".fileprovider", apk);
        Intent it = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(u, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            a.startActivity(it);
            View root = a.findViewById(android.R.id.content);
            if (root != null) {
                root.announceForAccessibility("下载完成，已打开安装界面");
            }
        } catch (Exception e) {
            View root = a.findViewById(android.R.id.content);
            if (root != null) {
                root.announceForAccessibility("打不开安装界面，请手动到文件管理器安装下载好的安装包");
            }
        }
    }

    public static int compare(String a, String b) {
        String[] x = a.replaceAll("[^0-9.]", "").split("\\.");
        String[] y = b.replaceAll("[^0-9.]", "").split("\\.");
        int len = Math.max(x.length, y.length);
        for (int i = 0; i < len; i++) {
            int va = i < x.length && !x[i].isEmpty() ? Integer.parseInt(x[i]) : 0;
            int vb = i < y.length && !y[i].isEmpty() ? Integer.parseInt(y[i]) : 0;
            if (va != vb) {
                return va > vb ? 1 : -1;
            }
        }
        return 0;
    }

    private static String getText(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(8000);
        c.setInstanceFollowRedirects(true);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 Android");
        try {
            if (c.getResponseCode() != 200) {
                return null;
            }
            InputStream in = c.getInputStream();
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] b = new byte[4096];
            int n;
            while ((n = in.read(b)) > 0) {
                bo.write(b, 0, n);
                if (bo.size() > 64 * 1024) {
                    break;
                }
            }
            in.close();
            return bo.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }
}
