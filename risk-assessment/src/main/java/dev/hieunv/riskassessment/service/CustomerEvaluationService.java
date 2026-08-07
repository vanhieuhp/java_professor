package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.dto.WatchlistSnapshot;
import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.EvaluateCustomerResponse;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.mapper.ScanEventMapper;
import dev.hieunv.riskassessment.matching.RiskAssessment;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CustomerEvaluationService {

    private final CustomerScanEventRepository scanQueueRepository;
    private final ScanRecordProcessor recordProcessor;
    private final WatchlistServiceImpl blackListServiceImpl;
    private final CustomerIdentityServiceImpl identitySyncService;

    @Transactional
    public EvaluateCustomerResponse evaluateAgainstWatchlists(CustomerEvaluateRequest request) {
        return evaluate(request, TriggerType.T3A, false, false);
    }

    @Transactional
    public EvaluateCustomerResponse evaluateFromCoreEvent(CustomerEvaluateRequest request) {
        return evaluate(request, TriggerType.T2, true, false);
    }

    private EvaluateCustomerResponse evaluate(CustomerEvaluateRequest request,
                                              TriggerType triggerType,
                                              boolean blacklistOnly,
                                              boolean syncMirror) {
        long startedAt = System.nanoTime();
        UUID batchId = UUID.randomUUID();

        // Ghi bản chiếu TRƯỚC khi chấm điểm — cùng transaction, nên thứ tự không đổi kết quả,
        // nhưng nó phản ánh đúng trình tự nghiệp vụ: ghi nhận sự thật mới, rồi mới phán xét.
        if (syncMirror) {
            identitySyncService.upsertFromRequest(request);
        }

        // B2 — sinh bản ghi hàng đợi, chuẩn hóa ngay lúc ghi
        CustomerScanEvent queued = scanQueueRepository.save(
                ScanEventMapper.fromRequestToScanEvent(request, triggerType, batchId));

        // Ảnh chụp danh sách: lấy một lần, không hỏi lại trong lúc so khớp.
        // Phải là bản đã kiểm tra cập nhật — nếu không, trong 30 giây sau mỗi lần sửa DS đen,
        // đường realtime sẽ cho qua đúng những người vừa bị đưa vào danh sách.
        WatchlistSnapshot lists = blackListServiceImpl.freshSnapshot();

        Optional<RiskAssessment> assessment = recordProcessor.process(queued, lists, blacklistOnly);
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        if (assessment.isEmpty()) {
            log.info("CIF {} không trùng danh sách nào ({} ms) — không gọi Core",
                    request.getCif(), elapsedMillis);
            return EvaluateCustomerResponse.builder()
                    .cif(request.getCif())
                    .matched(false)
                    .triggerType(triggerType)
                    .scanQueueId(queued.getId())
                    .scanBatchId(batchId.toString())
                    .elapsedMillis(elapsedMillis)
                    .build();
        }

        RiskAssessment result = assessment.get();
        return EvaluateCustomerResponse.builder()
                .cif(result.getCif())
                .matched(true)
                .riskLevel(result.getRiskLevel())
                .riskScore(result.getRiskScore())
                .reason(result.getReason())
                .categoryCode(result.getCategoryCode())
                .categoryName(result.getCategoryName())
                .priority(result.getPriority())
                .entryId(result.getEntryId())
                .matchedFields(result.getMatchedFields())
                .lockCifRequired(result.isLockCifRequired())
                .triggerType(triggerType)
                .scanQueueId(queued.getId())
                .scanBatchId(batchId.toString())
                .elapsedMillis(elapsedMillis)
                .build();
    }
}
