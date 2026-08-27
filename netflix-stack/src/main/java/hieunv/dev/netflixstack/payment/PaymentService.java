package hieunv.dev.netflixstack.payment;

import hieunv.dev.netflixstack.payment.dto.CreatePaymentRequest;
import hieunv.dev.netflixstack.payment.dto.StoredResponse;

public interface PaymentService {

    StoredResponse createPayment(String idempotencyKey, CreatePaymentRequest request);
}
