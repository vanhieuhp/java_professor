package dev.hieunv.riskassessment.mapper;

import dev.hieunv.riskassessment.constant.ScanStatus;
import dev.hieunv.riskassessment.constant.TriggerType;
import dev.hieunv.riskassessment.dto.CustomerEvaluateRequest;
import dev.hieunv.riskassessment.entity.CoreCustomer;
import dev.hieunv.riskassessment.entity.CustomerScanEvent;
import dev.hieunv.riskassessment.matching.CustomerSnapshot;
import dev.hieunv.riskassessment.utils.Normalizer;

import java.util.UUID;

public final class ScanEventMapper {

    /**
     * Bước B2 của TH1/TH3: chụp lại dữ liệu KH từ Core, chuẩn hóa ngay lúc ghi.
     */
    public static CustomerScanEvent fromCore(CoreCustomer c, TriggerType triggerType, UUID batchId) {
        return CustomerScanEvent.builder()
                .scanBatchId(batchId)
                .triggerType(triggerType)
                .status(ScanStatus.PENDING)
                .cif(c.getCif())
                .fullName(c.getFullName())
                .fullNameNorm(Normalizer.name(c.getFullName()))
                .dob(c.getDob())
                .phone(c.getPhone())
                .phoneNorm(Normalizer.phone(c.getPhone()))
                .idNumber(c.getIdNumber())
                .idNumberNorm(Normalizer.idNumber(c.getIdNumber()))
                .oldIdNumber(c.getOldIdNumber())
                .oldIdNumberNorm(Normalizer.idNumber(c.getOldIdNumber()))
                .countryCode(Normalizer.code(c.getCountryCode()))
                .occupationCode(Normalizer.code(c.getOccupationCode()))
                .positionCode(Normalizer.code(c.getPositionCode()))
                .coreRiskScore(c.getRiskScore())
                .build();
    }

    public static CustomerScanEvent fromRequestToScanEvent(CustomerEvaluateRequest evaluateRequest, TriggerType triggerType, UUID batchId) {
        return CustomerScanEvent.builder()
                .scanBatchId(batchId)
                .triggerType(triggerType)
                .status(ScanStatus.PENDING)
                .cif(evaluateRequest.getCif())
                .fullName(evaluateRequest.getFullName())
                .fullNameNorm(Normalizer.name(evaluateRequest.getFullName()))
                .dob(evaluateRequest.getDob())
                .phone(evaluateRequest.getPhone())
                .phoneNorm(Normalizer.phone(evaluateRequest.getPhone()))
                .idNumber(evaluateRequest.getIdNumber())
                .idNumberNorm(Normalizer.idNumber(evaluateRequest.getIdNumber()))
                .oldIdNumber(evaluateRequest.getOldIdNumber())
                .oldIdNumberNorm(Normalizer.idNumber(evaluateRequest.getOldIdNumber()))
                .countryCode(Normalizer.code(evaluateRequest.getCountryCode()))
                .occupationCode(Normalizer.code(evaluateRequest.getOccupationCode()))
                .positionCode(Normalizer.code(evaluateRequest.getPositionCode()))
                .build();
    }

    public static CustomerSnapshot toSnapshot(CustomerScanEvent q) {
        return CustomerSnapshot.builder()
                .cif(q.getCif())
                .fullNameNorm(q.getFullNameNorm())
                .dob(q.getDob())
                .phoneNorm(q.getPhoneNorm())
                .idNumberNorm(q.getIdNumberNorm())
                .oldIdNumberNorm(q.getOldIdNumberNorm())
                .countryCode(q.getCountryCode())
                .occupationCode(q.getOccupationCode())
                .positionCode(q.getPositionCode())
                .build();
    }
}
