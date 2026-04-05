package dev.hieunv.two_databases;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TwoDatabasesApplicationTests {

    @Test
    void contextLoads() {
    }

}
