package com.pablo.yt2pod.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** US-06 Link validation, US-05 accepted URL forms, US-13 playlists not supported (yet). */
class YoutubeUrlValidatorTest {

	private static final String CANONICAL = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

	private final YoutubeUrlValidator validator = new YoutubeUrlValidator();

	@ParameterizedTest
	@ValueSource(strings = {
			"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
			"https://youtube.com/watch?v=dQw4w9WgXcQ",
			"https://m.youtube.com/watch?v=dQw4w9WgXcQ",
			"https://music.youtube.com/watch?v=dQw4w9WgXcQ",
			"https://youtu.be/dQw4w9WgXcQ",
			"http://youtu.be/dQw4w9WgXcQ?t=42",
			"https://www.youtube.com/shorts/dQw4w9WgXcQ",
			"https://www.youtube.com/embed/dQw4w9WgXcQ",
			"  https://www.youtube.com/watch?v=dQw4w9WgXcQ  " })
	@DisplayName("US-05 accepts youtube.com, m.youtube.com and youtu.be link forms")
	void US05_acceptsSupportedLinkForms(String url) {
		assertThat(validator.canonicalize(url)).isEqualTo(CANONICAL);
	}

	@Test
	@DisplayName("US-05 drops extra query parameters so only the video id reaches yt-dlp")
	void US05_canonicalUrlKeepsOnlyTheVideoId() {
		assertThat(validator.canonicalize("https://www.youtube.com/watch?feature=share&v=dQw4w9WgXcQ&t=10s&x=--exec"))
				.isEqualTo(CANONICAL);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://vimeo.com/123456789", "https://evil.com/watch?v=dQw4w9WgXcQ",
			"https://youtube.com.evil.com/watch?v=dQw4w9WgXcQ", "https://notyoutu.be/dQw4w9WgXcQ" })
	@DisplayName("US-06 rejects links that are not from YouTube")
	void US06_rejectsNonYoutubeHosts(String url) {
		assertThatThrownBy(() -> validator.canonicalize(url)).isInstanceOf(InvalidYoutubeUrlException.class)
				.hasMessageContaining("YouTube");
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "not a url", "ftp://youtube.com/watch?v=dQw4w9WgXcQ",
			"javascript:alert(1)", "file:///etc/passwd" })
	@DisplayName("US-06 rejects empty, malformed and non-http(s) input")
	void US06_rejectsMalformedInput(String url) {
		assertThatThrownBy(() -> validator.canonicalize(url)).isInstanceOf(InvalidYoutubeUrlException.class);
	}

	@Test
	@DisplayName("US-06 rejects null input")
	void US06_rejectsNull() {
		assertThatThrownBy(() -> validator.canonicalize(null)).isInstanceOf(InvalidYoutubeUrlException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://www.youtube.com/watch", "https://www.youtube.com/watch?v=short",
			"https://www.youtube.com/watch?v=dQw4w9WgXcQ%3Bls", "https://www.youtube.com/", "https://youtu.be/" })
	@DisplayName("US-06 rejects YouTube links without a valid video id")
	void US06_rejectsMissingOrInvalidVideoId(String url) {
		assertThatThrownBy(() -> validator.canonicalize(url)).isInstanceOf(InvalidYoutubeUrlException.class);
	}

	@ParameterizedTest
	@ValueSource(strings = { "https://www.youtube.com/playlist?list=PLabcdefghijk",
			"https://www.youtube.com/channel/UCabcdefghijklmnopqrstuv", "https://www.youtube.com/@somechannel" })
	@DisplayName("US-13 playlists and channels are rejected with an explicit error (not supported yet)")
	void US13_rejectsPlaylistsAndChannels(String url) {
		assertThatThrownBy(() -> validator.canonicalize(url)).isInstanceOf(InvalidYoutubeUrlException.class)
				.hasMessageContaining("single YouTube video");
	}

	@Test
	@DisplayName("US-13 a video link inside a playlist downloads only that video")
	void US13_videoInsidePlaylistIsReducedToTheSingleVideo() {
		assertThat(validator.canonicalize("https://www.youtube.com/watch?v=dQw4w9WgXcQ&list=PLabcdefghijk&index=3"))
				.isEqualTo(CANONICAL);
	}

}
