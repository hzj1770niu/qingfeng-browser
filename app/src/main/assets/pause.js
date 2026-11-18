/* 交给内置播放器前，先让网页里的视频安静下来，避免两个音源打架 */
(function () {
    try {
        var vs = document.querySelectorAll('video');
        for (var i = 0; i < vs.length; i++) {
            try { vs[i].pause(); } catch (e) { }
        }
    } catch (e) { }
})();
