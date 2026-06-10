package com.arena.cpj;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CpjApplication {

	public static void main(String[] args) {
		SpringApplication.run(CpjApplication.class, args);
	}

}
