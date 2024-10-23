package com.exchange;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class OrderbookApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderbookApplication.class, args);
    }

}
