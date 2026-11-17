package ae.nest.crm.importexport;

import ae.nest.crm.common.CsvWriter;
import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadRequirement;
import ae.nest.crm.lead.LeadSpecifications;
import ae.nest.crm.repo.LeadRepository;
import ae.nest.crm.repo.LeadRequirementRepository;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExportService {

    private final LeadRepository leads;
    private final LeadRequirementRepository requirements;

    public ExportService(LeadRepository leads, LeadRequirementRepository requirements) {
        this.leads = leads;
        this.requirements = requirements;
    }

    private List<Lead> fetch(UUID workspaceId, boolean includeArchived) {
        Specification<Lead> spec = Specification.where(LeadSpecifications.inWorkspace(workspaceId));
        if (!includeArchived) {
            spec = spec.and(LeadSpecifications.archivedIs(false));
        }
        return leads.findAll(spec, Sort.by(Sort.Direction.ASC, "createdAt"));
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID workspaceId, boolean includeArchived) {
        CsvWriter csv = new CsvWriter();
        csv.row(List.of("id", "name", "email", "phone", "source", "stage", "purpose", "budget_aed",
                "bedrooms", "emirate", "community", "purchase_timeframe", "stated_intent", "sample",
                "archived_at", "created_at", "updated_at"));
        for (Lead l : fetch(workspaceId, includeArchived)) {
            csv.row(List.of(
                    s(l.getId()),
                    s(l.getName()),
                    s(l.getEmail()),
                    s(l.getPhone()),
                    s(l.getSource()),
                    s(l.getStage()),
                    s(l.getPurpose()),
                    s(l.getBudgetAed()),
                    s(l.getBedrooms()),
                    s(l.getEmirate()),
                    s(l.getCommunity()),
                    s(l.getPurchaseTimeframe()),
                    s(l.getStatedIntent()),
                    s(l.isSample()),
                    s(l.getArchivedAt()),
                    s(l.getCreatedAt()),
                    s(l.getUpdatedAt())));
        }
        return csv.build();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> exportJson(UUID workspaceId, boolean includeArchived) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Lead l : fetch(workspaceId, includeArchived)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.getId().toString());
            m.put("name", l.getName());
            m.put("email", l.getEmail());
            m.put("phone", l.getPhone());
            m.put("source", l.getSource());
            m.put("stage", l.getStage().name());
            m.put("purpose", l.getPurpose());
            m.put("budgetAed", l.getBudgetAed());
            m.put("bedrooms", l.getBedrooms());
            m.put("emirate", l.getEmirate());
            m.put("community", l.getCommunity());
            m.put("purchaseTimeframe", l.getPurchaseTimeframe());
            m.put("statedIntent", l.getStatedIntent());
            m.put("sample", l.isSample());
            m.put("archivedAt", l.getArchivedAt());
            LeadRequirement req = requirements.findByLeadId(l.getId()).orElse(null);
            Map<String, Object> reqMap = new LinkedHashMap<>();
            reqMap.put("mustHaveAmenities",
                    req == null ? List.of() : req.getMustHaveAmenities());
            reqMap.put("preferences", req == null ? Map.of() : req.getPreferences());
            m.put("requirement", reqMap);
            rows.add(m);
        }
        Map<String, Object> backup = new LinkedHashMap<>();
        backup.put("exportedAt", Instant.now().toString());
        backup.put("schema", "nest-crm-leads-v1");
        backup.put("leads", rows);
        return backup;
    }

    private static String s(Object o) {
        return o == null ? "" : o.toString();
    }
}
