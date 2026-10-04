package com.pablo.yt2pod.storage;

import java.nio.file.Path;
import java.util.Optional;

/** Minimal Drive operations needed by the uploader; lets the folder/naming logic be tested without Google. */
public interface DriveGateway {

	Optional<String> findFolderId(String name);

	String createFolder(String name);

	boolean fileExists(String folderId, String name);

	UploadedFile upload(String folderId, String name, String mimeType, Path file);

}
