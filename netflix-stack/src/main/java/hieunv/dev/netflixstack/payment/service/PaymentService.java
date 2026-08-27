package hieunv.dev.netflixstack.payment.service;

import hieunv.dev.netflixstack.payment.dto.request.CreatePaymentRequest;
import hieunv.dev.netflixstack.payment.dto.response.StoredResponse;

public interface PaymentService {

    StoredResponse createPayment(String idempotencyKey, CreatePaymentRequest request);
}
