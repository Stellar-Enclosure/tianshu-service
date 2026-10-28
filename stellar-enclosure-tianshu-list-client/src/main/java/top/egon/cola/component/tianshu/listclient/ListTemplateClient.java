package top.egon.cola.component.tianshu.listclient;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.egon.cola.component.tianshu.api.list.ListDefinitionBinding;
import top.egon.cola.component.tianshu.api.list.ListDelegationRequest;
import top.egon.cola.component.tianshu.api.list.ListDelegationResponse;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.UserPermissionScopePolicy;
import top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** A business host checks its own API boundary before delegating with both credentials. */
public final class ListTemplateClient {
    private static final int MAX_RESPONSE_BYTES = 2 * 1024 * 1024;
    private final URI endpoint;
    private final ServiceTokenSupplier serviceTokens;
    private final PermissionExecutionGuard authorization;
    private final PermissionFieldBindingGuard fields;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final Transport transport;

    public ListTemplateClient(URI endpoint, ServiceTokenSupplier serviceTokens, PermissionExecutionGuard authorization,
            PermissionFieldBindingGuard fields, ObjectMapper mapper, Duration timeout) {
        this(endpoint, serviceTokens, authorization, fields, mapper, timeout, new JdkTransport(timeout));
    }

    ListTemplateClient(URI endpoint, ServiceTokenSupplier serviceTokens, PermissionExecutionGuard authorization,
            PermissionFieldBindingGuard fields, ObjectMapper mapper, Duration timeout, Transport transport) {
        this.endpoint = endpoint(endpoint);
        this.serviceTokens = Objects.requireNonNull(serviceTokens, "serviceTokens");
        this.authorization = Objects.requireNonNull(authorization, "authorization");
        this.fields = Objects.requireNonNull(fields, "fields");
        this.mapper = Objects.requireNonNull(mapper, "mapper").copy().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        if (timeout == null || timeout.compareTo(Duration.ofMillis(100)) < 0 || timeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("List template timeout is invalid");
        }
        this.timeout = timeout;
        this.transport = Objects.requireNonNull(transport, "transport");
    }

    public ListDelegationResponse execute(ListDefinitionBinding binding, ListDelegationRequest.Operation operation,
            String templateId, String expectedVersion, String name, List<String> columns, Boolean defaultTemplate,
            boolean includeSystemMetadata) throws IOException, InterruptedException {
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(operation, "operation");
        String apiKey = switch (operation) {
            case LOAD, SELECT -> binding.pageApiKey();
            case SAVE_SYSTEM, DELETE_SYSTEM -> binding.systemTemplateApiKey();
            case SAVE_PERSONAL, DELETE_PERSONAL -> binding.personalTemplateApiKey();
        };
        var user = authorization.require(apiKey);
        var view = Objects.requireNonNull(user.snapshot().permissionSnapshot(), "current permission view");
        binding.definition().columns().stream().flatMap(column -> column.fieldKeys().stream()).distinct().forEach(key -> {
            var field = authorization.resource(key, ResourceType.FIELD);
            if (field == null || field.type() != ResourceType.FIELD) throw new IllegalStateException("LIST_DELEGATION_FIELD_NOT_OWNED");
        });
        UserPermissionScopePolicy policy = authorization.userScopePolicy();
        if (policy == null || !policy.enabled()) throw new IllegalStateException("LIST_DELEGATION_USER_SCOPE_POLICY_REQUIRED");
        if (!policyDigest(policy).equals(policyDigest(fields.userScopePolicy()))) throw new IllegalStateException("LIST_DELEGATION_SCOPE_POLICY_MISMATCH");
        if (includeSystemMetadata) authorization.require(binding.systemTemplateApiKey());
        String userToken = VerifiedUserTokenCarrier.current();
        String serviceToken = serviceTokens.forTenant(view.catalogScope().tenantId());
        if (serviceToken == null || serviceToken.isBlank() || serviceToken.length() > 16_384
                || serviceToken.chars().anyMatch(value -> Character.isISOControl(value) || Character.isWhitespace(value))) throw new IllegalStateException("LIST_DELEGATION_SERVICE_TOKEN_INVALID");
        Map<String, Set<String>> mappings = policy.permissionScopes();
        String policyDigest = ListDefinitionBinding.scopePolicyDigest(true, mappings);
        var request = new ListDelegationRequest(binding, operation, view.catalogVersion(), view.authVersion(), view.policyVersion(),
                view.activeRoleContext().rootRoleIds(), mappings, templateId, expectedVersion, name, columns, defaultTemplate, includeSystemMetadata);
        byte[] body = mapper.writeValueAsBytes(request);
        if (body.length > 1024 * 1024) throw new IllegalArgumentException("LIST_DELEGATION_REQUEST_TOO_LARGE");
        Response response = transport.post(endpoint, serviceToken, userToken, body, timeout);
        if (response.status() == 401 || response.status() == 403 || response.status() == 404) throw new SecurityException("LIST_DELEGATION_DENIED");
        if (response.status() == 409) throw new ConflictException();
        if (response.status() == 400 || response.status() == 413 || response.status() == 422) throw new IllegalArgumentException("LIST_DELEGATION_REQUEST_INVALID");
        if (response.status() != 200 || response.body().length > MAX_RESPONSE_BYTES) throw new IOException("LIST_DELEGATION_UNAVAILABLE");
        var root = mapper.readTree(response.body());
        if (root == null || !root.path("success").asBoolean(false) || !root.hasNonNull("data")) throw new IOException("LIST_DELEGATION_RESPONSE_INVALID");
        var result = mapper.treeToValue(root.get("data"), ListDelegationResponse.class);
        var current = authorization.require(apiKey).snapshot().permissionSnapshot();
        if (!same(view, current) || !policyDigest.equals(policyDigest(authorization.userScopePolicy()))
                || !policyDigest.equals(policyDigest(fields.userScopePolicy()))
                || !binding.definition().digest().equals(result.definitionDigest())
                || !view.catalogVersion().equals(result.catalogVersion())) throw new SecurityException("LIST_DELEGATION_VIEW_CHANGED");
        return result;
    }

    private static boolean same(EffectivePermissionSnapshot left, EffectivePermissionSnapshot right) {
        return right != null && left.issuer().equals(right.issuer()) && left.subject().equals(right.subject())
                && left.catalogScope().equals(right.catalogScope()) && left.catalogVersion().equals(right.catalogVersion())
                && left.authVersion().equals(right.authVersion()) && left.policyVersion().equals(right.policyVersion())
                && left.activeRoleContext().equals(right.activeRoleContext()) && left.permissionCodes().equals(right.permissionCodes());
    }
    private static String policyDigest(UserPermissionScopePolicy policy) {
        if (policy == null) throw new IllegalStateException("LIST_DELEGATION_USER_SCOPE_POLICY_REQUIRED");
        return ListDefinitionBinding.scopePolicyDigest(policy.enabled(), policy.permissionScopes());
    }
    private static URI endpoint(URI value) {
        Objects.requireNonNull(value, "endpoint");
        boolean loopback = "localhost".equalsIgnoreCase(value.getHost()) || "127.0.0.1".equals(value.getHost()) || "::1".equals(value.getHost());
        if (value.getHost() == null || value.getPort() > 65535
                || !"https".equalsIgnoreCase(value.getScheme()) && !("http".equalsIgnoreCase(value.getScheme()) && loopback)
                || value.getUserInfo() != null || value.getQuery() != null || value.getFragment() != null
                || !"/internal/v1/tianshu/list-delegation".equals(value.getPath())) throw new IllegalArgumentException("Invalid list delegation endpoint");
        return value;
    }

    @FunctionalInterface public interface ServiceTokenSupplier { String forTenant(String tenantId); }
    public static final class ConflictException extends RuntimeException {
        public ConflictException() { super("LIST_DELEGATION_CONFLICT"); }
    }
    @FunctionalInterface interface Transport { Response post(URI endpoint, String serviceToken, String userToken, byte[] body, Duration timeout) throws IOException, InterruptedException; }
    record Response(int status, byte[] body) { }
    private static final class JdkTransport implements Transport {
        private final HttpClient client;
        JdkTransport(Duration timeout) { client = HttpClient.newBuilder().connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build(); }
        @Override public Response post(URI endpoint, String serviceToken, String userToken, byte[] body, Duration timeout) throws IOException, InterruptedException {
            var request = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Authorization", "Bearer " + serviceToken)
                    .header("X-Egon-Subject-Token", userToken).header("Content-Type", "application/json").header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var input = response.body()) { return new Response(response.statusCode(), input.readNBytes(MAX_RESPONSE_BYTES + 1)); }
        }
    }
}
