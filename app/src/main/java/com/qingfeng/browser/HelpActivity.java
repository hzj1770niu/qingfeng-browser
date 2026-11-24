package com.qingfeng.browser;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class HelpActivity extends Activity {

    private static final String[][] SECTIONS = {
            {"地址栏", "在地址栏输入网址可直接打开；输入关键词则使用所选搜索引擎（默认百度）进行搜索。"},
            {"隐藏控件", "在菜单中选择“隐藏控件（全屏阅读）”，或长按“菜单”按钮，可收起顶部地址栏、按钮与底部工具条，仅保留网页。隐藏后不显示浮动按钮，按返回键或全面屏返回手势即可恢复控件；隐藏状态下下拉网页可刷新。"},
            {"下拉刷新", "网页位于顶部时向下拖动一段距离即可重新加载页面，无需点击刷新按钮；可在设置中关闭该手势。"},
            {"前进后退", "顶部提供“后退”“前进”“刷新”按钮，读屏会提示按钮是否可用。"},
            {"标签页", "点击“标签页”按钮打开标签页列表，点击条目即可切换；在条目上长按可关闭当前标签页或关闭全部标签页。在主界面长按“标签页”按钮，可一次性关闭全部标签页。"},
            {"书签与历史", "书签与浏览历史位于同一页面，通过“书签”“历史”两个按钮切换。长按“书签”按钮可将当前页面保存为书签，标题可修改；在历史记录中长按条目可添加为书签。"},
            {"内置播放器", "页面中出现可播放的视频时，自动交由内置播放器接管，无需在网页内寻找播放按钮。播放器提供播放暂停、后退十秒（长按后退三十秒）、前进十秒（长按三十秒）、进度条、全屏、倍速、音轨与字幕切换。双击画面可暂停或继续播放，有线耳机与蓝牙耳机的线控可控制播放暂停；应用切换到后台或返回主屏幕后，视频以画中画窗口继续播放。"},
            {"加密内容", "受 DRM 加密的会员正片（部分爱奇艺、优酷、腾讯视频付费内容）无法由内置播放器解码，播放失败时可选择改用网页播放器，该站点后续不再自动接管。"},
            {"退出与标签页保留", "菜单最后一项为“退出浏览器”。设置中“退出后关闭标签页”提供四个选项：永不关闭（手动）、一天后、一周后、一个月后。选择永不关闭时，下次打开将恢复上次的标签页。"},
            {"下载", "网页中的下载链接由系统下载服务处理，文件保存至 Download 文件夹，通知栏可查看进度。“下载管理”可查看进度、打开文件或删除记录。"},
            {"电脑版网页", "在菜单中切换为电脑版网页后，网站将识别为 Windows 平台的最新版 Chrome；再次点击可切回手机版网页。"},
            {"搜索引擎", "设置中可选择百度或谷歌，选择后立即生效。"},
            {"默认浏览器", "在设置或菜单中选择“设为默认浏览器”，在系统角色确认框中同意后，其他应用中的网页链接将由清风浏览器打开。"},
            {"关于", "网页引擎为系统自带的 Chromium WebView，与 Chrome 内核同源，可随系统或应用商店更新。"}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_help);
        ScrollView root = findViewById(R.id.helpRoot);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        Ui.applyInsets(root);

        LinearLayout content = findViewById(R.id.helpContent);

        TextView title = new TextView(this);
        title.setText("使用说明");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        title.setTextColor(0xFFFFFFFF);
        title.setPadding(0, 0, 0, Ui.dp(this, 10));
        content.addView(title);

        for (String[] s : SECTIONS) {
            TextView h = new TextView(this);
            h.setText(s[0]);
            h.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            h.setTextColor(0xFF00E676);
            h.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 4));
            content.addView(h);

            TextView b = new TextView(this);
            b.setText(s[1]);
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            b.setTextColor(0xFFEEEEEE);
            b.setLineSpacing(Ui.dp(this, 4), 1f);
            content.addView(b);
        }

        Button done = new Button(this);
        done.setText("返回");
        done.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        done.setMinHeight(Ui.dp(this, 56));
        done.setBackgroundResource(R.drawable.btn_bg);
        done.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 56));
        lp.topMargin = Ui.dp(this, 24);
        done.setLayoutParams(lp);
        done.setOnClickListener(v -> finish());
        content.addView(done);
    }
}
