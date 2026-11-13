# 今球讲 Android

《今球讲》专属播客播放器，基于 Escapepod（MIT）。应用只使用固定 RSS，无需注册。

- RSS：https://feed.xyzfm.space/jkr43da47kq7
- 包名：`com.jinqiujiang.fm`；版本：`1.0.0`；最低 Android 7.1 / API 25。
- 首页自动同步，支持下拉刷新和约四小时后台刷新（具体时间由系统调度）。新节目发布到 RSS 后即可获取，无需重装应用。
- 在线播放优先使用已完成的本地下载，否则读取 RSS 音频 URL。固定使用正常 1 倍速，支持下一集、睡眠定时、锁屏和耳机控制。
- 默认手动下载、仅 Wi-Fi 下载、关闭自动清理。删除下载保留收听进度；播放中的文件延后清理。

当前版本使用左侧单期封面、右侧大字号标题和日期/时长的列表。显示版本为 1.0.0，内部 versionCode 为 14，可覆盖此前版本，无需卸载。

源码包名与 namespace 统一为 `com.jinqiujiang.fm`，主源码位于 `app/src/main/java/com/jinqiujiang/fm/`，测试位于 `app/src/test/java/com/jinqiujiang/fm/`。升级兼容旧包名创建的后台任务，沿用原有数据库和发布签名。

首页只展示台标、节目名及两行标语。完整介绍位于设置 → 关于 → 关于节目，优先展示最近同步的 RSS 内容，首次离线也可查看内置介绍。底部播放栏和展开播放器使用单期封面。

展开播放器为全屏页面，左上角可收起。标题下的“单集详情”按钮可平滑定位说明，也可上推封面逐渐查看说明；进度条和播放控制固定在底部。升级时清除旧倍速设置，媒体会话不再支持调速。

系统返回键或边缘返回手势优先收起展开播放器；从设置返回或从桌面重新进入后也保持此行为。播放器已收起时，返回操作沿用正常页面导航。

主页点击单集标题、封面或行内空白可直接打开对应的全屏播放页并自动播放；点击当前正在播放的单集会保持播放。下载和独立播放按钮仍执行各自操作。

启动图标采用深绿色底、纯白色女排上半身蓄力剪影，完整显示排球。自适应图标按实际启动器尺寸进行相对缩放，避免固定 dp 图层在小尺寸桌面上裁切。素材与生成说明见 [artwork/README.md](artwork/README.md)。轻按节目、更新封面、OPML 导入导出和搜索提供商及其调用链已移除；节目内搜索及固定 RSS 刷新继续使用。设置整理记录见 [SETTINGS_AUDIT.md](SETTINGS_AUDIT.md)。

## 构建

使用项目 Gradle Wrapper、JDK 21 和 Android SDK 37。`local.properties` 配置本机 SDK，勿提交。

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest lintDebug
./gradlew assembleRelease
```

macOS 已安装 Android Studio 时，可在命令前指定 `JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`。Gradle daemon 使用仓库指定的 JDK 21；源码兼容版本为 Java 17。

输出位于 `app/build/outputs/apk/debug/` 和 `app/build/outputs/apk/release/`。

## 发布签名

本机已生成独立的 Release 身份，存于忽略的 `.signing/release.jks`，密码存于 `.signing/password`。请将整个 `.signing` 目录安全备份；后续覆盖升级必须使用同一密钥和包名。不要删除、重新生成或提交这些文件。

构建脚本检测到上述文件时自动签名 Release；缺少文件时只生成未签名包。新开发环境应恢复原签名材料，或为全新的应用生成自己的密钥（alias 为 `jinqiujiang`）。

## 配置与实现

`PodcastConfig` 集中定义 RSS、品牌名称和稳定节目标识。`PodcastSync` 串行执行前台/后台刷新，完成数据库事务后报告成功。刷新保留历史节目、筛选状态、下载和收听进度。更换 RSS 地址需要单独设计数据库中的源地址迁移。

播放器、下一集和媒体会话通过 `CollectionHelper.buildMediaItem` 统一选择音频来源；外部控制器只能请求本地目录已知的节目 ID。RSS 导入和外部订阅入口已关闭。

## 验证

测试使用 `app/src/test/resources/feed.xml` 的 21 期静态样本；这只是回归样本数量，应用不限制节目数量。测试涵盖 RSS 字段、时长、稳定 ID、刷新合并、音频来源回退和异常 XML。设备验证及已知限制见 `VALIDATION.md`。

## 开源许可

保留上游源码版权及 MIT 许可，详见 `LICENSE.md`。应用设置页也提供完整许可文本。Escapepod 原项目：https://codeberg.org/y20k/escapepod 。
