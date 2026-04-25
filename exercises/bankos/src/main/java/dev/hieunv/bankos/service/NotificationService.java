package dev.hieunv.bankos.service;

public interface NotificationService {
    void sendEmail(String to, String subject, String body);
    void sendSms(String phone, String message);

}
