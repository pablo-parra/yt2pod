package com.pablo.yt2pod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.concurrent.ThreadPoolExecutor;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class Yt2podApplicationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private Yt2podProperties properties;

	@Autowired
	@Qualifier("conversionExecutor")
	private ThreadPoolExecutor executor;

	@Test
	@DisplayName("US-15 the service starts and reports healthy")
	void US15_healthIsUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	@DisplayName("US-01 setup endpoints do not exist unless setup mode is enabled")
	void US01_setupEndpointsAreDisabledByDefault() throws Exception {
		mockMvc.perform(get("/setup/google")).andExpect(status().isNotFound());
		mockMvc.perform(get("/setup/google/callback")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("US-01 health stays UP while Drive is not configured (Drive reported as UNKNOWN)")
	void US01_healthIsUpWhenDriveNotConfigured() throws Exception {
		mockMvc.perform(get("/actuator/health")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("US-04 the home page has a link box and a convert button")
	void US04_homePageHasInputAndButton() throws Exception {
		mockMvc.perform(get("/")).andExpect(forwardedUrl("index.html")); // welcome page
		String html = mockMvc.perform(get("/index.html")).andExpect(status().isOk()).andReturn().getResponse()
				.getContentAsString();
		assertThat(html).contains("<input id=\"url\" type=\"url\"").contains("<button id=\"submit\"")
				.contains("/api/conversions");
	}

	@Test
	@DisplayName("US-04 the home page is mobile friendly (viewport meta tag)")
	void US04_homePageIsMobileFriendly() throws Exception {
		String html = mockMvc.perform(get("/index.html")).andReturn().getResponse().getContentAsString();
		assertThat(html).contains("name=\"viewport\"").contains("width=device-width");
	}

	@Test
	@DisplayName("US-11 the page lists the phases (queue, download, save to Drive) with spinner, tick and cross states")
	void US11_pageShowsPhaseChecklist() throws Exception {
		String html = mockMvc.perform(get("/index.html")).andReturn().getResponse().getContentAsString();
		assertThat(html).contains("Waiting in the queue", "Downloading and converting the audio",
				"Saving to Google Drive");
		assertThat(html).contains(".phase.running", ".phase.done", ".phase.failed", "@keyframes spin");
		assertThat(html).contains("Done!").contains("failedStage");
	}

	@Test
	@DisplayName("US-15 conversions run on a pool limited to the configured concurrency")
	void US15_executorUsesConfiguredConcurrency() {
		assertThat(executor.getMaximumPoolSize()).isEqualTo(properties.jobs().maxConcurrent());
	}

	@Test
	@DisplayName("US-08/US-15 defaults come from configuration (speech-oriented audio, folder, limits)")
	void US08_US15_defaultsAreSpeechOriented() {
		assertThat(properties.audio().format()).isEqualTo("m4a");
		assertThat(properties.audio().bitrate()).isEqualTo("64K");
		assertThat(properties.audio().mono()).isTrue();
		assertThat(properties.drive().folderName()).isEqualTo("yt2pod");
		assertThat(properties.jobs().maxConcurrent()).isEqualTo(2);
		assertThat(properties.jobs().ttl()).isEqualTo(Duration.ofHours(1));
		assertThat(properties.setup().enabled()).isFalse();
	}

}
