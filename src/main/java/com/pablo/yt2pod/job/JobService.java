package com.pablo.yt2pod.job;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.stream.Stream;

import com.pablo.yt2pod.config.Yt2podProperties;
import com.pablo.yt2pod.extract.AudioExtractor;
import com.pablo.yt2pod.extract.ExtractedAudio;
import com.pablo.yt2pod.extract.ExtractionException;
import com.pablo.yt2pod.extract.YoutubeUrlValidator;
import com.pablo.yt2pod.storage.DriveException;
import com.pablo.yt2pod.storage.DriveUploader;
import com.pablo.yt2pod.storage.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class JobService {

	private static final Logger log = LoggerFactory.getLogger(JobService.class);

	/** Unfinished jobs allowed = running slots times this factor. */
	static final int QUEUE_FACTOR = 5;

	private final YoutubeUrlValidator validator;

	private final AudioExtractor extractor;

	private final DriveUploader uploader;

	private final JobRegistry registry;

	private final Executor executor;

	private final Yt2podProperties properties;

	public JobService(YoutubeUrlValidator validator, AudioExtractor extractor, DriveUploader uploader,
			JobRegistry registry, @Qualifier("conversionExecutor") Executor executor, Yt2podProperties properties) {
		this.validator = validator;
		this.extractor = extractor;
		this.uploader = uploader;
		this.registry = registry;
		this.executor = executor;
		this.properties = properties;
	}

	/**
	 * Validates the link and queues the conversion. The URL only lives in the queued task and is never
	 * stored on the job.
	 */
	public ConversionJob submit(String url) {
		String canonicalUrl = validator.canonicalize(url);
		ConversionJob job = registry.create(properties.jobs().maxConcurrent() * QUEUE_FACTOR);
		executor.execute(() -> run(job, canonicalUrl));
		return job;
	}

	public Optional<ConversionJob> find(String jobId) {
		return registry.find(jobId);
	}

	private void run(ConversionJob job, String canonicalUrl) {
		Path dir = null;
		try {
			dir = Files.createTempDirectory("yt2pod-");
			job.extracting();
			ExtractedAudio audio = extractor.extract(canonicalUrl, dir);
			job.uploading();
			UploadedFile uploaded = uploader.upload(audio.file(), audio.file().getFileName().toString());
			job.done(uploaded.name(), uploaded.webViewLink());
			log.info("Job {} done", job.id());
		}
		catch (ExtractionException | DriveException e) {
			log.warn("Job {} failed: {}", job.id(), e.getMessage());
			job.failed(e.getMessage());
		}
		catch (Exception e) {
			// no stack trace or message: they could echo the submitted URL
			log.warn("Job {} failed unexpectedly ({})", job.id(), e.getClass().getSimpleName());
			job.failed("Unexpected error while converting the video.");
		}
		finally {
			deleteQuietly(dir);
		}
	}

	private static void deleteQuietly(Path dir) {
		if (dir == null) {
			return;
		}
		try (Stream<Path> paths = Files.walk(dir)) {
			paths.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
		}
		catch (IOException e) {
			log.warn("Could not delete temporary directory");
		}
	}

}
