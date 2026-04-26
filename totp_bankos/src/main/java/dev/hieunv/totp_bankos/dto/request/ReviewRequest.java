package dev.hieunv.totp_bankos.dto.request;

import lombok.Data;

@Data
public class ReviewRequest {
    // optional note — required only when rejecting
    private String note;
}