package dev.hieunv.two_databases.dto;

import java.math.BigDecimal;

public record CompensationPayload(String sagaId, Long fromAccountId, BigDecimal amount, String reason) {
}
