package com.jobgraph;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.jobgraph.config.AppProperties;

@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class JobGraphApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobGraphApplication.class, args);
    }
}
