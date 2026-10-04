package com.pablo.yt2pod.job;

import java.time.Clock;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.pablo.yt2pod.config.Yt2podProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JobExecutorConfiguration {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

	/** At most {@code yt2pod.jobs.max-concurrent} conversions run at once; the rest wait in the queue. */
	@Bean(name = "conversionExecutor", destroyMethod = "shutdown")
	ThreadPoolExecutor conversionExecutor(Yt2podProperties properties) {
		int threads = properties.jobs().maxConcurrent();
		return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>(),
				runnable -> {
					Thread thread = new Thread(runnable, "conversion");
					thread.setDaemon(true);
					return thread;
				});
	}

}
