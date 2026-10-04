package com.pablo.yt2pod.storage;

import java.nio.file.Path;

public interface DriveUploader {

	/**
	 * Uploads {@code file} to the configured Drive folder under {@code fileName}; an existing file with
	 * the same name is never overwritten (a numeric suffix is added instead).
	 *
	 * @throws DriveNotConfiguredException if no Google credentials are configured
	 * @throws DriveAuthorizationException if Google rejected the stored authorization
	 * @throws DriveUploadException for any other Drive failure
	 */
	UploadedFile upload(Path file, String fileName);

}
