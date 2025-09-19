package dev.hieunv.outboxpattern.dto.kafka;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
@JsonNaming(PropertyNamingStrategies.LowerCamelCaseStrategy.class)
public class KafkaMessage<T> extends KafkaMessageBase {

    private T payload;

}
