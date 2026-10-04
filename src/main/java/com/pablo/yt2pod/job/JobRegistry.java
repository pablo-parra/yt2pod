package com.pablo.yt2pod.job;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.springframework.stereotype.Component;

/** In-memory jobs, forgotten after the configured TTL. Nothing is persisted. */
@Component
public class JobRegistry {

	private final Map<String, ConversionJob> jobs = new ConcurrentHashMap<>();

	private final Clock clock;

	private final Yt2podProperties properties;

	public JobRegistry(Clock clock, Yt2podProperties properties) {
		this.clock = clock;
		this.properties = properties;
	}

	/** Creates a job unless {@code maxActive} unfinished jobs already exist. */
	public synchronized ConversionJob create(int maxActive) {
		evictExpired();
		if (jobs.values().stream().filter(j -> !j.status().isFinished()).count() >= maxActive) {
			throw new TooManyJobsException();
		}
		ConversionJob job = new ConversionJob(UUID.randomUUID().toString(), clock.instant());
		jobs.put(job.id(), job);
		return job;
	}

	public Optional<ConversionJob> find(String id) {
		evictExpired();
		return Optional.ofNullable(jobs.get(id));
	}

	private void evictExpired() {
		Instant cutoff = clock.instant().minus(properties.jobs().ttl());
		jobs.values().removeIf(j -> j.status().isFinished() && j.createdAt().isBefore(cutoff));
	}

}
