package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import jakarta.annotation.PostConstruct;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Deployment-pinned inventory shared by the observation writer and the Admin readers. */
@Slf4j
@Component("ddcServiceObservationBindings")
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcServiceObservationBindings {
    public static final String PREFIX = "egon.cola.component.tianshu.admin.service-observation";
    private static final int MAX_FILE_BYTES = 1024 * 1024;
    private static final int MAX_BINDINGS = 256;

    @NonNull @Qualifier("environment") private final Environment environment;
    @NonNull @Qualifier("jacksonObjectMapper") private final ObjectMapper json;
    @NonNull @Qualifier("ddcTenantRegistry") private final DdcTenantRegistry tenants;
    private List<DdcServiceObservationBindingVO> bindings = List.of();

    @PostConstruct
    void initialize() {
        String file = environment.getProperty(PREFIX + ".bindings-file", "");
        String sha256 = environment.getProperty(PREFIX + ".bindings-sha256", "");
        bindings = file.isEmpty() && sha256.isEmpty() ? List.of() : load(file, sha256, json, tenants);
        log.info("Loaded Tianshu Service observation inventory. bindings={}", bindings.size());
    }

    public List<DdcServiceObservationBindingVO> bindings() {
        return bindings;
    }

    private static List<DdcServiceObservationBindingVO> load(String file, String expectedSha256,
            ObjectMapper json, DdcTenantRegistry tenants) {
        Path path;
        try {
            path = Path.of(file);
        } catch (InvalidPathException failure) {
            throw invalid("TIANSHU_SERVICE_OBSERVATION_BINDINGS_PIN_REQUIRED");
        }
        if (!path.isAbsolute() || !expectedSha256.matches("[a-f0-9]{64}")) {
            throw invalid("TIANSHU_SERVICE_OBSERVATION_BINDINGS_PIN_REQUIRED");
        }

        byte[] bytes;
        try (var input = Files.newInputStream(path)) {
            bytes = input.readNBytes(MAX_FILE_BYTES + 1);
        } catch (IOException failure) {
            throw invalid("TIANSHU_SERVICE_OBSERVATION_BINDINGS_UNAVAILABLE");
        }
        if (bytes.length > MAX_FILE_BYTES
                || !MessageDigest.isEqual(HexFormat.of().parseHex(expectedSha256), sha256(bytes))) {
            throw invalid("TIANSHU_SERVICE_OBSERVATION_BINDINGS_HASH_MISMATCH");
        }

        BindingsFileVO document;
        try {
            document = strictReader(json).readValue(bytes, BindingsFileVO.class);
        } catch (IOException failure) {
            throw invalid("TIANSHU_SERVICE_OBSERVATION_BINDINGS_INVALID");
        }
        if (document == null || !"1".equals(document.schemaVersion()) || document.bindings() == null
                || document.bindings().size() > MAX_BINDINGS) {
            throw invalid("TIANSHU_SERVICE_OBSERVATION_BINDINGS_INVALID");
        }

        Set<String> bindingIds = new HashSet<>();
        Set<ServicePortVO> servicePorts = new HashSet<>();
        Map<ServiceVO, ServiceOwnerVO> serviceOwners = new HashMap<>();
        for (DdcServiceObservationBindingVO binding : document.bindings()) {
            if (binding == null) {
                throw invalid("TIANSHU_SERVICE_OBSERVATION_BINDINGS_INVALID");
            }
            var tenant = tenants.requireEnabledTenant(binding.tenantId());
            if (!binding.identityTenantId().equals(tenant.getIdentityTenantId())) {
                throw invalid("TIANSHU_SERVICE_OBSERVATION_TENANT_UNVERIFIED");
            }
            var service = new ServiceVO(binding.clusterId(), binding.kubernetesNamespace(), binding.serviceName());
            var owner = new ServiceOwnerVO(binding.tenantId(), binding.serviceUid());
            var existingOwner = serviceOwners.putIfAbsent(service, owner);
            if (!bindingIds.add(binding.bindingId()) || !servicePorts.add(new ServicePortVO(service, binding.portName()))
                    || (existingOwner != null && !existingOwner.equals(owner))) {
                throw invalid("TIANSHU_SERVICE_OBSERVATION_BINDINGS_AMBIGUOUS");
            }
        }
        return List.copyOf(document.bindings());
    }

    private static ObjectMapper strictReader(ObjectMapper json) {
        var strict = json.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS, DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);
        strict.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
        strict.disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT, DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        strict.coercionConfigFor(LogicalType.Textual)
                .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);
        strict.getFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
        strict.getFactory().setStreamReadConstraints(StreamReadConstraints.builder()
                .maxNestingDepth(16).maxStringLength(16_384).maxNumberLength(32).build());
        return strict;
    }

    private static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException failure) {
            throw new IllegalStateException("Required SHA-256 support is missing", failure);
        }
    }

    private static IllegalStateException invalid(String code) {
        return new IllegalStateException(code);
    }

    private record BindingsFileVO(String schemaVersion, List<DdcServiceObservationBindingVO> bindings) { }

    private record ServiceVO(String clusterId, String namespace, String name) { }

    private record ServicePortVO(ServiceVO service, String portName) { }

    private record ServiceOwnerVO(long tenantId, String serviceUid) { }
}
