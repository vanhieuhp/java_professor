package hieunv.dev.netflixstack.payment;

import hieunv.dev.netflixstack.common.IdempotencyStatus;
import hieunv.dev.netflixstack.common.RecoveryPoint;
import hieunv.dev.netflixstack.payment.dto.CreatePaymentRequest;
import hieunv.dev.netflixstack.payment.dto.CreatePaymentResponse;
import hieunv.dev.netflixstack.payment.dto.StoredResponse;
import hieunv.dev.netflixstack.payment.idempotency.IdempotencyRecord;
import hieunv.dev.netflixstack.payment.idempotency.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentPhases {

    private final IdempotencyRecordRepository idempotencyRepository;
    private final PaymentRepository paymentRepository;
    private final ObjectMapper objectMapper;

    @Value("${netflix-stack.payment.lock-lease:90s}")
    private Duration lockLease;


    public record Acquisition(Long recordId, RecoveryPoint recoveryPoint, StoredResponse replay) {

        public boolean isReplay() {
            return replay != null;
        }
    }

    public record ChargeIntent(String stripeIdempotencyKey,
                               long userId,
                               BigDecimal amount,
                               String currency) {
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Acquisition acquire(Long userId, String key, String requestHash) {
        Optional<IdempotencyRecord> found = idempotencyRepository.lockByUserAndKey(userId, key);

        if (found.isEmpty()) {
            IdempotencyRecord fresh = IdempotencyRecord.started(userId, key, requestHash);
            idempotencyRepository.saveAndFlush(fresh);
            log.debug("payment[{}] new idempotency key for user={}, stripeKey={}",
                    key, userId, fresh.getStripeIdempotencyKey());
            return new Acquisition(fresh.getId(), RecoveryPoint.STARTED, null);
        }

        IdempotencyRecord record = found.get();

        if (!record.getRequestHash().equals(requestHash)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "idempotency key '%s' was already used for a different request".formatted(key));
        }

        if (record.isTerminal()) {
            log.debug("payment[{}] replaying stored {} response", key, record.getStatus());
            return new Acquisition(record.getId(), RecoveryPoint.FINISHED, readStored(record));
        }

        if (record.isLeaseHeld(lockLease)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "a request with idempotency key '%s' is already in progress".formatted(key));
        }

        // Lease expired: the previous attempt died somewhere. Take it over and
        // resume from whatever it managed to commit.
        record.takeLease();
        log.info("payment[{}] taking over a stale lease, resuming from {}", key, record.getRecoveryPoint());
        return new Acquisition(record.getId(), record.getRecoveryPoint(), null);
    }

    /** STARTED -> PAYMENT_CREATED: our own row goes in, still PENDING. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecoveryPoint createPayment(Long recordId, CreatePaymentRequest request) {
        IdempotencyRecord record = lock(recordId);
        if (record.getRecoveryPoint() != RecoveryPoint.STARTED) {
            return record.getRecoveryPoint();
        }

        Payment payment = paymentRepository.save(Payment.pending(
                request.userId(), request.normalisedAmount(), request.normalisedCurrency()));

        record.setPaymentId(payment.getId());
        record.advanceTo(RecoveryPoint.PAYMENT_CREATED);

        log.debug("payment[{}] created payment id={} PENDING", record.getIdempotencyKey(), payment.getId());
        return RecoveryPoint.PAYMENT_CREATED;
    }

    /**
     * Reads what the charge needs. Separate from the call itself because the
     * call must not run inside a transaction - it is a network round trip to a
     * third party, and a pooled connection held across it is a connection nobody
     * else can use.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public ChargeIntent chargeIntent(Long recordId) {
        IdempotencyRecord record = idempotencyRepository.findById(recordId).orElseThrow(
                () -> new IllegalStateException("idempotency record " + recordId + " vanished"));
        Payment payment = payment(record);
        return new ChargeIntent(record.getStripeIdempotencyKey(), payment.getUserId(),
                payment.getAmount(), payment.getCurrency());
    }

    /** PAYMENT_CREATED -> CHARGE_CREATED: the charge id is now durable. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecoveryPoint recordCharge(Long recordId, String chargeId) {
        IdempotencyRecord record = lock(recordId);
        if (record.getRecoveryPoint() != RecoveryPoint.PAYMENT_CREATED) {
            return record.getRecoveryPoint();
        }

        record.setStripeChargeId(chargeId);
        payment(record).setStripeChargeId(chargeId);
        record.advanceTo(RecoveryPoint.CHARGE_CREATED);

        log.debug("payment[{}] recorded charge {}", record.getIdempotencyKey(), chargeId);
        return RecoveryPoint.CHARGE_CREATED;
    }

    /** CHARGE_CREATED -> FINISHED: settle the payment and freeze the answer. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecoveryPoint complete(Long recordId) {
        IdempotencyRecord record = lock(recordId);
        if (record.getRecoveryPoint() == RecoveryPoint.FINISHED) {
            return RecoveryPoint.FINISHED;
        }

        Payment payment = payment(record);
        payment.setStatus(PaymentStatus.SUCCEEDED);
        payment.touch();

        record.finish(IdempotencyStatus.COMPLETED,
                writeStored(new StoredResponse(HttpStatus.OK.value(), response(payment, null))));

        log.info("payment[{}] completed: payment={} charge={}",
                record.getIdempotencyKey(), payment.getId(), record.getStripeChargeId());
        return RecoveryPoint.FINISHED;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public RecoveryPoint fail(Long recordId, String reason) {
        IdempotencyRecord record = lock(recordId);
        if (record.getRecoveryPoint() == RecoveryPoint.FINISHED) {
            return RecoveryPoint.FINISHED;
        }

        Payment payment = payment(record);
        payment.setStatus(PaymentStatus.FAILED);
        payment.touch();

        record.finish(IdempotencyStatus.FAILED,
                writeStored(new StoredResponse(HttpStatus.PAYMENT_REQUIRED.value(), response(payment, reason))));

        log.info("payment[{}] failed permanently: {}", record.getIdempotencyKey(), reason);
        return RecoveryPoint.FINISHED;
    }


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLease(Long recordId) {
        idempotencyRepository.findById(recordId).ifPresent(IdempotencyRecord::releaseLease);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public StoredResponse storedResponse(Long recordId) {
        return readStored(idempotencyRepository.findById(recordId).orElseThrow(
                () -> new IllegalStateException("idempotency record " + recordId + " vanished")));
    }

    private IdempotencyRecord lock(Long recordId) {
        return idempotencyRepository.lockById(recordId).orElseThrow(
                () -> new IllegalStateException("idempotency record " + recordId + " vanished"));
    }

    private Payment payment(IdempotencyRecord record) {
        Long paymentId = record.getPaymentId();
        if (paymentId == null) {
            throw new IllegalStateException(
                    "idempotency record %d is at %s with no payment attached"
                            .formatted(record.getId(), record.getRecoveryPoint()));
        }
        return paymentRepository.findById(paymentId).orElseThrow(
                () -> new IllegalStateException("payment " + paymentId + " vanished"));
    }

    private CreatePaymentResponse response(Payment payment, String failureReason) {
        return new CreatePaymentResponse(payment.getId(), payment.getUserId(), payment.getAmount(),
                payment.getCurrency(), payment.getStatus(), payment.getStripeChargeId(),
                failureReason, payment.createdAtInstant(), false);
    }

    private String writeStored(StoredResponse stored) {
        try {
            return objectMapper.writeValueAsString(stored);
        } catch (JacksonException e) {
            throw new IllegalStateException("could not serialise the stored response", e);
        }
    }

    private StoredResponse readStored(IdempotencyRecord record) {
        if (record.getResponse() == null) {
            throw new IllegalStateException(
                    "idempotency record %d is FINISHED with no stored response".formatted(record.getId()));
        }
        try {
            return objectMapper.readValue(record.getResponse(), StoredResponse.class);
        } catch (JacksonException e) {
            throw new IllegalStateException("could not read the stored response", e);
        }
    }
}
