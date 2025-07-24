package dev.hieunv.config;

import dev.hieunv.domain.entity.CardInfoEntity;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisKeyValueAdapter;
import org.springframework.data.redis.core.convert.KeyspaceConfiguration;
import org.springframework.data.redis.core.convert.MappingConfiguration;
import org.springframework.data.redis.core.index.IndexConfiguration;
import org.springframework.data.redis.core.mapping.RedisMappingContext;
import org.springframework.data.redis.repository.configuration.EnableRedisRepositories;

import java.util.Collections;

@Configuration
@RequiredArgsConstructor
@EnableRedisRepositories(enableKeyspaceEvents = RedisKeyValueAdapter.EnableKeyspaceEvents.ON_STARTUP)
public class RedisConfiguration {

    private final RedisKeysProperties properties;

    @Bean
    public RedisMappingContext keyValueMappingContext() {
        return new RedisMappingContext(
                new MappingConfiguration(new IndexConfiguration(), new CustomKeyspaceConfiguration())
        );
    }

    public class CustomKeyspaceConfiguration extends KeyspaceConfiguration {
        @Override
        protected Iterable<KeyspaceSettings> initialConfiguration() {
            return Collections.singleton(customKeyspaceSettings(CardInfoEntity.class, CacheName.CARD_INFO));
        }

        private <T> KeyspaceSettings customKeyspaceSettings(Class<T> type, String keyspace) {
            final KeyspaceSettings settings = new KeyspaceSettings(type, keyspace);
            settings.setTimeToLive(properties.getCardInfo().getTimeToLive().toSeconds());

            return settings;
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static class CacheName {
        public static final String CARD_INFO = "cardInfo";
    }

}
