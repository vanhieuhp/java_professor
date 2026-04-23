package dev.hieunv.bankos;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.retry.annotation.EnableRetry;

@EnableKafkaStreams
@EnableRetry
@SpringBootApplication
public class BankosApplication {

    public static void main(String[] args) {
        SpringApplication.run(BankosApplication.class, args);
    }

}
