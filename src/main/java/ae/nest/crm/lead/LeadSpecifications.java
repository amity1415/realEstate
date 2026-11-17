package ae.nest.crm.lead;

import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadStage;
import java.util.UUID;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

/** Composable, workspace-scoped filters for the pipeline list endpoint. */
public final class LeadSpecifications {

    private LeadSpecifications() {}

    public static Specification<Lead> inWorkspace(UUID workspaceId) {
        return (root, q, cb) -> cb.equal(root.get("workspaceId"), workspaceId);
    }

    public static Specification<Lead> ownedBy(UUID ownerId) {
        if (ownerId == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("ownerId"), ownerId);
    }

    public static Specification<Lead> hasStage(LeadStage stage) {
        if (stage == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("stage"), stage);
    }

    public static Specification<Lead> sampleIs(Boolean sample) {
        if (sample == null) {
            return null;
        }
        return (root, q, cb) -> cb.equal(root.get("sample"), sample);
    }

    public static Specification<Lead> archivedIs(boolean archived) {
        return (root, q, cb) ->
                archived ? cb.isNotNull(root.get("archivedAt")) : cb.isNull(root.get("archivedAt"));
    }

    public static Specification<Lead> emirate(String emirate) {
        if (!StringUtils.hasText(emirate)) {
            return null;
        }
        return (root, q, cb) -> cb.equal(cb.lower(root.get("emirate")), emirate.toLowerCase());
    }

    public static Specification<Lead> search(String term) {
        if (!StringUtils.hasText(term)) {
            return null;
        }
        String like = "%" + term.toLowerCase().trim() + "%";
        return (root, q, cb) -> cb.or(
                cb.like(cb.lower(root.get("name")), like),
                cb.like(cb.lower(cb.coalesce(root.get("email"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("phone"), "")), like),
                cb.like(cb.lower(cb.coalesce(root.get("community"), "")), like));
    }
}
