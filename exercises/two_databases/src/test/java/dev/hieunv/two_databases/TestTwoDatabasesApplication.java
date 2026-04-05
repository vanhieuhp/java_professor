package dev.hieunv.two_databases;

import org.springframework.boot.SpringApplication;

public class TestTwoDatabasesApplication {

    public static void main(String[] args) {
        SpringApplication.from(TwoDatabasesApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
