package com.classpulse;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ClasspulseApplication {

	public static void main(String[] args) {
		SpringApplication.run(ClasspulseApplication.class, args);
	}

}
