package com.zdb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Spring Boot Web Appication
 * 
 * @author 06919
 *
 */
@EntityScan
@EnableScheduling
@SpringBootApplication
@Configuration
public class ZDBRestApplication {
	public static void main(String[] args) {
		SpringApplication app = new SpringApplication(ZDBRestApplication.class);
//		app.addListeners(new ApplicationPidFileWriter());
		app.run(args);
		
	}
}
