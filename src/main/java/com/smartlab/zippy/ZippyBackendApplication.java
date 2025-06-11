package com.smartlab.zippy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ZippyBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ZippyBackendApplication.class, args);
	}
	
	@Override
	public String toString() {
		return "ZippyBackendApplication{}";
	}
}
