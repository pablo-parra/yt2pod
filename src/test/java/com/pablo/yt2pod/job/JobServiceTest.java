package com.pablo.yt2pod.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import com.pablo.yt2pod.TestProperties;
import com.pablo.yt2pod.config.Yt2podProperties;
import com.pablo.yt2pod.extract.AudioExtractor;
import com.pablo.yt2pod.extract.ExtractedAudio;
import com.pablo.yt2pod.extract.ExtractionException;
import com.pablo.yt2pod.extract.ExtractionFailure;
import com.pablo.yt2pod.extract.InvalidYoutubeUrlException;
import com.pablo.yt2pod.extract.YoutubeUrlValidator;
import com.pablo.yt2pod.storage.DriveAuthorizationException;
import com.pablo.yt2pod.storage.DriveNotConfiguredException;
import com.pablo.yt2pod.storage.DriveUploadException;
import com.pablo.yt2pod.storage.DriveUploader;
import com.pablo.yt2pod.storage.UploadedFile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The whole conversion flow with fake extractor/uploader: US-05, US-06, US-07, US-09, US-11, US-14 and the NFRs. */
class JobServiceTest {

	private static final String URL = "https://youtu.be/dQw4w9WgXcQ";

	private final Yt2podProperties props = TestProperties.defaults();

	private final ManualExecutor executor = new ManualExecutor();

	private final List<Path> usedDirs = new ArrayList<>();

	private final List<String> extractedUrls = new ArrayList<>();

	private final List<String> uploadedNames = new ArrayList<>();

	private final List<JobStatus> statusSeenByExtractor = new ArrayList<>();

	private final List<JobStatus> statusSeenByUploader = new ArrayList<>();

	private RuntimeException extractionFailure;

	private RuntimeException uploadFailure;

	private ConversionJob lastJob;

	private final AudioExtractor extractor = (url, dir) -> {
		usedDirs.add(dir);
		extractedUrls.add(url);
		statusSeenByExtractor.add(lastJob.status());
		if (extractionFailure != null) {
			throw extractionFailure;
		}
		try {
			Path file = Files.writeString(dir.resolve("My Episode.m4a"), "audio");
			Files.writeString(dir.resolve("leftover.webp"), "x");
			return new ExtractedAudio(file, "My Episode");
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
	};

	private final DriveUploader uploader = (file, name) -> {
		statusSeenByUploader.add(lastJob.status());
		assertThat(file).exists();
		if (uploadFailure != null) {
			throw uploadFailure;
		}
		uploadedNames.add(name);
		return new UploadedFile("id-1", name, "https://drive.google.com/file/1");
	};

	private JobRegistry registry = new JobRegistry(Clock.systemUTC(), props);

	private final JobService service = new JobService(new YoutubeUrlValidator(), extractor, uploader, registry,
			executor, props);

	private ConversionJob submit(String url) {
		lastJob = service.submit(url);
		return lastJob;
	}

	@Test
	@DisplayName("US-05 submitting returns immediately with a QUEUED job (the conversion is asynchronous)")
	void US05_submitReturnsImmediatelyWithQueuedJob() {
		ConversionJob job = submit(URL);
		assertThat(job.id()).isNotBlank();
		assertThat(job.status()).isEqualTo(JobStatus.QUEUED);
		assertThat(extractedUrls).isEmpty();
		assertThat(service.find(job.id())).containsSame(job);
	}

	@Test
	@DisplayName("US-06 an invalid link is rejected immediately and no job is created")
	void US06_invalidLinkIsRejectedWithoutCreatingAJob() {
		assertThatThrownBy(() -> service.submit("https://vimeo.com/1")).isInstanceOf(InvalidYoutubeUrlException.class);
		assertThat(executor.pending()).isZero();
	}

	@Test
	@DisplayName("US-11 the job goes QUEUED -> EXTRACTING -> UPLOADING -> DONE")
	void US11_statusProgressesThroughTheStages() {
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(statusSeenByExtractor).containsExactly(JobStatus.EXTRACTING);
		assertThat(statusSeenByUploader).containsExactly(JobStatus.UPLOADING);
		assertThat(job.status()).isEqualTo(JobStatus.DONE);
	}

	@Test
	@DisplayName("US-11 a finished job exposes the Drive file name and link")
	void US11_doneJobHasFileNameAndLink() {
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.fileName()).isEqualTo("My Episode.m4a");
		assertThat(job.driveLink()).isEqualTo("https://drive.google.com/file/1");
		assertThat(job.error()).isNull();
	}

	@Test
	@DisplayName("US-07/US-09 the extracted audio file (not the thumbnail) is uploaded under its own file name")
	void US09_uploadsTheExtractedAudio() {
		submit(URL);
		executor.runAll();
		assertThat(uploadedNames).containsExactly("My Episode.m4a");
	}

	@Test
	@DisplayName("US-07 the extractor receives the canonical url")
	void US07_extractorReceivesCanonicalUrl() {
		submit("https://youtu.be/dQw4w9WgXcQ?si=tracking");
		executor.runAll();
		assertThat(extractedUrls).containsExactly("https://www.youtube.com/watch?v=dQw4w9WgXcQ");
	}

	@Test
	@DisplayName("NFR temporary files are deleted after a successful conversion")
	void NFR_tempFilesDeletedAfterSuccess() {
		submit(URL);
		executor.runAll();
		assertThat(usedDirs).hasSize(1).allSatisfy(dir -> assertThat(dir).doesNotExist());
	}

	@Test
	@DisplayName("NFR temporary files are deleted after a failed extraction")
	void NFR_tempFilesDeletedAfterExtractionFailure() {
		extractionFailure = new ExtractionException(ExtractionFailure.PRIVATE);
		submit(URL);
		executor.runAll();
		assertThat(usedDirs).hasSize(1).allSatisfy(dir -> assertThat(dir).doesNotExist());
	}

	@Test
	@DisplayName("NFR temporary files are deleted after a failed upload")
	void NFR_tempFilesDeletedAfterUploadFailure() {
		uploadFailure = new DriveUploadException(new IOException("boom"));
		submit(URL);
		executor.runAll();
		assertThat(usedDirs).hasSize(1).allSatisfy(dir -> assertThat(dir).doesNotExist());
	}

	@Test
	@DisplayName("US-11 an extraction failure is reported with a human-readable reason")
	void US11_extractionFailureReason() {
		extractionFailure = new ExtractionException(ExtractionFailure.AGE_RESTRICTED);
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(job.error()).isEqualTo(ExtractionFailure.AGE_RESTRICTED.message());
		assertThat(uploadedNames).isEmpty();
	}

	@Test
	@DisplayName("US-11 a failed extraction records that it failed during EXTRACTING (for the UI phase list)")
	void US11_failedStageIsExtracting() {
		extractionFailure = new ExtractionException(ExtractionFailure.PRIVATE);
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.failedStage()).isEqualTo(JobStatus.EXTRACTING);
	}

	@Test
	@DisplayName("US-11 a failed upload records that it failed during UPLOADING (for the UI phase list)")
	void US11_failedStageIsUploading() {
		uploadFailure = new DriveUploadException(new IOException("boom"));
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.failedStage()).isEqualTo(JobStatus.UPLOADING);
	}

	@Test
	@DisplayName("US-11 a successful job has no failed stage")
	void US11_successHasNoFailedStage() {
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.failedStage()).isNull();
	}

	@Test
	@DisplayName("US-14 a video over the duration limit fails with the limit message")
	void US14_limitExceededIsReported() {
		extractionFailure = new ExtractionException(ExtractionFailure.LIMIT_EXCEEDED);
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.error()).contains("maximum allowed duration");
	}

	@Test
	@DisplayName("US-11/US-03 revoked Drive authorization is reported as 'redo setup'")
	void US03_revokedAuthorizationIsReported() {
		uploadFailure = new DriveAuthorizationException(new RuntimeException("invalid_grant"));
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(job.error()).contains("Redo the one-off setup");
	}

	@Test
	@DisplayName("US-01/US-11 Drive not configured is reported to the Client")
	void US01_notConfiguredDriveIsReported() {
		uploadFailure = new DriveNotConfiguredException();
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.error()).contains("not configured");
	}

	@Test
	@DisplayName("US-11 an unexpected error gives a generic message that does not leak internals or the url")
	void US11_unexpectedErrorIsGeneric() {
		extractionFailure = new IllegalStateException("secret detail about " + URL);
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.status()).isEqualTo(JobStatus.FAILED);
		assertThat(job.error()).isEqualTo("Unexpected error while converting the video.")
				.doesNotContain("secret").doesNotContain("youtu");
	}

	@Test
	@DisplayName("NFR no link persistence: the job holds no URL, title or video information")
	void NFR_jobDoesNotKeepTheUrl() {
		ConversionJob job = submit(URL);
		executor.runAll();
		assertThat(job.toString() + job.fileName() + job.driveLink() + job.error()).doesNotContain("dQw4w9WgXcQ");
		assertThat(ConversionJob.class.getDeclaredFields()).extracting(f -> f.getName())
				.doesNotContain("url", "title", "videoId");
	}

	@Test
	@DisplayName("NFR too many unfinished jobs are refused (max-concurrent x 5)")
	void NFR_tooManyJobsAreRefused() {
		int capacity = props.jobs().maxConcurrent() * JobService.QUEUE_FACTOR;
		for (int i = 0; i < capacity; i++) {
			submit(URL);
		}
		assertThatThrownBy(() -> service.submit(URL)).isInstanceOf(TooManyJobsException.class);
		executor.runNext(); // one finishes, a slot frees up
		assertThat(service.submit(URL)).isNotNull();
	}

	@Test
	@DisplayName("NFR jobs are forgotten after the TTL (nothing is kept)")
	void NFR_finishedJobsExpireAfterTtl() {
		MutableClock clock = new MutableClock();
		JobRegistry timedRegistry = new JobRegistry(clock, props);
		JobService timedService = new JobService(new YoutubeUrlValidator(), extractor, uploader, timedRegistry,
				executor, props);
		lastJob = timedService.submit(URL);
		String id = lastJob.id();
		executor.runAll();
		clock.advance(props.jobs().ttl().minusSeconds(1));
		assertThat(timedService.find(id)).isPresent();
		clock.advance(Duration.ofSeconds(2));
		assertThat(timedService.find(id)).isEmpty();
	}

}
