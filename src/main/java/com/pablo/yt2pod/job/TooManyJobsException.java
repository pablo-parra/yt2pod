package com.pablo.yt2pod.job;

public class TooManyJobsException extends RuntimeException {

	public TooManyJobsException() {
		super("Too many conversions in progress. Try again in a few minutes.");
	}

}
