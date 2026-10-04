package com.pablo.yt2pod.storage;

import java.nio.file.Path;
import java.util.Locale;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.springframework.stereotype.Component;

@Component
public class GoogleDriveUploader implements DriveUploader {

	private final DriveGateway gateway;

	private final DriveStatus status;

	private final Yt2podProperties properties;

	public GoogleDriveUploader(DriveGateway gateway, DriveStatus status, Yt2podProperties properties) {
		this.gateway = gateway;
		this.status = status;
		this.properties = properties;
	}

	@Override
	public UploadedFile upload(Path file, String fileName) {
		try {
			String folderName = properties.drive().folderName();
			String folderId = gateway.findFolderId(folderName).orElseGet(() -> gateway.createFolder(folderName));
			String uniqueName = uniqueName(folderId, fileName);
			UploadedFile uploaded = gateway.upload(folderId, uniqueName, mimeType(uniqueName), file);
			status.authorizationWorks();
			return uploaded;
		}
		catch (DriveAuthorizationException e) {
			status.authorizationFailed();
			throw e;
		}
	}

	/** "Name.m4a" -> "Name (2).m4a" -> "Name (3).m4a" ... until it does not exist in the folder. */
	private String uniqueName(String folderId, String fileName) {
		int dot = fileName.lastIndexOf('.');
		String base = dot > 0 ? fileName.substring(0, dot) : fileName;
		String extension = dot > 0 ? fileName.substring(dot) : "";
		String candidate = fileName;
		for (int n = 2; gateway.fileExists(folderId, candidate); n++) {
			candidate = base + " (" + n + ")" + extension;
		}
		return candidate;
	}

	static String mimeType(String fileName) {
		String lower = fileName.toLowerCase(Locale.ROOT);
		if (lower.endsWith(".m4a")) {
			return "audio/mp4";
		}
		if (lower.endsWith(".mp3")) {
			return "audio/mpeg";
		}
		if (lower.endsWith(".opus") || lower.endsWith(".ogg")) {
			return "audio/ogg";
		}
		return "application/octet-stream";
	}

}
