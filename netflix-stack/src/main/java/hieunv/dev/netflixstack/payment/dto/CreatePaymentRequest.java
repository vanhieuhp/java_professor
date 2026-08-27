package hieunv.dev.netflixstack.payment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CreatePaymentRequest(

        @NotNull Long userId,

        @NotNull @DecimalMin(value = "0.01") BigDecimal amount,

        @NotNull @Pattern(regexp = "(?i)[a-z]{3}") String currency) {

    public String normalisedCurrency() {
        return currency.toUpperCase();
    }

    public BigDecimal normalisedAmount() {
        return amount.stripTrailingZeros();
    }
}
