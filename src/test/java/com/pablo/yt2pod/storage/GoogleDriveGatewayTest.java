package com.pablo.yt2pod.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.pablo.yt2pod.TestProperties;
import com.pablo.yt2pod.config.Yt2podProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Talks to a fake Google over HTTP: US-01, US-02, US-03 (credentials/token renewal) and US-09 (Drive calls). */
class GoogleDriveGatewayTest {

	@TempDir
	Path dir;

	private final Yt2podProperties.Google google = TestProperties.withGoogle("cid-1", "secret-1", "refresh-1",
			"yt2pod").google();

	private static boolean isToken(RecordingTransport.Call c) {
		return c.url().contains("/token");
	}

	private static String decoded(String url) {
		return URLDecoder.decode(url, StandardCharsets.UTF_8);
	}

	private GoogleDriveGateway gateway(RecordingTransport transport) {
		return new GoogleDriveGateway(GoogleDriveConfiguration.drive(google, transport));
	}

	@Test
	@DisplayName("US-02 credentials come only from configuration (client id, secret and refresh token)")
	void US02_credentialsComeFromConfiguration() {
		RecordingTransport transport = new RecordingTransport(
				c -> isToken(c) ? RecordingTransport.token("ya29.a") : RecordingTransport.json(200, "{}"));
		gateway(transport).findFolderId("yt2pod");
		RecordingTransport.Call tokenCall = transport.calls.stream().filter(GoogleDriveGatewayTest::isToken)
				.findFirst().orElseThrow();
		assertThat(tokenCall.body()).contains("client_id=cid-1", "client_secret=secret-1",
				"refresh_token=refresh-1", "grant_type=refresh_token");
	}

	@Test
	@DisplayName("US-03 the access token is obtained from the refresh token automatically and sent as Bearer")
	void US03_accessTokenIsRenewedAutomatically() {
		RecordingTransport transport = new RecordingTransport(
				c -> isToken(c) ? RecordingTransport.token("ya29.fresh") : RecordingTransport.json(200, "{}"));
		gateway(transport).findFolderId("yt2pod");
		RecordingTransport.Call driveCall = transport.calls.stream().filter(c -> !isToken(c)).findFirst()
				.orElseThrow();
		assertThat(driveCall.headers().get("authorization")).containsExactly("Bearer ya29.fresh");
	}

	@Test
	@DisplayName("US-03 a revoked/expired refresh token (invalid_grant) becomes a DriveAuthorizationException")
	void US03_invalidGrantIsReportedAsAuthorizationFailure() {
		RecordingTransport transport = new RecordingTransport(c -> RecordingTransport.json(400,
				"{\"error\":\"invalid_grant\",\"error_description\":\"Token has been expired or revoked.\"}"));
		assertThatThrownBy(() -> gateway(transport).findFolderId("yt2pod"))
				.isInstanceOf(DriveAuthorizationException.class);
	}

	@Test
	@DisplayName("US-09 finds the folder by exact name among non-trashed folders")
	void US09_findsFolderByName() {
		RecordingTransport transport = new RecordingTransport(c -> isToken(c) ? RecordingTransport.token("t")
				: RecordingTransport.json(200, "{\"files\":[{\"id\":\"folder-42\"}]}"));
		assertThat(gateway(transport).findFolderId("Pablo's podcasts")).contains("folder-42");
		String query = decoded(transport.calls.get(1).url());
		assertThat(query).contains("mimeType='application/vnd.google-apps.folder'")
				.contains("name='Pablo\\'s podcasts'").contains("trashed=false");
	}

	@Test
	@DisplayName("US-09 reports no folder when Drive returns none")
	void US09_noFolderFound() {
		RecordingTransport transport = new RecordingTransport(
				c -> isToken(c) ? RecordingTransport.token("t") : RecordingTransport.json(200, "{}"));
		assertThat(gateway(transport).findFolderId("yt2pod")).isEmpty();
	}

	@Test
	@DisplayName("US-09 creates the folder with the folder MIME type")
	void US09_createsFolder() {
		RecordingTransport transport = new RecordingTransport(
				c -> isToken(c) ? RecordingTransport.token("t") : RecordingTransport.json(200, "{\"id\":\"new-1\"}"));
		assertThat(gateway(transport).createFolder("yt2pod")).isEqualTo("new-1");
		RecordingTransport.Call create = transport.calls.get(1);
		assertThat(create.method()).isEqualTo("POST");
		assertThat(create.body()).contains("application/vnd.google-apps.folder").contains("yt2pod");
	}

	@Test
	@DisplayName("US-09 checks for an existing file by name inside the folder")
	void US09_detectsExistingFile() {
		RecordingTransport transport = new RecordingTransport(c -> isToken(c) ? RecordingTransport.token("t")
				: RecordingTransport.json(200, "{\"files\":[{\"id\":\"f1\"}]}"));
		assertThat(gateway(transport).fileExists("folder-42", "Episode.m4a")).isTrue();
		assertThat(decoded(transport.calls.get(1).url())).contains("'folder-42' in parents")
				.contains("name='Episode.m4a'");
	}

	@Test
	@DisplayName("US-09 uploads the file into the folder (resumable upload) and returns id, name and link")
	void US09_uploadsFileWithResumableUpload() throws Exception {
		Path file = Files.writeString(dir.resolve("Episode.m4a"), "audio-bytes");
		RecordingTransport transport = new RecordingTransport(c -> {
			if (isToken(c)) {
				return RecordingTransport.token("t");
			}
			if (c.method().equals("POST") && c.url().contains("uploadType=resumable")) {
				return new MockLowLevelHttpResponse().setStatusCode(200).addHeader("Location",
						"https://upload.example.test/session-1");
			}
			if (c.method().equals("PUT") && c.url().startsWith("https://upload.example.test/session-1")) {
				return RecordingTransport.json(200,
						"{\"id\":\"file-9\",\"name\":\"Episode.m4a\",\"webViewLink\":\"https://drive.google.com/f9\"}");
			}
			return RecordingTransport.json(500, "{}");
		});
		UploadedFile uploaded = gateway(transport).upload("folder-42", "Episode.m4a", "audio/mp4", file);
		assertThat(uploaded).isEqualTo(new UploadedFile("file-9", "Episode.m4a", "https://drive.google.com/f9"));
		List<RecordingTransport.Call> upload = transport.calls.stream().filter(c -> !isToken(c)).toList();
		assertThat(upload.get(0).body()).contains("folder-42").contains("Episode.m4a");
		assertThat(upload.get(1).body()).isEqualTo("audio-bytes");
	}

	@Test
	@DisplayName("US-09 a Drive error (not authorization) is reported as an upload failure")
	void US09_driveErrorsAreUploadFailures() {
		RecordingTransport transport = new RecordingTransport(c -> isToken(c) ? RecordingTransport.token("t")
				: RecordingTransport.json(403, "{\"error\":{\"code\":403,\"message\":\"storageQuotaExceeded\"}}"));
		assertThatThrownBy(() -> gateway(transport).findFolderId("yt2pod")).isInstanceOf(DriveUploadException.class);
	}

}
