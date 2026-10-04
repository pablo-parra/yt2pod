package com.pablo.yt2pod.storage;

public class DriveAuthorizationException extends RuntimeException {

	public DriveAuthorizationException(Throwable cause) {
		super("Drive authorization expired or revoked. Redo the one-off setup.", cause);
	}

}
