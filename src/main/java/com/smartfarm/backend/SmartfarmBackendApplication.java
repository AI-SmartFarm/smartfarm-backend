package com.smartfarm.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class SmartfarmBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(SmartfarmBackendApplication.class, args);
	}

}
