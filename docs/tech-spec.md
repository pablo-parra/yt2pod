# yt2pod — Technical Specification

Status: draft v0.2 · Covers user stories in [user-stories.md](user-stories.md)

## 1. Summary

A Spring Boot (Java) web service for a private LAN. Any client device submits a YouTube URL (web page or REST API, no login). The backend downloads **only the audio**, converts it to a small speech-oriented file, uploads it to a Google Drive folder using credentials configured once by the Owner, and discards the temporary file. No URLs or history are persisted.

```
Phone / tablet / PC (no login, no install)
        │  HTTP on LAN (web page or API)
        ▼
  Spring Boot ──► yt-dlp + ffmpeg (local process) ──► temp audio file
        │                                                   │
        └── Google Drive API (refresh token from env) ◄─────┘ upload, then delete temp
```

## 2. Research: extracting audio from YouTube

| Option | Type | Pros | Cons |
|---|---|---|---|
| **yt-dlp** (+ ffmpeg) | CLI tool run as a subprocess | Most actively maintained; fastest to react to YouTube changes (latest release seen: 2026.08.19); extracts audio, converts, embeds metadata/thumbnail | Not a Java library (needs the standalone binary, ffmpeg, and a JS runtime for full YouTube support); subprocess management |
| **NewPipeExtractor** (Java, JitPack) | Pure Java library | Native Java API | **GPL-3.0**; only returns stream URLs, so download, transcoding and tagging are still our job; fixes for YouTube breakages usually slower than yt-dlp |
| Java wrappers around yt-dlp | Thin Java wrapper over the CLI | Less boilerplate | Small, lightly maintained; `ProcessBuilder` does the same in ~50 lines |
| Other small Java YouTube extractors | Pure Java | — | Typically abandoned, break often; not recommended |
| Official YouTube Data API | API | Stable | Does **not** provide media downloads; unusable here |

**Recommendation:** **yt-dlp via `ProcessBuilder`** + **ffmpeg**, behind an `AudioExtractor` interface so another implementation (e.g. NewPipeExtractor) can be swapped in.

Notes (verify against the current yt-dlp README at implementation time):
- Since late 2025 yt-dlp needs an external **JavaScript runtime** (Deno recommended, ≥ 2.0) for full YouTube support. The official standalone executables bundle the solver component (`yt-dlp-ejs`); the runtime must still be available.
- YouTube may require **PO tokens** or cookies for some videos/IPs; datacenter IPs are more likely to be blocked than home connections, which favors the LAN/home deployment.
- yt-dlp must be easy to update (`yt-dlp -U` or image rebuild) — US-12.

Legal: downloading from YouTube may violate its Terms of Service; keep the service private and personal.

## 3. Google Drive access (no user login)

Clients are anonymous; only the **service** authenticates to Google, as the Owner's account.

- **Mechanism:** OAuth 2.0 "web application" client + a long-lived **refresh token** obtained once. The service builds `UserCredentials` (client ID, client secret, refresh token) from environment variables; the Google client library **requests and renews access tokens automatically** (~1 h lifetime). The service code does not implement renewal itself.
- **Scope:** `https://www.googleapis.com/auth/drive.file` (least privilege: only files created by the app). The service creates its own target folder, so this scope is enough.
- **Client libraries:** `com.google.apis:google-api-services-drive` (v3; latest seen `v3-rev20260913-2.0.0`), `google-api-client`, `google-auth-library-oauth2-http`.
- **Upload:** `Drive.Files.create` with `InputStreamContent`, resumable upload.
- **Obtaining the refresh token (one-off, US-01):**
  1. In Google Cloud: create a project, enable the Drive API, configure the OAuth consent screen, create an OAuth client ID (Web) with redirect URI `http://localhost:8080/setup/google/callback`.
  2. Start the service **on the host** with `YT2POD_SETUP_ENABLED=true`, port bound to localhost only (e.g. `-p 127.0.0.1:8080:8080`).
  3. Open `http://localhost:8080/setup/google` in the host's browser, sign in with the Drive account, approve. The app requests `access_type=offline` and `prompt=consent` so Google returns a refresh token.
  4. The callback page shows the refresh token **once**; copy it into the `GOOGLE_REFRESH_TOKEN` environment variable and restart without setup mode.
  - Google accepts plain `http` redirect URIs only for `localhost`, which is why setup is done on the host. Normal use afterwards involves no redirect at all.
  - Setup endpoints exist only while `YT2POD_SETUP_ENABLED=true`. A loopback-address check is not used because, with Docker, requests arrive from the bridge gateway rather than `127.0.0.1`; binding the published port to `127.0.0.1` is what protects the setup page.
- **Token lifetime caveat:** while the Google OAuth app is in *Testing* publishing status, refresh tokens expire after 7 days. Switch the app to *In production* (for a personal app using only `drive.file` this should not require Google's verification — to be confirmed during setup; an "unverified app" warning screen is expected and acceptable for the Owner).
- **Revocation/expiry:** if Google returns `invalid_grant`, jobs fail with "Drive authorization expired — redo setup" and the health endpoint reports the Drive indicator `DOWN`.

## 4. Technology stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 (LTS) |
| Framework | Spring Boot 4.1.x (latest stable at project start), Maven |
| Web | Spring MVC + one static HTML page (vanilla JS) |
| Security | None for Clients (trusted LAN); Spring Security not required in the MVP |
| Async jobs | `ThreadPoolTaskExecutor` (bounded, configurable); in-memory job registry (`ConcurrentHashMap`) with TTL eviction |
| Extraction | yt-dlp + ffmpeg via `ProcessBuilder` |
| Drive | Google API Client Java libraries (above) |
| Config | `application.yml` + environment variables |
| Packaging | Docker image (JRE 21 + ffmpeg + yt-dlp + Deno), `docker compose` |
| Observability | Spring Boot Actuator (health), SLF4J logs **without URLs or titles** (job id only) |
| Tests | JUnit 5, Mockito, Spring Boot Test, MockMvc; fake `AudioExtractor`/`DriveUploader` for integration tests |

## 5. Architecture

Packages (`com.pablo.yt2pod`):
- `web` — `ConversionController`, error handling, static page
- `setup` — one-off Google authorization (`SetupController`, enabled by flag)
- `job` — `ConversionJob`, `JobService`, `JobRegistry` (in-memory, TTL), rate limiter
- `extract` — `AudioExtractor` (interface), `YtDlpAudioExtractor`, `YoutubeUrlValidator`
- `storage` — `DriveUploader` (interface), `GoogleDriveUploader`, folder resolution
- `config` — `@ConfigurationProperties` classes

Flow:
1. `POST /api/conversions {url}` → validate → create job (`QUEUED`) → `202 {jobId}`.
2. Async worker: `EXTRACTING` → yt-dlp into a per-job temp dir → `UPLOADING` → Drive upload → `DONE` (file name + `webViewLink`) or `FAILED` (reason).
3. `finally`: delete the temp dir. The URL is dropped from the job object once extraction has started.
4. `GET /api/conversions/{jobId}` → status. Jobs are evicted after a TTL (default 1 h).

## 6. API

| Method | Path | Description |
|---|---|---|
| GET | `/` | Web page: URL input + status of the jobs submitted from that browser |
| POST | `/api/conversions` | Body `{ "url": "..." }` → `202 {jobId}`; `400` invalid URL; `429` too many jobs |
| GET | `/api/conversions/{jobId}` | `{status, fileName?, driveLink?, error?}` |
| GET | `/actuator/health` | Health (includes Drive credential check) |
| GET | `/setup/google`, `/setup/google/callback` | One-off authorization; only when `YT2POD_SETUP_ENABLED=true` |

Job states: `QUEUED → EXTRACTING → UPLOADING → DONE | FAILED`.

Phone/tablet clients use the web page manually (copy the link, open `http://<host>:8080`, paste, submit). Share-menu integration and shortcuts are out of scope.

## 7. yt-dlp invocation

```
yt-dlp --ffmpeg-location <ffmpeg> --no-playlist -f "bestaudio/best" -x \
       --audio-format m4a --audio-quality 64K --postprocessor-args "ffmpeg:-ac 1" \
       --embed-metadata --embed-thumbnail \
       --match-filters "!is_live & duration<=<max-seconds>" --no-progress \
       -o "<tmpdir>/%(title).150B.%(ext)s" -- <canonical-url>
```
- Arguments are passed as a list (no shell) with the URL after `--`; the URL must first pass `YoutubeUrlValidator`, which accepts only single-video links on `youtube.com`, `www.`, `m.`, `music.youtube.com` and `youtu.be`, and **rewrites them to `https://www.youtube.com/watch?v=<id>`** so no other part of the input reaches yt-dlp.
- Videos rejected by `--match-filters` (live or too long) make yt-dlp exit 0 without a file; the extractor detects this and reports `LIMIT_EXCEEDED`. After a successful run only the audio file is kept (thumbnails and intermediates are deleted).
- **Quality: speech-oriented, not best.** Default **64 kbps mono** M4A/AAC (≈ 29 MB per hour; 192 kbps would be ≈ 86 MB/hour). YouTube's own AAC is typically ~128 kbps, so 192 would only inflate the file. Configurable (`yt2pod.audio.bitrate`, `yt2pod.audio.mono`, `yt2pod.audio.format`). The target is lower than the source, so ffmpeg always re-encodes (small CPU cost).
- Timeout and output capture; yt-dlp output is mapped to user-friendly failures (`ExtractionFailure`: unavailable, private, age-restricted, geo-blocked, bot check, limit exceeded, timeout, tool not found, unknown).
- Drive file name collisions are resolved by suffixing.

## 8. Configuration (environment variables)

| Env var | Property | Default |
|---|---|---|
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | `yt2pod.google.client-id/secret` | — (required) |
| `GOOGLE_REFRESH_TOKEN` | `yt2pod.google.refresh-token` | — (required after setup) |
| `YT2POD_SETUP_ENABLED` | `yt2pod.setup.enabled` | `false` |
| `YT2POD_DRIVE_FOLDER_NAME` | `yt2pod.drive.folder-name` | `yt2pod` |
| `YT2POD_AUDIO_FORMAT` | `yt2pod.audio.format` | `m4a` |
| `YT2POD_AUDIO_BITRATE` | `yt2pod.audio.bitrate` | `64K` |
| `YT2POD_AUDIO_MONO` | `yt2pod.audio.mono` | `true` |
| `YT2POD_JOBS_MAX_CONCURRENT` | `yt2pod.jobs.max-concurrent` | `2` |
| `YT2POD_JOBS_TTL` | `yt2pod.jobs.ttl` | `1h` |
| `YT2POD_LIMITS_MAX_DURATION` | `yt2pod.limits.max-duration` | `3h` |
| `YT2POD_YTDLP_PATH`, `YT2POD_FFMPEG_PATH` | `yt2pod.extractor.*` | `yt-dlp`, `ffmpeg` |
| `YT2POD_EXTRACTOR_TIMEOUT` | `yt2pod.extractor.timeout` | `15m` |

## 9. Deployment

- **Phase 1 — MVP, LAN self-hosted (plain HTTP):** `docker compose` on an always-on computer, port 8080 published to the LAN; clients use `http://<host-ip-or-name>:8080`. Only outbound internet access is needed (Google, YouTube). No domain, certificate or proxy. Run the one-off setup first (§3) with the port bound to `127.0.0.1`, then restart in normal mode with the refresh token set. Give the host a fixed IP or local hostname so the address stays stable. **Never port-forward this service to the internet** (it has no authentication).
- **Phase 2 — Cloud (later, optional):** same image behind HTTPS with access control added (not designed here). YouTube may block datacenter IPs, possibly requiring a cookies/PO-token provider — validate early. In-memory jobs imply a single instance.
- **Possible future HTTPS for LAN:** own domain + Caddy with a Let's Encrypt DNS-01 certificate. Not needed for the MVP.

## 10. Security & privacy

- No client authentication by design (trusted LAN); mitigations: concurrency cap, rate limit, strict URL validation, no shell invocation, limits on duration/size.
- `drive.file` scope only; Google secrets only via environment variables/secret store, never committed or logged. The setup page shows the refresh token once and exists only in setup mode.
- No URL/history persistence; logs avoid URLs and titles; temp files always deleted.

## 11. Risks & open questions

1. **YouTube anti-bot changes** (PO tokens, cookies, JS runtime) can break extraction at any time → keep yt-dlp updatable; isolate behind `AudioExtractor`.
2. **Unauthenticated service on the LAN:** anyone on the network can use it and fill the Drive folder; accepted for the MVP, revisit if the network is shared/untrusted.
3. **Refresh-token lifetime:** requires the OAuth app in *In production* status to avoid 7-day expiry; verify during setup.
4. **Cloud IP blocking** by YouTube.
6. **ToS/legal:** personal use only.

## 12. Suggested milestones

1. Skeleton: Spring Boot project, config properties, health endpoint, Dockerfile skeleton.
2. `YtDlpAudioExtractor` + URL validation (tests with a fake process and a manual real run).
3. One-off Google setup flow + `GoogleDriveUploader` + folder handling.
4. Async job flow + REST API + simple web page.
5. `docker compose` packaging, README.
6. Optional: HTTPS for LAN, cloud trial.

## Sources

- [yt-dlp releases](https://github.com/yt-dlp/yt-dlp/releases) · [JS runtime requirement](https://github.com/yt-dlp/yt-dlp/issues/15012) · [PO token/requirements announcement](https://github.com/yt-dlp/yt-dlp/issues/14404)
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0, JitPack)
- [Drive API Java quickstart](https://developers.google.com/workspace/drive/api/quickstart/java) · [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) · [Drive Java client javadoc](https://googleapis.dev/java/google-api-services-drive/latest/com/google/api/services/drive/Drive.Files.Create.html)
