package com.pablo.yt2pod.extract;

public class ExtractionException extends RuntimeException {

	private final ExtractionFailure failure;

	public ExtractionException(ExtractionFailure failure) {
		super(failure.message());
		this.failure = failure;
	}

	public ExtractionFailure failure() {
		return failure;
	}

}
