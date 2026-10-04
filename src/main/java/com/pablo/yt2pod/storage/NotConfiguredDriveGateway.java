package com.pablo.yt2pod.storage;

import java.nio.file.Path;
import java.util.Optional;

public class NotConfiguredDriveGateway implements DriveGateway {

	@Override
	public Optional<String> findFolderId(String name) {
		throw new DriveNotConfiguredException();
	}

	@Override
	public String createFolder(String name) {
		throw new DriveNotConfiguredException();
	}

	@Override
	public boolean fileExists(String folderId, String name) {
		throw new DriveNotConfiguredException();
	}

	@Override
	public UploadedFile upload(String folderId, String name, String mimeType, Path file) {
		throw new DriveNotConfiguredException();
	}

}
