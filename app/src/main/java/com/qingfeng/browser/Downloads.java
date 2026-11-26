package com.qingfeng.browser;

import android.app.DownloadManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.text.TextUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Locale;

final class Downloads {

    private Downloads() {
    }

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
    }

    /** 走系统下载服务，存到 Download 目录，通知栏显示进度 */
    static long enqueue(Context c, String url, String userAgent, String contentDisposition,
                        String mime, String pageUrl) {
        String name = guessName(url, contentDisposition, mime);
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            if (!TextUtils.isEmpty(userAgent)) {
                r.addRequestHeader("User-Agent", userAgent);
            }
            if (!TextUtils.isEmpty(pageUrl) && MediaUtil.isHttp(pageUrl)) {
                r.addRequestHeader("Referer", pageUrl);
            }
            r.setTitle(name);
            r.setDescription("正在下载到 Download 文件夹");
            if (!TextUtils.isEmpty(mime)) {
                r.setMimeType(mime);
            }
            r.setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            r.setAllowedOverMetered(true);
            r.setAllowedOverRoaming(true);
            DownloadManager dm = (DownloadManager) c.getSystemService(Context.DOWNLOAD_SERVICE);
            if (dm == null) {
                return -1;
            }
            long id = dm.enqueue(r);
            SharedPreferences p = prefs(c);
            java.util.Set<String> ids = new java.util.HashSet<>(
                    p.getStringSet("dl_ids", new java.util.HashSet<>()));
            ids.add(String.valueOf(id));
            p.edit().putStringSet("dl_ids", ids).apply();
            return id;
        } catch (Exception e) {
            return -1;
        }
    }

    static String guessName(String url, String contentDisposition, String mime) {
        String name = null;
        if (contentDisposition != null) {
            name = parseDisposition(contentDisposition);
        }
        if (TextUtils.isEmpty(name)) {
            try {
                String path = new URI(url).getPath();
                if (path != null) {
                    int slash = path.lastIndexOf('/');
                    if (slash >= 0 && slash < path.length() - 1) {
                        name = path.substring(slash + 1);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (TextUtils.isEmpty(name)) {
            name = "download_" + System.currentTimeMillis();
        }
        name = sanitize(name);
        if (!name.contains(".") && !TextUtils.isEmpty(mime)) {
            String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            if (!TextUtils.isEmpty(ext)) {
                name = name + "." + ext;
            }
        }
        return name;
    }

    private static String parseDisposition(String cd) {
        try {
            String lower = cd.toLowerCase(Locale.US);
            int idx = lower.indexOf("filename*=");
            if (idx >= 0) {
                String v = cd.substring(idx + "filename*=".length()).trim();
                int semi = v.indexOf(';');
                if (semi > 0) {
                    v = v.substring(0, semi);
                }
                int q = v.indexOf("''");
                if (q >= 0) {
                    v = v.substring(q + 2);
                }
                v = v.replace("\"", "");
                return Uri.decode(v);
            }
            idx = lower.indexOf("filename=");
            if (idx >= 0) {
                String v = cd.substring(idx + "filename=".length()).trim();
                int semi = v.indexOf(';');
                if (semi > 0) {
                    v = v.substring(0, semi);
                }
                v = v.replace("\"", "");
                return Uri.decode(v);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    static String sanitize(String name) {
        if (name == null) {
            return "download";
        }
        StringBuilder sb = new StringBuilder();
        for (char ch : name.toCharArray()) {
            if (ch == '/' || ch == '\\' || ch == ':' || ch == '*' || ch == '?' || ch == '"'
                    || ch == '<' || ch == '>' || ch == '|' || ch < 0x20) {
                sb.append('_');
            } else {
                sb.append(ch);
            }
        }
        String s = sb.toString().trim();
        while (s.startsWith(".")) {
            s = s.substring(1);
        }
        if (s.length() > 120) {
            s = s.substring(0, 120);
        }
        return s.isEmpty() ? "download" : s;
    }

    /** 网页里由 JS 生成的 blob / data 链接，走这里落盘到 Download 目录 */
    static String saveBase64(Context c, String name, String mime, String base64) {
        try {
            byte[] data = android.util.Base64.decode(base64, android.util.Base64.DEFAULT);
            String fileName = sanitize(TextUtils.isEmpty(name) ? ("download_" + System.currentTimeMillis()) : name);
            if (!fileName.contains(".") && !TextUtils.isEmpty(mime)) {
                String ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
                if (!TextUtils.isEmpty(ext)) {
                    fileName = fileName + "." + ext;
                }
            }
            if (Build.VERSION.SDK_INT >= 29) {
                android.content.ContentValues values = new android.content.ContentValues();
                values.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                values.put(android.provider.MediaStore.MediaColumns.MIME_TYPE,
                        TextUtils.isEmpty(mime) ? "application/octet-stream" : mime);
                values.put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS);
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 1);
                Uri uri = c.getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
                if (uri == null) {
                    return null;
                }
                try (OutputStream out = c.getContentResolver().openOutputStream(uri)) {
                    if (out == null) {
                        return null;
                    }
                    out.write(data);
                }
                values.clear();
                values.put(android.provider.MediaStore.MediaColumns.IS_PENDING, 0);
                c.getContentResolver().update(uri, values, null, null);
                return fileName;
            } else {
                File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                if (!dir.exists() && !dir.mkdirs()) {
                    return null;
                }
                File f = new File(dir, fileName);
                try (FileOutputStream out = new FileOutputStream(f)) {
                    out.write(data);
                }
                return fileName;
            }
        } catch (Throwable e) {
            return null;
        }
    }
}
