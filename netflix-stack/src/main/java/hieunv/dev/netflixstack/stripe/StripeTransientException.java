package hieunv.dev.netflixstack.stripe;

public class StripeTransientException extends RuntimeException {

    public StripeTransientException(String message, Throwable cause) {
        super(message, cause);
    }

    public StripeTransientException(String message) {
        super(message);
    }
}
