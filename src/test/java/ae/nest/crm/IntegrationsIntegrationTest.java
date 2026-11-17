package ae.nest.crm;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ae.nest.crm.domain.AppUser;
import ae.nest.crm.domain.UserRole;
import ae.nest.crm.domain.UserStatus;
import ae.nest.crm.domain.Workspace;
import ae.nest.crm.notification.ReminderService;
import ae.nest.crm.outbox.OutboxDispatcher;
import ae.nest.crm.repo.AppUserRepository;
import ae.nest.crm.repo.WorkspaceRepository;
import ae.nest.crm.security.AppUserPrincipal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.zonky.test.db.AutoConfigureEmbeddedDatabase;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class IntegrationsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WorkspaceRepository workspaces;
    @Autowired AppUserRepository users;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ReminderService reminderService;
    @Autowired OutboxDispatcher outboxDispatcher;

    private AppUserPrincipal owner;
    private AppUserPrincipal agent;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE notifications, outbox_messages, inbound_sources, coaching_feedback, "
                + "coaching_runs, lead_stage_events, interactions, tasks, contact_permissions, "
                + "lead_requirements, import_jobs, audit_events, property_sources, "
                + "property_payment_terms, properties, leads, users, workspaces CASCADE");
        Workspace ws = new Workspace();
        ws.setName("NEST");
        ws.setTimezone("Asia/Dubai");
        ws = workspaces.save(ws);
        owner = seedUser(ws.getId(), "owner@nest.test", UserRole.OWNER);
        agent = seedUser(ws.getId(), "agent@nest.test", UserRole.AGENT);
    }

    private AppUserPrincipal seedUser(java.util.UUID wsId, String email, UserRole role) {
        AppUser u = new AppUser();
        u.setWorkspaceId(wsId);
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode("Secret123!"));
        u.setRole(role);
        u.setStatus(UserStatus.ACTIVE);
        return new AppUserPrincipal(users.save(u));
    }

    @Test
    void ownerCreatesMemberButAgentCannot() throws Exception {
        mvc.perform(post("/api/v1/users").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"new@nest.test\",\"role\":\"AGENT\","
                                + "\"initialPassword\":\"TempPass123\"}"))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/users").with(user(agent)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"x@nest.test\",\"role\":\"AGENT\","
                                + "\"initialPassword\":\"TempPass123\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void assignmentNotifiesTheAssignee() throws Exception {
        String leadId = createLead("Buyer");
        mvc.perform(post("/api/v1/leads/" + leadId + "/assign").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + agent.getUserId() + "\"}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/notifications").with(user(agent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("LEAD_ASSIGNED"));
    }

    @Test
    void dueTaskReminderIsDeliveredOnceAsNotification() throws Exception {
        String leadId = createLead("Lead");
        String due = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        mvc.perform(post("/api/v1/leads/" + leadId + "/tasks").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"Call back\",\"dueAt\":\"" + due + "\"}"))
                .andExpect(status().isCreated());

        org.assertj.core.api.Assertions.assertThat(reminderService.runDueReminders()).isEqualTo(1);
        // Re-running must not create a duplicate reminder (dedupe).
        org.assertj.core.api.Assertions.assertThat(reminderService.runDueReminders()).isEqualTo(0);

        mvc.perform(get("/api/v1/notifications/unread-count").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unread").value(1));
    }

    @Test
    void outboxDeliversViaSimulatedProviderAndDedupes() throws Exception {
        String leadId = createLead("Lead");
        mvc.perform(post("/api/v1/leads/" + leadId + "/outbox").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"recipient\":\"b@x.com\",\"body\":\"Hi\","
                                + "\"dedupeKey\":\"d1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"));

        // Duplicate dedupe key returns the same message, not a second one.
        MvcResult dup = mvc.perform(post("/api/v1/leads/" + leadId + "/outbox").with(user(owner))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"channel\":\"EMAIL\",\"recipient\":\"b@x.com\",\"body\":\"Hi\","
                                + "\"dedupeKey\":\"d1\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode dupNode = objectMapper.readTree(dup.getResponse().getContentAsString());

        OutboxDispatcher.DispatchSummary summary = outboxDispatcher.dispatchDue(10);
        org.assertj.core.api.Assertions.assertThat(summary.delivered()).isEqualTo(1);

        mvc.perform(get("/api/v1/leads/" + leadId + "/outbox").with(user(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SIMULATED_SENT"))
                .andExpect(jsonPath("$.content[0].simulated").value(true));
    }

    @Test
    void inboundCaptureCreatesLeadAndDedupesByEmail() throws Exception {
        MvcResult src = mvc.perform(post("/api/v1/inbound-sources").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Website\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String token = objectMapper.readTree(src.getResponse().getContentAsString()).get("token")
                .asText();

        mvc.perform(post("/api/v1/inbound/leads").header("X-Inbound-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Web Lead\",\"email\":\"web@x.com\",\"message\":\"Interested\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.duplicate").value(false));

        // Same email again attaches to the existing lead rather than duplicating.
        mvc.perform(post("/api/v1/inbound/leads").header("X-Inbound-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Web Lead\",\"email\":\"web@x.com\",\"message\":\"Still keen\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.duplicate").value(true));

        // Invalid token is rejected.
        mvc.perform(post("/api/v1/inbound/leads").header("X-Inbound-Token", "nope")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"z@x.com\"}"))
                .andExpect(status().isBadRequest());
    }

    private String createLead(String name) throws Exception {
        MvcResult res = mvc.perform(post("/api/v1/leads").with(user(owner)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }
}
