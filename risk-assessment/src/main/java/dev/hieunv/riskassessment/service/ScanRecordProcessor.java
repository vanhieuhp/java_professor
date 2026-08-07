package dev.hieunv.riskassessment.service;

import dev.hieunv.riskassessment.constant.ScanStatus;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistSnapshot;
import dev.hieunv.riskassessment.entity.CustomerRiskResult;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.mapper.ScanEventMapper;
import dev.hieunv.riskassessment.matching.CustomerSnapshot;
import dev.hieunv.riskassessment.matching.MatchField;
import dev.hieunv.riskassessment.matching.RiskAssessment;
import dev.hieunv.riskassessment.repository.CustomerRiskResultRepository;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ScanRecordProcessor {

    private final CustomerScanEventRepository scanQueueRepository;
    private final CustomerRiskResultRepository riskResultRepository;
    private final RiskEvaluator riskEvaluator;

    /**
     * @param blacklistOnly true = TH1/TH2 (chỉ DS đen); false = TH3 (toàn bộ tiêu chí trừ DS đen)
     */
    @Transactional
    public Optional<RiskAssessment> process(CustomerScanEvent event, WatchlistSnapshot lists, boolean blacklistOnly) {
        CustomerSnapshot snapshot = ScanEventMapper.toSnapshot(event);

        Optional<RiskAssessment> assessment = blacklistOnly
                ? riskEvaluator.evaluateAgainstBlacklist(snapshot, lists)
                : riskEvaluator.evaluateAgainstWatchlists(snapshot, lists);

        event.setStatus(ScanStatus.PROCESSED);
        event.setProcessedAt(Instant.now());
        scanQueueRepository.save(event);

        assessment.ifPresent(a -> writeResult(event, a));
        return assessment;
    }

    private void writeResult(CustomerScanEvent event, RiskAssessment result) {
        riskResultRepository.clearLatestFlag(event.getCif());
        riskResultRepository.flush();

        riskResultRepository.save(CustomerRiskResult.builder()
                .cif(event.getCif())
                .scanBatchId(event.getScanBatchId())
                .scanQueueId(event.getId())
                .triggerType(event.getTriggerType())
                .riskLevel(result.getRiskLevel())
                .riskScore(result.getRiskScore())
                .reason(result.getReason())
                .categoryCode(result.getCategoryCode())
                .entryId(result.getEntryId())
                .matchedFields(joinFields(result))
                .lockCifRequired(result.isLockCifRequired())
                .latest(true)
                .build());

        log.warn("CIF {} MATCHED [{}] score {} — entry #{} via {}{}",
                result.getCif(), result.getCategoryCode(), result.getRiskScore(),
                result.getEntryId(), result.getMatchedFields(),
                result.isLockCifRequired() ? " — CIF LOCK REQUIRED" : "");
    }

    private static String joinFields(RiskAssessment result) {
        if (result.getMatchedFields() == null) {
            return null;
        }
        return result.getMatchedFields().stream().map(MatchField::name).collect(Collectors.joining(","));
    }
}
