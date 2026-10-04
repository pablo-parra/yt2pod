# yt2pod — User Stories

**Goal:** send a YouTube link to a web service, which extracts only the audio and saves it as an audio file in a Google Drive folder. The user then manually imports the file into their podcatcher app. The import step is out of scope.

**Actor:** the *Owner* — a single person using the service for personal purposes, identified through a configurable Google account.

Priority: **M** = Must (MVP), **S** = Should, **C** = Could.

---

## Epic 1 — Authentication & authorization

### US-01 Sign in with Google (M)
As the Owner, I want to sign in with my Google account so that the service knows who I am and can access my Google Drive.
- Sign-in uses Google OAuth 2.0 / OpenID Connect.
- Unauthenticated requests to any conversion endpoint are rejected (401 / redirect to login).

### US-02 Configurable allowed account (M)
As the Owner, I want the permitted Google account(s) to be set in configuration, not hard-coded, so that I (or anyone deploying the service) can use a different account without changing code.
- Allowed account(s) are defined by configuration (e.g. an e-mail allowlist in a property / environment variable).
- A valid Google account that is not on the allowlist is denied with a clear message (403).

### US-03 Grant Drive access (M)
As the Owner, I want to authorize the service once to write to my Google Drive so that conversions can be saved without further prompts.
- The consent screen requests the minimum scope needed (only files created by the app).
- The service never accesses other files in the Drive.

### US-04 Sign out / revoke (S)
As the Owner, I want to sign out and forget the granted authorization so that I stay in control of my account access.

---

## Epic 2 — Submit a YouTube link

### US-05 Submit a link through a web page (M)
As the Owner, I want a simple page where I paste a YouTube URL and press a button, so I can convert a video from my phone or computer.
- The page works well on mobile browsers.

### US-06 Submit a link through an API (M)
As the Owner, I want a REST endpoint that accepts a YouTube URL, so I can call it from scripts, share sheets or iOS/Android shortcuts.
- Accepts `youtube.com/watch?v=…`, `youtu.be/…` and `m.youtube.com` forms.
- Returns immediately with a job identifier (the conversion is asynchronous).

### US-07 Link validation (M)
As the Owner, I want invalid or unsupported links to be rejected immediately with a clear error, so I don't wait for a job that cannot succeed.
- Non-YouTube URLs are rejected.
- Playlists, live streams and unavailable/private videos produce an explicit error (playlist support is a future story, US-14).

---

## Epic 3 — Audio extraction & storage

### US-08 Extract audio only (M)
As the Owner, I want only the audio track extracted, so that files are small and suitable for a podcast app.
- No video data is retained after the job finishes.

### US-09 Podcast-friendly audio format (M)
As the Owner, I want the audio saved in a format podcatcher apps play reliably (e.g. M4A/AAC or MP3).
- Default format is configurable.
- Speech-oriented quality by default: 64 kbps mono (configurable, e.g. up to 128 kbps), prioritizing small files over fidelity.

### US-10 Save to a Google Drive folder (M)
As the Owner, I want the audio file saved into a dedicated Google Drive folder so I can find it easily and import it into my podcatcher.
- Folder name/ID is configurable; created automatically if missing.
- Filename is derived from the video title (sanitized); name collisions do not overwrite existing files.

### US-11 Embedded metadata (S)
As the Owner, I want the file tagged with title, channel (as artist/author) and thumbnail (as cover art), so it shows well in the podcast app.

---

## Epic 4 — Feedback & operation

### US-12 Job status (M)
As the Owner, I want to see whether my conversion is queued, running, done or failed, so I know when the file is ready in Drive.
- On success, the status includes the Drive file name and a link to it.
- On failure, a human-readable reason is shown (e.g. video unavailable, Drive error).

### US-13 Resilience to YouTube changes (S)
As the Owner, I want the extraction tool to be easy to update, so the service keeps working when YouTube changes its internals.

### US-14 Playlist / batch support (C)
As the Owner, I want to submit a playlist or several links at once and get one file per video.

### US-15 Duration/size limit (C)
As the Owner, I want a configurable maximum duration to avoid accidentally converting enormous videos.

---

## Epic 5 — Deployment

### US-16 Self-host on a private computer, reachable over the LAN (M)
As the Owner, I want to run the service on my own computer or home server (e.g. with Docker), configured entirely by environment variables, and reach it from other devices on my LAN (phone, laptop).
- No cloud dependency other than Google (login and Drive) and YouTube.
- Client devices need no software installed; a browser (or any HTTP client) is enough.
- Reachable via a stable HTTPS hostname on the LAN (needed for Google login; see tech spec §9).

### US-17 Move to the cloud later (S)
As the Owner, I want the same artifact to be deployable on a cloud host without code changes, so I can reach it from anywhere over HTTPS.

---

## Non-functional requirements

- **No link persistence:** the service does not store submitted URLs, history or video information. Job state is kept in memory only and discarded after completion/expiry. Temporary audio files are deleted after upload (success or failure).
- **Minimal stored data:** the only persisted secret material is what is strictly needed to keep Drive access (OAuth tokens), if applicable; stored securely.
- **Security:** HTTPS in any non-local deployment; CSRF protection on the web page; only allowlisted accounts can use the service.
- **Single-user scale:** a small number of concurrent jobs (configurable) is sufficient.
- **Legal note:** downloading from YouTube may conflict with YouTube's Terms of Service and with the rights of content owners. The service is intended for personal, private use of content the Owner is entitled to listen to.

## Out of scope

- Importing the file into the podcatcher app (done manually by the Owner).
- Generating a podcast RSS feed.
- Storing history of conversions or links.
