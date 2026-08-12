package dev.hieunv.riskassessment.mapper;

import dev.hieunv.riskassessment.dto.UpsertCustomerRequest;
import dev.hieunv.riskassessment.entity.CustomerIdentity;
import dev.hieunv.riskassessment.utils.Normalizer;

import java.time.Instant;

public class CustomerMapper {

    public static void applyToEntity(CustomerIdentity entity, UpsertCustomerRequest request, Instant mark) {
        entity.setScanTarget(request.isScanTarget());
        entity.setFullNameNorm(Normalizer.name(request.getFullName()));
        entity.setDob(request.getDob());
        entity.setPhoneNorm(Normalizer.phone(request.getPhone()));
        entity.setIdNumberNorm(Normalizer.idNumber(request.getIdNumber()));
        entity.setOldIdNumberNorm(Normalizer.idNumber(request.getOldIdNumber()));
        // Nguyên văn cho báo cáo. Ghi cùng lúc với bản chuẩn hóa, từ cùng một nguồn — tách ra
        // hai đường ghi là tự tạo ra khả năng tên hiển thị và tên so khớp thuộc hai lần cập nhật.
        entity.setFullName(request.getFullName());
        entity.setPhone(request.getPhone());
        entity.setIdNumber(request.getIdNumber());
        entity.setCoreUpdatedAt(mark);
        entity.setSyncedAt(Instant.now());
    }
}
