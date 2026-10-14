# yt2pod — Privacy Policy

_Last updated: 2026-10-14_

yt2pod is a self-hosted, personal-use tool. It runs on the owner's own computer; there is no central service operated by the author.

## What the app accesses
- **Google Drive:** with the owner's explicit consent, the app uses the `drive.file` scope, which only lets it create and manage files **it created itself** (the converted audio files and their folder). It cannot see, read or modify any other file in the Google account.

## What the app stores
- The Google OAuth client ID/secret and refresh token, kept only in the environment variables of the owner's own deployment.
- Nothing else: submitted video links, titles and conversion history are not persisted. Job status lives in memory and is discarded after a short time. Temporary audio files are deleted after upload.

## What the app shares
- Nothing is sent to the author or to any third party. The app only talks to YouTube (to fetch the audio) and to Google (to upload the file to the owner's Drive).
- Google user data is not used for advertising, analytics or any purpose other than saving the converted audio to the owner's Drive folder.

## Revoking access
The owner can revoke the app's access at any time at <https://myaccount.google.com/permissions>.

## Contact
Open an issue at <https://github.com/pablo-parra/yt2pod/issues>.
