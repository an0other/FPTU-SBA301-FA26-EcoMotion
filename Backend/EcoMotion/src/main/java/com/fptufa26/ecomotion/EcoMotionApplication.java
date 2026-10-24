package com.fptufa26.ecomotion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class EcoMotionApplication {

	public static void main(String[] args) {
		SpringApplication.run(EcoMotionApplication.class, args);
	}

}
