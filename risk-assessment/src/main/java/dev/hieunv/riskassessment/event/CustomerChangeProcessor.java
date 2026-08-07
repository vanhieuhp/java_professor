package dev.hieunv.riskassessment.event;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.EvaluateCustomerResponse;
import dev.hieunv.riskassessment.service.CustomerAmlService;
import dev.hieunv.riskassessment.service.impl.CustomerIdentityServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerChangeProcessor {

    private final CustomerChangeValidator validator;
    private final CoreEventInboxService inbox;
    private final CustomerIdentityServiceImpl identitySync;
    private final CustomerAmlService cifAmlService;

    @Transactional
    public CoreEventOutcome process(CustomerChangedEvent event, String rawPayload, CoreEventSource source) {
        CustomerChangeValidator.Verdict verdict = validator.validate(event);
        if (verdict.isStructural()) {
            throw new PoisonEventException("Sự kiện hỏng cấu trúc: " + verdict.describe());
        }

        if (!inbox.claim(event, rawPayload, source)) {
            log.debug("Event {} (CIF {}) already processed — skipping", event.getEventId(), event.getCif());
            return count(CoreEventOutcome.DUPLICATE);
        }

        if (!verdict.isValid()) {
            inbox.markRejected(event.getEventId(), verdict.describe());
            log.error("Event {} (CIF {}) FAILED VALIDATION: {}",
                    event.getEventId(), event.getCif(), verdict.describe());
            return count(CoreEventOutcome.REJECTED);
        }

        CoreEventOutcome outcome = apply(event);

        inbox.markProcessed(event.getEventId(),
                outcome == CoreEventOutcome.STALE
                        ? "STALE — chở dữ liệu cũ hơn bản chiếu, không chấm điểm"
                        : null);

        return count(outcome);
    }

    private CoreEventOutcome count(CoreEventOutcome outcome) {
        counters.get(outcome).increment();
        return outcome;
    }

    private final Map<CoreEventOutcome, LongAdder> counters = newCounters();

    private static Map<CoreEventOutcome, LongAdder> newCounters() {
        Map<CoreEventOutcome, LongAdder> map = new EnumMap<>(CoreEventOutcome.class);
        for (CoreEventOutcome outcome : CoreEventOutcome.values()) {
            map.put(outcome, new LongAdder());
        }
        return map;
    }

    public Map<String, Long> outcomeCounts() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        counters.forEach((outcome, adder) -> snapshot.put(outcome.name(), adder.sum()));
        return snapshot;
    }

    private CoreEventOutcome apply(CustomerChangedEvent event) {
        if (event.getChangeType() == ChangeType.DELETED) {
            // Không chấm điểm. Người đã rời khỏi Core thì không có ví để khóa, và một kết quả
            // rủi ro sinh ra lúc này sẽ nằm mãi trong hàng đợi gửi Core với lỗi "CIF không
            // tồn tại" — đúng cái vòng lặp vô ích mà mã lỗi vĩnh viễn sinh ra để tránh.
            identitySync.retire(event.getCif(), event.getOccurredAt());
            return CoreEventOutcome.RETIRED;
        }

        if (!identitySync.upsertFromEvent(event)) {
            log.warn("Event {} (CIF {}) carries data older than the mirror — not scoring",
                    event.getEventId(), event.getCif());
            return CoreEventOutcome.STALE;
        }

        if (!CustomerIdentityServiceImpl.isScanTarget(event)) {
            log.info("CIF {} is not a scan target (type={}, status={}) — mirror updated only",
                    event.getCif(), event.getCustomerType(), event.getStatus());
            return CoreEventOutcome.MIRRORED_ONLY;
        }

        EvaluateCustomerResponse response = cifAmlService.evaluateFromCoreEvent(toRequest(event));
        if (response.isMatched()) {
            log.warn("Event {} — CIF {} MATCHED the blacklist on {}, CIF LOCK REQUIRED",
                    event.getEventId(), event.getCif(), response.getMatchedFields());
        }
        return CoreEventOutcome.SCREENED;
    }

    private static CustomerEvaluateRequest toRequest(CustomerChangedEvent e) {
        return CustomerEvaluateRequest.builder()
                .cif(e.getCif())
                .idNumber(e.getIdNumber())
                .fullName(e.getFullName())
                .dob(e.getDob())
                .phone(e.getPhone())
                .oldIdNumber(e.getOldIdNumber())
                .countryCode(e.getCountryCode())
                .occupationCode(e.getOccupationCode())
                .positionCode(e.getPositionCode())
                .status(e.getStatus())
                .build();
    }
}
