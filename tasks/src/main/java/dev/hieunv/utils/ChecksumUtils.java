package dev.hieunv.utils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

public class ChecksumUtils {

    private final static String HMAC_SHA256 = "HmacSHA256";

    public static String generateChecksum(String data, String secret) {
        try {
            Mac sha256_MAC = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(), HMAC_SHA256);
            sha256_MAC.init(secretKey);
            byte[] hash = sha256_MAC.doFinal(data.getBytes());
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to generate checksum", e);
        }
    }

}
