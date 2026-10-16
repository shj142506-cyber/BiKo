# 彼可 BiKo

一个基于 Kotlin + Jetpack Compose 的第三方 B 站（哔哩哔哩）Android 客户端。

> 当前版本：`0.1.0-beta.1`（versionCode 15）

## 功能

- **首页 / 推荐**：视频流、稍后再看、收藏、历史等桌面快捷入口
- **视频播放**：基于 Media3 (ExoPlayer) 的 DASH / HLS 播放，支持弹幕、倍速、画中画
- **弹幕引擎**：自研弹幕解析与渲染，支持普通弹幕与高级弹幕（BAS 脚本、proto 弹幕）
- **直播**：直播间播放与直播间弹幕（WebSocket）
- **短视频**：竖屏沉浸式播放
- **动态**：动态流浏览与发布
- **消息**：系统通知、回复我的、私信会话
- **搜索**：视频 / 用户搜索，含搜索历史
- **空间**：用户主页、关注 / 粉丝列表、番剧追番
- **收藏 / 历史 / 下载**：本地记录与离线下载管理
- **登录**：扫码登录、Bili 会话（SESSDATA / bili_jct）本地保存
- **其他**：夜间模式、WBI 签名、短信签名等

## 技术栈

| 类别 | 使用 |
|------|------|
| 语言 / UI | Kotlin 2.0、Jetpack Compose (Material 3)、Navigation Compose |
| 网络 | Retrofit 2 + OkHttp + kotlinx.serialization |
| 播放 | AndroidX Media3 (ExoPlayer，DASH / HLS / Session) |
| 图片 | Coil |
| 本地存储 | DataStore Preferences |
| 二维码 | ZXing |
| 构建 | AGP 8.5.2、Gradle Kotlin DSL、JDK 17 |

- `compileSdk = 34`，`minSdk = 24`，`targetSdk = 34`
- 应用 ID / 包名：`com.biko.client`

## 构建

1. 用 Android Studio（Ladybug 或更新版本）打开本项目根目录。
2. 首次打开会自动生成 `local.properties`（如需手动指定，写入 `sdk.dir=<你的 Android SDK 路径>`）。
3. 等待 Gradle Sync 完成后，运行 `app` 配置即可；或执行 `./gradlew assembleDebug`。

> 注意：仓库未包含 Gradle Wrapper（`gradlew`）。如需命令行构建，请用 Android Studio 自带的 Gradle，或先执行 `gradle wrapper` 生成。

## 目录结构

```
app/src/main/java/com/biko/client/
├── data/            # 数据层：BiliRepository* 各接口封装、本地 Store、下载管理
│   ├── local/       # DataStore 持久化（会话、历史、下载、设置等）
│   └── remote/      # BiliApi、DTO、WBI/SMS 签名、弹幕分段解析
├── player/          # 播放服务与播放控制器
├── ui/              # Compose 界面
│   ├── screens/     # 各功能页面
│   ├── components/  # 通用组件与弹幕渲染
│   └── theme/       # 主题与配色
└── util/            # 工具函数
app/src/main/java/com/android/purebilibili/danmaku/   # 弹幕解析引擎（BAS 脚本等）
```

## 免责声明

本项目为个人学习与技术研究用途的非官方客户端，与哔哩哔哩官方无任何关联。所有内容版权归原权利人所有，请勿用于商业用途。

## 许可证

未指定（如需开源请补充 LICENSE 文件）。
