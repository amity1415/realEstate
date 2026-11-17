package ae.nest.crm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ae.nest.crm.domain.AppUser;
import ae.nest.crm.domain.UserRole;
import ae.nest.crm.domain.UserStatus;
import ae.nest.crm.domain.Workspace;
import ae.nest.crm.repo.AppUserRepository;
import ae.nest.crm.repo.WorkspaceRepository;
import ae.nest.crm.security.AppUserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureEmbeddedDatabase
@ActiveProfiles("test")
class InventoryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WorkspaceRepository workspaces;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private AppUserPrincipal owner;

    private static final String INVENTORY = "{\"source\":{\"approvedSource\":\"Sheet\","
            + "\"sourceOwner\":\"NEST\",\"permission\":\"AUTHORISED\"},\"properties\":["
            + "{\"externalId\":\"A1\",\"project\":\"Creek Rise\",\"emirate\":\"Dubai\",\"bedrooms\":2,"
            + "\"priceAed\":1300000,\"listingStatus\":\"AVAILABLE\"},"
            + "{\"externalId\":\"A2\",\"project\":\"JVC Bellavista\",\"emirate\":\"Dubai\",\"bedrooms\":1,"
            + "\"priceAed\":800000,\"listingStatus\":\"AVAILABLE\"}]}";

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE coaching_feedback, coaching_runs, lead_stage_events, interactions, "
                + "tasks, contact_permissions, lead_requirements, import_jobs, audit_events, "
                + "property_sources, property_payment_terms, properties, leads, users, workspaces CASCADE");
        Workspace ws = new Workspace();
        ws.setName("NEST");
        ws.setTimezone("Asia/Dubai");
        ws = workspaces.save(ws);
        AppUser u = new AppUser();
        u.setWorkspaceId(ws.getId());
        u.setEmail("owner@nest.test");
        u.setPasswordHash(passwordEncoder.encode("Secret123!"));
        u.setRole(UserRole.OWNER);
        u.setStatus(UserStatus.ACTIVE);
        owner = new AppUserPrincipal(users.save(u));
    }

    @Test
    void previewAndIdempotentCommit() throws Exception {
        mvc.perform(post("/api/v1/imports/properties/preview").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(INVENTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.toCreate").value(2));

        mvc.perform(post("/api/v1/imports/properties/commit").with(user(owner)).with(csrf())
                        .header("Idempotency-Key", "inv1")
                        .contentType(MediaType.APPLICATION_JSON).content(INVENTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.idempotentReplay").value(false));

        // Replay with same key must not re-import.
        mvc.perform(post("/api/v1/imports/properties/commit").with(user(owner)).with(csrf())
                        .header("Idempotency-Key", "inv1")
                        .contentType(MediaType.APPLICATION_JSON).content(INVENTORY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true))
                .andExpect(jsonPath("$.created").value(2));
    }

    @Test
    void importedInventoryIsUnverifiedThenVerifiable() throws Exception {
        importInventory();
        String propId = firstPropertyId();

        mvc.perform(get("/api/v1/properties/" + propId).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freshness.status").value("UNVERIFIED"));

        mvc.perform(get("/api/v1/properties/needs-attention").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mvc.perform(post("/api/v1/properties/" + propId + "/verify").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"approvedSource\":\"Master sheet\",\"permission\":\"AUTHORISED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.freshness.status").value("VERIFIED_FRESH"));

        mvc.perform(get("/api/v1/properties/needs-attention").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void matchesFlagUnverifiedInventory() throws Exception {
        importInventory();
        String leadId = createLead("Buyer", 1500000, 2, "Dubai");

        MvcResult res = mvc.perform(get("/api/v1/leads/" + leadId + "/property-matches")
                        .with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andReturn();
        JsonNode matches = objectMapper.readTree(res.getResponse().getContentAsString());
        boolean flagged = false;
        for (JsonNode t : matches.get(0).get("tradeOffs")) {
            if (t.asText().toLowerCase().contains("verify")) {
                flagged = true;
            }
        }
        org.assertj.core.api.Assertions.assertThat(flagged).isTrue();
    }

    @Test
    void cohortConversionReport() throws Exception {
        String leadId = createLead("Winner", 1000000, 1, "Dubai");
        mvc.perform(patch("/api/v1/leads/" + leadId).with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Winner\",\"stage\":\"WON\",\"version\":0}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/metrics/conversion").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cohortSize").value(1))
                .andExpect(jsonPath("$.reachedByStage.WON").value(1))
                .andExpect(jsonPath("$.conversionRateByStage.WON").value(1.0));
    }

    // --- helpers ---

    private void importInventory() throws Exception {
        mvc.perform(post("/api/v1/imports/properties/commit").with(user(owner)).with(csrf())
                        .header("Idempotency-Key", "inv-" + System.nanoTime())
                        .contentType(MediaType.APPLICATION_JSON).content(INVENTORY))
                .andExpect(status().isOk());
    }

    private String firstPropertyId() throws Exception {
        MvcResult res = mvc.perform(get("/api/v1/properties?size=10").with(user(owner)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString())
                .get("content").get(0).get("id").asText();
    }

    private String createLead(String name, long budget, int beds, String emirate) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", name, "budgetAed", budget, "bedrooms", beds, "emirate", emirate));
        MvcResult res = mvc.perform(post("/api/v1/leads").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }
}
