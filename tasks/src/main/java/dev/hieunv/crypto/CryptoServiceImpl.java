package dev.hieunv.crypto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@Service
public class CryptoServiceImpl implements CryptoService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LEN = 12; // 96-bit IV recommended for GCM
    private static final int TAG_LEN_BITS = 128; // 16-byte tag

    private final SecretKey key;
    private final SecureRandom secureRandom = new SecureRandom();

    public CryptoServiceImpl(@Value("${app.crypto.key}") String base64Key) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        if (!List.of(16, 24, 32).contains(keyBytes.length) ) {
            throw new IllegalArgumentException("app.crypto.key must be 16/24/32 bytes Base64 (AES-128/192/256).");
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public EncryptedPayload encrypt(byte[] plaintext, byte[] aad) {
        try {
            byte[] iv = new byte[IV_LEN];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LEN_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, key, spec);
            if (aad != null && aad.length > 0) cipher.updateAAD(aad);

            byte[] ciphertextPlusTag = cipher.doFinal(plaintext);

            return new EncryptedPayload(
                    Base64.getEncoder().encodeToString(iv),
                    Base64.getEncoder().encodeToString(ciphertextPlusTag)
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt payload", e);
        }
    }

    @Override
    public byte[] decrypt(EncryptedPayload payload, byte[] aad) {
        try {
            byte[] iv = Base64.getDecoder().decode(payload.iv());
            byte[] ciphertextPlusTag = Base64.getDecoder().decode(payload.ciphertext());

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec spec = new GCMParameterSpec(TAG_LEN_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, key, spec);
            if (aad != null && aad.length > 0) cipher.updateAAD(aad);

            return cipher.doFinal(ciphertextPlusTag);
        } catch (Exception e) {
            // Includes AEADBadTagException when auth fails
            throw new IllegalArgumentException("Decryption failed (bad data, IV, tag, or key)", e);
        }
    }
}
