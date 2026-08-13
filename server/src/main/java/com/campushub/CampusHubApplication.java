package com.campushub;

import io.mongock.runner.springboot.EnableMongock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@EnableMongock
@SpringBootApplication
public class CampusHubApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusHubApplication.class, args);
    }
}
