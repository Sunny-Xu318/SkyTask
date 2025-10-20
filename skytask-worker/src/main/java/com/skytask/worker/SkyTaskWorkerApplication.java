package com.skytask.worker;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.skytask.worker")
public class SkyTaskWorkerApplication {

    public static void main(String[] args) {
        SpringApplication.run(SkyTaskWorkerApplication.class, args);
    }
}
