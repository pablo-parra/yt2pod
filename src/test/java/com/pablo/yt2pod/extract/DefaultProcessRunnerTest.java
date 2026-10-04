package com.pablo.yt2pod.extract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/** The real process runner (US-12: failures and hung tools are detected). Uses basic POSIX commands. */
@EnabledOnOs({ OS.MAC, OS.LINUX })
class DefaultProcessRunnerTest {

	private final DefaultProcessRunner runner = new DefaultProcessRunner();

	@Test
	@DisplayName("US-12 captures output (stdout and stderr) and the exit code")
	void US12_capturesOutputAndExitCode() throws IOException {
		ProcessRunner.Result result = runner.run(List.of("sh", "-c", "echo out; echo err 1>&2; exit 3"),
				Duration.ofSeconds(10));
		assertThat(result.exitCode()).isEqualTo(3);
		assertThat(result.output()).contains("out", "err");
	}

	@Test
	@DisplayName("US-12 an executable that does not exist raises IOException")
	void US12_missingExecutable() {
		assertThatThrownBy(() -> runner.run(List.of("definitely-not-a-real-binary"), Duration.ofSeconds(5)))
				.isInstanceOf(IOException.class);
	}

	@Test
	@DisplayName("US-12 a process exceeding the timeout is killed")
	void US12_timeoutKillsTheProcess() {
		assertThatThrownBy(() -> runner.run(List.of("sleep", "10"), Duration.ofMillis(300)))
				.isInstanceOf(ProcessRunner.ProcessTimeoutException.class);
	}

}
