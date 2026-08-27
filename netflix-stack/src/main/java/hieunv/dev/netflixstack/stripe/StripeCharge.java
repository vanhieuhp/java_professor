package hieunv.dev.netflixstack.stripe;

import java.math.BigDecimal;

public record StripeCharge(String id,
                           String idempotencyKey,
                           BigDecimal amount,
                           String currency,
                           boolean replayed) {
}
