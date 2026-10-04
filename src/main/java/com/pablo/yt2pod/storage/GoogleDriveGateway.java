package com.pablo.yt2pod.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import com.google.api.client.http.InputStreamContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;

public class GoogleDriveGateway implements DriveGateway {

	static final String FOLDER_MIME_TYPE = "application/vnd.google-apps.folder";

	private final Drive drive;

	public GoogleDriveGateway(Drive drive) {
		this.drive = drive;
	}

	@Override
	public Optional<String> findFolderId(String name) {
		FileList result = call(() -> drive.files().list()
				.setQ("mimeType='" + FOLDER_MIME_TYPE + "' and name='" + escape(name) + "' and trashed=false")
				.setFields("files(id)").setPageSize(1).execute());
		return firstId(result);
	}

	@Override
	public String createFolder(String name) {
		File folder = new File().setName(name).setMimeType(FOLDER_MIME_TYPE);
		return call(() -> drive.files().create(folder).setFields("id").execute()).getId();
	}

	@Override
	public boolean fileExists(String folderId, String name) {
		FileList result = call(() -> drive.files().list()
				.setQ("'" + escape(folderId) + "' in parents and name='" + escape(name) + "' and trashed=false")
				.setFields("files(id)").setPageSize(1).execute());
		return firstId(result).isPresent();
	}

	@Override
	public UploadedFile upload(String folderId, String name, String mimeType, Path file) {
		File metadata = new File().setName(name).setParents(List.of(folderId));
		File created = call(() -> {
			try (InputStream in = Files.newInputStream(file)) {
				InputStreamContent content = new InputStreamContent(mimeType, in);
				content.setLength(Files.size(file));
				Drive.Files.Create request = drive.files().create(metadata, content).setFields("id,name,webViewLink");
				request.getMediaHttpUploader().setDirectUploadEnabled(false);
				return request.execute();
			}
		});
		return new UploadedFile(created.getId(), created.getName(), created.getWebViewLink());
	}

	private static Optional<String> firstId(FileList result) {
		if (result.getFiles() == null || result.getFiles().isEmpty()) {
			return Optional.empty();
		}
		return Optional.ofNullable(result.getFiles().get(0).getId());
	}

	/** Escapes a value for use inside a single-quoted Drive query string. */
	static String escape(String value) {
		return value.replace("\\", "\\\\").replace("'", "\\'");
	}

	@FunctionalInterface
	private interface DriveCall<T> {

		T run() throws IOException;

	}

	private static <T> T call(DriveCall<T> call) {
		try {
			return call.run();
		}
		catch (IOException e) {
			if (isAuthorizationFailure(e)) {
				throw new DriveAuthorizationException(e);
			}
			throw new DriveUploadException(e);
		}
	}

	private static boolean isAuthorizationFailure(Throwable error) {
		for (Throwable t = error; t != null; t = t.getCause()) {
			String message = t.getMessage();
			if (message != null && (message.contains("invalid_grant") || message.contains("\"code\": 401")
					|| message.startsWith("401"))) {
				return true;
			}
		}
		return false;
	}

}
