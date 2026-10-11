# yt2pod — User Stories

**Goal:** send a YouTube link to a web service on my LAN, which extracts only the audio and saves it as an audio file in a Google Drive folder. I then manually import the file into my podcatcher app (out of scope).

**Actors**
- **Owner:** installs and configures the service, and connects it to a Google Drive account (one-off).
- **Client:** any device on the LAN (phone, tablet, computer) that sends a link. Clients need **no login and no installed software**.

Implementation status: all Must and Should stories are implemented; US-13 (playlist/batch) and US-16 (cloud) are not. See [test-traceability.md](test-traceability.md).

Priority: **M** = Must (MVP), **S** = Should, **C** = Could.

---

## Epic 1 — Google Drive connection (Owner, one-off setup)

### US-01 Connect the service to a Google Drive account (M)
As the Owner, I want to authorize the service once to write to a Google Drive account, so conversions can be saved without any later interaction.
- The authorization is done once, on the host machine.
- The resulting credentials are provided to the service through environment variables (Docker).
- The consent requests the minimum scope (only files created by the service).

### US-02 Configurable Drive account (M)
As the Owner, I want the Drive account to be whichever one I authorize, not hard-coded, so anyone deploying the service can use their own account.

### US-03 Automatic token renewal (M)
As the Owner, I want the service to renew Google access tokens by itself, so I never have to re-authorize during normal use.
- If Google revokes the authorization, the failure is reported clearly in the job status and logs.

---

## Epic 2 — Submit a YouTube link (Client)

### US-04 Submit a link through a web page (M)
As a Client, I want a simple page where I paste a YouTube URL and press a button, so I can convert a video from any device without logging in.
- On a phone or tablet the flow is manual: copy the link, open `http://<host>:8081`, paste it and press the button.
- Works well on mobile browsers.

### US-05 Submit a link through an API (M)
As a Client, I want a REST endpoint that accepts a YouTube URL, so I can call it from scripts.
- Accepts `youtube.com/watch?v=…`, `youtu.be/…` and `m.youtube.com` forms.
- Returns immediately with a job identifier (the conversion is asynchronous).

### US-06 Link validation (M)
As a Client, I want invalid or unsupported links to be rejected immediately with a clear error, so I don't wait for a job that cannot succeed.
- Non-YouTube URLs are rejected.
- Playlists, live streams and unavailable/private videos produce an explicit error (playlists: future story US-13).

---

## Epic 3 — Audio extraction & storage

### US-07 Extract audio only (M)
As a Client, I want only the audio extracted, so files are small and suitable for a podcast app.
- No video data is retained after the job finishes.

### US-08 Speech-oriented audio format (M)
As the Owner, I want the audio saved in a small, widely supported format, since the content is spoken word rather than music.
- Default: M4A/AAC, 64 kbps, mono (configurable bitrate, mono/stereo and format, e.g. MP3).
- Quality is deliberately "enough", not the best available.

### US-09 Save to a Google Drive folder (M)
As the Owner, I want the audio saved in a dedicated Drive folder so I can find it and import it into my podcatcher.
- Folder name is configurable; created automatically if missing.
- Filename derives from the video title (sanitized); name collisions never overwrite existing files.

### US-10 Embedded metadata (S)
As the Owner, I want the file tagged with title, channel (as artist) and thumbnail (as cover art), so it displays well in the podcast app.

---

## Epic 4 — Feedback & operation

### US-11 Job status (M)
As a Client, I want to see whether my conversion is queued, running, done or failed, so I know when the file is in Drive.
- The page shows the phases as a checklist (waiting in the queue, downloading and converting the audio, saving to Google Drive): a spinner next to the running phase, a green tick when it completes, a red cross if it fails, and a final message when everything is done.
- On success: Drive file name and a link.
- On failure: a human-readable reason (video unavailable, Drive error, authorization revoked…).

### US-12 Resilience to YouTube changes (S)
As the Owner, I want the extraction tool to be easy to update, so the service keeps working when YouTube changes.

### US-13 Playlist / batch support (C)
As a Client, I want to submit a playlist or several links and get one file per video.

### US-14 Duration/size limit (C)
As the Owner, I want a configurable maximum duration/size to avoid converting huge videos by accident.

---

## Epic 5 — Deployment

### US-15 Self-host on a private computer, available on the LAN (M)
As the Owner, I want to run the service on my own computer or home server with Docker, configured entirely by environment variables, and reach it from any LAN device over `http://<host>:<port>`.
- No cloud dependency other than Google Drive and YouTube.
- No domain, certificate or reverse proxy required.

### US-16 Move to the cloud later (C)
As the Owner, I want the same image to be deployable on a cloud host later. Cloud exposure would require adding access control and HTTPS (not part of the MVP).

---

## Non-functional requirements

- **No link persistence:** the service does not store submitted URLs, history or video information. Job state is in memory only and discarded after completion/expiry. Temporary audio files are deleted after upload (success or failure).
- **Only persisted secret:** the Google credentials supplied through environment variables (client ID/secret, refresh token). Nothing else is stored.
- **Simple access model (MVP):** no authentication for Clients; the service is meant for a trusted private LAN and must **not** be exposed to the internet (no port forwarding). A concurrency cap and basic rate limit protect the host.
- **Small scale:** a few concurrent jobs (configurable) are enough.
- **Legal note:** downloading from YouTube may conflict with YouTube's Terms of Service and content owners' rights. Intended for personal, private use only.

## Out of scope

- Importing the file into the podcatcher (manual).
- Generating a podcast RSS feed.
- Storing history of conversions or links.
- "Share" menu integration, phone shortcuts and other extra client-side tooling.
- Client authentication, HTTPS and domain setup (future, only if exposed beyond the LAN).
