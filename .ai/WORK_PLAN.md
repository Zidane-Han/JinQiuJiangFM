# WORK_PLAN — Escapepod

_No active tasks. Completed work is archived below._

## Archive

## ✅ B: update indicator spans the whole update (closed 2026-09-25)

The download progress indicator hides and reappears mid-update because `PREF_ACTIVE_DOWNLOADS` empties while work is still running — completions overtake the sequential enqueue loop, and a parsed feed's media downloads are enqueued only after its RSS ID is removed. Diagnosis and logcat evidence: `.ai/WORK_LOG.md`, 2026-09-25 triage entry.

Chosen approach (y20k, 2026-09-25): keep the single signal — `DownloadHelper` tracks outstanding work and never publishes an empty list while busy. UI (`MainActivityLayoutHolder`, `BaseMainActivity`, `MainFragment` scroll-to-top) stays untouched. Covers full update, single-podcast refresh and adding a podcast alike, since all share the enqueue and parse paths.

### Notes and risks

- `PREF_ACTIVE_DOWNLOADS` changes meaning: "downloads active or work still running". While busy it may briefly hold finished IDs; `getActiveDownloads` already prunes inactive IDs on load.
- If the process dies while busy, the pref may keep finished IDs — pruned on next load, so no stuck indicator.
- Out of scope, noted in the work log: MIME step 2a status mismatch.

### Iterations

| ID | Goal | Allowed files | Acceptance criteria (short) |
|----|------|---------------|-----------------------------|
| ✅ B1 | `activeDownloads` safe under concurrent access | `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt` | All access serialized; no behaviour change; update still completes |
| ✅ B2 | Download signal never empty while work is outstanding | `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt` | One `zero` per update, at the end; indicator visible from the first enqueue; no gap between a feed and its media |

#### ✅ B1 — `activeDownloads` thread safety

- Every read and write of `activeDownloads` is serialized: adds in the enqueue loop, removes in `processDownload`, the copy in `isNotInDownloadQueue`, the replacement in `reconcileActiveDownloads`/`initialize`, and building the string in `setActiveDownloads`.
- `reconcileActiveDownloads` no longer drops IDs that are enqueued but not yet written to the pref.
- No behaviour change: a full update on mobile data finishes and refreshes every feed. Compiles and runs.

#### ✅ B2 — Busy-aware publishing

- `DownloadHelper` tracks outstanding work: running enqueue loops, plus feed parses not yet finished (including the media enqueue they trigger). An empty list is published only when that count is zero and no IDs are active.
- The pref is written after each successful enqueue, not only at the end of the loop.
- Device check, update on mobile data: logcat (tags `DownloadHelper`, `PreferencesHelper`, `NetworkHelper`) shows `IDs of active downloads: zero` exactly once, after the last feed is processed; no visible flicker.
- Device check, update on Wi-Fi with new episodes: no `zero` between a feed finishing and its mp3 being enqueued; indicator stays until the last mp3 completes.

## ✅ F: security audit — input & download vectors (closed 2026-07-26)

Audit two untrusted-input vectors, then remediate confirmed findings. Vector 1 =
keyboard/clipboard text fields only (intent/IPC surface out of scope). Most
intensive phase (downloads & parsing) runs first.

### Phases

| Phase | Name | Description | Depends on |
|-------|------|-------------|------------|
| P1 | Audit — downloads & parsing | Review untrusted-byte handling: RSS + OPML XML (`xml/RssHelper.kt`, `xml/OpmlHelper.kt` — XXE, entity expansion, enclosure-URL & filename/path injection), cover-image decode (Glide), audio (Media3 malformed media), search JSON (Gson). Document findings. **No code.** **Complete.** | — |
| P2 | Audit — clipboard/text input | Review every text field that accepts pasted/typed input (add-podcast-by-URL, search dialog, any rename) and trace where the value flows: URL construction, file paths, Room queries, intents. Document findings. **No code.** **Complete.** | — |
| P3 | Remediate confirmed findings | Turn each confirmed P1/P2 finding into a small fix-iteration (`B1`, `B2`…, ≤3 files, compile-and-run testable). | P1, P2 |

### Notes and risks

- Audit phases (P1, P2) emit findings, not code. They write to `.ai/SECURITY_AUDIT.md`; iteration acceptance = "vector documented with severity + repro," not compile-and-run. Deliberate deviation from the standard iteration shape.
- Cleartext HTTP is intentional (`usesCleartextTraffic=true` for HTTP feeds). Remediation targets parser/decoder robustness against hostile bytes, not transport hardening.
- XML findings (RSS/OPML) are the transferable core — they seed the Transistor (M3U/PLS, lighter) and Trackbook (GPX, also XML) re-runs.
- Intent/IPC surface is out of scope — note anything incidental, don't fix it.

### Iterations

**Phases P1–P2 (audit) — complete.** All eight audit iterations (P1.1–P1.5, P2.1–P2.3) are done. Per-iteration detail (files reviewed, findings raised) is in `.ai/WORK_LOG.md`; findings F-1…F-24 in `.ai/SECURITY_AUDIT.md`; full history in git. Iteration specs removed here to keep the plan focused on live work.

#### Phase P3 — Remediate confirmed findings

Remediation iterations — small code fixes, compile-and-run testable, ≤3 files. Run via `JOB_IMPLEMENT_ITERATION.md`. Ordered severity-first, clustered by shared fix + file. **B1 introduces the shared URL scheme/host validator; B3/B4/B5/B7/B8 reuse it and depend on B1.** All 24 findings (F-1…F-24) are in scope and treated as **confirmed** for this backlog. Two scope bounds (user-approved): B1's SSRF defense is **pragmatic** (no DNS-rebinding / TOCTOU coverage); F-13's per-file **byte cap is deferred** (DownloadManager has no clean max-size API) — B9 does the free-space gate only.

- [x] **B1 — SSRF guards + shared URL scheme/host validator** (F-15)
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/helpers/NetworkHelper.kt`
        - `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt`
    - Acceptance criteria:
        - A reusable scheme/host validation function exists (http/https scheme allowlist + rejects private/link-local/loopback resolved IPs) that later iterations call.
        - `resolveRedirects`/`createConnection` reject a URL — and each redirect hop — that resolves to a private/link-local/loopback address before issuing the request.
        - Connect and read timeouts are set, so a slow/hanging host fails instead of blocking the download coroutine indefinitely.
        - Explicitly out of scope (documented, not implemented): DNS-rebinding / TOCTOU between validation and socket connect, exhaustive IPv6 coverage. A normal http(s) feed still subscribes and downloads unchanged.

- [x] **B2 — Filesystem path sanitizer** (F-11, F-12)
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/helpers/FileHelper.kt`
        - `app/src/main/java/org/y20k/escapepod/helpers/CollectionHelper.kt`
    - Acceptance criteria:
        - The podcast-name → folder sanitizer actually strips/replaces path separators and `.`/`..` (the `replace("[:/]","_")` literal no-op is fixed); a feed titled `../../evil` no longer escapes its per-podcast folder (F-11).
        - The download filename derived from the URL path segment is reduced to a safe basename (no `..`, no separators after percent-decoding) (F-12).
        - A normal podcast (ordinary title + enclosure names) still saves audio and cover to the correct per-podcast folder — happy path unchanged.

- [x] **B3 — RSS parser hardening** (F-1, F-2, F-3, F-4)
    - Depends: B1
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/xml/RssHelper.kt`
    - Acceptance criteria:
        - Parsing is bounded: episode count and per-field string length (`description`, `content:encoded`, title, …) are capped so a pathological feed can't exhaust memory (F-1).
        - A missing `<enclosure url>` attribute is handled gracefully (skip the enclosure) instead of throwing an NPE that silently aborts the whole feed (F-2).
        - The parse catch is narrowed and logs the failure instead of swallowing it silently (F-3).
        - Feed-supplied URLs are validated against the shared scheme allowlist (B1) at the parse boundary (F-4); a normal feed still imports all its episodes.

- [x] **B4 — OPML hardening** (F-6, F-7, F-8, F-10)
    - Depends: B1
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/xml/OpmlHelper.kt`
    - Acceptance criteria:
        - Imported `xmlUrl` values are validated against the shared scheme allowlist (B1) before being added to the subscribe list (F-6).
        - Imported outline count is capped (or a large import is confirmed) so a huge OPML can't mass-subscribe unboundedly (F-7).
        - A malformed/partial OPML import surfaces a partial/failed result to the caller instead of silently returning a truncated list (F-8).
        - OPML export escapes `remotePodcastFeedLocation` via `xmlEscapeText` so a feed URL with `"`/`<`/`&` produces valid, re-importable OPML (F-10); a normal import/export round-trips.

- [x] **B5 — Add-podcast dialog hardening** (F-20, F-21, F-22)
    - Depends: B1
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/dialogs/FindPodcastDialog.kt`
    - Acceptance criteria:
        - A pasted/typed feed URL is validated via the shared validator (B1) before *Add* is enabled, replacing the weak case-sensitive `startsWith("http")` check; a private/link-local host is rejected here (F-20).
        - The search query is URL-encoded via `Uri.Builder().appendQueryParameter` (or `URLEncoder`) so `&`/`#`/`%` no longer manipulate the request (F-21).
        - The dialog search query is length-capped (mirroring `SEARCH_TERM_CHARACTER_LIMIT`) before building the request (F-22).
        - A normal search and a normal paste-to-subscribe both still work.

- [x] **B6 — Search JSON null-safety** (F-16, F-17)
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/search/results/PodcastindexResult.kt`
        - `app/src/main/java/org/y20k/escapepod/search/results/GpodderResult.kt`
        - `app/src/main/java/org/y20k/escapepod/dialogs/FindPodcastDialog.kt`
    - Acceptance criteria:
        - Gson result-class fields are nullable with safe defaults; a response with null/missing `feeds`/`url` no longer NPEs, and blank-`url` results are dropped (F-16).
        - The `gson.fromJson` calls in `createSearchResults` are wrapped so malformed/type-mismatched JSON yields an empty result set instead of crashing the main-thread Volley listener (F-17).
        - A normal search still returns and displays results; an empty/garbage response shows no results instead of crashing.

- [x] **B7 — Streaming scheme guard + playback error handling** (F-19, F-18)
    - Depends: B1
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/collection/CollectionHelper.kt`
        - `app/src/main/java/org/y20k/escapepod/BasePlayerService.kt`
    - Acceptance criteria:
        - The streaming media URI is validated against the shared scheme allowlist (B1) before being handed to ExoPlayer, so `file://`/`content://` is rejected on the streaming path (F-19).
        - `onPlayerError` is overridden to surface a user-visible error and reset/skip the failed item instead of failing silently (F-18).
        - Normal streaming and download playback are unaffected; a deliberately corrupt episode shows an error instead of doing nothing.

- [x] **B8 — Feed-HTML render hardening** (F-23, F-24)
    - Depends: B1
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/extensions/TextViewExt.kt`
        - `app/src/main/java/org/y20k/escapepod/dialogs/DescriptionDialog.kt`
        - `app/src/main/java/org/y20k/escapepod/dialogs/ShowNotesDialog.kt`
    - Acceptance criteria:
        - Tappable links rendered from feed HTML are restricted to http/https (non-http schemes like `file://`/`content://` are neutralized or not launched), reusing the shared scheme check (B1) (F-23).
        - The feed description/HTML is length-capped before `Html.fromHtml` parses it, so a multi-MB body can't stall the main thread (F-24).
        - Normal descriptions/show-notes still render with working http(s) links.

- [x] **B9 — Download robustness** (F-13 free-space half, F-14)
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt`
        - `app/src/main/java/org/y20k/escapepod/helpers/FileHelper.kt`
    - Acceptance criteria:
        - Enqueue is gated on the existing (currently unused) `enoughFreeSpaceAvailable()` so downloads don't start when storage is insufficient (F-13, free-space half).
        - The six DownloadManager query helpers wrap their `Cursor` in `.use`, and `getFileName` guards `moveToFirst()`/column index before reading (F-14).
        - Deferred (documented, not implemented): a per-file maximum-byte cap — DownloadManager has no clean max-size API; poor effort-to-value.
        - Normal downloads still complete; no cursor leaks in the download-status paths.

- [x] **B10 — XML DOCTYPE hardening** (F-5, F-9)
    - Allowed files:
        - `app/src/main/java/org/y20k/escapepod/xml/RssHelper.kt`
        - `app/src/main/java/org/y20k/escapepod/xml/OpmlHelper.kt`
    - Acceptance criteria:
        - Both parsers explicitly set `FEATURE_PROCESS_DOCDECL = false`, making the currently-implicit safe behavior explicit (F-5, F-9) — pure defense-in-depth.
        - Optionally reject any DOCTYPE outright (documented if not done).
        - Normal RSS and OPML feeds still parse — no behavior change for well-formed input.
