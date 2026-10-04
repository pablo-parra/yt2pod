package com.pablo.yt2pod.setup;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;

import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.drive.DriveScopes;
import com.pablo.yt2pod.config.Yt2podProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "yt2pod.setup", name = "enabled", havingValue = "true")
public class DefaultGoogleOAuthClient implements GoogleOAuthClient {

	private final Yt2podProperties.Google google;

	private final HttpTransport transport;

	public DefaultGoogleOAuthClient(Yt2podProperties properties) throws GeneralSecurityException, IOException {
		this(properties, GoogleNetHttpTransport.newTrustedTransport());
	}

	DefaultGoogleOAuthClient(Yt2podProperties properties, HttpTransport transport) {
		this.google = properties.google();
		this.transport = transport;
	}

	@Override
	public String authorizationUrl(String redirectUri, String state) {
		// drive.file: only files created by this app; offline + consent make Google return a refresh token
		return new GoogleAuthorizationCodeRequestUrl(google.clientId(), redirectUri, List.of(DriveScopes.DRIVE_FILE))
				.setAccessType("offline").set("prompt", "consent").setState(state).build();
	}

	@Override
	public String exchangeForRefreshToken(String code, String redirectUri) throws IOException {
		return new GoogleAuthorizationCodeTokenRequest(transport, GsonFactory.getDefaultInstance(),
				google.clientId(), google.clientSecret(), code, redirectUri).execute().getRefreshToken();
	}

}
