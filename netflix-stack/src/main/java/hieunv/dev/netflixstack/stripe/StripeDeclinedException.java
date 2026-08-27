package hieunv.dev.netflixstack.stripe;

public class StripeDeclinedException extends RuntimeException {

    public StripeDeclinedException(String message) {
        super(message);
    }
}
