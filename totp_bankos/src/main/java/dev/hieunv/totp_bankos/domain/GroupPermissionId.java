package dev.hieunv.totp_bankos.domain;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@Embeddable
public class GroupPermissionId implements Serializable {
    private Long groupId;
    private Long permissionId;
}