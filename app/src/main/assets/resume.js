/* 从内置播放器回退到网页播放时，把页面里被暂停的视频重新拉起来 */
(function () {
    try {
        var vs = document.querySelectorAll('video');
        for (var i = 0; i < vs.length; i++) {
            var v = vs[i];
            try {
                v.muted = false;
                var p = v.play();
                if (p && p.catch) { p.catch(function () { }); }
            } catch (e) { }
        }
    } catch (e) { }
})();
