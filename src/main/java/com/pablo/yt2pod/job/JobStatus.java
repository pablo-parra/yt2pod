package com.pablo.yt2pod.job;

public enum JobStatus {

	QUEUED, EXTRACTING, UPLOADING, DONE, FAILED;

	public boolean isFinished() {
		return this == DONE || this == FAILED;
	}

}
