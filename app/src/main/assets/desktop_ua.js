/* 电脑版伪装：让网站的服务端与页面脚本都认为这是一台 Windows 上的最新版 Chrome */
(function () {
    if (window.__a11yDesktopUa) { return; }
    window.__a11yDesktopUa = true;

    var UA = 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/142.0.0.0 Safari/537.36';
    var VER = '142.0.0.0';

    try {
        Object.defineProperty(navigator, 'userAgent', { get: function () { return UA; }, configurable: true });
        Object.defineProperty(navigator, 'appVersion', {
            get: function () { return UA.replace('Mozilla/', ''); }, configurable: true
        });
        Object.defineProperty(navigator, 'platform', { get: function () { return 'Win32'; }, configurable: true });
        Object.defineProperty(navigator, 'vendor', { get: function () { return 'Google Inc.'; }, configurable: true });
        Object.defineProperty(navigator, 'maxTouchPoints', { get: function () { return 0; }, configurable: true });
    } catch (e) { }

    try {
        if (navigator.userAgentData) {
            var orig = navigator.userAgentData;
            var brands = [
                { brand: 'Chromium', version: '142' },
                { brand: 'Google Chrome', version: '142' },
                { brand: 'Not_A Brand', version: '24' }
            ];
            var data = {
                brands: brands,
                mobile: false,
                platform: 'Windows',
                toJSON: function () { return { brands: brands, mobile: false, platform: 'Windows' }; },
                getHighEntropyValues: function (hints) {
                    return orig.getHighEntropyValues(hints).then(function (v) {
                        v.platform = 'Windows';
                        v.platformVersion = '19.0.0';
                        v.architecture = 'x86';
                        v.bitness = '64';
                        v.mobile = false;
                        v.uaFullVersion = VER;
                        v.fullVersionList = [{ brand: 'Chromium', version: VER }, { brand: 'Google Chrome', version: VER }];
                        return v;
                    }).catch(function () {
                        return { platform: 'Windows', mobile: false, uaFullVersion: VER };
                    });
                }
            };
            Object.defineProperty(navigator, 'userAgentData', { get: function () { return data; }, configurable: true });
        }
    } catch (e) { }

    /* 常见的移动端特征，逐个抹掉 */
    try { window.ontouchstart = undefined; } catch (e) { }
    try {
        Object.defineProperty(window, 'orientation', {
            get: function () { return { type: 'landscape-primary', angle: 0, onchange: null }; },
            configurable: true
        });
    } catch (e) { }

    /* 屏幕尺寸也别露馅：报一个常见笔记本分辨率 */
    try {
        Object.defineProperty(screen, 'width', { get: function () { return 1536; }, configurable: true });
        Object.defineProperty(screen, 'height', { get: function () { return 864; }, configurable: true });
        Object.defineProperty(screen, 'availWidth', { get: function () { return 1536; }, configurable: true });
        Object.defineProperty(screen, 'availHeight', { get: function () { return 824; }, configurable: true });
    } catch (e) { }
})();
