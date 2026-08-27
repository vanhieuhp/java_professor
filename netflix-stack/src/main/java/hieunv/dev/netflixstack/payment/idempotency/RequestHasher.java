package hieunv.dev.netflixstack.payment.idempotency;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RequestHasher {

    private RequestHasher() {
    }

    public static String hash(Object... fields) {
        StringBuilder canonical = new StringBuilder();
        for (Object field : fields) {
            String value = field == null ? "" : field.toString();
            canonical.append(value.length()).append(':').append(value).append('|');
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every JRE", e);
        }
    }
}
