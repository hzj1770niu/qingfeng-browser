/* 无障碍浏览器页面探测脚本：捕获页面里的媒体地址，并通过 console 回传给原生层。
   仅上报 http/https 地址；不修改页面原有播放行为。 */
(function () {
    if (window.__a11yHookedV1) { return; }
    window.__a11yHookedV1 = true;

    var MEDIA_RE = /\.(m3u8|mpd|mp4|m4v|webm|flv|mov|3gp)(\?|$)|[?&][a-z0-9_]*(m3u8|playlist)/i;

    function isHttp(u) {
        return typeof u === 'string' && /^https?:\/\//i.test(u);
    }

    function report(kind, url) {
        try {
            if (!isHttp(url)) { return; }
            if (url.length > 2000) { url = url.substring(0, 2000); }
            console.log('A11Y_MEDIA|' + kind + '|' + url);
        } catch (e) { }
    }

    function maybeReport(url, kindHint) {
        try {
            if (!isHttp(url)) { return; }
            if (MEDIA_RE.test(url) || kindHint) { report(kindHint || 'auto', url); }
        } catch (e) { }
    }

    /* 1. 拦截 XHR：hls.js、dplayer 等库都是用 XHR 拉取 m3u8 的 */
    try {
        var xo = XMLHttpRequest.prototype.open;
        XMLHttpRequest.prototype.open = function (m, u) {
            try { maybeReport(typeof u === 'string' ? u : (u && u.url), null); } catch (e) { }
            return xo.apply(this, arguments);
        };
    } catch (e) { }

    /* 2. 拦截 fetch */
    try {
        if (window.fetch) {
            var f = window.fetch;
            window.fetch = function (input) {
                try {
                    var u = typeof input === 'string' ? input : (input && input.url);
                    maybeReport(u, null);
                } catch (e) { }
                return f.apply(this, arguments);
            };
        }
    } catch (e) { }

    /* 3. 扫描 video / source 元素 */
    function scan() {
        try {
            var vs = document.querySelectorAll('video');
            for (var i = 0; i < vs.length; i++) {
                var v = vs[i];
                var s = v.currentSrc || v.src || '';
                if (!s) {
                    var ss = v.getElementsByTagName('source');
                    if (ss && ss.length) { s = ss[0].src || ''; }
                }
                if (s) { maybeReport(s, 'video'); }
                if (!v.__a11yPlayHooked) {
                    v.__a11yPlayHooked = 1;
                    v.addEventListener('play', function () {
                        try {
                            var cur = this.currentSrc || this.src || '';
                            console.log('A11Y_PLAY|' + cur);
                            var ss2 = this.getElementsByTagName('source');
                            if ((!cur || cur.indexOf('blob:') === 0) && ss2 && ss2.length) {
                                console.log('A11Y_PLAY|' + (ss2[0].src || ''));
                            }
                        } catch (e) { }
                    }, true);
                }
            }
        } catch (e) { }
    }

    scan();
    try { setInterval(scan, 1200); } catch (e) { }

    /* 4. 监听 DOM 变化，尽可能早发现新插入的播放器 */
    try {
        if (window.MutationObserver) {
            var mo = new MutationObserver(function () { scan(); });
            mo.observe(document.documentElement, { childList: true, subtree: true });
        }
    } catch (e) { }
})();
