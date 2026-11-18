package ae.nest.crm.repo;

import ae.nest.crm.domain.Interaction;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InteractionRepository extends JpaRepository<Interaction, UUID> {

    Page<Interaction> findByLeadIdAndWorkspaceIdOrderByOccurredAtDesc(
            UUID leadId, UUID workspaceId, Pageable pageable);

    /** [count, max(occurredAt)] for one lead. */
    @Query("select count(i), max(i.occurredAt) from Interaction i where i.leadId = :leadId")
    List<Object[]> aggregateForLead(@Param("leadId") UUID leadId);

    /** [leadId, count, max(occurredAt)] per lead — for bulk re-scoring. */
    @Query("select i.leadId, count(i), max(i.occurredAt) from Interaction i group by i.leadId")
    List<Object[]> aggregatesAll();
}
