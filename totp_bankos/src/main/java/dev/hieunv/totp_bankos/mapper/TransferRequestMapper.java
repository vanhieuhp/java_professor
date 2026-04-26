package dev.hieunv.totp_bankos.mapper;

import dev.hieunv.totp_bankos.domain.TransferRequest;
import dev.hieunv.totp_bankos.dto.request.CreateTransferRequest;
import dev.hieunv.totp_bankos.dto.response.TransferRequestResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface TransferRequestMapper {

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "walletId",    ignore = true)   // set by service
    @Mapping(target = "createdBy",   ignore = true)   // set by service
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "reviewedBy",  ignore = true)
    @Mapping(target = "reviewedAt",  ignore = true)
    @Mapping(target = "rejectionNote", ignore = true)
    @Mapping(target = "status",      ignore = true)
    TransferRequest toEntity(CreateTransferRequest request);

    @Mapping(target = "status", expression = "java(entity.getStatus().name())")
    TransferRequestResponse toResponse(TransferRequest entity);

    List<TransferRequestResponse> toResponseList(List<TransferRequest> entities);
}