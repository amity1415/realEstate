package ae.nest.crm.meta;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Validates Meta's {@code X-Hub-Signature-256} header: HMAC-SHA256 of the raw body, keyed by app secret. */
public final class MetaSignature {

    private MetaSignature() {}

    /**
     * Constant-time check that {@code header} (e.g. "sha256=abcd…") matches HMAC-SHA256(body, appSecret).
     * Returns false for any missing/garbled input rather than throwing.
     */
    public static boolean valid(byte[] body, String header, String appSecret) {
        if (body == null || header == null || appSecret == null || appSecret.isBlank()) {
            return false;
        }
        String prefix = "sha256=";
        if (!header.startsWith(prefix)) {
            return false;
        }
        String expected = hmacSha256Hex(body, appSecret);
        byte[] a = expected.getBytes(StandardCharsets.UTF_8);
        byte[] b = header.substring(prefix.length()).trim().toLowerCase().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(a, b);
    }

    public static String hmacSha256Hex(byte[] body, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte x : digest) {
                sb.append(Character.forDigit((x >> 4) & 0xF, 16));
                sb.append(Character.forDigit(x & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 failed", e);
        }
    }
}
