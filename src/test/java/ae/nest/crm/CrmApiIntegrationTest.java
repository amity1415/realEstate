package ae.nest.crm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
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
class CrmApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WorkspaceRepository workspaces;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    private AppUserPrincipal owner;
    private AppUserPrincipal otherOwner;

    @BeforeEach
    void setUp() {
        // Clean application tables between tests (schema is created by Flyway at context start).
        jdbc.execute("TRUNCATE lead_stage_events, interactions, tasks, contact_permissions, "
                + "lead_requirements, import_jobs, audit_events, property_sources, "
                + "property_payment_terms, properties, leads, users, workspaces CASCADE");
        owner = seedOwner("owner@nest.test", "Primary");
        otherOwner = seedOwner("rival@nest.test", "Rival");
    }

    private AppUserPrincipal seedOwner(String email, String wsName) {
        Workspace ws = new Workspace();
        ws.setName(wsName);
        ws.setTimezone("Asia/Dubai");
        ws = workspaces.save(ws);

        AppUser u = new AppUser();
        u.setWorkspaceId(ws.getId());
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Secret123!"));
        u.setRole(UserRole.OWNER);
        u.setStatus(UserStatus.ACTIVE);
        return new AppUserPrincipal(users.save(u));
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mvc.perform(get("/api/v1/leads")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithSeededCredentialsSucceeds() throws Exception {
        String body = objectMapper.writeValueAsString(
                java.util.Map.of("email", "owner@nest.test", "password", "Secret123!"));
        mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("owner@nest.test"));
    }

    @Test
    void createGetAndOptimisticUpdate() throws Exception {
        String id = createLead(owner, "Aisha", 1500000, 2, "Dubai");

        mvc.perform(get("/api/v1/leads/" + id).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Aisha"))
                .andExpect(jsonPath("$.version").value(0));

        // Stale version -> conflict
        mvc.perform(patch("/api/v1/leads/" + id).with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Aisha Khan", "QUALIFIED", 5)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("conflict"));

        // Correct version -> ok, stage change recorded
        mvc.perform(patch("/api/v1/leads/" + id).with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("Aisha Khan", "QUALIFIED", 0)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("QUALIFIED"))
                .andExpect(jsonPath("$.version").value(1));

        mvc.perform(get("/api/v1/leads/" + id + "/stage-history").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].newStage").value("NEW"))
                .andExpect(jsonPath("$[1].newStage").value("QUALIFIED"));
    }

    @Test
    void crossWorkspaceAccessIsDenied() throws Exception {
        String id = createLead(owner, "Private", 1000000, 1, "Dubai");
        // The rival owner must not see another workspace's lead.
        mvc.perform(get("/api/v1/leads/" + id).with(user(otherOwner)))
                .andExpect(status().isNotFound());
    }

    @Test
    void propertyMatchesEnforceHardConstraintsAndExcludeSamples() throws Exception {
        String leadId = createLead(owner, "Buyer", 1000000, 2, "Dubai");

        createProperty(owner, "InBudget", 900000, 2, "Dubai", false, "AVAILABLE");
        createProperty(owner, "OverBudget", 1500000, 2, "Dubai", false, "AVAILABLE");
        createProperty(owner, "SampleUnit", 800000, 2, "Dubai", true, "AVAILABLE");

        mvc.perform(get("/api/v1/leads/" + leadId + "/property-matches").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].property.project").value("InBudget"));
    }

    @Test
    void importCommitIsIdempotent() throws Exception {
        String payload = "[{\"name\":\"Imported One\",\"email\":\"one@x.com\"},"
                + "{\"name\":\"Imported One Dup\",\"email\":\"one@x.com\"},"
                + "{\"email\":\"noname@x.com\"}]";

        mvc.perform(post("/api/v1/imports/leads/preview").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.invalid.length()").value(1))
                .andExpect(jsonPath("$.toImport").value(1));

        MvcResult first = mvc.perform(post("/api/v1/imports/leads/commit").with(user(owner))
                        .with(csrf()).header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.skippedDuplicates").value(1))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andReturn();

        // Replaying the same key must not import again.
        mvc.perform(post("/api/v1/imports/leads/commit").with(user(owner)).with(csrf())
                        .header("Idempotency-Key", "key-123")
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.imported").value(1))
                .andExpect(jsonPath("$.idempotentReplay").value(true));
    }

    @Test
    void exportCsvNeutralisesFormulaInjection() throws Exception {
        createLead(owner, "=HYPERLINK(evil)", 500000, 1, "Dubai");
        mvc.perform(get("/api/v1/exports/leads?format=csv").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("'=HYPERLINK")));
    }

    // --- helpers ---

    private String createLead(AppUserPrincipal who, String name, long budget, int beds, String emirate)
            throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "name", name, "budgetAed", budget, "bedrooms", beds, "emirate", emirate));
        MvcResult res = mvc.perform(post("/api/v1/leads").with(user(who)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(res.getResponse().getContentAsString());
        return node.get("id").asText();
    }

    private void createProperty(AppUserPrincipal who, String project, long price, int beds,
            String emirate, boolean sample, String listingStatus) throws Exception {
        String body = objectMapper.writeValueAsString(java.util.Map.of(
                "unitType", "apartment", "project", project, "priceAed", price, "bedrooms", beds,
                "emirate", emirate, "sample", sample, "listingStatus", listingStatus));
        mvc.perform(post("/api/v1/properties").with(user(who)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private String updateBody(String name, String stage, long version) throws Exception {
        return objectMapper.writeValueAsString(java.util.Map.of(
                "name", name, "stage", stage, "version", version));
    }
}
