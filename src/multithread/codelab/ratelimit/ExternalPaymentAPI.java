package multithread.codelab.ratelimit;

public class ExternalPaymentAPI {
    private static final int PROCESSING_MS = 200;
    private static final int MAX_CONCURRENT = 20;

    public static PaymentResult call(String txnId) throws InterruptedException {
        Thread.sleep(PROCESSING_MS);
        return new PaymentResult(txnId, "OK");
    }


}
