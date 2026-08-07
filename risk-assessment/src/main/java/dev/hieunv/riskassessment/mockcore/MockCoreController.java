package dev.hieunv.riskassessment.mockcore;

import dev.hieunv.riskassessment.dto.CoreRiskUpdateAck;
import dev.hieunv.riskassessment.dto.CoreRiskUpdateRequest;
import dev.hieunv.riskassessment.event.ChangeType;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * CORE VÍ GIẢ — không phải một phần của PCRT.
 * <p>
 * Tồn tại để đầu kia của tích hợp có thật: nhận lệnh, thực sự cập nhật
 * {@code core.wallet_customer}, thực sự khóa CIF, và thực sự từ chối lệnh trùng. Không có
 * nó thì retry, cầu dao và idempotency chỉ là code không ai chứng minh được là chạy đúng.
 * <p>
 * Tắt bằng {@code pcrt.mock-core.enabled=false} khi trỏ sang Core thật.
 */
@Slf4j
@RestController
@RequestMapping("/mock-core")
@RequiredArgsConstructor
@ConditionalOnProperty(name = "pcrt.mock-core.enabled", havingValue = "true", matchIfMissing = true)
public class MockCoreController {

    private final MockCoreRepository coreRepository;
    private final MockCoreSettings settings;
    private final MockCoreCustomerChanger customerChanger;
    private final MockCoreEventPublisher eventPublisher;

    // ----- Core đổi thông tin KH và phát sự kiện -----

    /**
     * Đổi dữ liệu khách hàng bên Core rồi publish sự kiện. Đây là <b>đầu phát</b> của luồng
     * mới: Core không gọi PCRT nữa, nó chỉ thông báo mình vừa đổi cái gì.
     * <p>
     * Trường nào không gửi thì giữ nguyên. Với {@code changeType=DELETED} thì thân request bị
     * bỏ qua và dòng bị xóa khỏi {@code core.wallet_customer}.
     */
    @PostMapping("/customers/{cif}/change")
    public ResponseEntity<?> changeCustomer(
            @PathVariable String cif,
            @RequestParam(defaultValue = "UPDATED") ChangeType changeType,
            @RequestBody(required = false) MockCoreChangeRequest body) {
        try {
            return ResponseEntity.ok(customerChanger.change(cif, changeType, body));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Bắn thẳng một chuỗi thô vào topic — để dựng được sự kiện hỏng.
     * <p>
     * Không có đường này thì không kiểm chứng được nhánh xử lý sự kiện hỏng: mọi gói tin đi
     * qua {@link MockCoreCustomerChanger} đều hợp lệ theo cấu trúc, vì nó dựng từ kiểu dữ
     * liệu Java. Mà nhánh hỏng mới là nhánh quyết định partition có đứng im hay không.
     */
    @PostMapping("/publish-raw")
    public ResponseEntity<?> publishRaw(@RequestParam(defaultValue = "RAW") String key,
                                        @RequestBody String payload) {
        eventPublisher.publishRaw(key, payload);
        return ResponseEntity.ok(Map.of("published", true, "key", key));
    }

    @PostMapping("/api/v1/customers/risk-assessment")
    @Transactional
    public ResponseEntity<?> receiveRiskAssessment(@Valid @RequestBody CoreRiskUpdateRequest request) {
        // --- Mô phỏng sự cố ---
        if (settings.getDelayMs() > 0) {
            sleep(settings.getDelayMs());
        }
        if (settings.isRejectAll()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Core từ chối lệnh (mô phỏng lỗi vĩnh viễn)"));
        }
        if (settings.isDown() || settings.shouldFailRandomly()) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Core tạm thời không khả dụng (mô phỏng)"));
        }

        // --- Nghiệp vụ thật ---
        if (coreRepository.countCustomer(request.getCif()) == 0) {
            // Lệnh sai bản chất — gửi lại cũng vô ích. Trả 4xx để PCRT KHÔNG thử lại.
            return ResponseEntity.badRequest().body(Map.of("error", "CIF không tồn tại: " + request.getCif()));
        }

        // Chốt chống trùng: chèn có ON CONFLICT DO NOTHING. Trả về 0 nghĩa là lệnh này
        // đã được nhận trước đó — bỏ qua, KHÔNG chạy lại quy trình khóa CIF.
        int inserted = coreRepository.recordIfNew(
                request.getIdempotencyKey(),
                request.getCif(),
                request.getRiskLevel().name(),
                request.getRiskScore(),
                request.getReason(),
                request.isLockCif());

        if (inserted == 0) {
            log.info("[CORE] Lệnh {} đã nhận từ trước — bỏ qua", request.getIdempotencyKey());
            return ResponseEntity.ok(CoreRiskUpdateAck.builder()
                    .cif(request.getCif())
                    .duplicate(true)
                    .cifLocked(false)
                    .message("Đã xử lý trước đó, không xử lý lại")
                    .build());
        }

        coreRepository.applyRiskAssessment(request.getCif(), request.getRiskScore(), request.isLockCif());
        log.info("[CORE] CIF {} — điểm {}, {}", request.getCif(), request.getRiskScore(),
                request.isLockCif() ? "ĐÃ KHÓA CIF" : "chỉ cập nhật điểm");

        return ResponseEntity.ok(CoreRiskUpdateAck.builder()
                .cif(request.getCif())
                .duplicate(false)
                .cifLocked(request.isLockCif())
                .message("Đã cập nhật")
                .build());
    }

    // ----- Núm vặn mô phỏng sự cố -----

    @GetMapping("/settings")
    public ResponseEntity<MockCoreSettings> settings() {
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/settings")
    public ResponseEntity<MockCoreSettings> updateSettings(
            @RequestParam(required = false) Boolean down,
            @RequestParam(required = false) Double failureRate,
            @RequestParam(required = false) Long delayMs,
            @RequestParam(required = false) Boolean rejectAll) {
        if (down != null) {
            settings.setDown(down);
        }
        if (failureRate != null) {
            settings.setFailureRate(failureRate);
        }
        if (delayMs != null) {
            settings.setDelayMs(delayMs);
        }
        if (rejectAll != null) {
            settings.setRejectAll(rejectAll);
        }
        log.warn("[CORE] Đổi cấu hình mô phỏng: {}", settings);
        return ResponseEntity.ok(settings);
    }

    /** Lịch sử lệnh Core đã nhận cho một khách hàng — để đếm xem có bị xử lý trùng không. */
    @GetMapping("/log/{cif}")
    public ResponseEntity<?> log(@PathVariable String cif) {
        return ResponseEntity.ok(coreRepository.findAll().stream()
                .filter(l -> l.getCif().equals(cif))
                .toList());
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
