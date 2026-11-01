# Work Log

## Current state

_None._

**Gradle standardisation is complete for Escapepod** — the cross-repo plan lives in `workbench/PLAN_GRADLE_STANDARDISATION.md`, not here. P4a landed 2026-09-04; **P5** merged `master` into the `non-free` branch with the Google Cast block ported to `.kts` (`229d14ef`); **P6**'s fdroiddata recipe MR `!49335` was merged 2026-09-18 and **v1.6.7 / versionCode 53 is published on f-droid.org**, which retires the release freeze from `25c93aa8` and proves the converted build end to end. Left unanswered since P4a.5: whether `optimization { }` can shrink *without* obfuscating, the way bare `minifyEnabled` could.

Prior task **F (security audit — input & download vectors)** closed 2026-07-26; all 10 P3 fix-iterations (B1–B10) shipped and committed to `master`. Findings in `SECURITY_AUDIT.md` (F-1…F-24, all `fixed`/documented); task archived in `WORK_PLAN.md`. Possible later task: transfer the XML/download findings to Transistor (M3U/PLS) and Trackbook (GPX) — see Open issues.

## Open issues

- Escapepod is the pilot; findings (esp. RSS/OPML XML) are meant to transfer to Transistor (M3U/PLS) and Trackbook (GPX, also XML).
- **Update indicator flickers mid-update** — triaged 2026-09-25 as actionable, fix direction A chosen (see History). Planned as task B (B1, B2) — see Current state.
- **Dead feed keeps the indicator on for ~15 min — triaged 2026-09-25 as not a bug.** `http://www.alternativlos.org/alternativlos.rss` (server resets every connection) was enqueued despite `resolveRedirects` failing. DownloadManager retried it with growing backoff and gave up after ~14.5 min (`HTTP_DATA_ERROR`); `processDownload`'s failure path then removed and deleted it (`Error code = 1004`) — indicator correct throughout, self-resolving. DM's retry policy has no API. Optional behaviour changes, y20k's call, neither clearly better: skip enqueue when the pre-check can't connect (loses DM's retry on a network blip); don't count retrying downloads as active (indicator then hides real background work).
- **Duration extraction failed for one mp3** (B2's Wi-Fi test, 2026-09-25): `Unable to extract duration metadata from audio file: java.lang.IllegalArgumentException: could not access content://downloads/all_downloads/145339` in `setEpisodeMediaUri` (async, after the ID was removed). Three later mp3s in the same run were fine. Not investigated; B2 did not touch this path.
- **MIME step 2a never succeeds** (`DownloadHelper.processDownload`): querying the `content://downloads/all_downloads/<id>` URI returns the provider's internal `COLUMN_STATUS` (200 = success), compared against `DownloadManager.STATUS_SUCCESSFUL` (8). Every download logs "Download status not successful (200)" and falls through to `ContentResolver.getType()`. Harmless today; not part of the indicator fix.

## Decisions

- 2026-07-06 — Split the audit into Audit (P1/P2, findings only) then Remediate (P3, code). Why: the two-stage Planner→Implementer workflow assumes compile-and-run acceptance; audit deliverables are findings, and fixes can't be planned before vulns are confirmed. Audit phases use findings-shaped acceptance, not compile-and-run.
- 2026-07-06 — Vector 1 scoped to keyboard/clipboard text fields only; intent/IPC surface out of scope (note incidental findings, don't fix).

## History

<!--
Newest first.

### YYYY-MM-DD — <ID> — <short goal>
- Outcome: done / partial / blocked
- Files changed:
    - `path/to/file`
- Notes: decisions made, issues encountered, things to watch next time.
-->

### 2026-09-25 — Task "B: update indicator spans the whole update" closed
- Outcome: done
- Summary: The download indicator no longer hides mid-update. B1 serialized all access to `activeDownloads` behind accessor functions (`71eda13c`); B2 added a `pendingWork` count of running enqueue loops and feed parses, and `setActiveDownloads` publishes the empty marker only when nothing is pending (`f9df8563`). Device-tested on mobile data and Wi-Fi with new episodes. All changes in `DownloadHelper.kt`.
- Residual issues: stuck retrying download holds the indicator on; duration extraction failed once; MIME step 2a status mismatch — all under Open issues, none caused by task B.

### 2026-09-25 — B2 — Busy-aware publishing
- Outcome: done — compiles; mobile-data check passed (log + no visible flicker); Wi-Fi check with new episodes passed (see Evidence).
- Files changed:
    - `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt`
- Notes: `pendingWork: Int` under `activeDownloadsLock`, via new `beginPendingWork()`/`endPendingWork()`. Counted: each `enqueueDownload` loop and each `readPodcastFeed` parse — begin is called *before* the coroutine launches, end via `.invokeOnCompletion { endPendingWork() }` (fires once on success, cancel or exception; no re-indent vs. `try`/`finally`). Order closes gap 2: `processDownload` calls `readPodcastFeed` before removing the RSS ID, and the parse's media `enqueueDownload` begins before the parse ends. `setActiveDownloads` saves a non-empty list always, the empty marker only when `pendingWork == 0` (if/else-if, no early return — y20k's call). `addActiveDownload` now publishes per enqueue; the loop-end write is gone. `endPendingWork` publishes when the count reaches 0. Not counted: `setEpisodeMediaUri`/`setPodcastImage` file copies after removal — local work, same as before.
- Evidence (mobile, 25 feeds): update 22:52:32.1–45.4, single `IDs of active downloads: zero` at 45.447, after the last parse (TWiT, ~1.8 s). Five mid-update moments with an empty in-memory list (32.609, 33.909, 34.714, 37.849, 43.641) were held back; before B2 each wrote `zero`. First list published 32.131, right after the first enqueue.
    Wi-Fi (other collection, new episodes): four mp3s enqueued, each after its feed's parse; no `zero` anywhere, incl. between a feed and its mp3; a 404 feed was removed cleanly. The final `zero` never came — held by a stuck feed download unrelated to B2 (see Open issues).

### 2026-09-25 — B1 — `activeDownloads` thread safety
- Outcome: done — compiles; device-tested (full update on mobile completes; switching away/back mid-update runs the new reconcile without issue).
- Files changed:
    - `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt`
- Notes: private `activeDownloadsLock: Any` (not `synchronized(this)` — `DownloadHelper` is a public object; not the list itself — the field was reassigned). Lock lives only in the list accessors: `initializeActiveDownloads`, `addActiveDownload`, `copyActiveDownloads` (new), `removeFromActiveDownloads`, `setActiveDownloads` — call sites carry no `synchronized` (y20k's call, readability). `setActiveDownloads` builds *and* saves under the lock so writers can't save out of order; lock is reentrant, `apply()` is cheap. `reconcileActiveDownloads` now prunes the in-memory list via `isDownloadActive` instead of reloading from the pref, which dropped enqueued-but-unwritten IDs. Slow calls (DownloadManager queries, network) stay outside the lock. No visible change — flicker is B2's job. B2's busy counter should sit under the same lock inside these accessors.

### 2026-09-25 — Triage — update indicator hides and reappears during an update
- Outcome: triaged as actionable bug; direction A chosen (y20k). No code.
- Files changed: `.ai/WORK_LOG.md`
- Cause: the indicator mirrors `PREF_ACTIVE_DOWNLOADS` (`MainActivityLayoutHolder.toggleDownloadProgressIndicator`). **Not** DownloadManager's parallel limit — queued IDs count as active (`STATUS_PENDING`). Two gaps:
    1. **Enqueue loop vs. completions (confirmed).** `enqueueDownload` runs `resolveRedirects` + `isSafeRemoteUrl` (DNS) + `isNotInDownloadQueue` per URL sequentially and writes the pref only after the whole loop. `processDownload` writes the pref on every completion. Feeds download as fast as the loop enqueues them, so the list empties repeatedly. Logcat, 25 feeds on mobile: update 22:07:27.0–39.5, indicator wrongly hidden 5× for ~6.3 s total (longest 3.0 s, slow art19/libsyn/memberful redirects); loop's full write only at 38.317.
    2. **Feed parse → media enqueue (unverified, needs Wi-Fi + new episodes).** `processDownload` removes the RSS ID while `readPodcastFeed` still runs async; media IDs arrive after parse + DB + the mp3's redirect/DNS checks.
- Rejected: per-ID fixes (hold RSS ID until parsed, write pref per enqueue). Even written per enqueue, the in-memory list was truly empty for 0.02–0.76 s between enqueues. Hide delay rejected — only works with per-enqueue writes and needs a feed-dependent threshold.
- Direction A: explicit "update in progress" state, set in `updateCollection` before the loop, cleared once the loop is done, active downloads are empty and no feed is still parsing. Indicator = update in progress OR active downloads (keeps single manual episode downloads visible).
- Watch: `activeDownloads` is a plain `ArrayList` mutated concurrently by IO coroutines (add) and the main thread (remove); `setActiveDownloads` iterates by index. Any fix here must make access thread-safe.

### 2026-09-22 — Debug session — media notification shows "previous/next" instead of −10 s / +30 s
- Outcome: partial — root cause found, official Media3 mechanism identified. No code (scope agreed: verdict only, fix goes to a quick fix).
- Files changed: none
- Symptom: on API 33+ the system media notification (shade + lock screen) shows previous / play-pause / next. The app's own seek buttons never appear. Headset next/prev do seek ±30 s/10 s as intended — only the labelling is wrong.
- Diagnosed cause: **two independent gaps, not one.** (a) `CustomNotificationProvider.getMediaButtons()` (`BasePlayerService.kt:431`) only builds the *app's* notification; on API 33+ System UI ignores notification actions and renders the platform `PlaybackState`. Custom actions reach that state only via `MediaSession.setCustomLayout`/`setMediaButtonPreferences`, which Escapepod never calls — so the state carries zero custom actions. (b) The `ForwardingPlayer` advertises `COMMAND_SEEK_TO_PREVIOUS`/`COMMAND_SEEK_TO_NEXT` (`:479/481`) purely to make the session accept headset keys; that advertisement becomes `ACTION_SKIP_TO_PREVIOUS`/`ACTION_SKIP_TO_NEXT`, which is what System UI draws.
- Fix mechanism (Media3 1.11.1, both `@UnstableApi`): **`MediaSession.Callback.onMediaButtonEvent`** for the keys — it runs *before* any command-availability check (`MediaSessionImpl.java:1613-1626`), so `KEYCODE_MEDIA_NEXT`/`PREVIOUS` can be turned into `seekForward()`/`seekBack()` without the player advertising the skip commands at all. And **`MediaSession.setMediaButtonPreferences`** for the display — Media3 deliberately strips `ACTION_SKIP_TO_PREVIOUS`/`NEXT` from the platform state when a button claims `SLOT_BACK`/`SLOT_FORWARD` (`MediaSessionLegacyStub.java:1922-1930`) and publishes the buttons as custom actions (`:1993`). Player-command buttons qualify: `CommandButton.convertToPredefinedCustomCommandButton` (`CommandButton.java:1621`) wraps them in a synthetic session command and `onCustomAction` (`MediaSessionLegacyStub.java:514`) routes taps back. Default slots derive from the player command (`CommandButton.java:775-784`), so `COMMAND_SEEK_BACK`/`COMMAND_PLAY_PAUSE`/`COMMAND_SEEK_FORWARD` land in BACK/CENTRAL/FORWARD unaided. Semantic icons exist: `ICON_SKIP_BACK_10` (`:208`), `ICON_SKIP_FORWARD_30` (`:194`).
- Evidence checked: media3 1.11.1 sources from the Gradle cache (`media3-session-1.11.1-sources.jar`) — sites above read directly, not from docs. Plus androidx/media issue #249, **closed 2026-01-19** by marcbaechinger: "this is solved by routing all key events through `MediaSession.Callback.onMediaButtonEvent()`". The 2023 half of that thread (incl. y20k's own comments) predates both APIs.
- Decisions (y20k, this session): skip-to-next-episode may disappear from lock screen and Auto — the strip is unconditional and also removes `COMMAND_SEEK_TO_NEXT_MEDIA_ITEM`'s action (`MediaSessionLegacyStub.java:2030`) — as long as up-next keeps working in-app (`MediaControllerExt.kt:112`, untouched). Rendering/placement doesn't matter; correct labels do.
- Watch when implementing: `onMediaButtonEvent` receives both `ACTION_DOWN` and `ACTION_UP` — act on DOWN only, return true for both, or every press seeks twice.
- Closed the same day by the quick fix in `c45e59a5`; `.ai/PROJECT_BRIEF.md` updated in `169b5b14`.
- Open device checks (all since verified on device, down to Android 7.1.1): does the shade show the two seek buttons with correct labels on API 33+; does `adb shell input keyevent 87` still reach `onMediaButtonEvent` once the player stops advertising `COMMAND_SEEK_TO_NEXT` (load-bearing — if the system filters by `PlaybackState` actions *before* dispatch rather than in the default callback Media3 overrides, the approach collapses).

### 2026-09-04 — Phase P4a complete
- Outcome: done
- Summary: Escapepod converted to the reference Gradle setup in seven iterations — Gradle 9.6.1 + AGP 9.4.0, version catalog, repositories into `settings.gradle.kts`, Kotlin DSL, R8 via `optimization { enable = true }` with keep rules under `src/main/keepRules/`, `gradle.properties` to spec, and `localeFilters`. L1–L5 all met; dependency resolution byte-identical to the P4a.1 baseline at every step.
- Open issues carried forward:
    - **Release freeze from `25c93aa8`** — the `.kts` rename makes the F-Droid recipe's foojay `sed` exit "No such file or directory"; no tag until the recipe MR rides with it (P6).
    - Next Escapepod work in this plan is **P5** — merge `master` into the `non-free` branch, porting the Google Cast block into the converted `.kts`.
    - Unanswered since P4a.5: whether `optimization { }` can shrink *without* obfuscating, the way bare `minifyEnabled` could. Undocumented; needs the AGP DSL reference for `BuildType.optimization`.
- Per-iteration entries P4a.1–P4a.7 pruned 2026-09-22; this summary replaces them, full detail in git.

### 2026-07-26 — Task "F: security audit — input & download vectors" closed
- Outcome: done
- Summary: All 24 findings (F-1…F-24) from the P1/P2 audit remediated across 10 fix-iterations (B1–B10), each compile-verified and committed to `master`. B1's shared `NetworkHelper` validators (`isAllowedScheme`/`isSafeRemoteUrl`) anchored the scheme/SSRF fixes reused by B3/B4/B5/B7/B8; the rest cover path-traversal (B2), parser bounds (B3/B4), dialog + search-JSON hardening (B5/B6), streaming + playback-error handling (B7), feed-HTML render (B8), download robustness (B9), and DOCTYPE documentation (B10). Escapepod was the pilot.
- Per-iteration entries P1.1–P1.5, P2.1–P2.3 and B1–B10 pruned 2026-09-22: findings live in `SECURITY_AUDIT.md`, per-iteration detail in git (`36cd06ef..350a2009`).
- Residual issues: F-13 per-file byte-cap deferred (no DownloadManager max-size API — free-space gate shipped instead); B1 SSRF pragmatic (no DNS-rebind/TOCTOU, no exhaustive IPv6); B3 single-field transient alloc + B4 caller-facing partial-import signal deferred (both need out-of-scope files). Findings meant to transfer to Transistor (M3U/PLS) and Trackbook (GPX, also XML) — separate future task.
