package dev.hieunv.bankos.model;

import dev.hieunv.bankos.enums.OrderStatus;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long productId;

    // Saga states: CREATED → STOCK_RESERVED → PAYMENT_COMPLETED → COMPLETED
    //                                       → PAYMENT_FAILED    → COMPENSATED
    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    // sagaId ties all steps of one Saga together — used as semantic lock key
    private String sagaId;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
