package dev.hieunv.totp_bankos.mapper;

import dev.hieunv.totp_bankos.domain.AuditLog;
import dev.hieunv.totp_bankos.dto.response.AuditLogResponse;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface AuditLogMapper {

    AuditLogResponse toResponse(AuditLog auditLog);

    List<AuditLogResponse> toResponseList(List<AuditLog> logs);
}