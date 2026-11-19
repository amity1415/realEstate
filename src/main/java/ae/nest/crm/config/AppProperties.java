package ae.nest.crm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application configuration mapped explicitly from environment variables.
 *
 * <p>These names are implemented here deliberately so that deployment variables map to typed config
 * rather than being assumed to configure libraries automatically.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Public base URL of the deployed application (used for absolute links/CORS exact-origin). */
    private String baseUrl = "http://localhost:8080";

    /** Advisor display timezone. Timestamps are stored in UTC; this only affects display defaults. */
    private String defaultTimezone = "Asia/Kolkata";

    private final Owner owner = new Owner();
    private final Ai ai = new Ai();
    private final Inventory inventory = new Inventory();
    private final Delivery delivery = new Delivery();
    private final Meta meta = new Meta();

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getDefaultTimezone() {
        return defaultTimezone;
    }

    public void setDefaultTimezone(String defaultTimezone) {
        this.defaultTimezone = defaultTimezone;
    }

    public Owner getOwner() {
        return owner;
    }

    public Ai getAi() {
        return ai;
    }

    public Inventory getInventory() {
        return inventory;
    }

    public Delivery getDelivery() {
        return delivery;
    }

    public Meta getMeta() {
        return meta;
    }

    /**
     * Meta (Facebook/Instagram) Lead Ads webhook integration. Disabled until the three secrets are set.
     * The webhook verifies the subscription handshake, checks the HMAC signature on every delivery, and
     * funnels each lead through the existing token-authenticated inbound intake using {@code sourceToken}
     * (so Meta leads reuse the same dedupe, attribution, notification and scoring as any inbound lead).
     */
    public static class Meta {
        /** Token echoed back on the GET subscription handshake (you choose this in the Meta dashboard). */
        private String verifyToken;
        /** Meta app secret — used to validate the X-Hub-Signature-256 HMAC on every POST. */
        private String appSecret;
        /** Page access token used to fetch a lead's field data from the Graph API. */
        private String pageAccessToken;
        /** Graph API version, e.g. v21.0. */
        private String graphVersion = "v21.0";
        /** Plaintext inbound-source token that maps Meta leads to a workspace + default owner. */
        private String sourceToken;

        /** The webhook is active only when the handshake + signature secrets are present. */
        public boolean isConfigured() {
            return notBlank(verifyToken) && notBlank(appSecret);
        }

        private static boolean notBlank(String s) {
            return s != null && !s.isBlank();
        }

        public String getVerifyToken() {
            return verifyToken;
        }

        public void setVerifyToken(String verifyToken) {
            this.verifyToken = verifyToken;
        }

        public String getAppSecret() {
            return appSecret;
        }

        public void setAppSecret(String appSecret) {
            this.appSecret = appSecret;
        }

        public String getPageAccessToken() {
            return pageAccessToken;
        }

        public void setPageAccessToken(String pageAccessToken) {
            this.pageAccessToken = pageAccessToken;
        }

        public String getGraphVersion() {
            return graphVersion;
        }

        public void setGraphVersion(String graphVersion) {
            this.graphVersion = graphVersion;
        }

        public String getSourceToken() {
            return sourceToken;
        }

        public void setSourceToken(String sourceToken) {
            this.sourceToken = sourceToken;
        }
    }

    /**
     * Outbound delivery (messaging). Defaults to the SIMULATED provider — no real channel is connected
     * and nothing is actually sent until a real provider is wired and verified.
     */
    public static class Delivery {
        private String provider = "simulated";

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }
    }

    /**
     * Inventory freshness policy. The window is configurable and agreed with the brokerage — there is
     * no assumed universal update frequency.
     */
    public static class Inventory {
        /** A verified property older than this many days is treated as stale. */
        private int freshnessDays = 30;

        public int getFreshnessDays() {
            return freshnessDays;
        }

        public void setFreshnessDays(int freshnessDays) {
            this.freshnessDays = freshnessDays;
        }
    }

    /** One-time owner bootstrap. Creates the single owner account if the workspace has no users. */
    public static class Owner {
        /** When true, bootstrap an owner on startup if none exists. */
        private boolean bootstrap = false;
        private String email;
        /** Plaintext password used ONLY to create the initial hash, then discarded. Never persisted. */
        private String password;
        private String workspaceName = "NEST";
        private String workspaceTimezone = "Asia/Kolkata";

        public boolean isBootstrap() {
            return bootstrap;
        }

        public void setBootstrap(boolean bootstrap) {
            this.bootstrap = bootstrap;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getWorkspaceName() {
            return workspaceName;
        }

        public void setWorkspaceName(String workspaceName) {
            this.workspaceName = workspaceName;
        }

        public String getWorkspaceTimezone() {
            return workspaceTimezone;
        }

        public void setWorkspaceTimezone(String workspaceTimezone) {
            this.workspaceTimezone = workspaceTimezone;
        }
    }

    /**
     * AI coach settings (Milestone 2). Present so configuration is explicit and wired; the coach is
     * disabled by default and uses a clearly-labelled mock provider until a real provider is verified.
     */
    public static class Ai {
        private boolean enabled = false;
        private String provider = "mock";
        private String model = "mock-model";
        private String apiKey;
        private int maxInputTokens = 8000;
        private int maxOutputTokens = 1500;
        private double dailyBudgetUsd = 0.0;
        /** Max coaching requests per workspace per minute. */
        private int maxPerMinute = 30;
        /** Max recent interactions included in the model context. */
        private int maxContextInteractions = 50;
        /** Provider call timeout in milliseconds. */
        private long timeoutMs = 20000;
        /** Model pricing for cost estimation (USD per 1M tokens). Defaults suit gpt-4o-mini. */
        private double inputCostPerMillion = 0.15;
        private double outputCostPerMillion = 0.60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getProvider() {
            return provider;
        }

        public void setProvider(String provider) {
            this.provider = provider;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public int getMaxInputTokens() {
            return maxInputTokens;
        }

        public void setMaxInputTokens(int maxInputTokens) {
            this.maxInputTokens = maxInputTokens;
        }

        public int getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(int maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public double getDailyBudgetUsd() {
            return dailyBudgetUsd;
        }

        public void setDailyBudgetUsd(double dailyBudgetUsd) {
            this.dailyBudgetUsd = dailyBudgetUsd;
        }

        public int getMaxPerMinute() {
            return maxPerMinute;
        }

        public void setMaxPerMinute(int maxPerMinute) {
            this.maxPerMinute = maxPerMinute;
        }

        public int getMaxContextInteractions() {
            return maxContextInteractions;
        }

        public void setMaxContextInteractions(int maxContextInteractions) {
            this.maxContextInteractions = maxContextInteractions;
        }

        public long getTimeoutMs() {
            return timeoutMs;
        }

        public void setTimeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
        }

        public double getInputCostPerMillion() {
            return inputCostPerMillion;
        }

        public void setInputCostPerMillion(double inputCostPerMillion) {
            this.inputCostPerMillion = inputCostPerMillion;
        }

        public double getOutputCostPerMillion() {
            return outputCostPerMillion;
        }

        public void setOutputCostPerMillion(double outputCostPerMillion) {
            this.outputCostPerMillion = outputCostPerMillion;
        }
    }
}
