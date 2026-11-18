package ae.nest.crm.repo;

import ae.nest.crm.domain.LeadScore;
import ae.nest.crm.domain.LeadSegment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LeadScoreRepository extends JpaRepository<LeadScore, UUID> {

    Optional<LeadScore> findByLeadId(UUID leadId);

    List<LeadScore> findByWorkspaceIdAndLeadIdIn(UUID workspaceId, List<UUID> leadIds);

    Page<LeadScore> findByWorkspaceIdAndSegment(UUID workspaceId, LeadSegment segment, Pageable pageable);

    Page<LeadScore> findByWorkspaceId(UUID workspaceId, Pageable pageable);

    /**
     * Scored leads that are still active (not archived, not a sample), highest score first; optionally
     * narrowed to one segment. Joined to the lead so a stale score on an archived lead never surfaces.
     */
    @Query("select s from LeadScore s, Lead l where s.leadId = l.id "
            + "and s.workspaceId = :ws and l.archivedAt is null and l.sample = false "
            + "and (:segment is null or s.segment = :segment) order by s.score desc")
    Page<LeadScore> findHot(
            @Param("ws") UUID ws, @Param("segment") LeadSegment segment, Pageable pageable);
}
