# PROJECT_BRIEF — Escapepod

Reference brief for Escapepod, read by the planning AI at the start
of every task and by the coding AI for the Build section. Keep it
terse and factual; no prose for human readers.

## Summary

Minimalist Android podcast player — two screens (podcast list, episode list), RSS feed parsing, background audio playback.

## Architecture

- **Language:** 100% Kotlin.
- **UI system:** XML layouts with manual view binding via layout holder classes (`ui/` package). No Compose, no ViewBinding/DataBinding plugin.
- **Navigation:** Jetpack Navigation (fragment-based). `MainActivity` hosts `MainFragment` (podcast list) and `PodcastFragment` (episode list).
- **Packages:**
  - `collection/` — RecyclerView adapters and ViewModels for podcast list and episode list.
  - `database/` — Room database (`CollectionDatabase`), DAOs (`daos/`), entities (`objects/`), query wrappers (`wrappers/`).
  - `dialogs/` — Dialog fragments (search, OPML import, show notes, errors).
  - `extensions/` — Kotlin extension functions on MediaController, String, TextView, ArrayList.
  - `helpers/` — Utility singletons: download management, file I/O, preferences, network checks, theming, audio focus, WorkManager scheduling.
  - `interfaces/` — Callback/listener interfaces for dialogs and UI events.
  - `search/` — Podcast search adapter; result models for Podcastindex and gpodder (`results/`).
  - `ui/` — Layout holder classes that inflate and bind XML layouts; `PlayerState` data class.
  - `xml/` — RSS/OPML parsing helpers.
  - `legacy/` — Old data classes for migration from pre-Room JSON storage.
- **DI:** None. Manual construction.
- **Async:** Kotlin coroutines + Flow (via Room). No RxJava.
- **Persistence:** Room (SQLite). Schema version exports in `app/schemas/`.

## Idioms

- Use `object FooHelper` for helpers.
- Don't use `companion object`.
- Player events read episode data from `MediaMetadata.extras` (not the DB).
- Haptic feedback on long-press uses `view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)`.

## Key files

| Path | Role |
|------|------|
| `app/src/main/java/.../Escapepod.kt` | `Application` subclass — app-level init |
| `app/src/main/java/.../Keys.kt` | Constants and string keys |
| `app/src/main/java/.../MainActivity.kt` | Single Activity host |
| `app/src/main/java/.../MainFragment.kt` | Podcast list screen (fragment) |
| `app/src/main/java/.../PodcastFragment.kt` | Episode list screen (fragment) |
| `app/src/main/java/.../PlayerService.kt` | Media3 `MediaSessionService` — foreground playback |
| `app/src/main/java/.../BasePlayerService.kt` | Base class for PlayerService |
| `app/src/main/java/.../database/CollectionDatabase.kt` | Room database definition |
| `app/src/main/java/.../helpers/DownloadHelper.kt` | Episode download logic (DownloadManager) |
| `app/src/main/java/.../helpers/CollectionHelper.kt` | Business logic for managing the podcast collection |
| `app/src/main/java/.../xml/RssHelper.kt` | RSS feed XML parsing |
| `app/src/main/java/.../xml/OpmlHelper.kt` | OPML import/export |
| `app/build.gradle.kts` | Module build config |
| `gradle/libs.versions.toml` | Dependency and plugin versions (version catalog) |

## System and external APIs in use

- **Media3 / MediaSession** — background audio playback, media notification, media button handling, Android Auto support. Headphone "next"/"previous" are remapped to seek ±30 s/−10 s in `MediaSession.Callback.onMediaButtonEvent`; the notification's buttons come from `setMediaButtonPreferences`, which also removes skip-to-next/previous from the platform session. Both are `@UnstableApi` — re-check on every Media3 upgrade.
- **DownloadManager** — system download service for RSS feeds, covers and episode audio; `DownloadFinishedReceiver` → `DownloadHelper.processDownload`. `PREF_ACTIVE_DOWNLOADS` drives the download indicator and means "downloads active *or* work still running": `DownloadHelper` publishes the empty marker only when `pendingWork` (running enqueue loops + feed parses) is zero. Touch `activeDownloads`/`pendingWork` only through `DownloadHelper`'s locked accessors (`activeDownloadsLock`) — both are shared between IO coroutines and the main thread.
- **WorkManager** — periodic background feed updates (`DownloadWorker`, `WorkerHelper`).
- **Foreground service** — `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` permissions for playback.
- **Network state** — `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE` to gate downloads over cellular.
- **FileProvider** — sharing OPML export files.
- **Cleartext traffic** — enabled (`usesCleartextTraffic=true`, `network_security_config.xml`) for HTTP podcast feeds.

## Key dependencies

| Library | Purpose |
|---------|---------|
| `androidx.media3` (ExoPlayer + Session) | Audio playback engine and media session |
| `androidx.room` + KSP | Local SQLite persistence |
| `androidx.navigation` | Fragment navigation |
| `androidx.work` | Background feed update scheduling |
| `com.android.volley` | HTTP requests (feed fetching, search) |
| `com.google.code.gson` | JSON serialisation (search results, legacy data) |
| `com.github.bumptech.glide` | Podcast cover image loading |
| `com.google.android.material` | Material Design widgets |

## Build

- **Gradle wrapper:** `./gradlew` present.
- **Toolchain:** Java/Kotlin target pinned in `app/build.gradle.kts`; JDK and Android build-tools unpinned — use Android Studio's bundled versions.
- **Debug build:** `./gradlew assembleDebug`
- **Release build:** `./gradlew assembleRelease` — R8 on via `optimization { enable = true }` in the release build type: code shrinking, obfuscation and resource shrinking in one switch. Custom keep rules live in `app/src/main/keepRules/rules.keep`; there is no `proguard-rules.pro`. No `signingConfigs` in `app/build.gradle.kts` — Gradle builds unsigned and the APK is signed afterwards with `apksigner`.
- **Flavors:** None. Single `app` module, two build types (debug, release).
- **No `local.properties` entries required** for debug builds beyond standard SDK path.
