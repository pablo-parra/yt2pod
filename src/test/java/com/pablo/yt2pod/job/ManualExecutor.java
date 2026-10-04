package com.pablo.yt2pod.job;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executor;

/** Runs queued tasks only when told to, so tests control the timing. */
class ManualExecutor implements Executor {

	private final Queue<Runnable> tasks = new ArrayDeque<>();

	@Override
	public void execute(Runnable command) {
		tasks.add(command);
	}

	int pending() {
		return tasks.size();
	}

	void runNext() {
		tasks.remove().run();
	}

	void runAll() {
		while (!tasks.isEmpty()) {
			runNext();
		}
	}

}
