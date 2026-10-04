package com.pablo.yt2pod.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.pablo.yt2pod.TestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

/** US-01 not configured is visible, US-03 revoked authorization turns health DOWN. */
class DriveHealthIndicatorTest {

	private final DriveStatus status = new DriveStatus();

	@Test
	@DisplayName("US-01 health is UNKNOWN (not DOWN) while Drive is not configured, so setup mode can start")
	void US01_unknownWhenNotConfigured() {
		DriveHealthIndicator indicator = new DriveHealthIndicator(status,
				TestProperties.withGoogle("", "", "", "yt2pod"));
		assertThat(indicator.health().getStatus()).isEqualTo(Status.UNKNOWN);
	}

	@Test
	@DisplayName("US-03 health is UP when configured and authorization works")
	void US03_upWhenConfigured() {
		assertThat(new DriveHealthIndicator(status, TestProperties.defaults()).health().getStatus())
				.isEqualTo(Status.UP);
	}

	@Test
	@DisplayName("US-03 health is DOWN after Google rejected the authorization")
	void US03_downWhenAuthorizationFailed() {
		status.authorizationFailed();
		assertThat(new DriveHealthIndicator(status, TestProperties.defaults()).health().getStatus())
				.isEqualTo(Status.DOWN);
	}

}
