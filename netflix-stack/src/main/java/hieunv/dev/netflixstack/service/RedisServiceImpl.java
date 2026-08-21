package hieunv.dev.netflixstack.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

/**
 * See {@link RedisService}. Backed by the {@link StringRedisTemplate} that
 * spring-boot-starter-data-redis auto-configures from the
 * {@code spring.data.redis.*} properties, so connection pooling and the
 * 200ms timeout in application.properties apply here too.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisServiceImpl implements RedisService {

    private final StringRedisTemplate redisTemplate;

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(key, value, ttl);
    }
}
