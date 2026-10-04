package com.pablo.yt2pod.storage;

/** Base of all Drive failures; the message is safe to show to the Client. */
public abstract class DriveException extends RuntimeException {

	protected DriveException(String message, Throwable cause) {
		super(message, cause);
	}

}
