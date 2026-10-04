package com.pablo.yt2pod.extract;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class YtDlpAudioExtractor implements AudioExtractor {

	private static final Logger log = LoggerFactory.getLogger(YtDlpAudioExtractor.class);

	private final YoutubeUrlValidator validator;

	private final YtDlpCommandBuilder commandBuilder;

	private final ProcessRunner runner;

	private final Yt2podProperties properties;

	public YtDlpAudioExtractor(YoutubeUrlValidator validator, YtDlpCommandBuilder commandBuilder,
			ProcessRunner runner, Yt2podProperties properties) {
		this.validator = validator;
		this.commandBuilder = commandBuilder;
		this.runner = runner;
		this.properties = properties;
	}

	@Override
	public ExtractedAudio extract(String url, Path outputDir) {
		String canonical = validator.canonicalize(url);
		List<String> command = commandBuilder.build(canonical, outputDir);
		ProcessRunner.Result result;
		try {
			result = runner.run(command, properties.extractor().timeout());
		}
		catch (IOException e) {
			throw new ExtractionException(ExtractionFailure.TOOL_NOT_FOUND);
		}
		catch (ProcessRunner.ProcessTimeoutException e) {
			throw new ExtractionException(ExtractionFailure.TIMEOUT);
		}
		Path audio = findAudioFile(outputDir);
		if (result.exitCode() != 0 || audio == null) {
			// the output can contain the video id/title, so it is only logged at DEBUG
			log.debug("yt-dlp exited with {}: {}", result.exitCode(), result.output());
			throw new ExtractionException(classify(result.output()));
		}
		deleteEverythingExcept(outputDir, audio);
		String fileName = audio.getFileName().toString();
		return new ExtractedAudio(audio, fileName.substring(0, fileName.lastIndexOf('.')));
	}

	static ExtractionFailure classify(String output) {
		String text = output.toLowerCase(Locale.ROOT);
		if (text.contains("does not pass filter")) {
			return ExtractionFailure.LIMIT_EXCEEDED;
		}
		if (text.contains("ffmpeg not found") || text.contains("ffprobe and ffmpeg not found")) {
			return ExtractionFailure.TOOL_NOT_FOUND;
		}
		if (text.contains("private video")) {
			return ExtractionFailure.PRIVATE;
		}
		if (text.contains("confirm your age") || text.contains("age-restricted")) {
			return ExtractionFailure.AGE_RESTRICTED;
		}
		if (text.contains("in your country") || text.contains("geo restrict")) {
			return ExtractionFailure.GEO_BLOCKED;
		}
		if (text.contains("not a bot")) {
			return ExtractionFailure.BOT_CHECK;
		}
		if (text.contains("video unavailable") || text.contains("has been removed")
				|| text.contains("is not available")) {
			return ExtractionFailure.UNAVAILABLE;
		}
		return ExtractionFailure.UNKNOWN;
	}

	private Path findAudioFile(Path dir) {
		String extension = "." + properties.audio().format().toLowerCase(Locale.ROOT);
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(extension))
					.findFirst().orElse(null);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	/** Leaves only the audio file: no video, thumbnail or intermediate files are kept. */
	private void deleteEverythingExcept(Path dir, Path keep) {
		try (Stream<Path> files = Files.list(dir)) {
			for (Path p : files.filter(p -> !p.equals(keep)).toList()) {
				Files.deleteIfExists(p);
			}
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}
