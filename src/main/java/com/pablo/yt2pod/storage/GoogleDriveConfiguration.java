package com.pablo.yt2pod.storage;

import java.io.IOException;
import java.security.GeneralSecurityException;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.Drive;
import com.google.auth.http.HttpCredentialsAdapter;
import com.google.auth.oauth2.UserCredentials;
import com.pablo.yt2pod.config.Yt2podProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GoogleDriveConfiguration {

	static final String APPLICATION_NAME = "yt2pod";

	static boolean isConfigured(Yt2podProperties.Google google) {
		return hasText(google.clientId()) && hasText(google.clientSecret()) && hasText(google.refreshToken());
	}

	private static boolean hasText(String value) {
		return value != null && !value.isBlank();
	}

	/** Credentials come only from configuration; the library renews access tokens from the refresh token. */
	static UserCredentials credentials(Yt2podProperties.Google google, HttpTransport transport) {
		return UserCredentials.newBuilder().setClientId(google.clientId()).setClientSecret(google.clientSecret())
				.setRefreshToken(google.refreshToken()).setHttpTransportFactory(() -> transport).build();
	}

	static Drive drive(Yt2podProperties.Google google, HttpTransport transport) {
		JsonFactory json = GsonFactory.getDefaultInstance();
		return new Drive.Builder(transport, json, new HttpCredentialsAdapter(credentials(google, transport)))
				.setApplicationName(APPLICATION_NAME).build();
	}

	@Bean
	DriveGateway driveGateway(Yt2podProperties properties) throws GeneralSecurityException, IOException {
		if (!isConfigured(properties.google())) {
			return new NotConfiguredDriveGateway();
		}
		return new GoogleDriveGateway(drive(properties.google(), GoogleNetHttpTransport.newTrustedTransport()));
	}

}
