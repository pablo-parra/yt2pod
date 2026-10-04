package com.pablo.yt2pod.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Extraction behaviour with a fake process runner: US-06, US-07, US-11 (failure reasons), US-12, US-14. */
class YtDlpAudioExtractorTest {

	private static final String URL = "https://youtu.be/dQw4w9WgXcQ";

	@TempDir
	Path dir;

	private final Yt2podProperties props = new Yt2podProperties(new Yt2podProperties.Google(null, null, null),
			new Yt2podProperties.Setup(false), new Yt2podProperties.Drive("yt2pod"),
			new Yt2podProperties.Audio("m4a", "64K", true), new Yt2podProperties.Jobs(2, Duration.ofHours(1)),
			new Yt2podProperties.Limits(Duration.ofHours(3)),
			new Yt2podProperties.Extractor("yt-dlp", "ffmpeg", Duration.ofMinutes(15)));

	private YtDlpAudioExtractor extractor(ProcessRunner runner) {
		return new YtDlpAudioExtractor(new YoutubeUrlValidator(), new YtDlpCommandBuilder(props), runner, props);
	}

	private static ProcessRunner producing(Path dir, int exitCode, String output, String... fileNames) {
		return (command, timeout) -> {
			for (String name : fileNames) {
				Files.writeString(dir.resolve(name), "data");
			}
			return new ProcessRunner.Result(exitCode, output);
		};
	}

	@Test
	@DisplayName("US-07 returns the extracted audio file and its title")
	void US07_returnsAudioFileAndTitle() {
		ExtractedAudio audio = extractor(producing(dir, 0, "", "My Podcast Episode.m4a")).extract(URL, dir);
		assertThat(audio.file()).isEqualTo(dir.resolve("My Podcast Episode.m4a")).exists();
		assertThat(audio.title()).isEqualTo("My Podcast Episode");
	}

	@Test
	@DisplayName("US-07 keeps only the audio file: thumbnails and other leftovers are deleted")
	void US07_noVideoOrIntermediateFilesRemain() throws IOException {
		extractor(producing(dir, 0, "", "Episode.m4a", "Episode.webp", "Episode.webm", "Episode.temp.m4a.part"))
				.extract(URL, dir);
		try (Stream<Path> files = Files.list(dir)) {
			assertThat(files.map(p -> p.getFileName().toString())).containsExactly("Episode.m4a");
		}
	}

	@Test
	@DisplayName("US-07 the canonical url (not the raw input) is what gets executed")
	void US07_executesCanonicalUrl() {
		List<String>[] captured = new List[1];
		ProcessRunner runner = (command, timeout) -> {
			captured[0] = command;
			Files.writeString(dir.resolve("Episode.m4a"), "data");
			return new ProcessRunner.Result(0, "");
		};
		extractor(runner).extract("https://youtu.be/dQw4w9WgXcQ?si=tracking&x=--exec", dir);
		assertThat(captured[0]).endsWith("--", "https://www.youtube.com/watch?v=dQw4w9WgXcQ");
	}

	@Test
	@DisplayName("US-06 an invalid link is rejected before any process is started")
	void US06_invalidUrlNeverStartsAProcess() {
		ProcessRunner runner = (command, timeout) -> {
			throw new AssertionError("process must not be started");
		};
		assertThatThrownBy(() -> extractor(runner).extract("https://vimeo.com/1", dir))
				.isInstanceOf(InvalidYoutubeUrlException.class);
	}

	@ParameterizedTest(name = "US-11 \"{0}\" is reported as {1}")
	@CsvSource(delimiter = '|', textBlock = """
			ERROR: [youtube] abc: Private video. Sign in if you've been granted access | PRIVATE
			ERROR: [youtube] abc: Sign in to confirm your age | AGE_RESTRICTED
			ERROR: [youtube] abc: The uploader has not made this video available in your country | GEO_BLOCKED
			ERROR: [youtube] abc: Video unavailable | UNAVAILABLE
			ERROR: [youtube] abc: This video has been removed by the uploader | UNAVAILABLE
			ERROR: [youtube] abc: Sign in to confirm you're not a bot | BOT_CHECK
			something completely different | UNKNOWN
			""")
	@DisplayName("US-11 failures are mapped to a human-readable reason")
	void US11_mapsYtDlpErrorsToReasons(String output, ExtractionFailure expected) {
		assertThatThrownBy(() -> extractor(producing(dir, 1, output)).extract(URL, dir))
				.isInstanceOfSatisfying(ExtractionException.class, e -> {
					assertThat(e.failure()).isEqualTo(expected);
					assertThat(e.getMessage()).isNotBlank();
				});
	}

	@Test
	@DisplayName("US-14 a video rejected by the duration/live filter is reported as limit exceeded")
	void US14_filteredVideoIsReportedAsLimitExceeded() {
		// yt-dlp exits 0 without producing a file when --match-filters rejects the video
		String output = "[download] abc does not pass filter (!is_live & duration<=10800), skipping ..";
		assertThatThrownBy(() -> extractor(producing(dir, 0, output)).extract(URL, dir))
				.isInstanceOfSatisfying(ExtractionException.class,
						e -> assertThat(e.failure()).isEqualTo(ExtractionFailure.LIMIT_EXCEEDED));
	}

	@Test
	@DisplayName("US-11 a successful exit without an audio file is still a failure")
	void US11_noOutputFileIsAFailure() {
		assertThatThrownBy(() -> extractor(producing(dir, 0, "")).extract(URL, dir))
				.isInstanceOfSatisfying(ExtractionException.class,
						e -> assertThat(e.failure()).isEqualTo(ExtractionFailure.UNKNOWN));
	}

	@Test
	@DisplayName("US-11 a missing yt-dlp/ffmpeg executable is reported as tool not found")
	void US11_toolNotFound() {
		ProcessRunner runner = (command, timeout) -> {
			throw new IOException("No such file");
		};
		assertThatThrownBy(() -> extractor(runner).extract(URL, dir)).isInstanceOfSatisfying(ExtractionException.class,
				e -> assertThat(e.failure()).isEqualTo(ExtractionFailure.TOOL_NOT_FOUND));
	}

	@Test
	@DisplayName("US-11 a hung extraction is cancelled and reported as timeout")
	void US11_timeout() {
		ProcessRunner runner = (command, timeout) -> {
			throw new ProcessRunner.ProcessTimeoutException();
		};
		assertThatThrownBy(() -> extractor(runner).extract(URL, dir)).isInstanceOfSatisfying(ExtractionException.class,
				e -> assertThat(e.failure()).isEqualTo(ExtractionFailure.TIMEOUT));
	}

	@Test
	@DisplayName("US-12 the configured timeout is passed to the process runner")
	void US12_usesConfiguredTimeout() {
		Duration[] seen = new Duration[1];
		ProcessRunner runner = (command, timeout) -> {
			seen[0] = timeout;
			Files.writeString(dir.resolve("Episode.m4a"), "data");
			return new ProcessRunner.Result(0, "");
		};
		extractor(runner).extract(URL, dir);
		assertThat(seen[0]).isEqualTo(Duration.ofMinutes(15));
	}

}
