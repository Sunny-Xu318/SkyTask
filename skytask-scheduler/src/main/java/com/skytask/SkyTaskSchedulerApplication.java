package com.skytask;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.skytask")
@EnableScheduling
public class SkyTaskSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyTaskSchedulerApplication.class, args);
    }
}
