package hieunv.dev.netflixstack.payment.service.impl;

import hieunv.dev.netflixstack.common.RecoveryPoint;
import hieunv.dev.netflixstack.payment.dto.Acquisition;
import hieunv.dev.netflixstack.payment.dto.ChargeIntent;
import hieunv.dev.netflixstack.payment.dto.request.CreatePaymentRequest;
import hieunv.dev.netflixstack.payment.dto.response.StoredResponse;
import hieunv.dev.netflixstack.payment.idempotency.RequestHasher;
import hieunv.dev.netflixstack.payment.service.PaymentService;
import hieunv.dev.netflixstack.stripe.StripeCharge;
import hieunv.dev.netflixstack.stripe.StripeClient;
import hieunv.dev.netflixstack.stripe.StripeDeclinedException;
import hieunv.dev.netflixstack.stripe.StripeTransientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private static final int MAX_PHASE_TRANSITIONS = 6;

    private final PaymentPhases phases;
    private final StripeClient stripeClient;

    @Override
    public StoredResponse createPayment(String idempotencyKey, CreatePaymentRequest request) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key header is required");
        }

        String requestHash = RequestHasher.hash(
                request.getUserId(), request.normalisedAmount(), request.normalisedCurrency());

        Acquisition acquisition = acquire(request.getUserId(), idempotencyKey, requestHash);
        if (acquisition.isReplay()) {
            return replayOf(acquisition.getReplay());
        }

        Long recordId = acquisition.getRecordId();
        RecoveryPoint point = acquisition.getRecoveryPoint();

        for (int pass = 0; pass < MAX_PHASE_TRANSITIONS; pass++) {
            if (point == RecoveryPoint.FINISHED) {
                return phases.storedResponse(recordId);
            }
            point = switch (point) {
                case STARTED -> phases.createPayment(recordId, request);
                case PAYMENT_CREATED -> charge(recordId, idempotencyKey);
                case CHARGE_CREATED -> phases.complete(recordId);
                case FINISHED -> RecoveryPoint.FINISHED;
            };
        }

        throw new IllegalStateException(
                "payment[%s] did not reach FINISHED within %d phases, stuck at %s"
                        .formatted(idempotencyKey, MAX_PHASE_TRANSITIONS, point));
    }

    private Acquisition acquire(Long userId, String key, String requestHash) {
        try {
            return phases.acquire(userId, key, requestHash);
        } catch (DataIntegrityViolationException e) {
            log.debug("payment[{}] lost the insert race, re-reading the winner's row", key);
            return phases.acquire(userId, key, requestHash);
        }
    }

    private RecoveryPoint charge(Long recordId, String idempotencyKey) {
        ChargeIntent intent = phases.chargeIntent(recordId);
        try {
            StripeCharge charge = stripeClient.createCharge(
                    intent.getStripeIdempotencyKey(), intent.getUserId(), intent.getAmount(), intent.getCurrency());
            if (charge.replayed()) {
                log.info("payment[{}] resumed onto an existing charge {} - not charged twice",
                        idempotencyKey, charge.id());
            }
            return phases.recordCharge(recordId, charge.id());
        } catch (StripeDeclinedException e) {
            return phases.fail(recordId, e.getMessage());
        } catch (StripeTransientException e) {
            phases.releaseLease(recordId);
            log.warn("payment[{}] stripe outcome unknown, key left resumable: {}",
                    idempotencyKey, e.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "payment provider did not answer - retry with the same Idempotency-Key", e);
        }
    }

    private StoredResponse replayOf(StoredResponse stored) {
        return new StoredResponse(stored.getHttpStatus(), stored.getBody().asReplay());
    }
}
