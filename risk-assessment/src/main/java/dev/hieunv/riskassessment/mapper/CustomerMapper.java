package dev.hieunv.riskassessment.mapper;

import dev.hieunv.riskassessment.dto.UpsertCustomerRequest;
import dev.hieunv.riskassessment.entity.CustomerIdentity;
import dev.hieunv.riskassessment.utils.Normalizer;

import java.time.Instant;

public class CustomerMapper {

    public static void applyToEntity(CustomerIdentity entity, UpsertCustomerRequest request, Instant mark) {
        if (request.getCoreId() != null) {
            entity.setCoreId(request.getCoreId());
        }

        entity.setScanTarget(request.isScanTarget());
        entity.setFullNameNorm(Normalizer.name(request.getFullName()));
        entity.setDob(request.getDob());
        entity.setPhoneNorm(Normalizer.phone(request.getPhone()));
        entity.setIdNumberNorm(Normalizer.idNumber(request.getIdNumber()));
        entity.setOldIdNumberNorm(Normalizer.idNumber(request.getOldIdNumber()));
        entity.setCoreUpdatedAt(mark);
        entity.setSyncedAt(Instant.now());
    }
}
