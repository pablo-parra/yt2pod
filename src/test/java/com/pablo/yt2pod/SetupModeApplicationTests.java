package com.pablo.yt2pod;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/** The real application context with setup mode on (what `YT2POD_SETUP_ENABLED=true` does). */
@SpringBootTest(properties = { "yt2pod.setup.enabled=true", "yt2pod.google.client-id=test-client-id",
		"yt2pod.google.client-secret=test-secret" })
@AutoConfigureMockMvc
class SetupModeApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	@DisplayName("US-01 with setup mode enabled the application starts and /setup/google redirects to Google")
	void US01_setupModeStartsAndRedirectsToGoogle() throws Exception {
		mockMvc.perform(get("/setup/google")).andExpect(status().isFound())
				.andExpect(header().string("Location",
						org.hamcrest.Matchers.startsWith("https://accounts.google.com/o/oauth2/")))
				.andExpect(header().string("Location", org.hamcrest.Matchers.containsString("client_id=test-client-id")))
				.andExpect(header().string("Location",
						org.hamcrest.Matchers.containsString("redirect_uri=http://localhost/setup/google/callback")));
	}

}
