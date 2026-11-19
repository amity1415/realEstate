package ae.nest.crm.meta;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** Unit tests for the Meta webhook HMAC check — no Spring. */
class MetaSignatureTest {

    private static final String SECRET = "app-secret-123";

    @Test
    void acceptsACorrectlySignedBody() {
        byte[] body = "{\"object\":\"page\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + MetaSignature.hmacSha256Hex(body, SECRET);
        assertThat(MetaSignature.valid(body, header, SECRET)).isTrue();
    }

    @Test
    void rejectsTamperedBody() {
        byte[] body = "{\"object\":\"page\"}".getBytes(StandardCharsets.UTF_8);
        String header = "sha256=" + MetaSignature.hmacSha256Hex(body, SECRET);
        byte[] tampered = "{\"object\":\"evil\"}".getBytes(StandardCharsets.UTF_8);
        assertThat(MetaSignature.valid(tampered, header, SECRET)).isFalse();
    }

    @Test
    void rejectsWrongSecretMissingPrefixAndNulls() {
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        String good = "sha256=" + MetaSignature.hmacSha256Hex(body, SECRET);
        assertThat(MetaSignature.valid(body, good, "other-secret")).isFalse();
        assertThat(MetaSignature.valid(body, MetaSignature.hmacSha256Hex(body, SECRET), SECRET)).isFalse();
        assertThat(MetaSignature.valid(body, null, SECRET)).isFalse();
        assertThat(MetaSignature.valid(body, good, null)).isFalse();
        assertThat(MetaSignature.valid(null, good, SECRET)).isFalse();
    }
}
