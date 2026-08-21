package hieunv.dev.netflixstack.service;

import java.time.Duration;
import java.util.Optional;

public interface RedisService {

    Optional<String> get(String key);

    void set(String key, String value, Duration ttl);
}
