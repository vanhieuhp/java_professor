package dev.hieunv.bankos.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {
    @Bean
    public NewTopic walletStatusTopic() {
        return TopicBuilder.name("wallet-status")
                .partitions(3)
                .replicas(1)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG,
                        TopicConfig.CLEANUP_POLICY_COMPACT)
                .build();
    }

    @Bean
    public NewTopic userRegisteredTopic() {
        return TopicBuilder.name("user.registered")
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentCompletedNotificationTopic() {
        return TopicBuilder.name("payment.completed")
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic walletLockedTopic() {
        return TopicBuilder.name("wallet.locked")
                .partitions(3).replicas(1).build();
    }
}
