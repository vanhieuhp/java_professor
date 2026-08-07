package dev.hieunv.riskassessment.event;

import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.EvaluateCustomerResponse;
import dev.hieunv.riskassessment.service.CustomerEvaluationService;
import dev.hieunv.riskassessment.service.CustomerIdentityServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * Xử lý một sự kiện đổi thông tin khách hàng — dùng chung cho cả Kafka lẫn REST dự phòng.
 *
 * <h2>Vì sao logic không nằm trong consumer</h2>
 * Bài học đã trả giá ở pcrt-lab: rule chỉ được nối vào consumer Kafka, còn đường REST dự
 * phòng thì nhận sự kiện, trả 200, và <b>không chấm điểm gì cả</b>. Hậu quả nếu lên
 * production: mỗi lần chuyển sang đường dự phòng là toàn bộ rà soát ngừng chạy, đúng lúc
 * Kafka đang có sự cố, và không có gì báo cho ai biết.
 * <p>
 * Ở đây consumer chỉ làm ba việc của riêng nó — giải mã JSON, ack, đếm — còn toàn bộ nghiệp
 * vụ nằm ở class này. Thêm một đường vào thứ ba sau này cũng không đụng tới nghiệp vụ.
 *
 * <h2>Toàn bộ method là MỘT transaction</h2>
 * Tấm vé chống trùng (dòng inbox) và hiệu ứng của nó (bản chiếu, hàng đợi, kết quả rủi ro)
 * phải cùng bền hóa hoặc cùng biến mất. Commit riêng tấm vé rồi hỏng ở bước sau nghĩa là mọi
 * lần gửi lại đều bị chặn ở cổng, và khách hàng đó không bao giờ được rà soát.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerChangeProcessor {

    private final CustomerChangeValidator validator;
    private final CoreEventInboxService inbox;
    private final CustomerIdentityServiceImpl identitySync;
    private final CustomerEvaluationService evaluationService;

    @Transactional
    public CoreEventOutcome process(CustomerChangedEvent event, String rawPayload, CoreEventSource source) {
        CustomerChangeValidator.Verdict verdict = validator.validate(event);

        // Hỏng tới mức không ghi nổi vào inbox — chính ba cột đó là NOT NULL, và không có
        // eventId thì cũng không chống trùng được. Ném ra để consumer ack và ghi log nguyên
        // văn payload; đây là món nợ có ý thức, xem CustomerChangeValidator.
        if (verdict.isStructural()) {
            throw new PoisonEventException("Sự kiện hỏng cấu trúc: " + verdict.describe());
        }

        if (!inbox.claim(event, rawPayload, source)) {
            log.debug("Sự kiện {} (CIF {}) đã xử lý trước đó — bỏ qua", event.getEventId(), event.getCif());
            return count(CoreEventOutcome.DUPLICATE);
        }

        // Đã có tấm vé rồi mới từ chối: dòng inbox ở lại với process_error và processed_at
        // NULL. Truy vấn được, đếm được, chạy lại được sau khi Core sửa nguồn — khác hẳn
        // "ghi log rồi bỏ", nơi bằng chứng biến mất cùng lần xoay vòng log kế tiếp.
        if (!verdict.isValid()) {
            inbox.markRejected(event.getEventId(), verdict.describe());
            log.error("Sự kiện {} (CIF {}) KHÔNG QUA XÁC THỰC: {}",
                    event.getEventId(), event.getCif(), verdict.describe());
            return count(CoreEventOutcome.REJECTED);
        }

        CoreEventOutcome outcome = apply(event);

        // STALE vẫn đóng sổ — thử lại cũng vẫn cũ — nhưng để lại ghi chú. Đóng sổ sạch sẽ thì
        // nó biến mất khỏi mọi truy vấn, và một đồng hồ Core chạy lùi sẽ khiến TOÀN BỘ sự kiện
        // thành STALE trong khi mọi con số vẫn xanh.
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

    /**
     * Đếm ở đây chứ không ở consumer, để đường REST dự phòng cũng được đếm.
     * <p>
     * Cùng một lý do khiến nghiệp vụ nằm ở class này: số liệu chỉ đúng nếu nó bao cả hai
     * đường vào. Đếm trong consumer thì lúc Kafka chết và mọi thứ chạy qua REST, bảng số liệu
     * sẽ hiển thị 0 — đúng lúc cần nhìn nhất thì nó mù.
     */
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
            // Chốt thứ tự chặn: sự kiện chở dữ liệu cũ hơn bản chiếu đang giữ. Dừng tại đây.
            // Chấm tiếp sẽ sinh một kết quả mang cờ is_latest bằng dữ liệu cũ, và nó đè lên
            // kết quả mới hơn — hồ sơ rủi ro của khách hàng bị kéo lùi trong im lặng.
            log.warn("Sự kiện {} (CIF {}) chở dữ liệu cũ hơn bản chiếu — không chấm điểm",
                    event.getEventId(), event.getCif());
            return CoreEventOutcome.STALE;
        }

        if (!CustomerIdentityServiceImpl.isScanTarget(event)) {
            // Bản chiếu đã cập nhật (scan_target vừa bị hạ xuống false), nhưng không chấm.
            // Đây chính là đường mà TH2 qua REST không có: thiếu nó thì khách hàng bị khóa ví
            // vẫn nằm lại trong tập quét với cờ cũ — bản chiếu chỉ có đường vào.
            log.info("CIF {} không thuộc tập quét (type={}, status={}) — chỉ cập nhật bản chiếu",
                    event.getCif(), event.getCustomerType(), event.getStatus());
            return CoreEventOutcome.MIRRORED_ONLY;
        }

        EvaluateCustomerResponse response = evaluationService.evaluateFromCoreEvent(toRequest(event));
        if (response.isMatched()) {
            log.warn("Sự kiện {} — CIF {} TRÙNG DS đen qua {}, yêu cầu khóa CIF",
                    event.getEventId(), event.getCif(), response.getMatchedFields());
        }
        return CoreEventOutcome.SCREENED;
    }

    /**
     * Đổi sự kiện thành đúng cái DTO mà đường REST TH2 vẫn dùng.
     * <p>
     * {@code updateTime} để trống <b>có chủ ý</b>: bản chiếu đã được ghi ở bước trước bằng
     * {@code occurredAt} thật. Trường này chỉ còn phục vụ việc ghi bản chiếu, mà ở đường sự
     * kiện thì {@code syncMirror = false}. Truyền vào cũng vô hại, nhưng để trống thì đọc
     * code là thấy ngay ai chịu trách nhiệm ghi bản chiếu.
     */
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
