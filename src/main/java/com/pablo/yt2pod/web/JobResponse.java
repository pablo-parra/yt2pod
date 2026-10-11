package com.pablo.yt2pod.web;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pablo.yt2pod.job.ConversionJob;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record JobResponse(String status, String fileName, String driveLink, String error, String failedStage) {

	static JobResponse of(ConversionJob job) {
		return new JobResponse(job.status().name(), job.fileName(), job.driveLink(), job.error(),
				job.failedStage() == null ? null : job.failedStage().name());
	}

}
