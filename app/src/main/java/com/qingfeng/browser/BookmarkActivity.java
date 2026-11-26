package com.qingfeng.browser;

import android.app.Activity;
import android.app.AlertDialog;
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

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class BookmarkActivity extends Activity {

    private static final int MODE_BOOKMARK = 0;
    private static final int MODE_HISTORY = 1;

    private int mode = MODE_BOOKMARK;
    private LinearLayout root;
    private ListView list;
    private TextView emptyTip;
    private Button modeBookmark;
    private Button modeHistory;
    private Button clearMode;
    private BaseAdapter adapter;
    private final List<Store.Entry> items = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmarks);
        root = findViewById(R.id.bmRoot);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        Ui.applyInsets(root);

        list = findViewById(R.id.bmList);
        emptyTip = findViewById(R.id.bmEmpty);
        modeBookmark = findViewById(R.id.modeBookmark);
        modeHistory = findViewById(R.id.modeHistory);
        clearMode = findViewById(R.id.clearMode);

        adapter = new Adapter();
        list.setAdapter(adapter);

        modeBookmark.setOnClickListener(v -> {
            mode = MODE_BOOKMARK;
            refresh();
        });
        modeHistory.setOnClickListener(v -> {
            mode = MODE_HISTORY;
            refresh();
        });

        Button addCurrent = findViewById(R.id.addCurrent);
        addCurrent.setOnClickListener(v -> addCurrentPage());

        clearMode.setOnClickListener(v -> {
            String what = mode == MODE_BOOKMARK ? "全部书签" : "全部历史记录";
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle("清空" + what)
                    .setMessage("删除后无法恢复，确定吗？")
                    .setPositiveButton("清空", (d, w) -> {
                        Store.clear(this, mode == MODE_BOOKMARK
                                ? Store.KEY_BOOKMARKS : Store.KEY_HISTORY);
                        refresh();
                        announce(what + "已清空");
                    })
                    .setNegativeButton("取消", null)
                    .show();
        });

        Button done = findViewById(R.id.bmDone);
        done.setOnClickListener(v -> finish());

        list.setOnItemClickListener((parent, view, position, id) -> open(items.get(position)));

        list.setOnItemLongClickListener((parent, view, position, id) -> {
            final Store.Entry e = items.get(position);
            String[] ops = mode == MODE_BOOKMARK
                    ? new String[]{"打开", "删除这个书签"}
                    : new String[]{"打开", "加为书签", "删除这条记录"};
            new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(e.title)
                    .setItems(ops, (d, which) -> {
                        if (which == 0) {
                            open(e);
                        } else if (mode == MODE_HISTORY && which == 1) {
                            boolean ok = Store.addBookmark(this, e.title, e.url);
                            announce(ok ? "已加为书签" : "这条已经在书签里了");
                        } else {
                            Store.remove(this, mode == MODE_BOOKMARK
                                    ? Store.KEY_BOOKMARKS : Store.KEY_HISTORY, e.url);
                            refresh();
                            announce("已删除");
                        }
                    })
                    .setNegativeButton("取消", null)
                    .show();
            return true;
        });

        refresh();
    }

    private void open(Store.Entry e) {
        MainActivity m = MainActivity.get();
        if (m != null) {
            m.openUrlFromUi(e.url);
        }
        finish();
    }

    private void addCurrentPage() {
        MainActivity m = MainActivity.get();
        if (m == null) {
            announce("浏览器窗口不在了");
            return;
        }
        String url = m.currentTabUrl();
        String title = m.currentTabTitle();
        if (!MediaUtil.isHttp(url)) {
            announce("当前是空白页，没有可收藏的网址");
            return;
        }
        boolean ok = Store.addBookmark(this, title, url);
        announce(ok ? "已收藏：" + title : "这个页面已经在书签里了");
        mode = MODE_BOOKMARK;
        refresh();
    }

    private void refresh() {
        items.clear();
        items.addAll(Store.list(this,
                mode == MODE_BOOKMARK ? Store.KEY_BOOKMARKS : Store.KEY_HISTORY));
        String empty = mode == MODE_BOOKMARK
                ? "还没有书签。"
                : "还没有浏览记录。";
        emptyTip.setText(empty);
        emptyTip.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        clearMode.setText(mode == MODE_BOOKMARK ? "清空书签" : "清空历史");
        adapter.notifyDataSetChanged();
        String label = mode == MODE_BOOKMARK ? "书签" : "历史";
        list.announceForAccessibility("当前显示" + label + "，共 " + items.size() + " 条");
    }

    private void announce(String s) {
        if (root != null) {
            root.announceForAccessibility(s);
        }
    }

    private class Adapter extends BaseAdapter {

        private final SimpleDateFormat fmt =
                new SimpleDateFormat("MM月dd日 HH:mm", Locale.US);

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
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(BookmarkActivity.this)
                        .inflate(R.layout.item_bookmark, parent, false);
            }
            Store.Entry e = items.get(position);
            TextView title = row.findViewById(R.id.bmTitle);
            TextView url = row.findViewById(R.id.bmUrl);
            title.setText("第" + (position + 1) + "条：" + e.title);
            String time = e.time > 0 ? fmt.format(new Date(e.time)) + " · " : "";
            url.setText(time + e.url);
            row.setContentDescription("第" + (position + 1) + "条，" + e.title + "，网址 " + e.url
                    + "，双击打开");
            return row;
        }
    }
}
