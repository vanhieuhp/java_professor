package dev.hieunv.crypto;

public record EncryptedPayload(String iv, String ciphertext) {}
