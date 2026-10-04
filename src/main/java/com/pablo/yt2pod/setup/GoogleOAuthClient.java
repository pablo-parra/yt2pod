package com.pablo.yt2pod.setup;

import java.io.IOException;

/** The two Google calls of the one-off authorization flow. */
public interface GoogleOAuthClient {

	String authorizationUrl(String redirectUri, String state);

	/** @return the refresh token, or {@code null} if Google did not return one */
	String exchangeForRefreshToken(String code, String redirectUri) throws IOException;

}
