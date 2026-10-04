package com.pablo.yt2pod.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.time.Clock;
import java.util.concurrent.Executor;

import com.pablo.yt2pod.TestProperties;
import com.pablo.yt2pod.extract.ExtractedAudio;
import com.pablo.yt2pod.extract.ExtractionException;
import com.pablo.yt2pod.extract.ExtractionFailure;
import com.pablo.yt2pod.extract.YoutubeUrlValidator;
import com.pablo.yt2pod.job.JobRegistry;
import com.pablo.yt2pod.job.JobService;
import com.pablo.yt2pod.storage.UploadedFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** HTTP API: US-05 (submit), US-06 (validation errors), US-11 (status). Jobs run synchronously here. */
class ConversionControllerTest {

	private static final String GOOD = "{\"url\":\"https://youtu.be/dQw4w9WgXcQ\"}";

	private RuntimeException extractionFailure;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		Executor sameThread = Runnable::run;
		var props = TestProperties.defaults();
		JobService service = new JobService(new YoutubeUrlValidator(), (url, dir) -> {
			if (extractionFailure != null) {
				throw extractionFailure;
			}
			try {
				return new ExtractedAudio(Files.writeString(dir.resolve("Episode.m4a"), "a"), "Episode");
			}
			catch (java.io.IOException e) {
				throw new IllegalStateException(e);
			}
		}, (file, name) -> new UploadedFile("1", name, "https://drive.google.com/f1"),
				new JobRegistry(Clock.systemUTC(), props), sameThread, props);
		mvc = MockMvcBuilders.standaloneSetup(new ConversionController(service))
				.setControllerAdvice(new ApiExceptionHandler()).build();
	}

	private String submitAndGetId(String body) throws Exception {
		String response = mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
		return response.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");
	}

	@Test
	@DisplayName("US-05 POST /api/conversions answers 202 Accepted with a job id and its status location")
	void US05_postReturns202WithJobId() throws Exception {
		mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON).content(GOOD))
				.andExpect(status().isAccepted()).andExpect(jsonPath("$.jobId").isNotEmpty())
				.andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/conversions/")));
	}

	@Test
	@DisplayName("US-05 accepts the youtube.com, m.youtube.com and youtu.be forms")
	void US05_acceptsSeveralLinkForms() throws Exception {
		for (String url : new String[] { "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
				"https://m.youtube.com/watch?v=dQw4w9WgXcQ", "https://youtu.be/dQw4w9WgXcQ" }) {
			mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON)
					.content("{\"url\":\"" + url + "\"}")).andExpect(status().isAccepted());
		}
	}

	@Test
	@DisplayName("US-06 a non-YouTube link is answered with 400 and a clear message")
	void US06_nonYoutubeLinkIs400() throws Exception {
		mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"https://vimeo.com/1\"}")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error").value("Only YouTube links are supported."));
	}

	@Test
	@DisplayName("US-06 a playlist link is answered with 400")
	void US06_playlistIs400() throws Exception {
		mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"https://www.youtube.com/playlist?list=PLabcdefghijk\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
	}

	@Test
	@DisplayName("US-06 a missing or blank url is answered with 400")
	void US06_missingUrlIs400() throws Exception {
		mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON).content("{\"url\":\"  \"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("US-06 a body that is not JSON is answered with 400")
	void US06_malformedBodyIs400() throws Exception {
		mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON).content("not json"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").isNotEmpty());
	}

	@Test
	@DisplayName("US-11 GET /api/conversions/{id} returns DONE with the Drive file name and link")
	void US11_statusOfFinishedJob() throws Exception {
		String id = submitAndGetId(GOOD);
		mvc.perform(get("/api/conversions/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE")).andExpect(jsonPath("$.fileName").value("Episode.m4a"))
				.andExpect(jsonPath("$.driveLink").value("https://drive.google.com/f1"))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	@Test
	@DisplayName("US-11 GET /api/conversions/{id} returns FAILED with a readable error")
	void US11_statusOfFailedJob() throws Exception {
		extractionFailure = new ExtractionException(ExtractionFailure.PRIVATE);
		String id = submitAndGetId(GOOD);
		mvc.perform(get("/api/conversions/" + id)).andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED")).andExpect(jsonPath("$.error").value("The video is private."))
				.andExpect(jsonPath("$.fileName").doesNotExist());
	}

	@Test
	@DisplayName("US-11 an unknown (or expired) job id is a 404")
	void US11_unknownJobIs404() throws Exception {
		mvc.perform(get("/api/conversions/does-not-exist")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("NFR no link persistence: neither the submit nor the status response echoes the url")
	void NFR_responsesDoNotEchoTheUrl() throws Exception {
		String submit = mvc.perform(post("/api/conversions").contentType(MediaType.APPLICATION_JSON).content(GOOD))
				.andReturn().getResponse().getContentAsString();
		String id = submit.replaceAll(".*\"jobId\":\"([^\"]+)\".*", "$1");
		String status = mvc.perform(get("/api/conversions/" + id)).andReturn().getResponse().getContentAsString();
		assertThat(submit + status).doesNotContain("youtu").doesNotContain("dQw4w9WgXcQ");
	}

}
