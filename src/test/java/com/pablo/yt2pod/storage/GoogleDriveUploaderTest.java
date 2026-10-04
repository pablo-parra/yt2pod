package com.pablo.yt2pod.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.pablo.yt2pod.TestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** US-09 Drive folder and file naming, US-03 authorization failures are surfaced. Uses an in-memory Drive. */
class GoogleDriveUploaderTest {

	private static final Path FILE = Path.of("/tmp/ignored.m4a");

	static class FakeDrive implements DriveGateway {

		String folderId; // null = folder does not exist

		final Set<String> filesInFolder = new HashSet<>();

		final List<String> createdFolders = new ArrayList<>();

		String lastFolderQuery;

		String lastUploadName;

		String lastMimeType;

		RuntimeException failWith;

		@Override
		public Optional<String> findFolderId(String name) {
			lastFolderQuery = name;
			fail();
			return Optional.ofNullable(folderId);
		}

		@Override
		public String createFolder(String name) {
			createdFolders.add(name);
			folderId = "folder-" + name;
			return folderId;
		}

		@Override
		public boolean fileExists(String folder, String name) {
			return filesInFolder.contains(name);
		}

		@Override
		public UploadedFile upload(String folder, String name, String mimeType, Path file) {
			lastUploadName = name;
			lastMimeType = mimeType;
			filesInFolder.add(name);
			return new UploadedFile("id-" + name, name, "https://drive.google.com/file/" + name);
		}

		private void fail() {
			if (failWith != null) {
				throw failWith;
			}
		}

	}

	private final FakeDrive drive = new FakeDrive();

	private final DriveStatus status = new DriveStatus();

	private GoogleDriveUploader uploader(String folderName) {
		return new GoogleDriveUploader(drive, status, TestProperties.withGoogle("c", "s", "r", folderName));
	}

	@Test
	@DisplayName("US-09 creates the configured folder when it does not exist")
	void US09_createsFolderIfMissing() {
		uploader("my-podcasts").upload(FILE, "Episode.m4a");
		assertThat(drive.createdFolders).containsExactly("my-podcasts");
		assertThat(drive.lastFolderQuery).isEqualTo("my-podcasts");
	}

	@Test
	@DisplayName("US-09 reuses the folder when it already exists")
	void US09_reusesExistingFolder() {
		drive.folderId = "existing";
		uploader("yt2pod").upload(FILE, "Episode.m4a");
		assertThat(drive.createdFolders).isEmpty();
	}

	@Test
	@DisplayName("US-09 returns the Drive file name and a link to it")
	void US09_returnsNameAndLink() {
		UploadedFile uploaded = uploader("yt2pod").upload(FILE, "Episode.m4a");
		assertThat(uploaded.name()).isEqualTo("Episode.m4a");
		assertThat(uploaded.webViewLink()).startsWith("https://drive.google.com/");
	}

	@Test
	@DisplayName("US-09 never overwrites: name collisions get a numeric suffix before the extension")
	void US09_nameCollisionsAddSuffix() {
		drive.folderId = "existing";
		drive.filesInFolder.addAll(Set.of("Episode.m4a", "Episode (2).m4a"));
		UploadedFile uploaded = uploader("yt2pod").upload(FILE, "Episode.m4a");
		assertThat(uploaded.name()).isEqualTo("Episode (3).m4a");
		assertThat(drive.filesInFolder).contains("Episode.m4a", "Episode (2).m4a", "Episode (3).m4a");
	}

	@Test
	@DisplayName("US-09 audio files are uploaded with an audio MIME type")
	void US09_usesAudioMimeTypes() {
		uploader("yt2pod").upload(FILE, "A.m4a");
		assertThat(drive.lastMimeType).isEqualTo("audio/mp4");
		uploader("yt2pod").upload(FILE, "B.mp3");
		assertThat(drive.lastMimeType).isEqualTo("audio/mpeg");
	}

	@Test
	@DisplayName("US-03 a rejected authorization is reported and marks the Drive status as failed")
	void US03_authorizationFailureIsReported() {
		drive.failWith = new DriveAuthorizationException(new RuntimeException("invalid_grant"));
		assertThatThrownBy(() -> uploader("yt2pod").upload(FILE, "Episode.m4a"))
				.isInstanceOf(DriveAuthorizationException.class).hasMessageContaining("Redo the one-off setup");
		assertThat(status.isAuthorizationFailed()).isTrue();
	}

	@Test
	@DisplayName("US-03 a later successful upload clears the failed status")
	void US03_successClearsFailedStatus() {
		status.authorizationFailed();
		uploader("yt2pod").upload(FILE, "Episode.m4a");
		assertThat(status.isAuthorizationFailed()).isFalse();
	}

	@Test
	@DisplayName("US-01 uploading without configured credentials fails with a clear 'not configured' error")
	void US01_notConfiguredGatewayFailsClearly() {
		GoogleDriveUploader notConfigured = new GoogleDriveUploader(new NotConfiguredDriveGateway(), status,
				TestProperties.withGoogle("", "", "", "yt2pod"));
		assertThatThrownBy(() -> notConfigured.upload(FILE, "Episode.m4a"))
				.isInstanceOf(DriveNotConfiguredException.class).hasMessageContaining("setup");
	}

}
