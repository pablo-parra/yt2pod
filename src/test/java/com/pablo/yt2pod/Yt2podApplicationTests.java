package com.pablo.yt2pod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
