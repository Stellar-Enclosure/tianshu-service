package top.egon.cola.component.tianshu.admin.permission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import top.egon.cola.component.tianshu.admin.internal.controller.DdcConfigDeliveryController;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublicI18nBundleResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcInstanceWorkloadVO;
import top.egon.cola.component.tianshu.admin.security.config.DdcConfigWorkloadVerifier;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigScopeVO;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nCatalog;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nValidation;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifest;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.PermissionManifestReader;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceAccess;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.ResourceType;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/** Compares javac artifacts with real Spring mappings and Jackson's reachable DTO properties. */
@EnabledIfSystemProperty(named = "permission.producer.enabled", matches = "true")
class PermissionProducerCoverageTest {
    private static final String ROOT = "top.egon.cola.component.tianshu.";
    private static final Set<String> CONFIG_PROTOCOL_API_KEYS = Set.of(
            "tianshu.api.configDelivery.snapshot", "tianshu.api.configDelivery.receipt");
    private static final Set<String> EXTERNAL_CONTRACTS = Set.of(
            "top.egon.cola.component.common.core.pojo.PageQuery",
            "top.egon.cola.component.common.core.pojo.PageResultRecord",
            "top.egon.cola.component.common.core.pojo.ResultRecord",
            "top.egon.cola.platform.tianquan.jianshen.contract.auth.AuthorizationBootstrapView",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.FieldCatalogSnapshot",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportReceipt",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportRequest",
            "top.egon.cola.component.tianshu.api.list.ListDelegationRequest",
            "top.egon.cola.component.tianshu.api.list.ListDelegationResponse");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

    @Test
    void workloadAndArchiveHttpMappingsHaveReadOnlyPermissionsAndAClosedProjection() throws Exception {
        Map<String, PermissionManifest.ResourceDefinition> resources = new TreeMap<>();
        for (var fragment : fragments()) {
            for (var resource : fragment.resources()) resources.put(resource.key(), resource);
        }
        Map<String, String> instances = Map.of(
                "list", "/api/v1/tianshu/instances",
                "page", "/api/v1/tianshu/instances/page",
                "historyList", "/api/v1/tianshu/instances/history",
                "historyPage", "/api/v1/tianshu/instances/history/page");
        for (var endpoint : instances.entrySet()) {
            var resource = resources.get("tianshu.api.instance." + endpoint.getKey());
            assertThat(resource.type()).isEqualTo(ResourceType.API);
            assertThat(resource.target()).containsEntry("paths", List.of(endpoint.getValue()))
                    .containsEntry("methods", List.of("GET", "HEAD"));
        }
        Set<String> fields = new TreeSet<>();
        Set<String> external = new TreeSet<>();
        walk(DdcInstanceWorkloadVO.class, true, resources, fields, external, new HashSet<>());
        assertThat(external).isEmpty();
        assertThat(resources.keySet().stream().filter(key -> key.startsWith("tianshu.field.DdcInstanceWorkloadVO.")))
                .containsExactlyInAnyOrderElementsOf(fields);
        assertThat(fields).hasSize(15);
    }

    @Test
    void everyManagedHttpMappingAndReachableDtoPropertyHasExactlyOneExportedResource() throws Exception {
        List<PermissionManifest> fragments = fragments();
        Map<String, PermissionManifest.ResourceDefinition> resources = new TreeMap<>();
        for (var fragment : fragments) for (var resource : fragment.resources()) {
            assertThat(resources.put(resource.key(), resource)).as(resource.key()).isNull();
        }
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        var environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("producer-coverage",
                Map.of("egon.cola.platform.tianquan.jianshen.enabled", "true")));
        scanner.setEnvironment(environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        var mappings = new Mappings();
        Set<String> apiKeys = new TreeSet<>();
        Set<String> fields = new TreeSet<>();
        Set<String> external = new TreeSet<>();
        for (var candidate : scanner.findCandidateComponents(ROOT + "admin.controller")) {
            Class<?> controller = Class.forName(candidate.getBeanClassName(), false, getClass().getClassLoader());
            for (Method method : controller.getDeclaredMethods()) {
                RequestMappingInfo mapping = mappings.mapping(method, controller);
                if (mapping == null) continue;
                PermissionResource declaration = method.getAnnotation(PermissionResource.class);
                assertThat(declaration).as(method.toGenericString()).isNotNull();
                assertThat(apiKeys.add(declaration.key())).isTrue();
                var exported = resources.get(declaration.key());
                assertThat(exported).as(declaration.key()).isNotNull();
                assertThat(exported.type()).isEqualTo(ResourceType.API);
                assertThat(exported.access()).isEqualTo(declaration.access());
                assertThat(strings(exported.target().get("paths"))).containsExactlyInAnyOrderElementsOf(mapping.getPatternValues());
                Set<String> methods = new TreeSet<>();
                mapping.getMethodsCondition().getMethods().forEach(value -> methods.add(value.name()));
                if (methods.contains("GET")) methods.add("HEAD");
                assertThat(strings(exported.target().get("methods"))).containsExactlyInAnyOrderElementsOf(methods);
                walk(method.getGenericReturnType(), true, resources, fields, external, new HashSet<>());
                for (Type parameter : method.getGenericParameterTypes()) {
                    walk(parameter, false, resources, fields, external, new HashSet<>());
                }
            }
        }
        // The ordinary managed API and reachable-field coverage stays exact and unchanged.
        assertThat(apiKeys).hasSize(97);
        assertThat(apiKeys.addAll(CONFIG_PROTOCOL_API_KEYS)).isTrue();
        assertThat(apiKeys).hasSize(99);
        assertThat(resources.values().stream().filter(value -> value.type() == ResourceType.API)
                .map(PermissionManifest.ResourceDefinition::key).toList()).containsExactlyInAnyOrderElementsOf(apiKeys);
        assertThat(resources.values().stream().filter(value -> value.type() == ResourceType.FIELD)
                .map(PermissionManifest.ResourceDefinition::key).toList()).containsExactlyInAnyOrderElementsOf(fields);
        assertThat(external).containsExactlyInAnyOrderElementsOf(EXTERNAL_CONTRACTS);
    }

    @Test
    void sourceLabelsAndConsoleDefaultsComeFromTheSameManifestDeclarations() throws Exception {
        List<PermissionManifest> fragments = fragments();
        Map<String, PermissionManifest.Message> messages = new TreeMap<>();
        Set<String> definitions = new TreeSet<>();
        for (var fragment : fragments) {
            fragment.permissionDefinitions().forEach(value -> assertThat(definitions.add(value.code())).isTrue());
            fragment.messages().forEach(message -> assertThat(messages.put(message.pageCode() + ":" + message.key(), message)).isNull());
        }
        assertThat(definitions).hasSize(12).doesNotContain("*");
        for (var fragment : fragments) {
            for (var resource : fragment.resources()) assertThat(messages)
                    .containsKey(resource.i18n().pageCode() + ":" + resource.i18n().labelKey());
            for (var definition : fragment.permissionDefinitions()) assertThat(messages)
                    .containsKey(definition.i18n().pageCode() + ":" + definition.i18n().labelKey());
        }
        var catalog = new DdcAdminUiI18nCatalog(new DdcI18nValidation());
        for (var page : catalog.pages()) for (var locale : page.translations().entrySet()) {
            for (var entry : locale.getValue().entrySet()) {
                var exported = messages.get(page.pageCode() + ":" + entry.getKey());
                assertThat(exported).isNotNull();
                assertThat(exported.defaultLocale()).isEqualTo(page.defaultLocale());
                assertThat(exported.defaults()).containsEntry(locale.getKey(), entry.getValue());
            }
        }
    }

    @Test
    void publicLoginProjectionCannotCarryTenantStateOrBecomeAWritableDto() throws Exception {
        var catalog = new DdcAdminUiI18nCatalog(new DdcI18nValidation());
        var output = json.valueToTree(catalog.publicBundle("en-US"));
        assertThat(output.size()).isEqualTo(5);
        assertThat(output.has("catalogScope")).isFalse();
        assertThat(output.has("catalogActivationVersion")).isFalse();
        var type = json.constructType(DdcPublicI18nBundleResult.class);
        assertThat(json.getDeserializationConfig().introspect(type).findProperties())
                .allSatisfy(property -> assertThat(property.couldDeserialize()).isFalse());
        for (Field field : DdcPublicI18nBundleResult.class.getDeclaredFields()) {
            assertThat(field.getAnnotation(PermissionResource.class).access()).isEqualTo(ResourceAccess.PUBLIC);
        }
    }

    @Test
    void dualCredentialProtocolExportsOnlyItsSupportedInternalServiceOperations() throws Exception {
        List<PermissionManifest> fragments = fragments();
        Map<String, PermissionManifest.ResourceDefinition> resources = new TreeMap<>();
        Set<String> messages = new HashSet<>();
        for (var fragment : fragments) {
            for (var resource : fragment.resources()) {
                assertThat(resources.put(resource.key(), resource)).as(resource.key()).isNull();
            }
            fragment.messages().forEach(message -> messages.add(message.pageCode() + ":" + message.key()));
        }
        var mappings = new Mappings();
        Class<DdcConfigDeliveryPermissionMetadata> metadata = DdcConfigDeliveryPermissionMetadata.class;
        Class<DdcConfigDeliveryController> controller = DdcConfigDeliveryController.class;
        assertThat(controller.getPackageName()).isEqualTo(ROOT + "admin.internal.controller");
        assertThat(controller.getAnnotation(PermissionResource.class)).isNull();
        assertThat(metadata.getAnnotation(RestController.class)).isNull();
        assertThat(metadata.getAnnotation(org.springframework.stereotype.Component.class)).isNull();
        assertThat(java.lang.reflect.Modifier.isFinal(metadata.getModifiers())).isTrue();
        assertThat(metadata.getDeclaredConstructors()).singleElement().satisfies(constructor -> {
            assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers())).isTrue();
            assertThat(constructor.getParameterCount()).isZero();
        });
        Map<String, String> supportedScopes = Map.of(
                "configSnapshot", DdcConfigWorkloadVerifier.READ_SCOPE,
                "reportConfigReceipt", DdcConfigWorkloadVerifier.RECEIPT_SCOPE);
        Set<String> keys = new TreeSet<>();
        Set<String> physicalHandlers = new TreeSet<>();
        for (Method handler : controller.getDeclaredMethods()) {
            if (mappings.mapping(handler, controller) != null) {
                physicalHandlers.add(handler.getName());
                assertThat(handler.getAnnotation(PermissionResource.class)).isNull();
            }
        }
        assertThat(physicalHandlers).containsExactlyInAnyOrderElementsOf(supportedScopes.keySet());
        assertThat(metadata.getDeclaredMethods()).hasSize(2);
        for (Method method : metadata.getDeclaredMethods()) {
            String scope = supportedScopes.get(method.getName());
            assertThat(scope).as(method.toGenericString()).isNotNull();
            assertThat(java.lang.reflect.Modifier.isPublic(method.getModifiers())).isTrue();
            assertThat(java.lang.reflect.Modifier.isStatic(method.getModifiers())).isTrue();
            assertThat(method.getReturnType()).isEqualTo(void.class);
            assertThat(method.getParameterCount()).isZero();
            var declaration = method.getAnnotation(PermissionResource.class);
            assertThat(declaration).isNotNull();
            assertThat(declaration.access()).isEqualTo(ResourceAccess.INTERNAL);
            assertThat(declaration.allowedPrincipals()).containsExactly(PermissionManifest.PrincipalType.SERVICE);
            assertThat(declaration.oauthScopes()).containsExactly(scope);
            assertThat(keys.add(declaration.key())).isTrue();
            var exported = resources.get(declaration.key());
            assertThat(exported).as(declaration.key()).isNotNull();
            assertThat(exported.type()).isEqualTo(ResourceType.API);
            assertThat(exported.access()).isEqualTo(ResourceAccess.INTERNAL);
            assertThat(exported.allowedPrincipals()).containsExactly(PermissionManifest.PrincipalType.SERVICE);
            assertThat(exported.oauthScopes()).containsExactly(scope);
            assertThat(exported.requirement()).isNull();
            assertThat(exported.fieldPolicy()).isNull();
            assertThat(messages).contains(exported.i18n().pageCode() + ":" + exported.i18n().labelKey());
            RequestMappingInfo declared = mappings.mapping(method, metadata);
            Method handler = java.util.Arrays.stream(controller.getDeclaredMethods())
                    .filter(value -> value.getName().equals(method.getName())).findFirst().orElseThrow();
            RequestMappingInfo physical = mappings.mapping(handler, controller);
            assertThat(declared).isNotNull();
            assertThat(physical).isNotNull();
            assertThat(declared.getPatternValues()).isEqualTo(physical.getPatternValues());
            assertThat(declared.getMethodsCondition()).isEqualTo(physical.getMethodsCondition());
            assertThat(declared.getConsumesCondition()).isEqualTo(physical.getConsumesCondition());
            assertThat(declared.getProducesCondition()).isEqualTo(physical.getProducesCondition());
            assertThat(strings(exported.target().get("paths"))).containsExactlyInAnyOrderElementsOf(physical.getPatternValues());
            Set<String> explicitMethods = new TreeSet<>();
            physical.getMethodsCondition().getMethods().forEach(value -> explicitMethods.add(value.name()));
            // This protocol's filter allows only explicit GET/POST. Implicit MVC HEAD is denied.
            assertThat(strings(exported.target().get("methods"))).containsExactlyInAnyOrderElementsOf(explicitMethods);
        }
        assertThat(keys).containsExactlyInAnyOrderElementsOf(CONFIG_PROTOCOL_API_KEYS);
        Set<String> protocolDtos = Set.of(DdcConfigSnapshotDTO.class.getCanonicalName(),
                DdcConfigScopeVO.class.getCanonicalName(), DdcConfigReceiptCommand.class.getCanonicalName(),
                DdcConfigDeliveryController.DeliveryErrorVO.class.getCanonicalName());
        assertThat(resources.values().stream().filter(value -> value.type() == ResourceType.FIELD)
                .map(value -> value.target().get("dto")).filter(protocolDtos::contains).toList()).isEmpty();
        for (Class<?> dto : List.of(DdcConfigSnapshotDTO.class, DdcConfigScopeVO.class,
                DdcConfigReceiptCommand.class, DdcConfigDeliveryController.DeliveryErrorVO.class)) {
            for (Field field : dto.getDeclaredFields()) assertThat(field.getAnnotation(PermissionResource.class)).isNull();
        }
    }

    private void walk(Type generic, boolean output, Map<String, PermissionManifest.ResourceDefinition> resources,
                      Set<String> fields, Set<String> external, Set<String> visiting) throws Exception {
        if (generic instanceof ParameterizedType parameterized) {
            for (Type argument : parameterized.getActualTypeArguments()) walk(argument, output, resources, fields, external, visiting);
            generic = parameterized.getRawType();
        }
        if (!(generic instanceof Class<?> type) || type.isPrimitive() || type.isEnum()) return;
        if (type.isArray()) { walk(type.componentType(), output, resources, fields, external, visiting); return; }
        if (!type.getName().startsWith(ROOT + "admin.model.") && !type.getName().startsWith(ROOT + "model.")) {
            if (type.getName().startsWith("top.egon.")) external.add(type.getName());
            return;
        }
        if (!visiting.add(type.getName() + output)) return;
        var dto = json.constructType(type);
        var description = output ? json.getSerializationConfig().introspect(dto) : json.getDeserializationConfig().introspect(dto);
        for (var property : description.findProperties()) {
            if (property.getPrimaryMember() == null) continue;
            Field field = type.getDeclaredField(property.getInternalName());
            PermissionResource declaration = field.getAnnotation(PermissionResource.class);
            assertThat(declaration).as(type.getName() + ":" + property.getName()).isNotNull();
            var resource = resources.get(declaration.key());
            assertThat(resource).as(declaration.key()).isNotNull();
            assertThat(resource.type()).isEqualTo(ResourceType.FIELD);
            assertThat(resource.target()).containsEntry("dto", type.getCanonicalName()).containsEntry("property", property.getName());
            fields.add(declaration.key());
            walk(field.getGenericType(), output, resources, fields, external, visiting);
        }
    }

    private List<PermissionManifest> fragments() throws Exception {
        List<PermissionManifest> fragments = new ArrayList<>();
        for (Path path : List.of(Path.of("target/classes/META-INF/permissions/fragment-v1.json"),
                Path.of("../stellar-enclosure-tianshu-starter/target/classes/META-INF/permissions/fragment-v1.json"))) {
            assertThat(path).isRegularFile();
            var manifest = new PermissionManifestReader().read(json.readValue(Files.readString(path), new TypeReference<Map<String, Object>>() { }));
            assertThat(manifest.producer().id()).isEqualTo("tianshu-java");
            assertThat(manifest.producer().ownedKeyPrefixes()).containsExactly("tianshu.api.", "tianshu.field.");
            assertThat(manifest.producer().snapshotMode()).isEqualTo(PermissionManifest.SnapshotMode.PARTIAL);
            assertThat(manifest.producer().expectedModules()).containsExactlyInAnyOrder(
                    "stellar-enclosure-tianshu-starter", "stellar-enclosure-tianshu-admin");
            fragments.add(manifest);
        }
        return fragments;
    }

    @SuppressWarnings("unchecked")
    private List<String> strings(Object value) { return (List<String>) value; }

    private static final class Mappings extends RequestMappingHandlerMapping {
        RequestMappingInfo mapping(Method method, Class<?> type) { return getMappingForMethod(method, type); }
    }
}
