<div align="center">

<img src="app/src/main/assets/biko_logo.png" width="128" alt="BiKo Logo" />

# BiKo

**An unofficial Bilibili client for Android, written from scratch in Kotlin + Jetpack Compose**

Clean · Fast · Modern · Pure native Compose

[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)](#)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?logo=kotlin&logoColor=white)](#)
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-Material%203-4285F4?logo=jetpackcompose&logoColor=white)](#)
[![minSdk](https://img.shields.io/badge/minSdk-24-orange)](#)
[![Release](https://img.shields.io/github/v/release/shj142506-cyber/BiKo?include_prereleases&label=Release&color=brightgreen)](https://github.com/shj142506-cyber/BiKo/releases)
[![Stars](https://img.shields.io/github/stars/shj142506-cyber/BiKo?style=social)](https://github.com/shj142506-cyber/BiKo/stargazers)

[Download the latest APK](https://github.com/shj142506-cyber/BiKo/releases/latest) · [Report an issue](https://github.com/shj142506-cyber/BiKo/issues) · [中文说明](README.md)

</div>

---

## Demo

<img src="screenshots/demo.gif" width="320" alt="BiKo demo — home feed, video playback with danmaku, and search" />

A short screen recording: the home feed, video playback with danmaku, and search.

---

## What is BiKo?

**BiKo** is a third-party **Bilibili** client written entirely in **Kotlin + Jetpack Compose**.

It is **not** a WebView shell. It takes Bilibili's public API surface (video, live streaming, dynamic posts, danmaku, user space, and more) and rebuilds it on a clean, modern Android architecture: a fully declarative Compose UI, a network layer built on Retrofit + OkHttp + kotlinx.serialization, and an AndroidX Media3 (ExoPlayer) player with a custom-built danmaku rendering engine.

The goal is simple — **use the most modern stack to make watching videos light, fast, and pleasant.**

> A good reference for developers who want to study **Jetpack Compose in practice**, **Media3 playback**, **danmaku engine internals**, or **WBI request signing / API reverse engineering**.

---

## Screenshots

| Home · Recommended | Home · Anime | Home · Movies | Home · Live | Search |
| --- | --- | --- | --- | --- |
| <img src="screenshots/home_recommend.png" width="160" alt="Home - Recommended" /> | <img src="screenshots/home_bangumi.png" width="160" alt="Home - Anime" /> | <img src="screenshots/home_movie.png" width="160" alt="Home - Movies" /> | <img src="screenshots/home_live.png" width="160" alt="Home - Live" /> | <img src="screenshots/search.png" width="160" alt="Search" /> |

---

## Features

| Module | Capabilities |
| --- | --- |
| **Home / Recommended** | Staggered-grid video feed, category entries, Watch Later, home-screen shortcuts for favorites and history |
| **Video playback** | Media3 (ExoPlayer) with DASH / HLS, playback speed, quality switching, background playback, picture-in-picture, media notification controls |
| **Danmaku engine** | Custom parser and renderer supporting standard danmaku, protobuf danmaku, and **advanced danmaku (BAS / Lua scripts)**, with multi-track scheduling |
| **Live streaming** | Live room playback, realtime danmaku over WebSocket, quality selection |
| **Short video** | Portrait, immersive swipe-to-play feed |
| **Dynamic posts** | Feed browsing, publishing, topic details |
| **Messages** | System notifications, replies, @-mentions, likes received, direct messages |
| **Search** | Video / user search, trending searches, search history |
| **User space** | Profile page, upload list, following / followers, followed anime, follow groups |
| **Favorites / History / Downloads** | Favorites and folder details, watch history, offline download task management |
| **Articles** | Article detail view with rich-text parsing |
| **Login** | QR-code login; session cookies (SESSDATA / bili_jct) stored securely on-device |
| **Polish** | Material 3 dynamic color, dark mode, playback position memory, "not interested" filtering |

---

## Getting Started

### Option 1: Install the APK (recommended)

1. Go to [Releases](https://github.com/shj142506-cyber/BiKo/releases/latest) and download `BiKo-x.x.x.apk`.
2. Transfer it to your phone and install. You will need to allow "install from unknown sources" the first time.
3. Open the app and log in with QR-code scanning.

> The APK in the current release is **debug-signed**. It is intended for testing and evaluation only — please do not redistribute it as a production build.

### Option 2: Build from source

1. Open the project root in **Android Studio (Ladybug or newer)**.
2. `local.properties` is generated automatically on first open. To set it manually, write `sdk.dir=<your Android SDK path>`.
3. Wait for **Gradle Sync** to finish, then run the `app` configuration, or execute `./gradlew assembleDebug`.

**Requirements**

| Item | Requirement |
| --- | --- |
| JDK | 17 |
| Android Studio | Ladybug (2024.2) or newer |
| Android SDK | API 34 (compileSdk / targetSdk) |
| Minimum runtime | Android 7.0 (API 24) |

> Note: the repository does not include a Gradle Wrapper (`gradlew`). For command-line builds, use the Gradle bundled with Android Studio, or run `gradle wrapper` first to generate one.

---

## Tech Stack

| Category | Choice |
| --- | --- |
| Language | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 + Navigation Compose |
| Network | Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization 1.7 |
| Playback | AndroidX Media3 1.4 (ExoPlayer: DASH / HLS / Session) |
| Images | Coil 2.7 |
| Local storage | DataStore Preferences 1.1 |
| QR codes | ZXing 3.5 |
| Build | AGP 8.5.2 · Gradle Kotlin DSL · KSP |

- Package / application ID: `com.biko.client`
- Version: `0.1.0-beta.1` (versionCode 15)

---

## Project Structure

```
app/src/main/java/
├── com/biko/client/
│   ├── data/                  # Data layer
│   │   ├── local/             # DataStore persistence (session, history, downloads, settings, night mode…)
│   │   ├── model/             # Data models
│   │   ├── remote/            # BiliApi, DTOs, WBI signing, SMS signing, danmaku segment parsing
│   │   └── BiliRepository*.kt # Repository layer split by domain (video / live / dynamic / search / messages / relations…)
│   ├── player/                # Playback service, DASH playback, live danmaku, media controls
│   ├── ui/
│   │   ├── screens/           # 25+ screens (home, playback, live, dynamic, space, search…)
│   │   ├── components/        # Shared components, player, danmaku and advanced-danmaku overlays
│   │   └── theme/             # Material 3 theme and colors
│   └── util/                  # Utilities and formatting
└── com/android/purebilibili/danmaku/
    ├── engine/                # Danmaku data models and rendering engine
    └── parser/                # BAS script parsing, protobuf danmaku, weighted text
```

---

## FAQ

**Q: Why does installation warn about "unknown sources"?**
A: The APK is not distributed through an app store, so this is expected. Allow installation from unknown sources in your system settings.

**Q: Do I need to log in?**
A: Basic features (home, search, watching videos) work without logging in. Favorites, history, dynamic posts, and direct messages require QR-code login.

**Q: Does it collect my data?**
A: No. There is no backend server; all data goes straight to the official API, and your session is stored only on your device.

---

## Disclaimer

This project is an unofficial client intended for **personal study and technical research**. It is **not affiliated with, authorized by, or endorsed by** Bilibili in any way.

All content rights belong to their respective owners. Please use it for learning and exchange only, and **do not use it for any commercial purpose**. Any consequences arising from its use are the responsibility of the user.

---

## License

Released under the **MIT License** — see [LICENSE](LICENSE).

<div align="center">

**If this project helps you, please consider giving it a star.**

</div>
