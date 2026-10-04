# syntax=docker/dockerfile:1
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q -B dependency:go-offline
COPY src src
RUN ./mvnw -q -B -DskipTests package && mv target/yt2pod-*.jar /app.jar

FROM eclipse-temurin:21-jre
ARG TARGETARCH
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg ca-certificates curl \
    && rm -rf /var/lib/apt/lists/* \
    && case "$TARGETARCH" in arm64) YT=yt-dlp_linux_aarch64 ;; *) YT=yt-dlp_linux ;; esac \
    && curl -fsSL "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$YT" -o /usr/local/bin/yt-dlp \
    && chmod +x /usr/local/bin/yt-dlp
# JavaScript runtime required by yt-dlp for full YouTube support
COPY --from=denoland/deno:bin /deno /usr/local/bin/deno
RUN useradd --system --create-home yt2pod
USER yt2pod
COPY --from=build /app.jar /app/yt2pod.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/yt2pod.jar"]
