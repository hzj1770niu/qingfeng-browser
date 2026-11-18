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
            {"地址栏", "输入网址就直接打开；输入的是关键词，就用你在设置里选的搜索引擎（默认百度）去搜。整条地址栏点一下就能改。"},
            {"隐藏控件", "菜单里“隐藏控件（全屏阅读）”会把上面的地址栏、按钮和下面的播放器条都收起来，只剩网页。隐藏后网页右上角有一个“显示控件”的小按钮，点一下就回来。长按“菜单”按钮也能直接隐藏。"},
            {"下拉刷新", "在网页最顶部往下拉一段，页面就会重新加载，不用再去点刷新按钮；隐藏控件的时候这个最顺手。设置里可以关掉。"},
            {"前进后退", "顶部的“后退”“前进”“刷新”三个按钮，读屏会念出是否可以点。"},
            {"标签页", "点“标签页”按钮打开标签页列表，点哪一条就切到哪一页；随便哪一条上长按，都能选“关闭这个标签页”或“关闭所有标签页”。在主界面上长按“标签页”按钮，同样可以一次关掉全部。"},
            {"书签与历史", "点“书签”按钮，书签和浏览历史在同一个页面里，用“书签”“历史”两个按钮来回切。在浏览器里长按“书签”按钮，就把当前这一页存成书签，标题可以改，跟 Safari 一样。历史里长按一条，还能顺手加为书签。"},
            {"内置播放器", "页面上一出现能播的视频，就自动交给我们内置的播放器，不用再去网页里找全屏按钮。播放器里是整排的大按钮：播放暂停、后退十秒（长按三十秒）、前进十秒（长按三十秒）、进度条、全屏、倍速、音轨字幕切换。按钮只在你按下去的时候才出现。"},
            {"加密内容", "带 DRM 加密的会员正片（部分爱奇艺、优酷、腾讯的付费内容）内置播放器解不了，播放失败时点“改用网页播放”就行，那个网站以后不会再自动接管。"},
            {"隐藏控件", "菜单里选“隐藏控件（全屏阅读）”，或者在“菜单”按钮上长按一下，顶部和底部的按钮都会收起来，网页铺满整屏。这时候不需要去找刷新按钮，只要把网页往下拉一下就能刷新。右上角会留下一个小小的“显示控件”按钮，点它就恢复。"},
            {"下拉刷新", "网页已经在最顶部时，手指往下拖一段距离就重新加载，不用再去点刷新按钮；可以在设置里关掉。"},
            {"退出与标签页保留", "菜单最后一项是“退出浏览器”。设置里“退出后关闭标签页”跟 Safari 一样有四个选项：永不关闭（手动）、一天后、一周后、一个月后。选永不关闭，下次打开会把上次的标签页原样恢复。"},
            {"下载", "网页里的下载链接会交给系统的下载服务，文件存在 Download 文件夹，通知栏能看到进度。菜单里的“下载管理”可以看进度、打开文件、删除记录。"},
            {"隐藏控件", "菜单里的“隐藏控件（全屏阅读）”，或者在主界面上长按“菜单”按钮，顶栏和底栏全部收起，只剩网页本身，右上角留一个“显示控件”的小按钮。跟 Chrome、Safari 的全屏阅读是一套逻辑。"},
            {"下拉刷新", "把网页往下拖，拖过一小段松手前的动作就会刷新，不用再去找刷新按钮；控件隐藏的时候尤其顺手。设置里可以关掉这个手势。"},
            {"电脑版网页", "菜单里“切换为电脑版网页”，网站会以为这是一台 Windows 上的最新版 Chrome。切回来再点一次就行。"},
            {"搜索引擎", "设置里可以选百度或谷歌，选完立刻生效。"},
            {"默认浏览器", "设置里点“设为默认浏览器”，或者菜单里的同一项。会弹系统的角色确认框，同意之后，别的 App 里点网页链接就直接用清风浏览器打开。"},
            {"关于", "网页引擎用的是系统自带的 Chromium WebView，和 Chrome 同源，可以在应用商店里单独更新。"}
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
