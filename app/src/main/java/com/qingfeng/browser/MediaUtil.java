package com.qingfeng.browser;

import java.net.URI;
import java.util.Locale;

final class MediaUtil {

    private MediaUtil() {
    }

    static boolean isHttp(String u) {
        return u != null && (u.toLowerCase(Locale.US).startsWith("http://")
                || u.toLowerCase(Locale.US).startsWith("https://"));
    }

    /** 是否是“值得交给内置播放器”的媒体地址（排除分片、blob、data） */
    static boolean isMedia(String u) {
        if (!isHttp(u)) {
            return false;
        }
        String p = u.toLowerCase(Locale.US);
        int q = p.indexOf('?');
        String base = q >= 0 ? p.substring(0, q) : p;
        if (base.endsWith(".m3u8") || base.endsWith(".mpd") || base.endsWith(".mp4")
                || base.endsWith(".m4v") || base.endsWith(".webm") || base.endsWith(".flv")
                || base.endsWith(".mov") || base.endsWith(".3gp") || base.endsWith(".mkv")) {
            return true;
        }
        // 形如 https://xx.com/index.m3u8?token=xxx 或 https://xx.com/api/play?format=m3u8
        if (q >= 0) {
            String query = p.substring(q + 1);
            if (query.contains("m3u8") || query.contains("playlist") || query.contains("hls")) {
                return true;
            }
        }
        return false;
    }

    static String kindOf(String u) {
        String p = u == null ? "" : u.toLowerCase(Locale.US);
        if (p.contains(".m3u8") || p.contains("m3u8") || p.contains("hls")) {
            return "HLS";
        }
        if (p.contains(".mpd") || p.contains("dash")) {
            return "DASH";
        }
        if (p.contains(".mp4") || p.contains(".m4v") || p.contains(".mov") || p.contains(".3gp")) {
            return "MP4";
        }
        if (p.contains(".webm")) {
            return "WEBM";
        }
        if (p.contains(".flv")) {
            return "FLV";
        }
        return "视频";
    }

    /** 给读屏念的一行说明 */
    static String label(String u) {
        String kind = kindOf(u);
        String k = kind;
        if ("HLS".equals(kind)) {
            k = "HLS 播放列表（m3u8）";
        } else if ("DASH".equals(kind)) {
            k = "DASH 流（mpd）";
        } else if ("MP4".equals(kind)) {
            k = "MP4 视频";
        }
        String host = hostOf(u);
        String tail = tailOf(u);
        return k + "，来自 " + host + (tail.isEmpty() ? "" : "，文件 " + tail);
    }

    static String hostOf(String u) {
        try {
            String h = new URI(u).getHost();
            return h == null ? "" : h;
        } catch (Exception e) {
            return "";
        }
    }

    static String tailOf(String u) {
        try {
            String path = new URI(u).getPath();
            if (path == null || path.isEmpty()) {
                return "";
            }
            int slash = path.lastIndexOf('/');
            String name = slash >= 0 ? path.substring(slash + 1) : path;
            if (name.length() > 40) {
                name = name.substring(name.length() - 40);
            }
            return name;
        } catch (Exception e) {
            return "";
        }
    }

    static String fmtTime(long ms) {
        if (ms <= 0) {
            return "00:00";
        }
        long totalSec = ms / 1000;
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", h, m, s);
        }
        return String.format(Locale.US, "%02d:%02d", m, s);
    }
}
