package com.pablo.yt2pod.extract;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

/** Runs an external command; abstracted so the extractor can be tested without yt-dlp. */
public interface ProcessRunner {

	/** Combined stdout/stderr and exit code. */
	record Result(int exitCode, String output) {
	}

	/**
	 * @throws IOException if the command cannot be started
	 * @throws ProcessTimeoutException if the command does not finish within {@code timeout}
	 */
	Result run(List<String> command, Duration timeout) throws IOException;

	class ProcessTimeoutException extends RuntimeException {

		public ProcessTimeoutException() {
			super("process timed out");
		}

	}

}
