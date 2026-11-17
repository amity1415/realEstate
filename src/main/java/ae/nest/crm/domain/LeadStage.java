package ae.nest.crm.domain;

/** Pipeline stages. Order defines the funnel for conversion reporting. */
public enum LeadStage {
    NEW,
    CONTACTED,
    QUALIFIED,
    VIEWING,
    WON,
    LOST
}
