package com.pablo.yt2pod.storage;

public class DriveUploadException extends RuntimeException {

	public DriveUploadException(Throwable cause) {
		super("The file could not be saved to Google Drive.", cause);
	}

}
