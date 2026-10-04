package com.pablo.yt2pod.setup;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.HtmlUtils;

/**
 * One-off Google authorization: only exists while {@code yt2pod.setup.enabled=true}. Shows the refresh
 * token once so the Owner can put it in {@code GOOGLE_REFRESH_TOKEN}; nothing is stored.
 */
@RestController
@ConditionalOnProperty(prefix = "yt2pod.setup", name = "enabled", havingValue = "true")
public class SetupController {

	private static final Logger log = LoggerFactory.getLogger(SetupController.class);

	private static final String CALLBACK_PATH = "/setup/google/callback";

	private final GoogleOAuthClient oauth;

	private final Yt2podProperties properties;

	private final AtomicReference<String> pendingState = new AtomicReference<>();

	private final SecureRandom random = new SecureRandom();

	public SetupController(GoogleOAuthClient oauth, Yt2podProperties properties) {
		this.oauth = oauth;
		this.properties = properties;
		log.warn("Setup mode is ENABLED: keep the port bound to 127.0.0.1 and disable it after setup");
	}

	@GetMapping("/setup/google")
	public ResponseEntity<String> start() {
		Yt2podProperties.Google google = properties.google();
		if (!StringUtils.hasText(google.clientId()) || !StringUtils.hasText(google.clientSecret())) {
			return page(HttpStatus.BAD_REQUEST,
					"Set GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET, restart, and try again.");
		}
		byte[] bytes = new byte[24];
		random.nextBytes(bytes);
		String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		pendingState.set(state);
		String url = oauth.authorizationUrl(redirectUri(), state);
		return ResponseEntity.status(HttpStatus.FOUND).header(HttpHeaders.LOCATION, url)
				.cacheControl(CacheControl.noStore()).build();
	}

	@GetMapping(CALLBACK_PATH)
	public ResponseEntity<String> callback(@RequestParam(required = false) String code,
			@RequestParam(required = false) String state, @RequestParam(required = false) String error) {
		String expected = pendingState.getAndSet(null); // a state can only be used once
		if (expected == null || !expected.equals(state)) {
			return page(HttpStatus.BAD_REQUEST, "Invalid or expired request. Start again from /setup/google.");
		}
		if (error != null || !StringUtils.hasText(code)) {
			return page(HttpStatus.BAD_REQUEST, "Google did not grant access. Start again from /setup/google.");
		}
		String refreshToken;
		try {
			refreshToken = oauth.exchangeForRefreshToken(code, redirectUri());
		}
		catch (IOException e) {
			log.warn("Google token exchange failed: {}", e.getMessage());
			return page(HttpStatus.BAD_GATEWAY, "Google rejected the authorization code. Start again.");
		}
		if (!StringUtils.hasText(refreshToken)) {
			return page(HttpStatus.BAD_GATEWAY, "Google returned no refresh token. Remove yt2pod's access at "
					+ "https://myaccount.google.com/permissions and start again.");
		}
		return htmlPage(HttpStatus.OK, "Copy this value into GOOGLE_REFRESH_TOKEN, then restart without setup mode. "
				+ "It is shown only once.<br><br><code>" + HtmlUtils.htmlEscape(refreshToken) + "</code>";
	}

	private String redirectUri() {
		return ServletUriComponentsBuilder.fromCurrentContextPath().path(CALLBACK_PATH).toUriString();
	}

	private static ResponseEntity<String> page(HttpStatus status, String body) {
		return htmlPage(status, HtmlUtils.htmlEscape(body));
	}

	private static ResponseEntity<String> htmlPage(HttpStatus status, String htmlBody) {
		return ResponseEntity.status(status).contentType(MediaType.TEXT_HTML).cacheControl(CacheControl.noStore())
				.body("<!doctype html><meta charset=\"utf-8\"><title>yt2pod setup</title><body style=\"font-family:"
						+ "sans-serif;max-width:40rem;margin:2rem auto;word-break:break-all\"><h1>yt2pod setup</h1><p>"
						+ htmlBody + "</p></body>");
	}

}
