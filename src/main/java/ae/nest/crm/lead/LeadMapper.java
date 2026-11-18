package ae.nest.crm.lead;

import ae.nest.crm.domain.Lead;
import ae.nest.crm.domain.LeadRequirement;
import ae.nest.crm.domain.LeadScore;
import ae.nest.crm.domain.LeadStageEvent;
import ae.nest.crm.lead.LeadDtos.HotLeadSummary;
import ae.nest.crm.lead.LeadDtos.LeadResponse;
import ae.nest.crm.lead.LeadDtos.LeadSummary;
import ae.nest.crm.lead.LeadDtos.RequirementDto;
import ae.nest.crm.lead.LeadDtos.StageEventDto;
import java.util.List;
import java.util.Map;

final class LeadMapper {

    private LeadMapper() {}

    static RequirementDto requirementDto(LeadRequirement req) {
        if (req == null) {
            return new RequirementDto(List.of(), Map.of(), null);
        }
        return new RequirementDto(
                req.getMustHaveAmenities() == null ? List.of() : req.getMustHaveAmenities(),
                req.getPreferences() == null ? Map.of() : req.getPreferences(),
                req.getProvenance());
    }

    static LeadResponse toResponse(Lead lead, LeadRequirement req) {
        return new LeadResponse(
                lead.getId().toString(),
                lead.getName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getSource(),
                lead.getStage(),
                lead.getPurpose(),
                lead.getBudgetAed(),
                lead.getBedrooms(),
                lead.getEmirate(),
                lead.getCommunity(),
                lead.getPurchaseTimeframe(),
                lead.getStatedIntent(),
                lead.isSample(),
                lead.getArchivedAt(),
                lead.getVersion(),
                lead.getCreatedAt(),
                lead.getUpdatedAt(),
                requirementDto(req));
    }

    static LeadSummary toSummary(Lead lead) {
        return toSummary(lead, null);
    }

    static LeadSummary toSummary(Lead lead, LeadScore score) {
        return new LeadSummary(
                lead.getId().toString(),
                lead.getName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getStage(),
                lead.getSource(),
                lead.getBudgetAed(),
                lead.getBedrooms(),
                lead.getEmirate(),
                lead.isSample(),
                lead.getArchivedAt(),
                lead.getUpdatedAt(),
                score == null ? null : score.getScore(),
                score == null ? null : score.getSegment());
    }

    static HotLeadSummary toHotSummary(Lead lead, LeadScore score) {
        return new HotLeadSummary(
                lead.getId().toString(),
                lead.getName(),
                lead.getEmail(),
                lead.getPhone(),
                lead.getStage(),
                lead.getEmirate(),
                lead.getBudgetAed(),
                lead.getBedrooms(),
                score.getScore(),
                score.getSegment(),
                score.getFactors() == null ? List.of() : score.getFactors(),
                score.getComputedAt(),
                lead.getUpdatedAt());
    }

    static StageEventDto toStageEvent(LeadStageEvent e) {
        return new StageEventDto(
                e.getId().toString(), e.getOldStage(), e.getNewStage(), e.getChangedAt(),
                e.getReason());
    }
}
