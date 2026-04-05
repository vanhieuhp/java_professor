package dev.hieunv.two_databases;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles(profiles = "test")
@Testcontainers
public abstract class BaseIntegrationTest {

    @Container
    static PostgreSQLContainer<?> primaryPostgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("bankos_primary")
            .withUsername("bankos")
            .withPassword("bankos123");

    @Container
    static PostgreSQLContainer<?> secondaryPostgres =
            new PostgreSQLContainer<>("postgres:15")
                    .withDatabaseName("bankos_secondary")
                    .withUsername("bankos")
                    .withPassword("bankos123");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.primary.jdbc-url",
                primaryPostgres::getJdbcUrl);
        registry.add("spring.datasource.primary.username",
                primaryPostgres::getUsername);
        registry.add("spring.datasource.primary.password",
                primaryPostgres::getPassword);

        registry.add("spring.datasource.secondary.jdbc-url",
                secondaryPostgres::getJdbcUrl);
        registry.add("spring.datasource.secondary.username",
                secondaryPostgres::getUsername);
        registry.add("spring.datasource.secondary.password",
                secondaryPostgres::getPassword);
    }
}
