package com.pablo.yt2pod.extract;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Accepts only single-video YouTube links and returns a canonical
 * {@code https://www.youtube.com/watch?v=<id>} URL, so nothing else from the submitted text
 * (extra query parameters, playlist ids, fragments) ever reaches yt-dlp.
 */
@Component
public class YoutubeUrlValidator {

	private static final Set<String> YOUTUBE_HOSTS =
			Set.of("youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com");

	private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

	public String canonicalize(String input) {
		if (input == null || input.isBlank()) {
			throw new InvalidYoutubeUrlException("Please provide a YouTube link.");
		}
		URI uri = parse(input.trim());
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new InvalidYoutubeUrlException("The link must start with http:// or https://.");
		}
		String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
		String path = uri.getRawPath() == null ? "" : uri.getRawPath();

		String videoId;
		if (host.equals("youtu.be")) {
			videoId = firstSegment(path);
		}
		else if (YOUTUBE_HOSTS.contains(host)) {
			videoId = videoIdFromYoutubeHost(path, uri.getRawQuery());
		}
		else {
			throw new InvalidYoutubeUrlException("Only YouTube links are supported.");
		}
		if (videoId == null || !VIDEO_ID.matcher(videoId).matches()) {
			throw new InvalidYoutubeUrlException("The link does not point to a single YouTube video.");
		}
		return "https://www.youtube.com/watch?v=" + videoId;
	}

	private static URI parse(String input) {
		try {
			return new URI(input);
		}
		catch (URISyntaxException e) {
			throw new InvalidYoutubeUrlException("The link is not a valid URL.");
		}
	}

	private static String videoIdFromYoutubeHost(String path, String query) {
		if (path.equals("/watch")) {
			return queryParam(query, "v");
		}
		for (String prefix : new String[] { "/shorts/", "/embed/" }) {
			if (path.startsWith(prefix)) {
				return firstSegment(path.substring(prefix.length() - 1));
			}
		}
		return null; // /playlist, /channel/..., /live/..., home page, etc.
	}

	private static String firstSegment(String path) {
		String trimmed = path.startsWith("/") ? path.substring(1) : path;
		int slash = trimmed.indexOf('/');
		return slash < 0 ? trimmed : trimmed.substring(0, slash);
	}

	private static String queryParam(String query, String name) {
		if (query == null) {
			return null;
		}
		for (String pair : query.split("&")) {
			int eq = pair.indexOf('=');
			if (eq > 0 && pair.substring(0, eq).equals(name)) {
				return pair.substring(eq + 1);
			}
		}
		return null;
	}

}
