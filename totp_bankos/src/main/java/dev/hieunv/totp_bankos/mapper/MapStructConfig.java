package dev.hieunv.totp_bankos.mapper;

import org.mapstruct.MapperConfig;
import org.mapstruct.ReportingPolicy;

@MapperConfig(
        componentModel = "spring",          // mappers are Spring beans → @Autowired works
        unmappedTargetPolicy = ReportingPolicy.IGNORE  // ignore fields with no mapping
)
public interface MapStructConfig {}