package com.pablo.yt2pod.extract;

import java.nio.file.Path;

/** The audio file produced by an extraction; {@code title} is the file name without extension. */
public record ExtractedAudio(Path file, String title) {
}
