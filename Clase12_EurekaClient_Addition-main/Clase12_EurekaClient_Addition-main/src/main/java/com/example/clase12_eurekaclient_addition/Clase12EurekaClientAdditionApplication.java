package com.example.clase12_eurekaclient_addition;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class Clase12EurekaClientAdditionApplication {

    public static void main(String[] args) {
        SpringApplication.run(Clase12EurekaClientAdditionApplication.class, args);
    }

}

