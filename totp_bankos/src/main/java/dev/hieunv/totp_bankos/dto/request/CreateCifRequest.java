package dev.hieunv.totp_bankos.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCifRequest {

    @NotBlank(message = "CIF code is required")
    private String code;

    @NotBlank(message = "CIF name is required")
    private String name;
}