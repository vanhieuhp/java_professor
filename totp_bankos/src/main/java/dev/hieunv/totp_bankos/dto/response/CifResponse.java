package dev.hieunv.totp_bankos.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class CifResponse {

    private Long id;
    private String code;
    private String name;
    private boolean isActive;
    private LocalDateTime createdAt;
}