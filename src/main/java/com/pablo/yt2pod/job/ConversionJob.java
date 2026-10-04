package com.pablo.yt2pod.job;

import java.time.Instant;

/** In-memory state of one conversion. Deliberately holds no URL, title or video information. */
public class ConversionJob {

	private final String id;

	private final Instant createdAt;

	private volatile JobStatus status = JobStatus.QUEUED;

	private volatile String fileName;

	private volatile String driveLink;

	private volatile String error;

	ConversionJob(String id, Instant createdAt) {
		this.id = id;
		this.createdAt = createdAt;
	}

	public String id() {
		return id;
	}

	public Instant createdAt() {
		return createdAt;
	}

	public JobStatus status() {
		return status;
	}

	public String fileName() {
		return fileName;
	}

	public String driveLink() {
		return driveLink;
	}

	public String error() {
		return error;
	}

	void extracting() {
		status = JobStatus.EXTRACTING;
	}

	void uploading() {
		status = JobStatus.UPLOADING;
	}

	void done(String fileName, String driveLink) {
		this.fileName = fileName;
		this.driveLink = driveLink;
		status = JobStatus.DONE;
	}

	void failed(String error) {
		this.error = error;
		status = JobStatus.FAILED;
	}

}
