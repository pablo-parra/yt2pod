package com.pablo.yt2pod;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import com.pablo.yt2pod.config.Yt2podProperties;
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
	void healthIsUp() throws Exception {
		mockMvc.perform(get("/actuator/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void defaultsAreSpeechOriented() {
		assertThat(properties.audio().format()).isEqualTo("m4a");
		assertThat(properties.audio().bitrate()).isEqualTo("64K");
		assertThat(properties.audio().mono()).isTrue();
		assertThat(properties.drive().folderName()).isEqualTo("yt2pod");
		assertThat(properties.jobs().maxConcurrent()).isEqualTo(2);
		assertThat(properties.jobs().ttl()).isEqualTo(Duration.ofHours(1));
		assertThat(properties.setup().enabled()).isFalse();
	}

}
