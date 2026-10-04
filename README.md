# yt2pod

Self-hosted service that extracts the audio of a YouTube video and saves it to a Google Drive folder, ready to be imported into a podcatcher. Meant for a private LAN, personal use.

- Docs: [user stories](docs/user-stories.md) · [tech spec](docs/tech-spec.md)
- Stack: Java 21, Spring Boot 4.1, Maven, yt-dlp + ffmpeg

## Development

```bash
./mvnw test
./mvnw spring-boot:run     # http://localhost:8080/actuator/health
```

## Docker

```bash
cp .env.example .env       # fill in values
docker compose up --build
```

## Status

Done: skeleton, yt-dlp extractor, Google setup and Drive upload, job API and web page. Next: Docker build and a real end-to-end run with Google credentials.
