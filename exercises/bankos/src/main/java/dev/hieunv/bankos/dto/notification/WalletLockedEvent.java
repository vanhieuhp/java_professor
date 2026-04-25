package dev.hieunv.bankos.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WalletLockedEvent {
    private Long accountId;
    private String reason;
    private LocalDateTime occurredAt;
}