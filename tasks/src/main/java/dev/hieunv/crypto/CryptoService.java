package dev.hieunv.crypto;

public interface CryptoService {
    EncryptedPayload encrypt(byte[] plaintext, byte[] aad);

    byte[] decrypt(EncryptedPayload payload, byte[] aad);
}
