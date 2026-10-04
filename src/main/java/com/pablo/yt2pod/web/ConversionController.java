package com.pablo.yt2pod.web;

import java.net.URI;
import java.util.Map;

import com.pablo.yt2pod.job.ConversionJob;
import com.pablo.yt2pod.job.JobService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversions")
public class ConversionController {

	public record ConversionRequest(String url) {
	}

	private final JobService jobService;

	public ConversionController(JobService jobService) {
		this.jobService = jobService;
	}

	@PostMapping
	public ResponseEntity<Map<String, String>> submit(@RequestBody ConversionRequest request) {
		ConversionJob job = jobService.submit(request.url());
		return ResponseEntity.accepted().location(URI.create("/api/conversions/" + job.id()))
				.body(Map.of("jobId", job.id()));
	}

	@GetMapping("/{jobId}")
	public ResponseEntity<JobResponse> status(@PathVariable String jobId) {
		return jobService.find(jobId).map(job -> ResponseEntity.ok(JobResponse.of(job)))
				.orElseGet(() -> ResponseEntity.notFound().build());
	}

}
