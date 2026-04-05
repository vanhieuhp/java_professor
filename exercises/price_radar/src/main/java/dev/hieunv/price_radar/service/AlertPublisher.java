package dev.hieunv.price_radar.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertPublisher {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChannelTopic alertTopic;

    public void publish(String userId, String product, String alertId, double threshold, double actualPrice, String supplier) {
        Map<String, Object> message = Map.of(
            "userId",      userId,
            "alertId",     alertId,
            "product",     product,
            "threshold",   threshold,
            "actualPrice", actualPrice,
            "supplier",    supplier,
            "timestamp",   Instant.now().toString()
        );
        redisTemplate.convertAndSend(alertTopic.getTopic(), message);
        log.info("Published alert to Redis channel: userId={} product={} price={}", userId, product, actualPrice);
    }
}
