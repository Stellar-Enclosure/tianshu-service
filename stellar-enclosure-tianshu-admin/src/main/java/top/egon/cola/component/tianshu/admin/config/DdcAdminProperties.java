package top.egon.cola.component.tianshu.admin.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "egon.cola.component.tianshu.admin")
public class DdcAdminProperties {

    private long maxConfigBytes = 1024L * 1024L;

    private Redis redis = new Redis();

    private Lease lease = new Lease();

    private Security security = new Security();

    private Publish publish = new Publish();

    /** Tianshu registration credential verification settings. */
    private Registration registration = new Registration();

    public long getMaxConfigBytes() {
        return maxConfigBytes;
    }

    public void setMaxConfigBytes(long maxConfigBytes) {
        this.maxConfigBytes = maxConfigBytes;
    }

    public Redis getRedis() {
        return redis;
    }

    public void setRedis(Redis redis) {
        this.redis = redis;
    }

    public Lease getLease() {
        return lease;
    }

    public void setLease(Lease lease) {
        this.lease = lease;
    }

    public Security getSecurity() {
        return security;
    }

    public void setSecurity(Security security) {
        this.security = security;
    }

    public Publish getPublish() {
        return publish;
    }

    public void setPublish(Publish publish) {
        this.publish = publish;
    }

    /**
     * 返回 Tianshu registration credential verification settings。
     *
     * <p>Returns Tianshu registration credential verification settings.</p>
     *
     * @return registration 配置；registration settings
     */
    public Registration getRegistration() {
        return registration;
    }

    /**
     * 设置 Tianshu registration credential verification settings。
     *
     * <p>Sets Tianshu registration credential verification settings.</p>
     *
     * @param registration registration 配置；registration settings
     */
    public void setRegistration(Registration registration) {
        this.registration = registration;
    }

    public static class Redis {

        private boolean enabled = true;

        private String mode = "SINGLE";

        private List<String> nodes = new ArrayList<>();

        private String masterName;

        private String host = "127.0.0.1";

        private int port = 6379;

        private String password;

        private int database = 0;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getMode() {
            return mode;
        }

        public void setMode(String mode) {
            this.mode = mode;
        }

        public List<String> getNodes() {
            return nodes;
        }

        public void setNodes(List<String> nodes) {
            this.nodes = nodes;
        }

        public String getMasterName() {
            return masterName;
        }

        public void setMasterName(String masterName) {
            this.masterName = masterName;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public int getDatabase() {
            return database;
        }

        public void setDatabase(int database) {
            this.database = database;
        }
    }

    public static class Lease {

        private int minimumSeconds = 5;

        private int maximumSeconds = 300;

        private long scanIntervalMillis = 5000;

        public int getMinimumSeconds() {
            return minimumSeconds;
        }

        public void setMinimumSeconds(int minimumSeconds) {
            this.minimumSeconds = minimumSeconds;
        }

        public int getMaximumSeconds() {
            return maximumSeconds;
        }

        public void setMaximumSeconds(int maximumSeconds) {
            this.maximumSeconds = maximumSeconds;
        }

        public long getScanIntervalMillis() {
            return scanIntervalMillis;
        }

        public void setScanIntervalMillis(long scanIntervalMillis) {
            this.scanIntervalMillis = scanIntervalMillis;
        }
    }

    public static class Security {

        private boolean localDev;

        private Jwt jwt = new Jwt();

        public boolean isLocalDev() {
            return localDev;
        }

        public void setLocalDev(boolean localDev) {
            this.localDev = localDev;
        }

        public Jwt getJwt() {
            return jwt;
        }

        public void setJwt(Jwt jwt) {
            this.jwt = jwt;
        }
    }

    public static class Jwt {

        private String issuer;

        private String audience;

        private String jwkSetUri;

        private String hmacSecretBase64;

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public String getAudience() {
            return audience;
        }

        public void setAudience(String audience) {
            this.audience = audience;
        }

        public String getJwkSetUri() {
            return jwkSetUri;
        }

        public void setJwkSetUri(String jwkSetUri) {
            this.jwkSetUri = jwkSetUri;
        }

        public String getHmacSecretBase64() {
            return hmacSecretBase64;
        }

        public void setHmacSecretBase64(String hmacSecretBase64) {
            this.hmacSecretBase64 = hmacSecretBase64;
        }
    }

    /**
     * Tianquan-Shoubing PLATFORM SERVICE registration verification settings。
     *
     * <p>Tianquan-Shoubing PLATFORM SERVICE registration verification settings.</p>
     */
    public static class Registration {

        /** Tianshu Resource Server stable identifier. */
        private String resourceServerId;

        /** Tianshu Resource URI used as the token audience. */
        private URI resourceUri;

        /** Least-privilege registration scope. */
        private String requiredScope = "tianshu:registration:write";

        /**
         * 返回 Tianshu Resource Server 稳定标识。
         *
         * <p>Returns the Tianshu Resource Server stable identifier.</p>
         *
         * @return Resource Server stable identifier
         */
        public String getResourceServerId() {
            return resourceServerId;
        }

        /**
         * 设置 Tianshu Resource Server 稳定标识。
         *
         * <p>Sets the Tianshu Resource Server stable identifier.</p>
         *
         * @param resourceServerId Resource Server stable identifier
         */
        public void setResourceServerId(String resourceServerId) {
            this.resourceServerId = resourceServerId;
        }

        /**
         * 返回 Tianshu Resource URI。
         *
         * <p>Returns the Tianshu Resource URI.</p>
         *
         * @return Resource URI
         */
        public URI getResourceUri() {
            return resourceUri;
        }

        /**
         * 设置 Tianshu Resource URI。
         *
         * <p>Sets the Tianshu Resource URI.</p>
         *
         * @param resourceUri Resource URI
         */
        public void setResourceUri(URI resourceUri) {
            this.resourceUri = resourceUri;
        }

        /**
         * 返回 Tianshu registration scope。
         *
         * <p>Returns the least-privilege Tianshu registration scope.</p>
         *
         * @return registration scope
         */
        public String getRequiredScope() {
            return requiredScope;
        }

        /**
         * 设置 Tianshu registration scope。
         *
         * <p>Sets the least-privilege Tianshu registration scope.</p>
         *
         * @param requiredScope registration scope
         */
        public void setRequiredScope(String requiredScope) {
            this.requiredScope = requiredScope;
        }
    }

    public static class Publish {

        /**
         * Redis notification content threshold; larger resources are pulled through Tianshu RPC.
         */
        private long inlineContentMaxBytes = 4L * 1024L * 1024L;

        private long dispatchTimeoutMs = 5000;

        private long defaultTimeoutMs = 30000;

        private long maxTimeoutMs = 60000;

        private long scanIntervalMs = 1000;

        private long completionPollIntervalMs = 100;

        private long recoveryStaleMs = 120000;

        public long getInlineContentMaxBytes() {
            return inlineContentMaxBytes;
        }

        public void setInlineContentMaxBytes(long inlineContentMaxBytes) {
            this.inlineContentMaxBytes = inlineContentMaxBytes;
        }

        public long getDispatchTimeoutMs() {
            return dispatchTimeoutMs;
        }

        public void setDispatchTimeoutMs(long dispatchTimeoutMs) {
            this.dispatchTimeoutMs = dispatchTimeoutMs;
        }

        public long getDefaultTimeoutMs() {
            return defaultTimeoutMs;
        }

        public void setDefaultTimeoutMs(long defaultTimeoutMs) {
            this.defaultTimeoutMs = defaultTimeoutMs;
        }

        public long getMaxTimeoutMs() {
            return maxTimeoutMs;
        }

        public void setMaxTimeoutMs(long maxTimeoutMs) {
            this.maxTimeoutMs = maxTimeoutMs;
        }

        public long getScanIntervalMs() {
            return scanIntervalMs;
        }

        public void setScanIntervalMs(long scanIntervalMs) {
            this.scanIntervalMs = scanIntervalMs;
        }

        public long getCompletionPollIntervalMs() {
            return completionPollIntervalMs;
        }

        public void setCompletionPollIntervalMs(
                long completionPollIntervalMs) {
            this.completionPollIntervalMs = completionPollIntervalMs;
        }

        public long getRecoveryStaleMs() {
            return recoveryStaleMs;
        }

        public void setRecoveryStaleMs(long recoveryStaleMs) {
            this.recoveryStaleMs = recoveryStaleMs;
        }
    }
}
