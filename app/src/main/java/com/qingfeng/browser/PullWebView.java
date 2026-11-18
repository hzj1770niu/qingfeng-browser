package com.qingfeng.browser;

import android.content.Context;
import android.view.MotionEvent;
import android.webkit.WebView;

/** 支持下拉刷新的 WebView：在页面顶部往下拖超过阈值就触发刷新 */
public class PullWebView extends WebView {

    public interface Listener {
        void onPullRefresh();
    }

    private Listener listener;
    private boolean enabled = true;
    private boolean triggered;
    private float downX;
    private float downY;
    private final int threshold;

    public PullWebView(Context c) {
        super(c);
        threshold = Ui.dp(c, 90);
    }

    public void setPullRefreshEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void setListener(Listener l) {
        this.listener = l;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (enabled && listener != null) {
            switch (ev.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getX();
                    downY = ev.getY();
                    triggered = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (!triggered
                            && ev.getY() - downY > threshold
                            && Math.abs(ev.getY() - downY) > Math.abs(ev.getX() - downX) * 1.5f
                            && !canScrollVertically(-1)) {
                        triggered = true;
                        listener.onPullRefresh();
                    }
                    break;
                default:
                    break;
            }
        }
        return super.onTouchEvent(ev);
    }
}
