package dev.hieunv.bankos.exception;

public class PaymentBusinessException extends RuntimeException {
    public PaymentBusinessException(String message) {
        super(message);
    }
}
