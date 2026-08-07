package dev.hieunv.riskassessment.service.impl;

import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.dto.EvaluateCustomerResponse;
import dev.hieunv.riskassessment.dto.watchlist.WatchlistSnapshot;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.mapper.ScanEventMapper;
import dev.hieunv.riskassessment.matching.RiskAssessment;
import dev.hieunv.riskassessment.repository.CustomerScanEventRepository;
import dev.hieunv.riskassessment.service.CustomerAmlService;
import dev.hieunv.riskassessment.service.CustomerIdentityService;
import dev.hieunv.riskassessment.service.ScanRecordProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomerAmlServiceImpl implements CustomerAmlService {

    private final CustomerIdentityService customerIdentityService;
    private final CustomerScanEventRepository customerScanEventRepository;
    private final ScanRecordProcessor recordProcessor;
    private final WatchlistServiceImpl watchlistService;

    @Transactional
    @Override
    public EvaluateCustomerResponse evaluateFromCoreEvent(CustomerEvaluateRequest request) {
        return evaluate(request, TriggerType.T2, true, false);
    }

    @Transactional
    @Override
    public EvaluateCustomerResponse evaluateAgainstBlacklist(CustomerEvaluateRequest request) {
        return evaluate(request, TriggerType.T2, true, true);
    }

    @Transactional
    @Override
    public EvaluateCustomerResponse evaluateAgainstWatchlists(CustomerEvaluateRequest request) {
        return evaluate(request, TriggerType.T3A, false, false);
    }

    private EvaluateCustomerResponse evaluate(CustomerEvaluateRequest request,
                                              TriggerType triggerType,
                                              boolean blacklistOnly,
                                              boolean syncMirror) {
        long startedAt = System.currentTimeMillis();
        UUID batchId = UUID.randomUUID();

        if (syncMirror) {
            customerIdentityService.upsertFromRequest(request);
        }

        // B2 - normalize data to help matching step
        CustomerScanEvent scanEvent = ScanEventMapper.fromRequestToScanEvent(request, triggerType, batchId);

        // B3 — create event
        CustomerScanEvent queued = customerScanEventRepository.save(scanEvent);

        WatchlistSnapshot lists = watchlistService.freshSnapshot();

        Optional<RiskAssessment> assessment = recordProcessor.process(queued, lists, blacklistOnly);
        long elapsedMillis = (System.currentTimeMillis() - startedAt);

        if (assessment.isEmpty()) {
            log.info("Cif {} does not match any entry", request.getCif());
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
