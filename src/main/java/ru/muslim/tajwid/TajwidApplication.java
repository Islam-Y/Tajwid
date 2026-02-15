package ru.muslim.tajwid;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TajwidApplication {

	public static void main(String[] args) {
		SpringApplication.run(TajwidApplication.class, args);
	}

}
