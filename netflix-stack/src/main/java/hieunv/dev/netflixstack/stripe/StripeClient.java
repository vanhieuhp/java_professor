package hieunv.dev.netflixstack.stripe;

import java.math.BigDecimal;

public interface StripeClient {


    StripeCharge createCharge(String idempotencyKey, long userId, BigDecimal amount, String currency);
}
