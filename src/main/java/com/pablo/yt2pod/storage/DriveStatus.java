package com.pablo.yt2pod.storage;

import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

/** Remembers whether Google last rejected our authorization, for the health endpoint. */
@Component
public class DriveStatus {

	private final AtomicBoolean authorizationFailed = new AtomicBoolean();

	public void authorizationFailed() {
		authorizationFailed.set(true);
	}

	public void authorizationWorks() {
		authorizationFailed.set(false);
	}

	public boolean isAuthorizationFailed() {
		return authorizationFailed.get();
	}

}
