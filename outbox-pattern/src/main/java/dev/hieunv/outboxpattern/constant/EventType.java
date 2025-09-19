package dev.hieunv.outboxpattern.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum EventType {
    NOTI_PUSH(0),
    EMAIL(1),
    SMS(2);

    private final int code;
}
