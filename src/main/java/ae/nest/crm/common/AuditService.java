package ae.nest.crm.common;

import ae.nest.crm.domain.AuditEvent;
import ae.nest.crm.repo.AuditEventRepository;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records advisor/system actions with safe metadata only (ids, counts) — never PII, secrets or
 * full text. Kept separate from AI suggestions so advisor decisions are distinguishable.
 */
@Service
public class AuditService {

    private final AuditEventRepository repo;

    public AuditService(AuditEventRepository repo) {
        this.repo = repo;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID workspaceId, UUID actorId, String operation, String result,
            Map<String, Object> metadata) {
        AuditEvent event = new AuditEvent();
        event.setWorkspaceId(workspaceId);
        event.setActorId(actorId);
        event.setOperation(operation);
        event.setResult(result);
        event.setMetadata(metadata);
        repo.save(event);
    }
}
