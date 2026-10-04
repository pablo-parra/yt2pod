package com.pablo.yt2pod.extract;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

@Component
public class DefaultProcessRunner implements ProcessRunner {

	@Override
	public Result run(List<String> command, Duration timeout) throws IOException {
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		StringBuilder output = new StringBuilder();
		Thread reader = Thread.ofVirtual().start(() -> {
			try (InputStream in = process.getInputStream()) {
				output.append(new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
			catch (IOException ignored) {
				// process destroyed
			}
		});
		try {
			if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				throw new ProcessTimeoutException();
			}
			reader.join(5_000);
		}
		catch (InterruptedException e) {
			process.destroyForcibly();
			Thread.currentThread().interrupt();
			throw new ProcessTimeoutException();
		}
		return new Result(process.exitValue(), output.toString());
	}

}
