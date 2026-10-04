package com.pablo.yt2pod.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Builds the yt-dlp command line: US-07, US-08, US-10, US-12, US-13, US-14. */
class YtDlpCommandBuilderTest {

	private static final String URL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

	private static final Path DIR = Path.of("/tmp/job-1");

	private static Yt2podProperties props(String format, String bitrate, boolean mono, Duration maxDuration,
			String ytDlp) {
		return new Yt2podProperties(new Yt2podProperties.Google(null, null, null), new Yt2podProperties.Setup(false),
				new Yt2podProperties.Drive("yt2pod"), new Yt2podProperties.Audio(format, bitrate, mono),
				new Yt2podProperties.Jobs(2, Duration.ofHours(1)), new Yt2podProperties.Limits(maxDuration),
				new Yt2podProperties.Extractor(ytDlp, "ffmpeg", Duration.ofMinutes(15)));
	}

	private static List<String> build(Yt2podProperties p) {
		return new YtDlpCommandBuilder(p).build(URL, DIR);
	}

	private static List<String> defaults() {
		return build(props("m4a", "64K", true, Duration.ofHours(3), "yt-dlp"));
	}

	@Test
	@DisplayName("US-07 extracts only the audio (-x, audio-only format selection)")
	void US07_requestsAudioOnly() {
		List<String> cmd = defaults();
		assertThat(cmd).contains("-x");
		assertThat(cmd).containsSequence("-f", "bestaudio/best");
	}

	@Test
	@DisplayName("US-08 default output is M4A, 64 kbps, mono")
	void US08_defaultsAreSpeechOriented() {
		List<String> cmd = defaults();
		assertThat(cmd).containsSequence("--audio-format", "m4a");
		assertThat(cmd).containsSequence("--audio-quality", "64K");
		assertThat(cmd).containsSequence("--postprocessor-args", "ffmpeg:-ac 1");
	}

	@Test
	@DisplayName("US-08 format, bitrate and mono/stereo are configurable")
	void US08_audioSettingsAreConfigurable() {
		List<String> cmd = build(props("mp3", "128K", false, Duration.ofHours(3), "yt-dlp"));
		assertThat(cmd).containsSequence("--audio-format", "mp3");
		assertThat(cmd).containsSequence("--audio-quality", "128K");
		assertThat(cmd).doesNotContain("--postprocessor-args");
	}

	@Test
	@DisplayName("US-10 embeds title/channel metadata and the thumbnail as cover art")
	void US10_embedsMetadataAndThumbnail() {
		assertThat(defaults()).contains("--embed-metadata", "--embed-thumbnail");
	}

	@Test
	@DisplayName("US-12 the yt-dlp executable is configurable so it can be updated or replaced")
	void US12_ytDlpPathIsConfigurable() {
		assertThat(build(props("m4a", "64K", true, Duration.ofHours(3), "/opt/yt-dlp-nightly")).get(0))
				.isEqualTo("/opt/yt-dlp-nightly");
	}

	@Test
	@DisplayName("US-13 never downloads playlists")
	void US13_noPlaylist() {
		assertThat(defaults()).contains("--no-playlist");
	}

	@Test
	@DisplayName("US-14 skips live streams and videos longer than the configured maximum duration")
	void US14_appliesDurationLimit() {
		assertThat(build(props("m4a", "64K", true, Duration.ofMinutes(90), "yt-dlp")))
				.containsSequence("--match-filters", "!is_live & duration<=5400");
	}

	@Test
	@DisplayName("US-07 writes into the job directory and passes the url as a separate argument after '--'")
	void US07_urlIsPassedAsAnArgumentAfterDoubleDash() {
		List<String> cmd = defaults();
		assertThat(cmd.get(cmd.size() - 2)).isEqualTo("--");
		assertThat(cmd.get(cmd.size() - 1)).isEqualTo(URL);
		assertThat(cmd.get(cmd.indexOf("-o") + 1)).startsWith(DIR.toString());
	}

}
