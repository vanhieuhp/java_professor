package hieunv.dev.netflixstack.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChargeIntent {

    private String stripeIdempotencyKey;
    private long userId;
    private BigDecimal amount;
    private String currency;
}
