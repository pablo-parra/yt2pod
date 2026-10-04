package com.pablo.yt2pod;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Yt2podApplication {

	public static void main(String[] args) {
		SpringApplication.run(Yt2podApplication.class, args);
	}

}
