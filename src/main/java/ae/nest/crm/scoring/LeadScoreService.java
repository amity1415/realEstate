package ae.nest.crm.scoring;

import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadScore;
import ae.nest.crm.domain.LeadSegment;
import ae.nest.crm.repo.InteractionRepository;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.LeadScoreRepository;
import ae.nest.crm.scoring.LeadScorer.Factor;
import ae.nest.crm.scoring.LeadScorer.ScoreResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LeadScoreService {

    private static final Logger log = LoggerFactory.getLogger(LeadScoreService.class);

    private final LeadScoreRepository scores;
    private final LeadRepository leads;
    private final InteractionRepository interactions;

    public LeadScoreService(
            LeadScoreRepository scores, LeadRepository leads, InteractionRepository interactions) {
        this.scores = scores;
        this.leads = leads;
        this.interactions = interactions;
    }

    /** Re-score one lead (cheap: one aggregate query + one upsert). Never touches the lead row. */
    @Transactional
    public void recomputeForLead(Lead lead) {
        int count = 0;
        Instant last = null;
        List<Object[]> agg = interactions.aggregateForLead(lead.getId());
        if (!agg.isEmpty() && agg.get(0)[0] != null) {
            count = ((Number) agg.get(0)[0]).intValue();
            last = toInstant(agg.get(0)[1]);
        }
        ScoreResult r = score(lead, count, last);
        LeadScore sc = scores.findByLeadId(lead.getId()).orElseGet(() -> blank(lead));
        apply(sc, r);
        scores.save(sc);
    }

    /** Re-score every active lead in two queries + a batch upsert — for the scheduled job. */
    @Transactional
    public int recomputeAll() {
        Map<UUID, int[]> counts = new HashMap<>();
        Map<UUID, Instant> lasts = new HashMap<>();
        for (Object[] row : interactions.aggregatesAll()) {
            UUID lid = (UUID) row[0];
            counts.put(lid, new int[] { ((Number) row[1]).intValue() });
            lasts.put(lid, toInstant(row[2]));
        }
        Map<UUID, LeadScore> existing = new HashMap<>();
        scores.findAll().forEach(s -> existing.put(s.getLeadId(), s));

        List<Lead> active = leads.findByArchivedAtIsNull();
        List<LeadScore> toSave = new ArrayList<>(active.size());
        for (Lead lead : active) {
            int c = counts.containsKey(lead.getId()) ? counts.get(lead.getId())[0] : 0;
            ScoreResult r = score(lead, c, lasts.get(lead.getId()));
            LeadScore sc = existing.getOrDefault(lead.getId(), blank(lead));
            apply(sc, r);
            toSave.add(sc);
        }
        scores.saveAll(toSave);
        log.info("Re-scored {} active leads.", toSave.size());
        return toSave.size();
    }

    /** Scores keyed by lead id, for merging into a board/list page. */
    @Transactional(readOnly = true)
    public Map<UUID, LeadScore> scoresFor(UUID workspaceId, List<UUID> leadIds) {
        Map<UUID, LeadScore> m = new HashMap<>();
        if (!leadIds.isEmpty()) {
            scores.findByWorkspaceIdAndLeadIdIn(workspaceId, leadIds).forEach(s -> m.put(s.getLeadId(), s));
        }
        return m;
    }

    private ScoreResult score(Lead lead, int interactionCount, Instant last) {
        return LeadScorer.score(
                lead.getStage(),
                lead.getBudgetAed() != null,
                lead.getBedrooms() != null,
                hasText(lead.getEmirate()),
                hasText(lead.getStatedIntent()),
                interactionCount,
                last,
                Instant.now());
    }

    private LeadScore blank(Lead lead) {
        LeadScore s = new LeadScore();
        s.setLeadId(lead.getId());
        s.setWorkspaceId(lead.getWorkspaceId());
        return s;
    }

    private void apply(LeadScore sc, ScoreResult r) {
        sc.setScore(r.score());
        sc.setSegment(r.segment());
        List<Map<String, Object>> factors = new ArrayList<>();
        for (Factor f : r.factors()) {
            factors.add(Map.of("factor", f.factor(), "points", f.points()));
        }
        sc.setFactors(factors);
        sc.setComputedAt(Instant.now());
    }

    private static Instant toInstant(Object o) {
        if (o == null) {
            return null;
        }
        if (o instanceof Instant i) {
            return i;
        }
        if (o instanceof java.sql.Timestamp t) {
            return t.toInstant();
        }
        if (o instanceof OffsetDateTime odt) {
            return odt.toInstant();
        }
        return null;
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }
}
