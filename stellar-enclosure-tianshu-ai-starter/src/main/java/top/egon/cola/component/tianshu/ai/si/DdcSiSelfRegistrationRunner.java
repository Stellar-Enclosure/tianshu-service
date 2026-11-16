package top.egon.cola.component.tianshu.ai.si;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import top.egon.cola.component.tianshu.api.client.DdcSiCatalogClient;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** Collects local resource contributors on startup and registers them idempotently with exponential backoff. */
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcSiSelfRegistrationRunner implements ApplicationRunner {

    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private final DdcSiCatalogClient catalogClient;
    private final DdcSiClientProperties properties;
    private final List<DdcSiLocalResourceContributor> contributors;

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || contributors == null || contributors.isEmpty()) {
            return;
        }
        for (DdcSiLocalResourceContributor contributor : contributors) {
            List<DdcSiSelfRegistrationRequest> items = contributor.contributeResources();
            if (items == null || items.isEmpty()) {
                continue;
            }
            for (DdcSiSelfRegistrationRequest item : items) {
                registerWithRetry(normalizeHash(item));
            }
        }
    }

    private void registerWithRetry(DdcSiSelfRegistrationRequest request) {
        int maxAttempts = Math.max(1, properties.getSelfRegistrationMaxAttempts());
        long backoffMs = Math.max(1L, properties.getSelfRegistrationBackoff().toMillis());
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                catalogClient.registerManagedResource(request);
                return;
            } catch (IllegalArgumentException invalid) {
                handlePermanentFailure(request, invalid);
                return;
            } catch (StatusRuntimeException rpcFailure) {
                if (rpcFailure.getStatus().getCode() == Status.Code.INVALID_ARGUMENT
                        || rpcFailure.getStatus().getCode() == Status.Code.FAILED_PRECONDITION) {
                    handlePermanentFailure(request, rpcFailure);
                    return;
                }
                lastFailure = rpcFailure;
            } catch (RuntimeException transientFailure) {
                lastFailure = transientFailure;
            }
            if (attempt < maxAttempts) {
                sleepBackoff(backoffMs * (1L << (attempt - 1)));
            }
        }
        String message = "Failed SI self-registration after " + maxAttempts + " attempts key="
                + request.getResourceKey();
        if (properties.isFailFastOnStartup()) {
            throw new IllegalStateException(message, lastFailure);
        }
        log.warn("{}: {}", message, lastFailure != null ? lastFailure.getMessage() : "unknown");
    }

    private void handlePermanentFailure(DdcSiSelfRegistrationRequest request, RuntimeException failure) {
        String message = "Rejected SI self-registration due to invalid contract key=" + request.getResourceKey();
        if (properties.isFailFastOnStartup()) {
            throw failure;
        }
        log.warn("{}: {}", message, failure.getMessage());
    }

    private void sleepBackoff(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private DdcSiSelfRegistrationRequest normalizeHash(DdcSiSelfRegistrationRequest source) {
        if (source.getSpecHash() != null && source.getSpecHash().matches("^[0-9a-f]{64}$")) {
            return source;
        }
        String canonical = canonicalJson(source.getSpecJson());
        String sha256 = sha256Hex(canonical);
        return DdcSiSelfRegistrationRequest.builder()
                .tenantId(source.getTenantId())
                .bizCode(source.getBizCode())
                .namespaceCode(source.getNamespaceCode())
                .envCode(source.getEnvCode())
                .serviceName(source.getServiceName())
                .instanceId(source.getInstanceId())
                .kind(source.getKind())
                .resourceKey(source.getResourceKey())
                .displayName(source.getDisplayName())
                .description(source.getDescription())
                .specJson(canonical)
                .specHash(sha256)
                .labels(source.getLabels())
                .references(source.getReferences())
                .autoPublishIfNew(source.isAutoPublishIfNew())
                .build();
    }

    private String canonicalJson(String raw) {
        String input = raw == null || raw.isBlank() ? "{}" : raw;
        try {
            JsonNode tree = CANONICAL_MAPPER.readTree(input);
            Object obj = CANONICAL_MAPPER.treeToValue(tree, Object.class);
            return CANONICAL_MAPPER.writeValueAsString(obj);
        } catch (Exception ignored) {
            return input;
        }
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
