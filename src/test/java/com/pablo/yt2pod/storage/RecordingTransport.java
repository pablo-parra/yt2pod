package com.pablo.yt2pod.storage;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.api.client.http.LowLevelHttpRequest;
import com.google.api.client.http.LowLevelHttpResponse;
import com.google.api.client.testing.http.MockHttpTransport;
import com.google.api.client.testing.http.MockLowLevelHttpRequest;
import com.google.api.client.testing.http.MockLowLevelHttpResponse;

/** Fake Google backend: records every HTTP call and answers with whatever the handler returns. */
class RecordingTransport extends MockHttpTransport {

	record Call(String method, String url, Map<String, List<String>> headers, String body) {
	}

	final List<Call> calls = new ArrayList<>();

	private final Function<Call, MockLowLevelHttpResponse> handler;

	RecordingTransport(Function<Call, MockLowLevelHttpResponse> handler) {
		this.handler = handler;
	}

	static MockLowLevelHttpResponse json(int status, String body) {
		return new MockLowLevelHttpResponse().setStatusCode(status).setContentType("application/json")
				.setContent(body);
	}

	static MockLowLevelHttpResponse token(String accessToken) {
		return json(200, "{\"access_token\":\"" + accessToken + "\",\"expires_in\":3600,\"token_type\":\"Bearer\"}");
	}

	@Override
	public LowLevelHttpRequest buildRequest(String method, String url) {
		return new MockLowLevelHttpRequest(url) {
			@Override
			public LowLevelHttpResponse execute() throws IOException {
				Call call = new Call(method, url, getHeaders(), getContentAsString());
				calls.add(call);
				return handler.apply(call);
			}
		};
	}

}
