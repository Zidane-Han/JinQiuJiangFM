# SECURITY_AUDIT — Escapepod

Findings from the input & download-vector audit (`F: security audit`, see
`.ai/WORK_PLAN.md`). Severity: **Critical / High / Medium / Low / Info**.
Finding IDs are `F-<n>`, unique within the task, never reused. `Status`:
`open` → `confirmed` (user agrees it's real) → `fixed (B<n>)` / `wontfix`.

Ordered most-severe first.

## Vector P1 — downloads & parsing

### P1.1 — RSS feed XML parsing (`xml/RssHelper.kt`)

Parser: `android.util.Xml.newPullParser()` → Android **KXmlParser**.
`FEATURE_PROCESS_NAMESPACES=false` (line 55); `FEATURE_PROCESS_DOCDECL` never
set → default **false**. KXmlParser performs no external-entity resolution.

### F-1 — Unbounded memory during feed parse (DoS)
- Severity: Medium
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/RssHelper.kt:100` (episode loop), `:128` (`episodes.add`), text fields via `XmlHelper.readText` (e.g. `:161`, `:165`)
- Untrusted input path: downloaded feed XML (local file from a remote, possibly hostile feed) is streamed into KXmlParser; parsing builds an in-memory `RssPodcast` with an **uncapped** `episodes` list and **unbounded** string fields (`description`, `content:encoded`, etc.). No limit on item count or per-field size.
- Impact: memory exhaustion / app crash (denial of service) on a pathological or malicious feed.
- Repro / PoC: a feed containing millions of `<item>` elements, or a single multi-hundred-MB `<content:encoded>` body.
- Fix direction: cap episode count and per-field string length during parse; bound the feed download size. Reachable size partly depends on download limits — coordinate with **P1.3**.
- Status: fixed (B3)

### F-2 — NPE on `<enclosure>` with missing `url` silently aborts the whole feed parse
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/RssHelper.kt:347` (read), `:348` (NPE), aborts at `:60` (catch)
- Untrusted input path: `parser.getAttributeValue(null, "url")` is a Java platform type treated as non-null but returns `null` when the `url` attribute is absent; `value.substring(value.lastIndexOf(".") + 1)` then throws NPE. The exception propagates to `read()`'s `catch (e: Exception) {}` (line 60) → parse aborts, the partially built podcast is returned, no log (printStackTrace commented out), no user signal.
- Impact: a single malformed enclosure early in a hostile feed drops all subsequent episodes; silent partial/empty import.
- Repro / PoC: feed item with `<enclosure type="audio/mpeg" length="1"/>` (no `url`).
- Fix direction: null-check the `url` attribute before use; skip the enclosure gracefully instead of aborting.
- Status: fixed (B3)

### F-3 — Blanket exception swallow hides all feed-parse failures
- Severity: Info
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/RssHelper.kt:60-64`
- Untrusted input path: any malformed feed that triggers an exception during parse.
- Impact: `catch (e: Exception) { }` with `printStackTrace` commented out makes every parse failure invisible and indistinguishable from an empty feed; directly enables the silent behaviour in F-2 and removes diagnostics.
- Repro / PoC: any feed that triggers F-2 — the failure produces no log line.
- Fix direction: catch narrowly, log the failure, and surface a parse-failed result to the caller.
- Status: fixed (B3)

### F-4 — No URL scheme/host validation at the parse boundary (cross-vector)
- Severity: Info
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/RssHelper.kt:347` (audio), `:287`/`:318`/`:329` (cover), `:213` (website)
- Untrusted input path: feed-supplied URLs are stored raw. The audio link is filtered only by MIME type **or** file extension (line 349), **not scheme** — a non-http(s) scheme with an audio MIME passes.
- Impact: unexpected schemes (`file://`, `content://`) can be forwarded to downstream sinks (DownloadManager, Glide). Exploitability depends on those sinks, which are **P1.3 / P1.5** scope; recorded here as the parse-boundary origin.
- Repro / PoC: `<enclosure url="file:///data/..." type="audio/mpeg"/>` passes the filter at line 349.
- Fix direction: validate scheme against an http/https allowlist at the parse boundary; assess sink-side handling in P1.3 / P1.5.
- Status: fixed (B3)

### F-5 — DOCTYPE/entity safety is implicit, not enforced
- Severity: Info
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/RssHelper.kt:54-56`
- Untrusted input path: a feed containing a `<!DOCTYPE>` / internal DTD.
- Impact: XXE and entity-expansion are currently blocked only because KXmlParser defaults `FEATURE_PROCESS_DOCDECL` to false; the code never asserts this, so the safe behaviour would silently break under a future parser or config change.
- Repro / PoC: not reproducible today (see Notes) — defense-in-depth only.
- Fix direction: explicitly set `FEATURE_PROCESS_DOCDECL = false`, and consider rejecting any DOCTYPE outright.
- Status: fixed (B10) — resolved by a documented warning, not a code toggle (setting the feature can throw on KXmlParser and abort parsing; the safe default is relied on intentionally). See WORK_LOG B10.

### Notes (P1.1)

- **XXE / external entity — ruled out.** KXmlParser has no external-entity resolution (no file/network fetch); with `FEATURE_PROCESS_DOCDECL=false` the DTD subset is not processed. No sink can resolve `SYSTEM`/`PUBLIC` identifiers. Not reproducible.
- **Billion-laughs / entity expansion — ruled out.** Custom entities live in the internal DTD, which is not processed (DOCDECL=false); only the 5 predefined non-nesting entities exist. A nested-entity payload resolves to an "unresolved entity" exception, not expansion. Not reproducible. (Hardening tracked as F-5.)
- **Date parsing — confirmed robust (context read: `helpers/DateTimeHelper.kt`, outside P1.1 scope).** `convertFromRfc2822` tries four `DateTimeFormatter` patterns plus a legacy `SimpleDateFormat` fallback, all wrapped in `try/catch`; malformed or too-short input returns `Keys.DEFAULT_DATE` with no exception escaping — including the `substring` at `DateTimeHelper.kt:117`, which sits in its own catch. Consequence in RssHelper: a bad/missing date → `DEFAULT_DATE` → episode excluded by the guard at `RssHelper.kt:121` (benign data loss). The date sink does **not** contribute to the F-2/F-3 silent-abort path.

### P1.2 — OPML import parsing (`xml/OpmlHelper.kt`)

Parser: `android.util.Xml.newPullParser()` → Android **KXmlParser** (identical to
P1.1). `FEATURE_PROCESS_NAMESPACES=false` (line 89); `FEATURE_PROCESS_DOCDECL`
never set → default **false**.

### F-6 — No URL scheme/host validation on imported OPML `xmlUrl` (cross-vector)
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/OpmlHelper.kt:176` (extract `xmlUrl`), `:153` (add to list)
- Untrusted input path: a user-imported OPML file (shareable/downloadable, therefore attacker-controllable). `readOutline` extracts the `xmlUrl` attribute with no scheme/host check; `readBody` adds it to `feedUrlList`; the returned array flows into feed subscription/fetch (RssHelper / `DownloadHelper` — **P1.3** scope).
- Impact: imported feed URLs may be `file://`, `content://`, or internal hosts, forwarded to downstream fetch. Exploitability is sink-dependent (P1.3).
- Repro / PoC: OPML containing `<outline type="rss" xmlUrl="file:///data/..."/>` → URL passes through untouched. (A null `xmlUrl` is safely handled via `?: String()` at line 176 — no NPE.)
- Fix direction: validate scheme against an http/https allowlist at the import boundary; assess sink handling in P1.3.
- Status: fixed (B4)

### F-7 — Unbounded OPML import (resource / mass-subscribe)
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/OpmlHelper.kt:133` (outline loop), `:153` (unbounded add)
- Untrusted input path: a user-imported OPML with an uncapped number of `<outline>` entries; `feedUrlList` grows unbounded and each URL becomes a subscription fetch.
- Impact: memory pressure plus a flood of feed downloads. Lower reachability than RSS F-1 (import is a deliberate user file-pick).
- Repro / PoC: OPML with millions of `<outline type="rss" xmlUrl="...">`.
- Fix direction: cap imported entry count, and/or confirm large imports with the user.
- Status: fixed (B4)

### F-10 — OPML export writes feed URL unescaped into an XML attribute
- Severity: Low
- Vector: P1 downloads & parsing (export path — outside the import vector, promoted from note)
- Location: `app/src/main/java/org/y20k/escapepod/xml/OpmlHelper.kt:209` (unescaped append); contrast `:207` (`podcast.name` escaped via `xmlEscapeText`)
- Untrusted input path: `podcast.remotePodcastFeedLocation` (user-added feed URL) is appended raw into the `xmlUrl="…"` attribute in `createOpmlString`, while `podcast.name` is escaped — asymmetric handling.
- Impact: a feed URL containing `"`, `<`, or `&` produces malformed / attribute-injectable exported OPML (export integrity; breaks re-import). This is the export path, not untrusted-input parsing.
- Repro / PoC: subscribe to a podcast whose feed URL contains a `"`; export OPML → broken/injected `xmlUrl` attribute.
- Fix direction: run `remotePodcastFeedLocation` through `xmlEscapeText` (or an attribute-safe encoder) before appending.
- Status: fixed (B4)

### F-8 — Silent partial OPML import on parse error
- Severity: Info
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/OpmlHelper.kt:94-95` (catch + `printStackTrace`), `:101` (`resume` with partial list)
- Untrusted input path: a malformed OPML that throws mid-parse; URLs are added in-place (line 153), so `feedUrlList` retains whatever was collected before the throw.
- Impact: a truncated import is returned with no user signal. Better than RSS F-3 — this path does log via `printStackTrace` — but still silent to the user.
- Repro / PoC: OPML that is valid up to N outlines then malformed → only the first N URLs import, silently.
- Fix direction: surface a partial/failed-import result to the caller.
- Status: fixed (B4)

### F-9 — OPML DOCTYPE/entity safety is implicit, not enforced
- Severity: Info
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/xml/OpmlHelper.kt:88-90`
- Untrusted input path: an imported OPML containing a `<!DOCTYPE>` / internal DTD.
- Impact: XXE and entity-expansion are blocked only because KXmlParser defaults `FEATURE_PROCESS_DOCDECL` to false; the code never asserts it. Mirrors F-5 for the OPML parser; would silently break under a future parser/config change.
- Repro / PoC: not reproducible today (see Notes) — defense-in-depth only.
- Fix direction: explicitly set `FEATURE_PROCESS_DOCDECL = false`, and consider rejecting any DOCTYPE. Remediate together with F-5.
- Status: fixed (B10) — resolved by a documented warning, not a code toggle (mirrors F-5). See WORK_LOG B10.

### Notes (P1.2)

- **XXE / billion-laughs / external entity — ruled out.** Identical KXmlParser config to P1.1 (`FEATURE_PROCESS_DOCDECL` default false, no external-entity resolution). Not reproducible. Hardening tracked as F-9.

### P1.3 — downloaded-file naming & storage (`helpers/{DownloadHelper,CollectionHelper,FileHelper}.kt`)

Attacker-controlled → filesystem path: feed `<title>` / enclosure URL →
**podcast name** & **download filename** → `FileHelper.determineDestinationFolderPath()`
+ `File(getExternalFilesDir(...), fileName)` in `saveCopyOfFile` / `saveCover`.
`DownloadFinishedReceiver.kt` and `NetworkHelper.kt` were read as context (approved,
outside the allowed-files set).

### F-11 — Path traversal via podcast name; sanitizer is a no-op bug
- Severity: Medium
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/helpers/FileHelper.kt:185` (broken sanitizer), sinks at `:218` (`saveCopyOfFile`), `:236` (`saveCover`); also `CollectionHelper.kt:121`/`:129`/`:132`
- Untrusted input path: `podcastName` originates from the feed `<title>` (`RssHelper`, only `stripHtml`'d — slashes/dots survive) and reaches `determineDestinationFolderPath()`, where `podcastName.replace("[:/]", "_")` is Kotlin's **literal** `replace(String, String)` (replaces the 4-char sequence `[:/]`, not a regex) → effectively no sanitization. The value becomes `FOLDER_AUDIO/IMAGES + "/" + subDirectory` and is passed to `File(getExternalFilesDir(...), ...)`.
- Impact: a feed titled `../../evil` writes/deletes audio & cover files outside the intended per-podcast folder. Bounded to the app's external-storage sandbox on modern Android, but escapes `files/` and can clobber other podcasts' folders / the OPML backup area.
- Repro / PoC: subscribe to a feed whose `<title>` is `../../../evil`; audio/cover save resolves outside the podcast folder.
- Fix direction: real sanitization — allowlist (`replace(Regex("[^\\w-]"), "_")`) or reject `.`/`/`/`\` in `podcastName` before building the path.
- Status: fixed (B2)

### F-15 — SSRF via redirect resolution of feed-supplied URLs
- Severity: Medium
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt:286` (call site) → `helpers/NetworkHelper.kt:169` (`resolveRedirects`), `:182-213` (`createConnection`) — context file, outside P1.3 allowed set
- Untrusted input path: `enqueueDownload` calls `resolveRedirects(uris[i].toString())` on every enclosure/cover/feed URL **before** the `startsWith("http")` guard. `createConnection` opens a real `HttpURLConnection` and reads `responseCode`, following up to 5 redirects (`:194-199`) with no host/IP validation and no timeouts.
- Impact: a malicious/redirecting feed makes the app issue GETs to attacker-chosen internal/link-local http(s) endpoints (`http://192.168.x.x`, `http://169.254.169.254/` metadata, `localhost:port`). Blind SSRF / internal port-scan; cleartext HTTP is enabled so plain-http internal targets work. Redirect targets are not re-validated (public URL can 302 → internal). No timeouts → a slow host hangs the download coroutine. (`file://`/`content://` are NOT reachable — the `as HttpURLConnection` cast throws and is caught.)
- Repro / PoC: feed enclosure URL `http://public/redir` returning `302 Location: http://169.254.169.254/latest/meta-data/`.
- Fix direction: block private/link-local/loopback ranges before connecting; re-validate each redirect hop; set connect/read timeouts. Remediate in `NetworkHelper.kt` (its own pass).
- Status: fixed (B1)

### F-12 — Download filename from attacker-controlled URL path segment, unsanitized
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/helpers/FileHelper.kt:162` (`getFileNameFromUri`), consumed at `DownloadHelper.kt:294-296` (`setDestinationInExternalFilesDir`) and `:395`/`:402` (copy via `getFileName`)
- Untrusted input path: `getFileNameFromUri` uses `uri.pathSegments.last()`; `pathSegments` are percent-**decoded**, so a segment `..%2F..%2Ffile` decodes to `../../file`, which flows into the download destination and the copy filename (same sink class as F-11).
- Impact: traversal / overwrite in the temp/copy target. Reach depends on how DownloadManager normalizes `../` in `setDestinationInExternalFilesDir` — confirm on-device.
- Repro / PoC: enclosure URL ending `/..%2F..%2Ffoo.mp3`.
- Fix direction: sanitize the derived filename (basename only; strip `..` and path separators).
- Status: fixed (B2)

### F-13 — No download size limit (disk-fill DoS)
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt:292-298` (`enqueueDownload`); unused guard at `FileHelper.kt:302` (`enoughFreeSpaceAvailable`)
- Untrusted input path: a feed serves an arbitrarily large enclosure/cover; the `DownloadManager.Request` sets no max size and downloads are enqueued unboundedly (per-episode, per-cover, whole collection).
- Impact: storage exhaustion. `enoughFreeSpaceAvailable()` exists but is not called on this path.
- Repro / PoC: feed with a multi-GB enclosure.
- Fix direction: gate enqueue on `enoughFreeSpaceAvailable()`; consider a per-file size cap.
- Status: fixed (B9)

### F-14 — DownloadManager Cursor leaks + unchecked cursor reads
- Severity: Info
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/helpers/DownloadHelper.kt:509-580` (six query helpers), `FileHelper.kt:106-112` (`getFileName`)
- Untrusted input path: not directly attacker-controlled; robustness hygiene surfaced during the traversal trace.
- Impact: `getRemoteFileLocation`/`getDownloadFileName`/`getDownloadDescription`/`isDownloadFinished`/`isDownloadActive`/`getDownloadError` never close their `Cursor` (unlike `processDownload`, which uses `.use`) → cursor leak. `getFileName` calls `moveToFirst()`/`getString(nameIndex)` without checking success or `nameIndex != -1` → possible crash on a URI lacking `DISPLAY_NAME`.
- Repro / PoC: n/a (leak) / a content URI without `DISPLAY_NAME` for the crash path.
- Fix direction: wrap cursors in `.use`; guard `moveToFirst()` and column index.
- Status: fixed (B9)

### Notes (P1.3)

- **F-4 / F-6 download half fully mitigated.** `file://`/`content://` enclosure/cover URLs are blocked both at enqueue (`startsWith("http")`, `DownloadHelper.kt:290`) and in `resolveRedirects` (the `as HttpURLConnection` cast throws for non-http schemes). Remaining F-4/F-6 exposure is the **streaming** path → P1.5.
- **Streaming bypasses the scheme guard.** `CollectionHelper.buildMediaItem` (`:158`,`:161`,`:187`) feeds `episode.remoteAudioFileLocation` straight to Media3 with no scheme check → `file://`/`content://` reaches the media pipeline. Sink is Media3 → **P1.5**.
- **Cover decode** via Glide in `saveCover` (`FileHelper.kt:239-247`) decodes attacker bytes → **P1.5**.
- **MIME trust reviewed — reasonable.** `processDownload` (`DownloadHelper.kt:181-241`) does not blindly trust the server `Content-Type`; it cross-checks DownloadManager type → resolver → magic bytes (`detectMimeType`) → extension before routing. No finding.
- **`DownloadFinishedReceiver`** (`:36`) only forwards the download ID to `processDownload`. A forged `ACTION_DOWNLOAD_COMPLETE` (if the receiver is exported — manifest/IPC, out of scope) would pass an arbitrary ID, but `getUriForDownloadedFile` scopes URIs to this app → null → error toast. Incidental; intent/IPC surface out of scope, not chased.

### P1.4 — search-result JSON deserialization (`search/SearchResultAdapter.kt`, `search/results/{Podcastindex,Gpodder}Result.kt`)

Search responses come from fixed HTTPS endpoints (podcastindex.org / gpodder.net),
so the threat model is weaker than feed content. `SearchResult.kt` and
`dialogs/FindPodcastDialog.kt` (the Gson call site + URL sink) were read as context
(approved, outside the allowed-files set). Result classes are `@Keep`/`@Expose`
Gson data classes with **non-null Kotlin `String`/`List`** fields; `toSearchResult()`
maps `url`/`title`/`description` → `SearchResult`; on tap `url` flows to subscription.

### F-16 — Gson populates non-null fields reflectively → NPE crash on null/missing API field
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/search/results/PodcastindexResult.kt:29`/`:36-37` (non-null `feeds`/`url`), `GpodderResult.kt:27-28`; sink at `dialogs/FindPodcastDialog.kt:365-366` (context)
- Untrusted input path: Gson constructs these objects reflectively (no Kotlin null-checks), so a JSON `null`/missing field yields a runtime `null` in a non-null type. The sample already shows `"generator": null` (`PodcastindexResult.kt:93`). If `feeds` is null/absent, `podcastindexResults.feeds.map { ... }` NPEs; a null `url`/`title` propagates via `toSearchResult()`.
- Impact: NPE in the Volley `responseListener`, which runs on the **main thread** with **no try/catch** → app crash.
- Repro / PoC: a search response omitting `feeds`, or with `"url": null` on a feed entry.
- Fix direction: nullable fields with defaults (`= null` / `= ""`), drop results with null/blank `url`; see shared fix under F-17.
- Status: fixed (B6)

### F-17 — Unguarded Gson parse of API response on the main thread → crash on malformed JSON
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/dialogs/FindPodcastDialog.kt:356-375` (`createSearchResults`, context file), called from `:322`/`:337`
- Untrusted input path: `gson.fromJson(result, ...)` for both providers is not wrapped in try/catch; type-mismatched or structurally-wrong JSON (e.g. gpodder response that isn't a JSON array, `status` type mismatch) throws `JsonSyntaxException`. `errorListener` (`:350`) handles only Volley transport errors, not parse errors.
- Impact: uncaught exception in the main-thread Volley `responseListener` → app crash. (Confirmed by reading the call site — upgraded from Info.)
- Repro / PoC: search endpoint returns a non-array / type-mismatched body.
- Fix direction (shared with F-16): wrap `createSearchResults` in try/catch returning `emptyArray()`; make result-class fields nullable with defaults; ideally parse off the main thread.
- Status: fixed (B6)

### Notes (P1.4)

- **`searchResult.url` sink = the download pipeline.** On tap, `url` → `activateAddButton` → `podcastFeedLocation` → subscription → `DownloadHelper`, inheriting the **F-4/F-6 scheme guard** and the **F-15 SSRF** surface. No new URL-injection sink in these files.
- **Display is injection-safe.** `title`/`description` render as plain text in `MaterialTextView.setText` (`SearchResultAdapter.kt:68-69`) — no HTML/WebView.
- **P2 flag — search query unencoded in request URL.** `FindPodcastDialog.kt:254`/`:289` build `"...?q=${query.replace(" ", "+")}"` from keyboard input with no URL-encoding; `&`/`#`/`%`/CRLF could alter the request. Belongs to the **P2 (clipboard/text input)** vector.
- **P2 flag — direct URL input.** A pasted `http…` string (`FindPodcastDialog.kt:174`,`:218`) becomes `podcastFeedLocation` → subscription, entering the download pipeline. Belongs to **P2**.

### P1.5 — image & audio decode boundaries (`BasePlayerService.kt`, `collection/CollectionAdapter.kt`)

Decode is delegated to Glide (covers) and Media3/ExoPlayer (audio). Glide decode
is the safe path; Media3 has a failure-handling gap and an unguarded streaming URI.
This iteration resolves the streaming/decode cross-refs parked in P1.3.

### F-18 — No `onPlayerError` / decode-failure handling for malformed episode media
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/BasePlayerService.kt:498` (`playerListener` — no `onPlayerError` override), `:598-600` (`STATE_IDLE` no-op)
- Untrusted input path: ExoPlayer decodes attacker-controlled episode bytes (downloaded file or stream). A malformed/undecodable file surfaces as a `PlaybackException` via `Player.Listener.onPlayerError`, which is never overridden; the `STATE_IDLE` branch is a `// todo` no-op.
- Impact: silent playback failure — no user feedback, no recovery/logging. (Not a crash; ExoPlayer contains the decode error internally.)
- Repro / PoC: subscribe to a feed whose enclosure has an audio MIME/extension but corrupt media bytes; playback silently does nothing.
- Fix direction: override `onPlayerError` — surface a user-visible error and reset/skip the failed item.
- Status: fixed (B7)

### F-19 — Streaming media URI reaches ExoPlayer without scheme validation
- Severity: Low
- Vector: P1 downloads & parsing
- Location: `app/src/main/java/org/y20k/escapepod/collection/CollectionHelper.kt:158`/`:187` (streaming source = `remoteAudioFileLocation`) → `BasePlayerService.kt:289` (`onAddMediaItems` `setUri`)
- Untrusted input path: for streaming, `buildMediaItem` uses the feed-supplied `episode.remoteAudioFileLocation` directly as the ExoPlayer URI, with no scheme guard (unlike the download path's `startsWith("http")` at `DownloadHelper.kt:290`). `file://`/`content://` is therefore reachable.
- Impact: Low — ExoPlayer only attempts a *local decode* of the target; there is no channel to exfiltrate bytes back to the attacker, and no cross-app access beyond the app's own permissions. This is the residual streaming half of F-4/F-6.
- Repro / PoC: stream an episode whose enclosure `url` is `file:///…` with an audio MIME.
- Fix direction: apply an http/https scheme allowlist to the media URI before playback (streaming half of F-6's fix).
- Status: fixed (B7)

### Notes (P1.5)

- **F-4 / F-6 streaming cross-ref resolved** — now tracked concretely as F-19 (Low residual). The download half remains fully mitigated (see P1.3 notes).
- **Glide cover decode — failure handled, no finding.** `CollectionAdapter.kt:135-141`/`:168-174` loads the app's own re-encoded local JPEG with an `.error(default)` fallback. The raw attacker-image first-decode is upstream in `saveCover` (`FileHelper.kt:239-247`, P1.3): try/caught with a default-cover fallback, and `.override(size,size)` makes Glide downsample during decode → decompression-bomb memory bounded. Residual Info: no explicit source-dimension cap, relies on Glide downsampling.
- **IPC surface = out of scope, incidental.** `MediaLibraryService` callbacks (`onGetItem`/`onGetChildren`/`onCustomCommand`/`onConnect`) are reachable by media controllers (Android Auto/system); `mediaId` args feed parameterized Room lookups (`findByMediaId`) — no injection. The `analyticsListener` AudioFX **implicit broadcast** (`BasePlayerService.kt:617-621`) is IPC and currently **dead code** (`addAnalyticsListener` commented out at `:471`). Intent/IPC deliberately out of scope; noted, not chased.

## Vector P2 — clipboard/text input

### P2.1 — add-podcast dialog text input (`dialogs/FindPodcastDialog.kt`)

The dialog's `SearchView` accepts typed/pasted text via `onQueryTextChange` (`:129`)
and `onQueryTextSubmit` (`:133`). Two behaviours: input starting `http` →
`activateAddButton` → `podcastFeedLocation` → subscription/download pipeline;
otherwise → a gpodder/podcastindex search-request URL. Clipboard here is copy-**out**
only (swept in P2.2). Findings inherit the download-side F-15 / F-4 / F-6 surface (P1.3).

### F-20 — Pasted feed URL reaches the subscription/SSRF pipeline behind only a weak `startsWith("http")` check
- Severity: Low
- Vector: P2 clipboard/text input
- Location: `app/src/main/java/org/y20k/escapepod/dialogs/FindPodcastDialog.kt:174` & `:187` (`query.startsWith("http")`), `:214`-`:218` (`activateAddButton` → `podcastFeedLocation = query`), `:111` (handoff to `onFindPodcastDialog`)
- Untrusted input path: text typed or **pasted** into the search box; if it starts with the literal `http` it is taken verbatim as `podcastFeedLocation` and, on *Add*, passed to `FindPodcastDialogListener.onFindPodcastDialog` → subscription → `DownloadHelper` fetch. No URL parse, no host check; case-sensitive prefix only (`HTTP://` falls through to search, `httpjunk` is accepted). Input-boundary origin of the download-side **F-15 SSRF** and F-4/F-6 scheme handling.
- Impact: a socially-engineered paste ("paste this to subscribe") makes the app GET an attacker-chosen URL → blind SSRF against the user's local/link-local network. Inherits F-15's downstream reach.
- Repro / PoC: paste `http://169.254.169.254/latest/meta-data/` (or a public URL that 302s inward) into the search box → *Add* enables → subscribe → request issued. (Same downstream as F-15.)
- Fix direction: validate at this boundary — parse as a URL and enforce an http/https host allowlist / reject non-public hosts before enabling *Add*. Remediate jointly with F-15 at the `NetworkHelper` choke point. Distinct reachable path from F-15 (user paste, not feed-supplied), shares the sink and fix.
- Status: fixed (B5)

### F-21 — Search query spliced into request URL without URL-encoding (only spaces→`+`)
- Severity: Low
- Vector: P2 clipboard/text input
- Location: `app/src/main/java/org/y20k/escapepod/dialogs/FindPodcastDialog.kt:254` (gpodder), `:289` (podcastindex); input from `:129`/`:133`
- Untrusted input path: `query` (typed/pasted) is interpolated into `"...?q=${query.replace(" ", "+")}"`; only the space→`+` substitution is applied, so `&`, `#`, `%`, `+` and other reserved characters are not percent-encoded.
- Impact: query-string manipulation of the fixed HTTPS search request — `&` injects extra parameters, `#` truncates the query into a fragment, `%`/malformed escapes produce an invalid request. Bounded to the public gpodder/podcastindex GET endpoints (no auth, no secrets; CRLF / header-injection **not** reachable through the `URL`/`HttpURLConnection` stack). A malformed request can also return an unexpected/empty body that feeds the unguarded Gson parse (**F-16/F-17**) → crash.
- Repro / PoC: search `foo&bar=1` → request `...?q=foo&bar=1` (injected param); search `foo#x` → server sees `q=foo`.
- Fix direction: build the URL with `Uri.Builder().appendQueryParameter("q", query)` or `URLEncoder.encode(query, "UTF-8")` instead of the space-only replace.
- Status: fixed (B5)

### F-22 — No length bound on the dialog search query
- Severity: Info
- Vector: P2 clipboard/text input
- Location: `app/src/main/java/org/y20k/escapepod/dialogs/FindPodcastDialog.kt:190` (fires on `length > 4`, no upper cap) → `:254`/`:289`
- Untrusted input path: a very long pasted query builds an unbounded request URL. Contrast the main-screen field's `SEARCH_TERM_CHARACTER_LIMIT` cap (P2.2).
- Impact: over-long URL → server 414 / request failure. Negligible.
- Repro / PoC: paste a multi-KB string into the search box → over-length request URL.
- Fix direction: cap the query length (mirror `SEARCH_TERM_CHARACTER_LIMIT`) before building the request.
- Status: fixed (B5)

### Notes (P2.1)

- **Search-result tap = server-provided URL.** `onSearchResultTapped` (`:82`) → `activateAddButton` uses a URL from the search-API response, not text input — covered by P1.4, not re-audited here.
- **Query logged to logcat.** `Log.v` (`:251`,`:286`) writes the raw query to logcat — local-only, not a vector.
- **Weak `startsWith("http")` check is duplicated downstream** at `DownloadHelper.kt:290` (fixed B1 → `isSafeRemoteUrl`), `BaseMainActivity.downloadPodcastFeed:388` (fixed B5 → `isAllowedScheme`; the case-sensitive form errored "Invalid Podcast Feed" on uppercase schemes once B5 routed them to subscribe), and `BaseMainActivity.handleViewIntent:524` (left as-is — intent/IPC surface, out of audit scope, fails closed) — same boundary weakness, fixed per layer (see F-20).

### Notes (P2.2)

- **Main-screen episode search field — no finding.** `PodcastFragmentLayoutHolder.searchTextField` (`fragment_podcast.xml:122`, singleLine) → `submitSearchTerm` (`:317`) trims and caps input at `Keys.SEARCH_TERM_CHARACTER_LIMIT` (=100). Two sinks, both safe: (1) `EpisodeAdapter.filterBySearchTerm` (`EpisodeAdapter.kt:174-177`, context) = plain `title.lowercase().contains(...)` substring match — no regex/`Pattern` compile → no ReDoS; (2) `PodcastDao.updateEpisodeListSearchTerm` (`PodcastDao.kt:140-141`, context) = parameterized Room `@Query` (`:searchTerm` bound) — no SQL injection. Persisted term round-trips via `restoreSearchState` (`:230`) into the same safe path.
- **Clipboard = copy-out only, no finding.** All `ClipData.newPlainText` sites write app data *to* the clipboard: `PodcastFragmentLayoutHolder.kt:194` (feed URL), `SettingsFragment.kt:309` (app version), `DescriptionDialog.kt:83` / `ShowNotesDialog.kt:91` (feed URL). No path ingests a clipboard *paste* as an untrusted sink beyond the OS-handled text fields (P2.1 / P2.3).
- **`setCleanHtml` call site.** `updatePodcastDescriptionView` (`:346`,`:352`) feeds feed `description` into `setCleanHtml` — the render sink audited in P2.3.

### P2.3 — feed-HTML render sink (`extensions/TextViewExt.kt`, `dialogs/{DescriptionDialog,ShowNotesDialog}.kt`)

`setCleanHtml` (`TextViewExt.kt:25-27`) strips `<img …>` via regex, then `Html.fromHtml(…, FROM_HTML_MODE_COMPACT)`
and sets the result as TextView text. Both dialogs attach `LinkMovementMethod` (tappable links). Input =
feed `description` / episode show-notes HTML (attacker-controllable). Renders into a `Spanned`/TextView,
**not a WebView** — no JS execution.

### F-23 — Tappable feed-supplied `<a href>` links launch `ACTION_VIEW` with an unvalidated URI scheme
- Severity: Low
- Vector: P2 clipboard/text input (feed-HTML render sink, folded in by decision)
- Location: `app/src/main/java/org/y20k/escapepod/extensions/TextViewExt.kt:26` (`Html.fromHtml` → `URLSpan`s); `app/src/main/java/org/y20k/escapepod/dialogs/DescriptionDialog.kt:67` & `app/src/main/java/org/y20k/escapepod/dialogs/ShowNotesDialog.kt:75` (`movementMethod = LinkMovementMethod`)
- Untrusted input path: feed HTML → `<a href="…">` becomes a `URLSpan`; `LinkMovementMethod` makes it tappable; a tap fires `Intent(ACTION_VIEW, Uri.parse(href))` with the attacker-chosen scheme/URL — no allowlist, no confirmation, no display of the true target.
- Impact: the feed controls both link text and target → phishing/redirect (innocuous text, malicious destination); non-http schemes (`file://`, `content://`, custom deep links) are also launched via `ACTION_VIEW`, bounded by the target app's own permissions. Requires a user tap.
- Repro / PoC: description `<a href="https://evil/">Official site</a>`, or `<a href="file:///…">read more</a>` → tap opens the attacker URI. (`intent:`-scheme arbitrary-intent injection is NOT reachable — `URLSpan` uses `ACTION_VIEW`+`Uri.parse`, not `Intent.parseUri`.)
- Fix direction: validate the scheme (http/https allowlist) and/or surface the destination before launching; or neutralize non-http(s) `href` schemes in `setCleanHtml`.
- Status: fixed (B8)

### F-24 — Unbounded feed HTML parsed by `Html.fromHtml` on the main thread (render-side DoS/ANR)
- Severity: Low
- Vector: P2 clipboard/text input (feed-HTML render sink) — cross-ref F-1
- Location: `app/src/main/java/org/y20k/escapepod/extensions/TextViewExt.kt:26`; sinks at `dialogs/DescriptionDialog.kt:66`, `dialogs/ShowNotesDialog.kt:74`, `ui/PodcastFragmentLayoutHolder.kt:346`/`:352`
- Untrusted input path: `description` is uncapped (root of F-1); `Html.fromHtml` parses it synchronously on the UI thread in the dialogs' `show()`. A multi-MB or deeply-nested HTML body is parsed on the main thread.
- Impact: main-thread stall / ANR / OOM when opening the description/show-notes of a hostile feed. Distinct sink from F-1 (render vs. parse) — adds the ANR angle F-1 lacks.
- Repro / PoC: feed with a multi-MB `<description>` (or deeply nested tags) → open description dialog → jank/OOM.
- Fix direction: cap description length before rendering (coordinate with F-1's per-field cap); consider parsing off the main thread.
- Status: fixed (B8)

### Notes (P2.3)

- **No JS execution — key safety property.** `Html.fromHtml` renders into a `Spanned`/TextView, **not a WebView**; `<script>` is inert. Feed HTML is therefore **not an XSS vector**.
- **`intent:`-scheme arbitrary-intent injection NOT reachable.** `URLSpan` uses `ACTION_VIEW` + `Uri.parse`, not `Intent.parseUri` — no attacker-built intent. Bounds F-23.
- **`<img.+?>` strip is cosmetic, not a sanitizer.** Removes placeholder images only; bypassable (multiline / bare `<img>`) and `Html.fromHtml` renders no images anyway (no `ImageGetter`). Not ReDoS-prone (single non-greedy quantifier). Not a security control.
- **Clipboard copy-out** in both dialogs (`DescriptionDialog.kt:83`, `ShowNotesDialog.kt:91`) already swept in P2.2 — copy-out only.
- **Double `setCleanHtml`** at `PodcastFragmentLayoutHolder.kt:346`/`:352` — minor double-parse inefficiency, not security.
