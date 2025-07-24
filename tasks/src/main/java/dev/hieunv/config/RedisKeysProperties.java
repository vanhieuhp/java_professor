package dev.hieunv.config;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Data
@Component
@ConfigurationProperties(prefix = "redis.keys")
@Validated
public class RedisKeysProperties {

    @NotNull
    private KeyParameters cardInfo;

    @Data
    @Validated
    public static class KeyParameters {
        @NotNull
        private Duration timeToLive;
    }
}
