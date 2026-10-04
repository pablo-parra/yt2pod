package com.pablo.yt2pod.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "yt2pod")
public record Yt2podProperties(
        @Valid @DefaultValue Google google,
        @Valid @DefaultValue Setup setup,
        @Valid @DefaultValue Drive drive,
        @Valid @DefaultValue Audio audio,
        @Valid @DefaultValue Jobs jobs,
        @Valid @DefaultValue Limits limits,
        @Valid @DefaultValue Extractor extractor) {

    /** Google OAuth credentials. All optional at startup; required once the Drive upload is used. */
    public record Google(String clientId, String clientSecret, String refreshToken) {
    }

    /** One-off Google authorization flow; keep disabled in normal operation. */
    public record Setup(@DefaultValue("false") boolean enabled) {
    }

    public record Drive(@NotBlank @DefaultValue("yt2pod") String folderName) {
    }

    public record Audio(
            @NotBlank @DefaultValue("m4a") String format,
            @NotBlank @DefaultValue("64K") String bitrate,
            @DefaultValue("true") boolean mono) {
    }

    public record Jobs(
            @Min(1) @DefaultValue("2") int maxConcurrent,
            @NotNull @DefaultValue("1h") Duration ttl) {
    }

    public record Limits(@NotNull @DefaultValue("3h") Duration maxDuration) {
    }

    public record Extractor(
            @NotBlank @DefaultValue("yt-dlp") String ytDlpPath,
            @NotBlank @DefaultValue("ffmpeg") String ffmpegPath) {
    }
}
