package com.pablo.yt2pod.extract;

import java.nio.file.Path;

public interface AudioExtractor {

	/**
	 * Downloads only the audio of the given YouTube video into {@code outputDir}.
	 * The caller owns {@code outputDir} and must delete it afterwards.
	 *
	 * @throws InvalidYoutubeUrlException if the url is not a single YouTube video
	 * @throws ExtractionException if the audio could not be extracted
	 */
	ExtractedAudio extract(String url, Path outputDir);

}
