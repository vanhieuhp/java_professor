package dev.hieunv.totp_bankos.domain;

import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "permissions",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_permissions",
                columnNames = {"feature_id", "function_id"}
        )
)
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_id", nullable = false)
    private Long featureId;

    @Column(name = "function_id", nullable = false)
    private Long functionId;

    @Column(nullable = false, unique = true, length = 100)
    private String code;

    @Column
    private String description;
}