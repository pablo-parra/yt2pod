package com.pablo.yt2pod.extract;

/** Why an extraction failed, with a message that is safe to show to the Client. */
public enum ExtractionFailure {

	UNAVAILABLE("The video is unavailable or has been removed."),
	PRIVATE("The video is private."),
	AGE_RESTRICTED("The video is age-restricted and cannot be downloaded."),
	GEO_BLOCKED("The video is not available in the server's country."),
	LIMIT_EXCEEDED("The video is a live stream or longer than the maximum allowed duration."),
	BOT_CHECK("YouTube is asking for verification; try again later or update yt-dlp."),
	TIMEOUT("The extraction took too long and was cancelled."),
	TOOL_NOT_FOUND("yt-dlp or ffmpeg could not be started on the server."),
	UNKNOWN("The audio could not be extracted.");

	private final String message;

	ExtractionFailure(String message) {
		this.message = message;
	}

	public String message() {
		return message;
	}

}
