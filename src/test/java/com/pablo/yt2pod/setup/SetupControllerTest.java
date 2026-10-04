package com.pablo.yt2pod.setup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.URI;

import com.pablo.yt2pod.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** US-01 one-off Google authorization flow (Owner, on the host). */
class SetupControllerTest {

	private String lastRedirectUri;

	private String refreshTokenToReturn = "1//refresh-token<script>";

	private IOException exchangeFailure;

	private final GoogleOAuthClient fakeGoogle = new GoogleOAuthClient() {
		@Override
		public String authorizationUrl(String redirectUri, String state) {
			lastRedirectUri = redirectUri;
			return "https://accounts.example.test/auth?state=" + state;
		}

		@Override
		public String exchangeForRefreshToken(String code, String redirectUri) throws IOException {
			if (exchangeFailure != null) {
				throw exchangeFailure;
			}
			return refreshTokenToReturn;
		}
	};

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(new SetupController(fakeGoogle, TestProperties.defaults())).build();
	}

	private String startAndGetState() throws Exception {
		MvcResult result = mvc.perform(get("/setup/google")).andExpect(status().isFound()).andReturn();
		String location = result.getResponse().getHeader("Location");
		return URI.create(location).getQuery().substring("state=".length());
	}

	@Test
	@DisplayName("US-01 /setup/google redirects the browser to Google's consent screen")
	void US01_startRedirectsToGoogle() throws Exception {
		mvc.perform(get("/setup/google")).andExpect(status().isFound())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("https://accounts.example.test/auth")));
		assertThat(lastRedirectUri).isEqualTo("http://localhost/setup/google/callback");
	}

	@Test
	@DisplayName("US-01 setup cannot start without the Google client id and secret configured")
	void US01_startRequiresClientCredentials() throws Exception {
		MockMvc noCredentials = MockMvcBuilders.standaloneSetup(
				new SetupController(fakeGoogle, TestProperties.withGoogle("", "", "", "yt2pod"))).build();
		noCredentials.perform(get("/setup/google")).andExpect(status().isBadRequest())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("GOOGLE_CLIENT_ID")));
	}

	@Test
	@DisplayName("US-01 the callback shows the refresh token (HTML-escaped) and forbids caching")
	void US01_callbackShowsRefreshTokenOnce() throws Exception {
		String state = startAndGetState();
		mvc.perform(get("/setup/google/callback").param("code", "abc").param("state", state))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("1//refresh-token&lt;script&gt;")))
				.andExpect(content().string(org.hamcrest.Matchers.containsString("GOOGLE_REFRESH_TOKEN")));
	}

	@Test
	@DisplayName("US-01 a callback with an unknown or wrong state is rejected")
	void US01_callbackRejectsWrongState() throws Exception {
		startAndGetState();
		mvc.perform(get("/setup/google/callback").param("code", "abc").param("state", "forged"))
				.andExpect(status().isBadRequest())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("refresh-token"))));
	}

	@Test
	@DisplayName("US-01 a callback without having started the flow is rejected")
	void US01_callbackWithoutStartIsRejected() throws Exception {
		mvc.perform(get("/setup/google/callback").param("code", "abc").param("state", "anything"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("US-01 a state can only be used once (the token cannot be shown again)")
	void US01_stateIsSingleUse() throws Exception {
		String state = startAndGetState();
		mvc.perform(get("/setup/google/callback").param("code", "abc").param("state", state))
				.andExpect(status().isOk());
		mvc.perform(get("/setup/google/callback").param("code", "abc").param("state", state))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("US-01 the user denying access is reported without a token")
	void US01_deniedAccess() throws Exception {
		String state = startAndGetState();
		mvc.perform(get("/setup/google/callback").param("error", "access_denied").param("state", state))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("US-01 if Google returns no refresh token the page explains how to retry")
	void US01_missingRefreshTokenExplainsRetry() throws Exception {
		refreshTokenToReturn = null;
		String state = startAndGetState();
		mvc.perform(get("/setup/google/callback").param("code", "abc").param("state", state))
				.andExpect(status().isBadGateway())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("myaccount.google.com/permissions")));
	}

	@Test
	@DisplayName("US-01 a failed code exchange is reported without leaking details")
	void US01_failedExchange() throws Exception {
		exchangeFailure = new IOException("secret internal detail");
		String state = startAndGetState();
		mvc.perform(get("/setup/google/callback").param("code", "abc").param("state", state))
				.andExpect(status().isBadGateway())
				.andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("secret internal detail"))));
	}

}
