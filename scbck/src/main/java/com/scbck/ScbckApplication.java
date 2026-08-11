package com.scbck;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ScbckApplication {

	public static void main(String[] args) {
		SpringApplication.run(ScbckApplication.class, args);

		// Prints a message to the console when the application starts successfully
		System.out.println("School Application Started");
	}

}
