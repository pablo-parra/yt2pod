package com.pablo.yt2pod.web;

import java.util.Map;

import com.pablo.yt2pod.extract.InvalidYoutubeUrlException;
import com.pablo.yt2pod.job.TooManyJobsException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

	@ExceptionHandler(InvalidYoutubeUrlException.class)
	ResponseEntity<Map<String, String>> invalidUrl(InvalidYoutubeUrlException e) {
		return error(HttpStatus.BAD_REQUEST, e.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	ResponseEntity<Map<String, String>> unreadableBody() {
		return error(HttpStatus.BAD_REQUEST, "Send a JSON body like {\"url\": \"https://youtu.be/...\"}.");
	}

	@ExceptionHandler(TooManyJobsException.class)
	ResponseEntity<Map<String, String>> tooMany(TooManyJobsException e) {
		return error(HttpStatus.TOO_MANY_REQUESTS, e.getMessage());
	}

	private static ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
		return ResponseEntity.status(status).body(Map.of("error", message));
	}

}
