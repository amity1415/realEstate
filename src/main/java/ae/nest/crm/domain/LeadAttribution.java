package ae.nest.crm.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Where a lead came from — marketing attribution (campaign, ad, form, UTMs). */
@Entity
@Table(name = "lead_attribution")
@Getter
@Setter
public class LeadAttribution extends BaseEntity {

    @Column(name = "lead_id", nullable = false, unique = true)
    private UUID leadId;

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    private String channel;

    @Column(name = "utm_source")
    private String utmSource;

    @Column(name = "utm_medium")
    private String utmMedium;

    @Column(name = "utm_campaign")
    private String utmCampaign;

    @Column(name = "ad_id")
    private String adId;

    @Column(name = "form_id")
    private String formId;

    @Column(name = "landing_url")
    private String landingUrl;

    @Column(name = "first_touch_at")
    private Instant firstTouchAt;

    @Column(name = "last_touch_at")
    private Instant lastTouchAt;
}
