package hieunv.dev.netflixstack.stripe;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class StubStripeClient implements StripeClient {

    private final Map<String, StripeCharge> chargesByKey = new ConcurrentHashMap<>();

    @Value("${netflix-stack.payment.stripe.latency-ms:25}")
    private long latencyMillis;

    @Value("${netflix-stack.payment.stripe.decline-amount-over:1000000}")
    private BigDecimal declineAmountOver;

    @Override
    public StripeCharge createCharge(String idempotencyKey, long userId, BigDecimal amount, String currency) {
        StripeCharge existing = chargesByKey.get(idempotencyKey);
        if (existing != null) {
            log.info("stripe[{}] replaying existing charge {} - no second charge made",
                    idempotencyKey, existing.id());
            return new StripeCharge(existing.id(), idempotencyKey, existing.amount(),
                    existing.currency(), true);
        }

        sleep();

        if (amount.compareTo(declineAmountOver) > 0) {
            log.info("stripe[{}] declining amount {} {} (over {})",
                    idempotencyKey, amount, currency, declineAmountOver);
            throw new StripeDeclinedException(
                    "amount %s %s exceeds the stub's decline threshold %s"
                            .formatted(amount, currency, declineAmountOver));
        }

        StripeCharge charge = new StripeCharge("ch_" + UUID.randomUUID().toString().replace("-", ""),
                idempotencyKey, amount, currency, false);
        // putIfAbsent, not put: two threads racing on the same key must agree on
        // one charge, the same way Stripe would.
        StripeCharge raced = chargesByKey.putIfAbsent(idempotencyKey, charge);
        if (raced != null) {
            log.info("stripe[{}] concurrent call lost the race, returning {}", idempotencyKey, raced.id());
            return new StripeCharge(raced.id(), idempotencyKey, raced.amount(), raced.currency(), true);
        }

        log.info("stripe[{}] created charge {} for user={} amount={} {}",
                idempotencyKey, charge.id(), userId, amount, currency);
        return charge;
    }

    private void sleep() {
        if (latencyMillis <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new StripeTransientException("interrupted while calling stripe", e);
        }
    }
}
