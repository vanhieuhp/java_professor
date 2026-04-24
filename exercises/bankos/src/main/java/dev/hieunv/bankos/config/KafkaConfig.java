package dev.hieunv.bankos.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableKafka
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory(
//            ConsumerFactory<String, Object> consumerFactory,
//            KafkaTemplate<String, Object> kafkaTemplate) {
//
//        ConcurrentKafkaListenerContainerFactory<String, Object> factory =
//                new ConcurrentKafkaListenerContainerFactory<>();
//
//        factory.setConsumerFactory(consumerFactory);
//
//        DeadLetterPublishingRecoverer recoverer =
//                new DeadLetterPublishingRecoverer(kafkaTemplate);
//
//        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
//                recoverer,
//                new FixedBackOff(1000L, 3L)
//        );
//
//        errorHandler.addNotRetryableExceptions(IllegalArgumentException.class);
//        factory.setCommonErrorHandler(errorHandler);
//
//        return factory;
//    }

    @Bean("paymentProducerFactory")
    public ProducerFactory<String, Object> paymentProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 0);          // send immediately
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);     // 16KB
        props.put(ProducerConfig.ACKS_CONFIG, "all");           // full durability
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "none"); // no compression
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        props.put(JsonSerializer.TYPE_MAPPINGS,
                "paymentProcessed:dev.hieunv.bankos.dto.payment.PaymentProcessedEvent," +
                        "orderCreated:dev.hieunv.bankos.dto.order.OrderCreatedEvent," +
                        "paymentCompleted:dev.hieunv.bankos.dto.payment.PaymentCompletedEvent," +
                        "paymentFailed:dev.hieunv.bankos.dto.payment.PaymentFailedEvent," +
                        "walletStatus:dev.hieunv.bankos.dto.wallet.WalletStatusEvent");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("paymentKafkaTemplate")
    public KafkaTemplate<String, Object> paymentKafkaTemplate(
            @Qualifier("paymentProducerFactory")
            ProducerFactory<String, Object> factory) {
        return new KafkaTemplate<>(factory);
    }

    @Bean("backgroundProducerFactory")
    public ProducerFactory<String, Object> backgroundProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.LINGER_MS_CONFIG, 20);         // wait 20ms to fill batch
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);     // 64KB
        props.put(ProducerConfig.ACKS_CONFIG, "1");             // leader ack only
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4"); // compress batches
        props.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        props.put(JsonSerializer.TYPE_MAPPINGS,
                "paymentProcessed:dev.hieunv.bankos.dto.payment.PaymentProcessedEvent," +
                        "orderCreated:dev.hieunv.bankos.dto.order.OrderCreatedEvent," +
                        "paymentCompleted:dev.hieunv.bankos.dto.payment.PaymentCompletedEvent," +
                        "paymentFailed:dev.hieunv.bankos.dto.payment.PaymentFailedEvent," +
                        "walletStatus:dev.hieunv.bankos.dto.wallet.WalletStatusEvent");
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean("backgroundKafkaTemplate")
    public KafkaTemplate<String, Object> backgroundKafkaTemplate(
            @Qualifier("backgroundProducerFactory")
            ProducerFactory<String, Object> factory) {
        return new KafkaTemplate<>(factory);
    }
}
