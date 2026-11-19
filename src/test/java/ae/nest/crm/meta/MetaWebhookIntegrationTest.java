package ae.nest.crm.meta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ae.nest.crm.domain.AppUser;
import ae.nest.crm.domain.InboundSource;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadAttribution;
import ae.nest.crm.domain.UserRole;
import ae.nest.crm.domain.UserStatus;
import ae.nest.crm.domain.Workspace;
import ae.nest.crm.repo.AppUserRepository;
import ae.nest.crm.repo.InboundSourceRepository;
import ae.nest.crm.repo.LeadAttributionRepository;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.WorkspaceRepository;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end test of the Meta Lead Ads webhook: the subscription handshake, signature enforcement, and
 * a signed delivery (with inline field data) creating a lead with marketing attribution.
 */
@SpringBootTest(properties = {
        "app.meta.verify-token=verify-abc",
        "app.meta.app-secret=secret-xyz",
        "app.meta.source-token=meta-known-token"
})
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase
@ActiveProfiles("test")
class MetaWebhookIntegrationTest {

    private static final String APP_SECRET = "secret-xyz";
    private static final String SOURCE_TOKEN = "meta-known-token";

    @Autowired MockMvc mvc;
    @Autowired WorkspaceRepository workspaces;
    @Autowired AppUserRepository users;
    @Autowired InboundSourceRepository sources;
    @Autowired LeadRepository leads;
    @Autowired LeadAttributionRepository attribution;
    @Autowired JdbcTemplate jdbc;

    private java.util.UUID workspaceId;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE lead_stage_events, interactions, tasks, contact_permissions, "
                + "lead_requirements, import_jobs, audit_events, property_sources, "
                + "property_payment_terms, properties, inbound_sources, notifications, leads, "
                + "users, workspaces CASCADE");
        Workspace ws = new Workspace();
        ws.setName("Meta WS");
        ws.setTimezone("Asia/Dubai");
        ws = workspaces.save(ws);
        workspaceId = ws.getId();

        AppUser owner = new AppUser();
        owner.setWorkspaceId(ws.getId());
        owner.setEmail("owner@meta.test");
        owner.setPasswordHash("x");
        owner.setRole(UserRole.OWNER);
        owner.setStatus(UserStatus.ACTIVE);
        owner = users.save(owner);

        // The source the Meta integration routes leads through — keyed by the known token's hash.
        InboundSource src = new InboundSource();
        src.setWorkspaceId(ws.getId());
        src.setName("Meta Lead Ads");
        src.setTokenHash(sha256(SOURCE_TOKEN));
        src.setCreatedBy(owner.getId());
        src.setDefaultOwnerId(owner.getId());
        src.setActive(true);
        sources.save(src);
    }

    @Test
    void handshakeEchoesChallengeOnlyWhenVerifyTokenMatches() throws Exception {
        mvc.perform(get("/webhooks/meta/leads")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "verify-abc")
                        .param("hub.challenge", "42"))
                .andExpect(status().isOk())
                .andExpect(content().string("42"));

        mvc.perform(get("/webhooks/meta/leads")
                        .param("hub.mode", "subscribe")
                        .param("hub.verify_token", "WRONG")
                        .param("hub.challenge", "42"))
                .andExpect(status().isForbidden());
    }

    @Test
    void unsignedDeliveryIsRejectedAndCreatesNothing() throws Exception {
        byte[] body = payload().getBytes(StandardCharsets.UTF_8);
        mvc.perform(post("/webhooks/meta/leads")
                        .header("X-Hub-Signature-256", "sha256=deadbeef")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        assertThat(leads.count()).isZero();
    }

    @Test
    void signedDeliveryCreatesLeadWithAttribution() throws Exception {
        byte[] body = payload().getBytes(StandardCharsets.UTF_8);
        String sig = "sha256=" + MetaSignature.hmacSha256Hex(body, APP_SECRET);

        mvc.perform(post("/webhooks/meta/leads")
                        .header("X-Hub-Signature-256", sig)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(content().string("EVENT_RECEIVED"));

        assertThat(leads.count()).isEqualTo(1);
        Lead lead = leads.findAll().get(0);
        assertThat(lead.getName()).isEqualTo("Fatima Noor");
        assertThat(lead.getEmail()).isEqualTo("fatima@example.com");
        assertThat(lead.getWorkspaceId()).isEqualTo(workspaceId);

        LeadAttribution attr = attribution.findByLeadId(lead.getId()).orElseThrow();
        assertThat(attr.getChannel()).isEqualTo("Meta Lead Ads");
        assertThat(attr.getUtmCampaign()).isEqualTo("Downtown Launch");
        assertThat(attr.getAdId()).isEqualTo("ad_987");
    }

    /** A webhook payload carrying inline field data (as a test delivery would), so no Graph call is needed. */
    private static String payload() {
        return "{\"object\":\"page\",\"entry\":[{\"id\":\"page_1\",\"changes\":[{"
                + "\"field\":\"leadgen\",\"value\":{"
                + "\"leadgen_id\":\"lead_1\",\"form_id\":\"form_55\",\"ad_id\":\"ad_987\","
                + "\"campaign_name\":\"Downtown Launch\",\"created_time\":1700000000,"
                + "\"field_data\":["
                + "{\"name\":\"full_name\",\"values\":[\"Fatima Noor\"]},"
                + "{\"name\":\"email\",\"values\":[\"fatima@example.com\"]},"
                + "{\"name\":\"phone_number\",\"values\":[\"+971500000000\"]},"
                + "{\"name\":\"budget\",\"values\":[\"2M AED\"]}"
                + "]}}]}]}";
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
