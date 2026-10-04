package com.pablo.yt2pod.storage;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * UNKNOWN while Drive is not configured (e.g. during setup), DOWN after Google rejected the
 * authorization, UP otherwise. It never calls Google, so health checks are cheap.
 */
@Component("drive")
public class DriveHealthIndicator implements HealthIndicator {

	private final DriveStatus status;

	private final Yt2podProperties properties;

	public DriveHealthIndicator(DriveStatus status, Yt2podProperties properties) {
		this.status = status;
		this.properties = properties;
	}

	@Override
	public Health health() {
		if (!GoogleDriveConfiguration.isConfigured(properties.google())) {
			return Health.unknown().withDetail("reason", "not configured").build();
		}
		if (status.isAuthorizationFailed()) {
			return Health.down().withDetail("reason", "authorization expired or revoked; redo setup").build();
		}
		return Health.up().build();
	}

}
