package dev.hieunv.bankos.mornitor;


import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class KafkaLagMonitor {

    private final KafkaAdmin kafkaAdmin;

    private static final Map<String, Long> LAG_ALERT_THRESHOLDS = Map.of(
            "bankos-payment-group", 1000L,
            "bankos-notification-group", 50L,
            "bankos-audit-group", 10000L,
            "bankos-fraud-group", 500L
    );

    private AdminClient adminClient;

    @PostConstruct
    public void init() {
        Map<String, Object> props = kafkaAdmin.getConfigurationProperties();
        log.info("[AdminClient] Connecting to: {}",
                props.get(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG));
        adminClient = AdminClient.create(props);
    }

    @PreDestroy
    public void destroy() {
        if (adminClient != null) {
            adminClient.close();
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void checkConsumerLag() {
        try {
            for (String groupId : LAG_ALERT_THRESHOLDS.keySet()) {
                try {
                    Map<TopicPartition, OffsetAndMetadata> offsets = adminClient
                            .listConsumerGroupOffsets(groupId)
                            .partitionsToOffsetAndMetadata()
                            .get(10, TimeUnit.SECONDS);

                    if (offsets.isEmpty()) {
                        return;
                    }

                    Map<TopicPartition, OffsetSpec> topicPartitionOffsets = offsets.keySet().stream().collect(
                            Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

                    Map<TopicPartition, Long> endOffsets = adminClient
                            .listOffsets(topicPartitionOffsets)
                            .all()
                            .get(10, TimeUnit.SECONDS)
                            .entrySet().stream()
                            .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().offset()));

                    long totalLag = offsets.entrySet().stream()
                            .mapToLong(entry -> {
                                long endOffset = endOffsets.getOrDefault(entry.getKey(), 0L);
                                long currentOffset = entry.getValue().offset();
                                return Math.max(0, endOffset - currentOffset);
                            }).sum();

                    long threshold = LAG_ALERT_THRESHOLDS.get(groupId);

                    if (totalLag > threshold) {
                        log.warn("[LAG ALERT] group={} lag={} threshold={}", groupId, totalLag, threshold);
                    } else {
                        log.info("[LAG] group={} lag={}", groupId, totalLag);
                    }
                } catch (Exception e) {
                    log.error("[LAG] Failed to check lag for group={} error={}", groupId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("[LAG] AdminClient error: {}", e.getMessage());
        }
    }
}
