package hieunv.dev.netflixstack.counter.reader;

import hieunv.dev.netflixstack.counter.rollup.RollupStore;
import hieunv.dev.netflixstack.counter.rollup.dto.RollupCheckpoint;
import hieunv.dev.netflixstack.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;


@Service("eventual")
@RequiredArgsConstructor
@Slf4j
public class EventuallyConsistentReaderServiceImpl implements CounterReaderService {

    private static final String KEY_PREFIX = "rollup:";

    private final RollupStore rollupStore;
    private final RedisService redisService;

    @Value("${netflix-stack.counter.reader.cache-ttl:5s}")
    private Duration cacheTtl;

    @Override
    public long getCount(String counterId) {
        String key = KEY_PREFIX + counterId;

        Long cached = readCache(key);
        if (cached != null) {
            log.debug("reader[{}] cache HIT -> {}", counterId, cached);
            return cached;
        }

        RollupCheckpoint checkpoint = rollupStore.getCheckpoint(counterId);
        long count = checkpoint.lastRollupCount();
        log.debug("reader[{}] cache MISS -> checkpoint count={} ts={}",
                counterId, count, checkpoint.lastRollupTs());

        writeCache(key, count);
        return count;
    }

    private Long readCache(String key) {
        Optional<String> raw;
        try {
            raw = redisService.get(key);
        } catch (RuntimeException e) {
            log.warn("cache read failed for {}, falling through to Cassandra: {}", key, e.toString());
            return null;
        }
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Long.parseLong(raw.get());
        } catch (NumberFormatException e) {
            log.warn("cache holds a non-numeric value for {}: '{}' - treating as a miss", key, raw.get());
            return null;
        }
    }

    private void writeCache(String key, long count) {
        try {
            redisService.set(key, Long.toString(count), cacheTtl);
        } catch (RuntimeException e) {
            log.warn("cache write failed for {}: {}", key, e.toString());
        }
    }
}
