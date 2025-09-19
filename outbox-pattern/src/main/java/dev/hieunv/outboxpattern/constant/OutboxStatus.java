package dev.hieunv.outboxpattern.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum OutboxStatus {
    NEW(0),
    SUCCESS(1),
    FAILED(2),
    SKIPPED(3),
    PROCESSING(4);

    private final int code;
}

