package dev.hieunv.totp_bankos.mapper;

import dev.hieunv.totp_bankos.domain.Cif;
import dev.hieunv.totp_bankos.dto.request.CreateCifRequest;
import dev.hieunv.totp_bankos.dto.response.CifResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = MapStructConfig.class)
public interface CifMapper {

    // CreateCifRequest → Cif entity
    @Mapping(target = "id",        ignore = true)
    @Mapping(target = "isActive",  constant = "true")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Cif toEntity(CreateCifRequest request);

    // Cif entity → CifResponse
    CifResponse toResponse(Cif cif);
}