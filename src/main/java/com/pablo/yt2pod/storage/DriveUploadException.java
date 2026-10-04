package com.pablo.yt2pod.storage;

public class DriveUploadException extends DriveException {

	public DriveUploadException(Throwable cause) {
		super("The file could not be saved to Google Drive.", cause);
	}

}
