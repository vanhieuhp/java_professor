package dev.hieunv.totp_bankos.exception;

public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) { super(message); }
}

