package dev.hieunv.price_radar.config;

import dev.hieunv.price_radar.service.AlertNotificationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;

@Configuration
public class RedisPubSubConfig {

    @Bean
    public ChannelTopic alertTopic() {
        return new ChannelTopic("price-alerts");
    }

    @Bean
    public MessageListenerAdapter messageListenerAdapter(AlertNotificationListener listener) {
        return new MessageListenerAdapter(listener, "onAlertTriggered");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory factory,
            MessageListenerAdapter adapter,
            ChannelTopic topic) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(adapter, topic);
        return container;
    }
}
