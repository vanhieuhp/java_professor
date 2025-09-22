package dev.hieunv.uploadfile;

import dev.hieunv.uploadfile.storage.StorageProperties;
import dev.hieunv.uploadfile.storage.StorageService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@EnableConfigurationProperties(StorageProperties.class)
@SpringBootApplication
public class UploadfileApplication {

    public static void main(String[] args) {
        SpringApplication.run(UploadfileApplication.class, args);
    }

    @Bean
    CommandLineRunner init(StorageService storageService) {
        return (args) -> {
            storageService.deleteAll();
            storageService.init();
        };
    }
}
