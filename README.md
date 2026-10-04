# yt2pod

Self-hosted service that extracts the **audio** of a YouTube video and saves it to a **Google Drive** folder, ready to be imported into a podcatcher app. For personal use on a private network.

1. Open `http://<server>:8080` from any device on your LAN (no login, nothing to install).
2. Paste a YouTube link and press **Convert to audio**.
3. The file (M4A, 64 kbps mono by default) appears in the `yt2pod` folder of your Drive; import it into your podcatcher manually.

- Docs: [user stories](docs/user-stories.md) · [tech spec](docs/tech-spec.md) · [test traceability](docs/test-traceability.md)
- Stack: Java 21, Spring Boot 4.1, Maven, yt-dlp + ffmpeg (+ Deno) · Docker

## Quick start (Docker)

Requirements: Docker. The image already contains yt-dlp, ffmpeg and Deno.

### 1. One-off Google setup

The service writes to Drive with an OAuth refresh token that you create once.

1. In [Google Cloud Console](https://console.cloud.google.com) create a project (free, no billing needed) and enable the **Google Drive API**.
2. **Google Auth Platform** → configure the consent screen: audience **External**, add your Google account under **Test users**. Optionally **publish the app** ("In production") so the refresh token does not expire after 7 days.
3. **Clients → Create client**: type **Web application**, authorized redirect URI exactly `http://localhost:8080/setup/google/callback`. Copy the client ID and secret.
4. `cp .env.example .env` and set `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET` (never commit `.env`).
5. Run the setup mode on the machine where you will open the browser:
   ```bash
   set -a && source .env && set +a && YT2POD_SETUP_ENABLED=true ./mvnw spring-boot:run
   ```
   Open `http://localhost:8080/setup/google` (it must be `localhost`), sign in, accept the "unverified app" warning (**Advanced → Go to yt2pod**) and approve. Copy the refresh token shown (only once) into `GOOGLE_REFRESH_TOKEN` in `.env`, then stop the app with Ctrl+C.

### 2. Run

```bash
docker compose up -d --build
curl localhost:8080/actuator/health
```

Open `http://localhost:8080`. To use it from a phone or tablet on the same Wi-Fi, open `http://<server-LAN-ip>:8080` (e.g. `ipconfig getifaddr en0` on macOS). Give the server a fixed IP in your router so the address does not change.

Update yt-dlp (YouTube changes often) by rebuilding the image: `docker compose build --no-cache && docker compose up -d`.

**Security:** clients are not authenticated, so keep the service on a trusted LAN and **never port-forward it to the internet**.

## Configuration

All settings are environment variables (put them in `.env`).

| Variable | Default | Meaning |
|---|---|---|
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | — | OAuth client of your Google Cloud project |
| `GOOGLE_REFRESH_TOKEN` | — | Obtained once in the setup (see above) |
| `YT2POD_SETUP_ENABLED` | `false` | Enables `/setup/google`; only for the one-off setup |
| `YT2POD_DRIVE_FOLDER_NAME` | `yt2pod` | Drive folder (created if missing) |
| `YT2POD_AUDIO_FORMAT` | `m4a` | e.g. `mp3` |
| `YT2POD_AUDIO_BITRATE` | `64K` | Speech-oriented; use `96K`/`128K` for music |
| `YT2POD_AUDIO_MONO` | `true` | |
| `YT2POD_JOBS_MAX_CONCURRENT` | `2` | Parallel conversions (more than 5x this many unfinished jobs are refused with 429) |
| `YT2POD_JOBS_TTL` | `1h` | How long finished jobs are remembered |
| `YT2POD_LIMITS_MAX_DURATION` | `3h` | Longer videos and live streams are refused |
| `YT2POD_EXTRACTOR_TIMEOUT` | `15m` | Per-conversion timeout |
| `YT2POD_YTDLP_PATH`, `YT2POD_FFMPEG_PATH` | `yt-dlp`, `ffmpeg` | Executables (names are looked up in PATH) |

## API

```bash
curl -X POST localhost:8080/api/conversions -H 'Content-Type: application/json' \
     -d '{"url":"https://youtu.be/<id>"}'          # 202 {"jobId":"..."}
curl localhost:8080/api/conversions/<jobId>          # {"status":"DONE","fileName":"...","driveLink":"..."}
```

Statuses: `QUEUED`, `EXTRACTING`, `UPLOADING`, `DONE`, `FAILED` (with an `error` message). Accepted links: single videos on `youtube.com`, `m.youtube.com`, `music.youtube.com`, `youtu.be`, and Shorts. Playlists and channels are rejected.

## Troubleshooting

- **"Drive authorization expired"** (health `DOWN`): the refresh token was revoked or expired (Google *Testing* apps expire it after 7 days); repeat step 5 of the setup.
- **"Google Drive is not configured"**: `GOOGLE_REFRESH_TOKEN` is empty.
- **"The audio could not be extracted"**: set the log level to see yt-dlp's output (`LOGGING_LEVEL_COM_PABLO_YT2POD_EXTRACT=DEBUG`; the output may contain video titles) and try updating yt-dlp.
- **Page not reachable from the phone**: same Wi-Fi? Mac firewall allowing Docker? Router client isolation off?
- **`redirect_uri_mismatch` / `access_denied` during setup**: the redirect URI must be exactly the one above; your account must be listed as a test user.

## Development

```bash
./mvnw test                # unit and integration tests, named by user story
./mvnw spring-boot:run     # needs yt-dlp, ffmpeg and Deno installed locally for real conversions
```

## Status

MVP complete and verified end to end (Docker on macOS, conversion from a phone over the LAN). Not implemented: playlist/batch conversion, cloud deployment (see the tech spec).
