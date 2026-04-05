package dev.hieunv.two_databases.dto;

import java.math.BigDecimal;

public record TransferPayload(String sagaId, Long toAccountId, BigDecimal amount) {
}
