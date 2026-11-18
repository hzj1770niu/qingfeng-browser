# 清风浏览器

基于 Android System WebView（Chromium 内核）的个人自用浏览器。

- 包名：`com.qingfeng.browser`
- 强制内置播放器接管网页 HTML5 视频（含 M3U8），可全屏、快进快退、暂停；加密视频回退系统播放器
- 桌面版网页（伪装桌面 Chrome UA）
- 标签页管理：长按关闭全部标签页
- 书签与历史合并入口，长按保存当前页面
- 下载：走系统 DownloadManager，落到默认下载目录
- 下拉刷新、隐藏控件、退出浏览器入口、标签页保留策略
- 默认搜索引擎：百度，可切换 Google

## 构建

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
gradle assembleRelease
```
