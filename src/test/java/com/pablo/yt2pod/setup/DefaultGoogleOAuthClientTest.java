package com.pablo.yt2pod.setup;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;
import com.pablo.yt2pod.TestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** US-01: minimal scope, offline access, and refresh token extraction. */
class DefaultGoogleOAuthClientTest {

	private static final String REDIRECT = "http://localhost:8080/setup/google/callback";

	private final DefaultGoogleOAuthClient client = new DefaultGoogleOAuthClient(TestProperties.defaults(),
			new MockHttpTransport());

	private static String param(String url, String name) {
		for (String pair : URI.create(url).getRawQuery().split("&")) {
			String[] kv = pair.split("=", 2);
			if (kv[0].equals(name)) {
				return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
			}
		}
		return null;
	}

	@Test
	@DisplayName("US-01 requests only the drive.file scope (files created by the app)")
	void US01_requestsMinimumScope() {
		String url = client.authorizationUrl(REDIRECT, "st");
		assertThat(param(url, "scope")).isEqualTo("https://www.googleapis.com/auth/drive.file");
	}

	@Test
	@DisplayName("US-01 asks for offline access with forced consent so Google returns a refresh token")
	void US01_requestsOfflineAccessAndConsent() {
		String url = client.authorizationUrl(REDIRECT, "st");
		assertThat(url).startsWith("https://accounts.google.com/o/oauth2/");
		assertThat(param(url, "access_type")).isEqualTo("offline");
		assertThat(param(url, "prompt")).isEqualTo("consent");
		assertThat(param(url, "response_type")).isEqualTo("code");
	}

	@Test
	@DisplayName("US-01/US-02 the authorization uses the configured client id, redirect uri and state")
	void US01_US02_usesConfiguredClientAndState() {
		String url = client.authorizationUrl(REDIRECT, "state-xyz");
		assertThat(param(url, "client_id")).isEqualTo("client-id");
		assertThat(param(url, "redirect_uri")).isEqualTo(REDIRECT);
		assertThat(param(url, "state")).isEqualTo("state-xyz");
	}

	@Test
	@DisplayName("US-01 exchanges the authorization code for a refresh token")
	void US01_exchangesCodeForRefreshToken() throws IOException {
		StringBuilder sentBody = new StringBuilder();
		MockHttpTransport transport = new MockHttpTransport() {
			@Override
			public com.google.api.client.http.LowLevelHttpRequest buildRequest(String method, String url) {
				return new MockLowLevelHttpRequest(url) {
					@Override
					public com.google.api.client.http.LowLevelHttpResponse execute() throws IOException {
						sentBody.append(getContentAsString());
						return new MockLowLevelHttpResponse().setContentType("application/json").setContent(
								"{\"access_token\":\"a\",\"refresh_token\":\"refresh-xyz\",\"expires_in\":3600,"
										+ "\"token_type\":\"Bearer\"}");
					}
				};
			}
		};
		String token = new DefaultGoogleOAuthClient(TestProperties.defaults(), transport)
				.exchangeForRefreshToken("the-code", REDIRECT);
		assertThat(token).isEqualTo("refresh-xyz");
		assertThat(sentBody.toString()).contains("code=the-code", "grant_type=authorization_code");
	}

}
