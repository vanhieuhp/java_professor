package dev.hieunv.riskassessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@EnableResilientMethods
@SpringBootApplication
public class RiskAssessmentApplication {

    public static void main(String[] args) {
        SpringApplication.run(RiskAssessmentApplication.class, args);
    }

}
