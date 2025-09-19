package dev.hieunv.outboxpattern.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum KafkaMessageCode {

    MAIL_MESSAGE("MAIL_MESSAGE");

    private final String code;
}
