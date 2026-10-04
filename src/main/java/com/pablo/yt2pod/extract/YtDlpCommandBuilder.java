package com.pablo.yt2pod.extract;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.springframework.stereotype.Component;

@Component
public class YtDlpCommandBuilder {

	private final Yt2podProperties properties;

	public YtDlpCommandBuilder(Yt2podProperties properties) {
		this.properties = properties;
	}

	/** Builds the argument list (never a shell string); {@code canonicalUrl} must come from the validator. */
	public List<String> build(String canonicalUrl, Path outputDir) {
		Yt2podProperties.Audio audio = properties.audio();
		List<String> cmd = new ArrayList<>();
		cmd.add(properties.extractor().ytDlpPath());
		String ffmpegPath = properties.extractor().ffmpegPath();
		if (ffmpegPath.contains("/")) {
			// yt-dlp treats this value as a path, so a bare "ffmpeg" must be left to the PATH lookup
			cmd.add("--ffmpeg-location");
			cmd.add(ffmpegPath);
		}
		cmd.add("--no-playlist");
		cmd.add("-f");
		cmd.add("bestaudio/best");
		cmd.add("-x");
		cmd.add("--audio-format");
		cmd.add(audio.format());
		cmd.add("--audio-quality");
		cmd.add(audio.bitrate());
		if (audio.mono()) {
			cmd.add("--postprocessor-args");
			cmd.add("ffmpeg:-ac 1");
		}
		cmd.add("--embed-metadata");
		cmd.add("--embed-thumbnail");
		cmd.add("--match-filters");
		cmd.add("!is_live & duration<=" + properties.limits().maxDuration().toSeconds());
		cmd.add("--no-progress");
		cmd.add("-o");
		cmd.add(outputDir.resolve("%(title).150B.%(ext)s").toString());
		cmd.add("--");
		cmd.add(canonicalUrl);
		return cmd;
	}

}
