package com.qingfeng.browser;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class Store {

    static final String KEY_BOOKMARKS = "bookmarks";
    static final String KEY_HISTORY = "history";
    static final int MAX_HISTORY = 300;

    private Store() {
    }

    static class Entry {
        String title = "";
        String url = "";
        long time;
    }

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
    }

    static List<Entry> list(Context c, String key) {
        List<Entry> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                Entry e = new Entry();
                e.url = o.optString("url");
                e.title = o.optString("title");
                e.time = o.optLong("time");
                if (e.url != null && !e.url.isEmpty()) {
                    out.add(e);
                }
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    private static void save(Context c, String key, List<Entry> items) {
        JSONArray a = new JSONArray();
        for (Entry e : items) {
            JSONObject o = new JSONObject();
            try {
                o.put("url", e.url);
                o.put("title", e.title);
                o.put("time", e.time);
            } catch (Exception ignored) {
            }
            a.put(o);
        }
        p(c).edit().putString(key, a.toString()).apply();
    }

    static void addHistory(Context c, String title, String url) {
        if (url == null || !MediaUtil.isHttp(url)) {
            return;
        }
        List<Entry> items = list(c, KEY_HISTORY);
        for (int i = 0; i < items.size(); i++) {
            if (url.equals(items.get(i).url)) {
                items.remove(i);
                break;
            }
        }
        Entry e = new Entry();
        e.url = url;
        e.title = title == null || title.trim().isEmpty() ? MediaUtil.hostOf(url) : title;
        e.time = System.currentTimeMillis();
        items.add(0, e);
        while (items.size() > MAX_HISTORY) {
            items.remove(items.size() - 1);
        }
        save(c, KEY_HISTORY, items);
    }

    /** @return true 表示新加成功，false 表示已经有了 */
    static boolean addBookmark(Context c, String title, String url) {
        if (url == null || !MediaUtil.isHttp(url)) {
            return false;
        }
        List<Entry> items = list(c, KEY_BOOKMARKS);
        for (Entry x : items) {
            if (url.equals(x.url)) {
                return false;
            }
        }
        Entry e = new Entry();
        e.url = url;
        e.title = title == null || title.trim().isEmpty() ? MediaUtil.hostOf(url) : title;
        e.time = System.currentTimeMillis();
        items.add(0, e);
        save(c, KEY_BOOKMARKS, items);
        return true;
    }

    static boolean hasBookmark(Context c, String url) {
        for (Entry x : list(c, KEY_BOOKMARKS)) {
            if (url != null && url.equals(x.url)) {
                return true;
            }
        }
        return false;
    }

    static void remove(Context c, String key, String url) {
        List<Entry> items = list(c, key);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).url.equals(url)) {
                items.remove(i);
                break;
            }
        }
        save(c, key, items);
    }

    static void clear(Context c, String key) {
        p(c).edit().remove(key).apply();
    }
}
