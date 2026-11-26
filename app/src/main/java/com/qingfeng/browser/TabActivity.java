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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class TabActivity extends Activity {

    private ListView list;
    private LinearLayout root;
    private BaseAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tabs);
        root = findViewById(R.id.tabsRoot);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        Ui.applyInsets(root);

        list = findViewById(R.id.tabList);
        adapter = new TabAdapter();
        list.setAdapter(adapter);

        list.setOnItemClickListener((parent, view, position, id) -> {
            MainActivity m = MainActivity.get();
            if (m != null) {
                m.selectTabByIndex(position);
            }
            finish();
        });

        list.setOnItemLongClickListener((parent, view, position, id) -> {
            showTabMenu(position);
            return true;
        });

        Button newTabBtn = findViewById(R.id.newTabBtn);
        newTabBtn.setOnClickListener(v -> {
            MainActivity m = MainActivity.get();
            if (m != null) {
                m.newTabFromUi(null);
            }
            adapter.notifyDataSetChanged();
        });

        Button closeAllBtn = findViewById(R.id.closeAllBtn);
        closeAllBtn.setOnClickListener(v -> closeAllDialog());

        Button doneBtn = findViewById(R.id.doneBtn);
        doneBtn.setOnClickListener(v -> finish());
    }

    private void showTabMenu(int position) {
        MainActivity m = MainActivity.get();
        if (m == null) {
            finish();
            return;
        }
        String[] items = {"打开这个标签页", "关闭这个标签页", "关闭所有标签页"};
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("第 " + (position + 1) + " 个标签页：" + m.tabTitle(position))
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0:
                            m.selectTabByIndex(position);
                            finish();
                            break;
                        case 1:
                            m.closeTab(position);
                            adapter.notifyDataSetChanged();
                            break;
                        default:
                            closeAllDialog();
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void closeAllDialog() {
        MainActivity m = MainActivity.get();
        int n = m == null ? 0 : m.tabCount();
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("关闭所有标签页")
                .setMessage("当前共有 " + n + " 个标签页，关闭后会新建一个空白页。")
                .setPositiveButton("关闭全部", (d, w) -> {
                    if (m != null) {
                        m.closeAllTabs();
                    }
                    adapter.notifyDataSetChanged();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private class TabAdapter extends BaseAdapter {

        @Override
        public int getCount() {
            MainActivity m = MainActivity.get();
            return m == null ? 0 : m.tabCount();
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            if (row == null) {
                row = LayoutInflater.from(TabActivity.this).inflate(R.layout.item_tab, parent, false);
            }
            TextView title = row.findViewById(R.id.tabTitle);
            TextView url = row.findViewById(R.id.tabUrl);
            MainActivity m = MainActivity.get();
            if (m == null) {
                return row;
            }
            String t = m.tabTitle(position);
            String u = m.tabUrl(position);
            boolean cur = m.isCurrentTab(position);
            title.setText((cur ? "● 当前：" : "") + "第" + (position + 1) + "个：" + t);
            url.setText(u == null ? "" : u);
            row.setContentDescription("标签页" + (position + 1) + "，"
                    + (cur ? "当前标签页，" : "") + t);
            return row;
        }
    }
}
