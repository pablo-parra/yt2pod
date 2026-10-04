package com.pablo.yt2pod;

import java.time.Duration;

import com.pablo.yt2pod.config.Yt2podProperties;

public final class TestProperties {

	private TestProperties() {
	}

	public static Yt2podProperties withGoogle(String clientId, String clientSecret, String refreshToken,
			String folderName) {
		return new Yt2podProperties(new Yt2podProperties.Google(clientId, clientSecret, refreshToken),
				new Yt2podProperties.Setup(false), new Yt2podProperties.Drive(folderName),
				new Yt2podProperties.Audio("m4a", "64K", true), new Yt2podProperties.Jobs(2, Duration.ofHours(1)),
				new Yt2podProperties.Limits(Duration.ofHours(3)),
				new Yt2podProperties.Extractor("yt-dlp", "ffmpeg", Duration.ofMinutes(15)));
	}

	public static Yt2podProperties defaults() {
		return withGoogle("client-id", "client-secret", "refresh-123", "yt2pod");
	}

}
