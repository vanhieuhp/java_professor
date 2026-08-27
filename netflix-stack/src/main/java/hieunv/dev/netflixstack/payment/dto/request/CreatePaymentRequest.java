package hieunv.dev.netflixstack.payment.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class CreatePaymentRequest {

    @NotNull
    private Long userId;

    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amount;

    @NotNull
    @Pattern(regexp = "(?i)[a-z]{3}")
    private String currency;

    public String normalisedCurrency() {
        return currency.toUpperCase();
    }

    public BigDecimal normalisedAmount() {
        return amount.stripTrailingZeros();
    }
}
