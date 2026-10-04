# yt2pod — Technical Specification

Status: draft v0.1 · Covers user stories in [user-stories.md](user-stories.md)

## 1. Summary

A Spring Boot (Java) web service. The Owner signs in with Google, submits a YouTube URL, and the backend downloads **only the audio**, converts it to a podcast-friendly format, uploads it to a Google Drive folder, and discards the temporary file. No URLs or history are persisted.

```
Browser / script ──► Spring Boot ──► yt-dlp + ffmpeg (local process) ──► temp audio file
        ▲  Google login     │                                                  │
        └──── job status ◄──┘                              Google Drive API ◄───┘ (upload, then delete temp)
```

## 2. Research: extracting audio from YouTube

| Option | Type | Pros | Cons |
|---|---|---|---|
| **yt-dlp** (+ ffmpeg) | CLI tool run as a subprocess | Most actively maintained; fastest to react to YouTube changes (latest release seen: 2026.08.19); can extract audio, convert (`-x --audio-format`), embed metadata and thumbnail; handles PO tokens/signature logic | Not a Java library (needs Python-free standalone binary, ffmpeg, and a JS runtime for full YouTube support); subprocess management |
| **NewPipeExtractor** (Java, JitPack) | Pure Java library | Native Java API, no external binary for resolving stream URLs | **GPL-3.0** (affects distribution of the project); only returns stream URLs, so downloading, transcoding and tagging are still our job; YouTube breakages are fixed by library releases, typically slower than yt-dlp |
| Java wrappers around yt-dlp (e.g. `ytdlp-java`-style libs) | Thin Java wrapper over the CLI | Less boilerplate | Small, lightly maintained projects; add a dependency for what `ProcessBuilder` does in ~50 lines |
| Other small Java YouTube extractors (e.g. cipher-parsing GitHub projects) | Pure Java | — | Typically abandoned, break often; not recommended |
| Official YouTube Data API | API | Stable | Does **not** provide media downloads; unusable for this goal |

**Recommendation:** use **yt-dlp invoked via `ProcessBuilder`**, with **ffmpeg** for conversion. Wrap it behind an interface (`AudioExtractor`) so a NewPipeExtractor implementation could be swapped in later if desired.

Notes on yt-dlp requirements (verify against the current yt-dlp README at implementation time):
- Since late 2025 yt-dlp needs an external **JavaScript runtime** (Deno recommended, ≥ 2.0) for full YouTube support. The official standalone executables bundle the solver component (`yt-dlp-ejs`); the runtime itself must still be available.
- YouTube may require **PO tokens** or cookies for some videos/IPs; datacenter (cloud) IPs are more likely to be blocked than home connections. This is the main risk for US-17 (cloud) and is why the home server is the first target.
- Keep yt-dlp updatable (`yt-dlp -U` or image rebuild) — US-13.

Legal: downloading from YouTube may violate its Terms of Service; keep the service private and for personal use.

## 3. Research: Google authentication and Drive

- **Login:** Spring Security **OAuth2 Client** with the `google` provider (OpenID Connect, scopes `openid email profile`).
- **Authorization of the account:** property `yt2pod.auth.allowed-emails` (comma-separated, via env var `YT2POD_AUTH_ALLOWED_EMAILS`). A custom `OidcUser` check rejects any other account and requires `email_verified=true`. This satisfies "account configurable, not fixed".
- **Drive access:** request the extra scope `https://www.googleapis.com/auth/drive.file` (least privilege: the app only sees files it created). Because the app creates the target folder itself, `drive.file` is sufficient.
- **Client library:** `com.google.apis:google-api-services-drive` (v3; latest seen `v3-rev20260913-2.0.0`) with `google-api-client` and `google-auth-library-oauth2-http` (`AccessToken`/`GoogleCredentials` built from the user's Spring-managed access token).
- **Upload:** `Drive.Files.create` with `InputStreamContent` and **resumable upload** enabled for large files.
- **Token lifetime:** the Google access token lasts ~1 hour, which covers a normal job if it runs inside the signed-in session. If background/offline use is wanted later (e.g. an API token for iOS Shortcuts), request `access_type=offline` and persist the refresh token (encrypted file or small store). This is the only data persisted; MVP can skip it.
- **Google Cloud setup (one-off):** create a project, enable the Drive API, configure the OAuth consent screen (External, *Testing* mode with the Owner as a test user is enough for personal use; note refresh tokens in Testing mode expire after 7 days), create an OAuth client ID (Web) with redirect URI `{baseUrl}/login/oauth2/code/google`.

## 4. Technology stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 (LTS) |
| Framework | Spring Boot 3.x (latest stable 3.x at project start), Maven |
| Web | Spring MVC + a single static HTML page (vanilla JS) or Thymeleaf |
| Security | spring-boot-starter-oauth2-client, spring-boot-starter-security |
| Async jobs | `@Async` / `ThreadPoolTaskExecutor` (bounded, configurable concurrency); in-memory job registry (`ConcurrentHashMap`) with TTL eviction |
| Extraction | yt-dlp + ffmpeg via `ProcessBuilder` |
| Drive | Google API Client Java libraries (above) |
| Config | `application.yml` + environment variables |
| Packaging | Docker image (JRE 21 + ffmpeg + yt-dlp + Deno) |
| Observability | Spring Boot Actuator (health), SLF4J logging with **no URL/title logging beyond job id at INFO** |
| Tests | JUnit 5, Mockito, Spring Boot Test, MockMvc; WireMock/fake `AudioExtractor` and `DriveUploader` for integration tests |

## 5. Architecture

Packages (`com.pablo.yt2pod`):
- `web` — `ConvertController`, `JobController`, error handling
- `security` — `SecurityConfig`, allowlist `OidcUserService`
- `job` — `ConversionJob`, `JobService`, `JobRegistry` (in-memory, TTL)
- `extract` — `AudioExtractor` (interface), `YtDlpAudioExtractor`, `YoutubeUrlValidator`
- `storage` — `DriveUploader` (interface), `GoogleDriveUploader`, folder resolution
- `config` — `@ConfigurationProperties` classes

Flow:
1. `POST /api/conversions {url}` → validate URL → create job (`QUEUED`) → return `202` with `{jobId}`.
2. Async worker: `RUNNING_EXTRACT` → run yt-dlp into a per-job temp dir → `RUNNING_UPLOAD` → upload to Drive folder → `DONE` (stores Drive file name + webViewLink) or `FAILED` (reason).
3. `finally`: delete temp dir. The URL is not stored on the job object after extraction starts.
4. `GET /api/conversions/{jobId}` → status. Jobs are owned by the user's email and evicted after a TTL (default 1 h).

## 6. API

| Method | Path | Description |
|---|---|---|
| GET | `/` | Web page: URL input + status list |
| POST | `/api/conversions` | Body `{ "url": "..." }` → `202 {jobId}`; `400` invalid URL; `401/403` auth |
| GET | `/api/conversions/{jobId}` | `{status, fileName?, driveLink?, error?}` |
| GET | `/actuator/health` | Health |

Job states: `QUEUED → EXTRACTING → UPLOADING → DONE | FAILED`.

## 7. yt-dlp invocation

```
yt-dlp --no-playlist -f "bestaudio/best" -x --audio-format m4a --audio-quality 64K \
       --postprocessor-args "ffmpeg:-ac 1" \
       --embed-metadata --embed-thumbnail --restrict-filenames \
       --max-filesize <cfg> -o "<tmpdir>/%(title).150B.%(ext)s" -- <url>
```
- Arguments passed as a list (no shell) and the URL after `--` to prevent option/command injection; the URL must pass `YoutubeUrlValidator` first (host allowlist: `youtube.com`, `www.`, `m.`, `youtu.be`, `music.youtube.com`).
- **Quality: speech-oriented, not best.** Default is **64 kbps mono** AAC/M4A (≈ 29 MB per hour; 192 kbps would be ≈ 86 MB/hour). 192 kbps is more than speech needs, and YouTube's own AAC audio is typically ~128 kbps, so 192 would only inflate the file without adding quality. Configurable via `yt2pod.audio.bitrate` (e.g. `96K` for stereo/music-like content) and `yt2pod.audio.mono`. Because the target is lower than the source, ffmpeg always re-encodes (small CPU cost).
- `m4a` default (AAC); `mp3` selectable via `yt2pod.audio.format`.
- Timeout and output capture (stderr parsed to map errors: unavailable, private, age-restricted, geo-blocked → user-friendly messages).
- Title/metadata is read from yt-dlp's output file name; Drive file name collisions are resolved by suffixing.

## 8. Configuration (env-driven)

| Property | Env var | Default |
|---|---|---|
| `spring.security.oauth2.client.registration.google.client-id/secret` | `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | — |
| `yt2pod.auth.allowed-emails` | `YT2POD_AUTH_ALLOWED_EMAILS` | — (required) |
| `yt2pod.drive.folder-name` | `YT2POD_DRIVE_FOLDER_NAME` | `yt2pod` |
| `yt2pod.audio.format` | `YT2POD_AUDIO_FORMAT` | `m4a` |
| `yt2pod.audio.bitrate` | `YT2POD_AUDIO_BITRATE` | `64K` |
| `yt2pod.audio.mono` | `YT2POD_AUDIO_MONO` | `true` |
| `yt2pod.extractor.yt-dlp-path` / `ffmpeg-path` | | `yt-dlp` / `ffmpeg` |
| `yt2pod.jobs.max-concurrent` | | `2` |
| `yt2pod.jobs.ttl` | | `1h` |
| `yt2pod.limits.max-duration` | | `3h` |

## 9. Deployment

- **Self-hosted on a private computer, LAN access (first target):** fully compatible with the requirements; nothing needs a public server. Run with `docker compose` (or plain JAR) on the always-on machine and open it from phones/laptops on the LAN. One constraint to plan for: **Google login needs an HTTPS redirect URI with a real domain name**; Google only allows plain `http` for `localhost`, and (to my knowledge) rejects raw private IPs such as `192.168.x.x` and `.local` names for web OAuth clients. Options, simplest first:
  1. **Tailscale** (free for personal use): the machine gets an HTTPS name via `tailscale cert`/`serve`; reachable from the LAN and from outside, no port opening.
  2. **Own domain** (a cheap one, or a subdomain) whose DNS A record points to the LAN IP (or local DNS override), with a Let's Encrypt certificate obtained through the DNS-01 challenge (no inbound exposure), served by a reverse proxy (Caddy/Traefik) in front of Spring Boot.
  3. **Fallback for the machine itself only:** `http://localhost:8080` works without HTTPS.
  The service itself stays HTTP internally; the proxy terminates TLS (`server.forward-headers-strategy=framework`). Only outbound internet access is required (Google, YouTube).
- **Cloud (later):** same image on any container host (VPS, Cloud Run, Fly.io, etc.), behind HTTPS (`server.forward-headers-strategy=framework`). Caveats: temp disk is ephemeral (fine, files are transient); in-memory jobs require a single instance (or sticky sessions); **YouTube may block datacenter IPs**, possibly requiring cookies/PO-token provider — to be validated early.

## 10. Security & privacy

- Only allowlisted, verified Google accounts; all endpoints authenticated; CSRF enabled for the web page.
- `drive.file` scope only; secrets only via env vars/secret store, never committed.
- No URL/history persistence; logs avoid URLs and titles; temp files always deleted.
- Rate limit per user (simple in-memory) to protect the host.

## 11. Risks & open questions

1. **YouTube anti-bot changes** (PO tokens, cookies, JS runtime) can break extraction at any time → keep yt-dlp updatable; isolate behind `AudioExtractor`.
2. **Cloud IP blocking** → validate from a cloud host before committing to the cloud stage.
3. **Google OAuth app in *Testing* mode** → refresh tokens expire in 7 days; acceptable for the MVP since login is interactive.
4. **Background/API access without browser session** (e.g. iOS Shortcut) → needs a persisted refresh token or a personal API token; decide after MVP.
5. **ToS/legal** → personal use only.
6. Output format preference (and bitrate: 64K mono assumed enough for speech): M4A vs MP3 (confirm podcatcher import behavior).

## 12. Suggested milestones

1. Skeleton: Spring Boot project, Google login + allowlist, health.
2. `YtDlpAudioExtractor` + URL validation (CLI-level tests).
3. `GoogleDriveUploader` + folder handling.
4. Async job flow + REST API + simple web page.
5. Dockerfile, docs, home-server deployment.
6. Cloud trial and hardening.

## Sources

- [yt-dlp releases](https://github.com/yt-dlp/yt-dlp/releases) · [JS runtime requirement](https://github.com/yt-dlp/yt-dlp/issues/15012) · [PO token/requirements announcement](https://github.com/yt-dlp/yt-dlp/issues/14404)
- [NewPipeExtractor](https://github.com/TeamNewPipe/NewPipeExtractor) (GPL-3.0, JitPack)
- [Drive API Java quickstart](https://developers.google.com/workspace/drive/api/quickstart/java) · [Drive scopes](https://developers.google.com/workspace/drive/api/guides/api-specific-auth) · [Drive Java client javadoc](https://googleapis.dev/java/google-api-services-drive/latest/com/google/api/services/drive/Drive.Files.Create.html)
