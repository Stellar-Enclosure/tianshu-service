package top.egon.cola.component.tianshu.admin.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import top.egon.cola.component.tianshu.admin.model.vo.DdcServiceObservationBindingVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static top.egon.cola.component.tianshu.admin.config.DdcServiceObservationBindings.PREFIX;

class DdcServiceObservationBindingsTest {
    private final ObjectMapper json = new ObjectMapper();
    private final DdcTenantRegistry tenants = mock(DdcTenantRegistry.class);
    private final MockEnvironment environment = new MockEnvironment();
    @TempDir private Path directory;

    @BeforeEach
    void tenantRegistry() {
        when(tenants.requireEnabledTenant(7011L)).thenReturn(new DdcTenantBindingDTO(7011L, "identity-7011", true));
        when(tenants.requireEnabledTenant(7022L)).thenReturn(new DdcTenantBindingDTO(7022L, "identity-7022", true));
    }

    @Test
    void loadsAnImmutableInventoryAndAllowsDifferentPortsForTheSameOwnerAndUid() throws Exception {
        var http = binding(7011L, "identity-7011", "north-http", "service-uid", "http");
        var grpc = binding(7011L, "identity-7011", "north-grpc", "service-uid", "grpc");
        pin(document(http, grpc));

        var bindings = load().bindings();

        assertThat(bindings).containsExactly(http, grpc);
        assertThatThrownBy(bindings::clear).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void anUnsetInventoryDoesNotAuthorizeServiceObservation() throws Exception {
        assertThat(load().bindings()).isEmpty();
        pin(document());
        assertThat(load().bindings()).isEmpty();
        verifyNoInteractions(tenants);
    }

    @Test
    void refusesIncompletePinAndChangedFile() throws Exception {
        Path pinned = pin(document(binding(7011L, "identity-7011", "north-http", "service-uid", "http")));
        Files.writeString(pinned, document());
        assertThatThrownBy(this::load).hasMessage("TIANSHU_SERVICE_OBSERVATION_BINDINGS_HASH_MISMATCH");
        environment.setProperty(PREFIX + ".bindings-sha256", "");
        assertThatThrownBy(this::load).hasMessage("TIANSHU_SERVICE_OBSERVATION_BINDINGS_PIN_REQUIRED");
    }

    @Test
    void refusesUnknownOrCoercedPropertiesAndDuplicateJsonKeys() throws Exception {
        String valid = document(binding(7011L, "identity-7011", "north-http", "service-uid", "http"));
        pin(valid.replace("\"tenantId\":7011", "\"tenantId\":\"7011\""));
        assertThatThrownBy(this::load).hasMessage("TIANSHU_SERVICE_OBSERVATION_BINDINGS_INVALID");
        pin(valid.replace("\"schemaVersion\":\"1\"", "\"schemaVersion\":1"));
        assertThatThrownBy(this::load).hasMessage("TIANSHU_SERVICE_OBSERVATION_BINDINGS_INVALID");
        pin(valid.replace("\"tenantId\":7011", "\"tenantId\":7011,\"tenantId\":7022"));
        assertThatThrownBy(this::load).hasMessage("TIANSHU_SERVICE_OBSERVATION_BINDINGS_INVALID");
        pin(valid.replace("\"tenantId\":7011", "\"tenantId\":7011,\"extra\":true"));
        assertThatThrownBy(this::load).hasMessage("TIANSHU_SERVICE_OBSERVATION_BINDINGS_INVALID");
    }

    @Test
    void refusesUnverifiedTenantIdentity() throws Exception {
        pin(document(binding(7011L, "different-identity", "north-http", "service-uid", "http")));
        assertThatThrownBy(this::load).hasMessage("TIANSHU_SERVICE_OBSERVATION_TENANT_UNVERIFIED");
    }

    @Test
    void refusesDuplicateBindingsAndConflictingTenantOrUidForTheSameService() throws Exception {
        var http = binding(7011L, "identity-7011", "north-http", "service-uid", "http");
        var duplicateId = binding(7011L, "identity-7011", "north-http", "service-uid", "grpc");
        var duplicatePort = binding(7011L, "identity-7011", "another-http", "service-uid", "http");
        var anotherUid = binding(7011L, "identity-7011", "north-grpc", "recreated-uid", "grpc");
        var anotherOwner = binding(7022L, "identity-7022", "north-grpc", "service-uid", "grpc");
        for (DdcServiceObservationBindingVO conflict : List.of(duplicateId, duplicatePort, anotherUid, anotherOwner)) {
            pin(document(http, conflict));
            assertThatThrownBy(this::load).hasMessage("TIANSHU_SERVICE_OBSERVATION_BINDINGS_AMBIGUOUS");
        }
    }

    private DdcServiceObservationBindings load() {
        var inventory = new DdcServiceObservationBindings(environment, json, tenants);
        inventory.initialize();
        return inventory;
    }

    private String document(DdcServiceObservationBindingVO... bindings) throws Exception {
        return json.writeValueAsString(Map.of("schemaVersion", "1", "bindings", List.of(bindings)));
    }

    private Path pin(String document) throws Exception {
        byte[] bytes = document.getBytes(StandardCharsets.UTF_8);
        Path file = Files.write(directory.resolve("inventory.json"), bytes);
        environment.setProperty(PREFIX + ".bindings-file", file.toString());
        environment.setProperty(PREFIX + ".bindings-sha256", HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(bytes)));
        return file;
    }

    private static DdcServiceObservationBindingVO binding(long tenant, String identity, String bindingId,
            String uid, String port) {
        return new DdcServiceObservationBindingVO(tenant, identity, "example", "catalog", "staging", "demo",
                bindingId, 2, "cluster-1", "platform-prod", "tianshu-demo", uid, port,
                "http".equals(port) ? 8080 : 9090, port, "DemoService", "", "v1", Set.of("workload-uid"));
    }
}
