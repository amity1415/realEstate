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
class CoachingIntegrationTest {

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
        jdbc.execute("TRUNCATE coaching_feedback, coaching_runs, lead_stage_events, interactions, "
                + "tasks, contact_permissions, lead_requirements, import_jobs, audit_events, "
                + "property_sources, property_payment_terms, properties, leads, users, workspaces CASCADE");
        owner = seedOwner("owner@nest.test");
        otherOwner = seedOwner("rival@nest.test");
    }

    private AppUserPrincipal seedOwner(String email) {
        Workspace ws = new Workspace();
        ws.setName("WS " + email);
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
    void coachProducesGroundedCompletedRunAndIsIdempotent() throws Exception {
        String leadId = createLead("Aisha", 1500000, 2, "Dubai");
        addInteraction(leadId, "I like the apartment but the down payment is high. Maybe I'll wait.");
        createProperty("InBudget", 1300000, 2, "Dubai", false);
        createProperty("SampleUnit", 1200000, 2, "Dubai", true);

        MvcResult res = mvc.perform(post("/api/v1/leads/" + leadId + "/coaching-runs")
                        .with(user(owner)).with(csrf())
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"language\":\"en\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.mock").value(true))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.result.nextAction.type").value("CLARIFY_REQUIREMENTS"))
                .andExpect(jsonPath("$.result.propertyMatches.length()").value(1))
                .andExpect(jsonPath("$.result.propertyMatches[0].property.project").value("InBudget"))
                .andReturn();

        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        String runId = body.get("id").asText();
        boolean hasUpfront = false;
        for (JsonNode o : body.get("result").get("objections")) {
            if ("UPFRONT_PAYMENT".equals(o.get("category").asText())) {
                hasUpfront = true;
                // Evidence must resolve to a real interaction id.
                org.assertj.core.api.Assertions.assertThat(o.get("interactionIds").size())
                        .isGreaterThan(0);
            }
        }
        org.assertj.core.api.Assertions.assertThat(hasUpfront).isTrue();

        // Same idempotency key returns the same run (no new charge / duplicate).
        mvc.perform(post("/api/v1/leads/" + leadId + "/coaching-runs")
                        .with(user(owner)).with(csrf())
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(runId));
    }

    @Test
    void identicalInputIsCachedAcrossCalls() throws Exception {
        String leadId = createLead("Omar", 1000000, 1, "Dubai");
        addInteraction(leadId, "Looks good, keen to see options.");

        String first = coach(leadId);
        String second = coach(leadId);
        org.assertj.core.api.Assertions.assertThat(second).isEqualTo(first);

        mvc.perform(get("/api/v1/leads/" + leadId + "/coaching-runs").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void runIsMarkedOutdatedAfterLeadChanges() throws Exception {
        String leadId = createLead("Nadia", 1200000, 2, "Dubai");
        addInteraction(leadId, "Interested, please follow up.");
        String runId = coach(leadId);

        mvc.perform(patch("/api/v1/leads/" + leadId).with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nadia K\",\"stage\":\"QUALIFIED\",\"version\":0}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/coaching-runs/" + runId).with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outdated").value(true));
    }

    @Test
    void insufficientContextAsksQuestions() throws Exception {
        String leadId = createLead("Fresh Lead", 0, 0, "Dubai");
        // No interactions.
        mvc.perform(post("/api/v1/leads/" + leadId + "/coaching-runs").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.nextAction.type").value("CLARIFY_REQUIREMENTS"))
                .andExpect(jsonPath("$.result.nextAction.questions.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void feedbackIsAccepted() throws Exception {
        String leadId = createLead("Sara", 900000, 1, "Dubai");
        addInteraction(leadId, "Thanks for the info.");
        String runId = coach(leadId);

        mvc.perform(post("/api/v1/coaching-runs/" + runId + "/feedback").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"helpful\":true,\"edited\":true,\"actionTaken\":\"sent edited message\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void crossWorkspaceCoachingRunIsDenied() throws Exception {
        String leadId = createLead("Private", 1000000, 1, "Dubai");
        addInteraction(leadId, "Hello");
        String runId = coach(leadId);

        mvc.perform(get("/api/v1/coaching-runs/" + runId).with(user(otherOwner)))
                .andExpect(status().isNotFound());
    }

    // --- helpers ---

    private String coach(String leadId) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/leads/" + leadId + "/coaching-runs")
                        .with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
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

    private void addInteraction(String leadId, String text) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("type", "NOTE", "text", text));
        mvc.perform(post("/api/v1/leads/" + leadId + "/interactions").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }

    private void createProperty(String project, long price, int beds, String emirate, boolean sample)
            throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "unitType", "apartment", "project", project, "priceAed", price, "bedrooms", beds,
                "emirate", emirate, "sample", sample, "listingStatus", "AVAILABLE"));
        mvc.perform(post("/api/v1/properties").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
    }
}
