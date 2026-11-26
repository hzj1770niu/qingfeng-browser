package com.qingfeng.browser;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import androidx.core.content.FileProvider;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DownloadActivity extends Activity {

    private static class Item {
        long id;
        String name = "";
        String status = "";
        String mime = "";
        String localUri = "";
        int managerStatus;
        long done;
        long total;
    }

    private ListView list;
    private LinearLayout root;
    private TextView emptyTip;
    private BaseAdapter adapter;
    private final List<Item> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_downloads);
        root = findViewById(R.id.dlRoot);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        Ui.applyInsets(root);

        list = findViewById(R.id.dlList);
        emptyTip = findViewById(R.id.emptyTip);
        adapter = new Adapter();
        list.setAdapter(adapter);

        list.setOnItemClickListener((parent, view, position, id) -> open(items.get(position)));
        list.setOnItemLongClickListener((parent, view, position, id) -> {
            Item it = items.get(position);
            String[] ops = {"打开文件", "只删除这一条记录", "清空全部记录"};
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(it.name)
                    .setItems(ops, (d, which) -> {
                        if (which == 0) {
                            open(it);
                        } else if (which == 1) {
                            DownloadManager dm = dm();
                            if (dm != null) {
                                dm.remove(it.id);
                            }
                            removeId(it.id);
                            refresh();
                        } else {
                            clearAll();
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        Button refreshBtn = findViewById(R.id.refreshBtn);
        refreshBtn.setOnClickListener(v -> refresh());

        Button clearBtn = findViewById(R.id.clearBtn);
        clearBtn.setOnClickListener(v -> clearAll());

        Button done = findViewById(R.id.dlDoneBtn);
        done.setOnClickListener(v -> finish());

        refresh();
    }

    private DownloadManager dm() {
        return (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE);
    }

    private void removeId(long id) {
        SharedPreferences p = prefs();
        Set<String> ids = new HashSet<>(p.getStringSet("dl_ids", new HashSet<>()));
        ids.remove(String.valueOf(id));
        p.edit().putStringSet("dl_ids", ids).apply();
    }

    private void clearAll() {
        DownloadManager dm = dm();
        Set<String> ids = new HashSet<>(prefs().getStringSet("dl_ids", new HashSet<>()));
        if (dm != null) {
            for (String s : ids) {
                try {
                    dm.remove(Long.parseLong(s));
                } catch (Exception ignored) {
                }
            }
        }
        prefs().edit().remove("dl_ids").apply();
        items.clear();
        adapter.notifyDataSetChanged();
        emptyTip.setVisibility(View.VISIBLE);
    }

    private void refresh() {
        items.clear();
        DownloadManager dm = dm();
        Set<String> ids = new HashSet<>(prefs().getStringSet("dl_ids", new HashSet<>()));
        if (dm != null && !ids.isEmpty()) {
            long[] arr = new long[ids.size()];
            int i = 0;
            for (String s : ids) {
                try {
                    arr[i++] = Long.parseLong(s);
                } catch (Exception e) {
                    arr[i - 1] = 0;
                }
            }
            Cursor c = dm.query(new DownloadManager.Query().setFilterById(arr));
            if (c != null) {
                while (c.moveToNext()) {
                    Item it = new Item();
                    it.id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID));
                    it.name = str(c, DownloadManager.COLUMN_TITLE);
                    it.mime = str(c, DownloadManager.COLUMN_MEDIA_TYPE);
                    it.localUri = str(c, DownloadManager.COLUMN_LOCAL_URI);
                    it.managerStatus = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    it.done = c.getLong(c.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
                    it.total = c.getLong(c.getColumnIndexOrThrow(
                            DownloadManager.COLUMN_TOTAL_SIZE_BYTES));
                    it.status = describe(it);
                    items.add(it);
                }
                c.close();
            }
        }
        emptyTip.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        adapter.notifyDataSetChanged();
    }

    private String str(Cursor c, String col) {
        int idx = c.getColumnIndex(col);
        if (idx < 0) {
            return "";
        }
        String v = c.getString(idx);
        return v == null ? "" : v;
    }

    private String describe(Item it) {
        String size = "";
        if (it.total > 0) {
            size = "，" + mb(it.done) + " / " + mb(it.total);
        }
        switch (it.managerStatus) {
            case DownloadManager.STATUS_SUCCESSFUL:
                return "已完成" + (it.total > 0 ? size : "");
            case DownloadManager.STATUS_RUNNING: {
                int pct = it.total > 0 ? (int) (it.done * 100 / it.total) : 0;
                return "下载中 " + pct + "%" + size;
            }
            case DownloadManager.STATUS_PAUSED:
                return "已暂停" + size;
            case DownloadManager.STATUS_PENDING:
                return "等待中";
            case DownloadManager.STATUS_FAILED:
                return "下载失败";
            default:
                return "未知状态";
        }
    }

    private String mb(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        if (bytes < 1024 * 1024) {
            return (bytes / 1024) + "KB";
        }
        return String.format(java.util.Locale.US, "%.1fMB", bytes / 1024.0 / 1024.0);
    }

    private void open(Item it) {
        if (it.managerStatus != DownloadManager.STATUS_SUCCESSFUL) {
            list.announceForAccessibility(it.name + " 还在下载中，完成后才能打开");
            return;
        }
        Uri uri = null;
        DownloadManager dm = dm();
        if (dm != null) {
            try {
                uri = dm.getUriForDownloadedFile(it.id);
            } catch (Exception ignored) {
            }
        }
        if (uri == null && !it.localUri.isEmpty()) {
            Uri raw = Uri.parse(it.localUri);
            if ("file".equals(raw.getScheme())) {
                File f = new File(raw.getPath());
                try {
                    uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
                } catch (Exception ignored) {
                }
            } else {
                uri = raw;
            }
        }
        if (uri == null) {
            list.announceForAccessibility("找不到这个文件了，可能已被删除");
            return;
        }
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, it.mime.isEmpty() ? "*/*" : it.mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (Exception e) {
            list.announceForAccessibility("没有可以打开这种文件的应用");
        }
    }

    private class Adapter extends BaseAdapter {
        @Override
        public int getCount() {
            return items.size();
        }

        @Override
        public Object getItem(int position) {
            return items.get(position);
        }

        @Override
        public long getItemId(int position) {
            return items.get(position).id;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(DownloadActivity.this)
                        .inflate(R.layout.item_download, parent, false);
            }
            Item it = items.get(position);
            TextView name = row.findViewById(R.id.dlName);
            TextView status = row.findViewById(R.id.dlStatus);
            name.setText(it.name);
            status.setText(it.status);
            row.setContentDescription(it.name + "，" + it.status + "，双击打开");
            return row;
        }
    }
}
