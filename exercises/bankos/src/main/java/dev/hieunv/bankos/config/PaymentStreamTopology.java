package dev.hieunv.bankos.config;

import dev.hieunv.bankos.dto.payment.PaymentProcessedEvent;
import dev.hieunv.bankos.dto.payment.PaymentStatEvent;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.kstream.TimeWindows;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerde;

import java.time.Duration;
import java.util.Map;

@Configuration
public class PaymentStreamTopology {

    private static final String INPUT_TOPIC = "payment-events";
    private static final String LARGE_PAYMENTS_TOPIC = "large-payment-events";
    private static final String PAYMENT_STATS_TOPIC = "payment-stats";
    private static final double LARGE_PAYMENT_THRESHOLD = 1000.0;

    @Bean
    public KStream<String, PaymentProcessedEvent> paymentStream(StreamsBuilder builder) {

        // 1. Source — read from payment-events
        JsonSerde<PaymentProcessedEvent> paymentSerde = new JsonSerde<>(PaymentProcessedEvent.class);
        paymentSerde.configure(Map.of(
                JsonDeserializer.TRUSTED_PACKAGES, "dev.hieunv.bankos.*",
                JsonDeserializer.TYPE_MAPPINGS,
                "paymentProcessed:dev.hieunv.bankos.dto.payment.PaymentProcessedEvent," +
                        "orderCreated:dev.hieunv.bankos.dto.order.OrderCreatedEvent",
                JsonDeserializer.USE_TYPE_INFO_HEADERS, true
        ), false); // false = consumer side (deserializer config)

        KStream<String, PaymentProcessedEvent> stream = builder.stream(
                INPUT_TOPIC,
                Consumed.with(Serdes.String(), paymentSerde)
        );

        // 2. Filter large payments → write to large-payment-events topic
        stream.filter((accountId, event) -> event.getAmount().doubleValue() > LARGE_PAYMENT_THRESHOLD)
                .to(LARGE_PAYMENTS_TOPIC, Produced.with(Serdes.String(), paymentSerde));

        // 3. Count payments per accountId in a 5-minute tumbling window
        stream.groupByKey()
                .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5)))
                .count(Materialized.as("payment-count-store"))
                .toStream()
                .map((windowedKey, count) -> KeyValue.pair(
                        windowedKey.key(),
                        new PaymentStatEvent(windowedKey.key(), count,
                                windowedKey.window().startTime(),
                                windowedKey.window().endTime())
                ))
                .to(PAYMENT_STATS_TOPIC, Produced.with(Serdes.String(),
                        new JsonSerde<>(PaymentStatEvent.class)));

        return stream;
    }

}
