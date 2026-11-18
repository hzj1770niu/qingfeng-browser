/* 下载钩子：处理网页里由 JS 生成的 blob / data 下载，普通 http 下载由原生层接管 */
(function () {
    if (window.__qfDlHooked) { return; }
    window.__qfDlHooked = true;

    var MAX = 40 * 1024 * 1024; /* 内存里转 base64 的上限 */

    function push(name, mime, blob) {
        try {
            if (!blob || blob.size === 0) { return; }
            if (blob.size > MAX) { return; }
            var r = new FileReader();
            r.onload = function () {
                try {
                    var s = r.result;
                    var comma = s.indexOf(',');
                    if (comma < 0) { return; }
                    if (window.QfDL) {
                        QfDL.saveBase64(name, mime || 'application/octet-stream', s.substring(comma + 1));
                    }
                } catch (e) { }
            };
            r.readAsDataURL(blob);
        } catch (e) { }
    }

    document.addEventListener('click', function (ev) {
        try {
            var a = ev.target && ev.target.closest ? ev.target.closest('a[download]') : null;
            if (!a) { return; }
            var href = a.getAttribute('href') || a.href || '';
            if (href.indexOf('blob:') !== 0 && href.indexOf('data:') !== 0) { return; }
            ev.preventDefault();
            ev.stopPropagation();
            var name = a.getAttribute('download') || '下载文件';
            if (href.indexOf('data:') === 0) {
                var m = href.match(/^data:([^;]*);base64,([\s\S]*)$/);
                if (m && window.QfDL) {
                    QfDL.saveBase64(name, m[1], m[2]);
                }
                return;
            }
            var xhr = new XMLHttpRequest();
            xhr.open('GET', href, true);
            xhr.responseType = 'blob';
            xhr.onload = function () {
                push(name, xhr.response && xhr.response.type, xhr.response);
            };
            xhr.send();
        } catch (e) { }
    }, true);
})();
