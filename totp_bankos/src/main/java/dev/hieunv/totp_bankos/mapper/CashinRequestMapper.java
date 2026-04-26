package dev.hieunv.totp_bankos.mapper;

import dev.hieunv.totp_bankos.domain.CashinRequest;
import dev.hieunv.totp_bankos.dto.request.CreateCashinRequest;
import dev.hieunv.totp_bankos.dto.response.CashinRequestResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface CashinRequestMapper {

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "walletId",    ignore = true)
    @Mapping(target = "createdBy",   ignore = true)
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "reviewedBy",  ignore = true)
    @Mapping(target = "reviewedAt",  ignore = true)
    @Mapping(target = "rejectionNote", ignore = true)
    @Mapping(target = "status",      ignore = true)
    CashinRequest toEntity(CreateCashinRequest request);

    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    CashinRequestResponse toResponse(CashinRequest entity);

    List<CashinRequestResponse> toResponseList(List<CashinRequest> entities);
}