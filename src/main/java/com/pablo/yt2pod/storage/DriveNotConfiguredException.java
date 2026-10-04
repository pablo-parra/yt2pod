package com.pablo.yt2pod.storage;

public class DriveNotConfiguredException extends DriveException {

	public DriveNotConfiguredException() {
		super("Google Drive is not configured on the server. Run the one-off setup first.", null);
	}

}
