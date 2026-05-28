package com.bujirun.bujirun;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BujirunApplication {

	public static void main(String[] args) {
		SpringApplication.run(BujirunApplication.class, args);
	}

}
