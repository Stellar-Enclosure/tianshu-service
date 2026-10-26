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
import top.egon.cola.component.tianshu.admin.model.vo.DdcPublicI18nBundleResult;
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
    private static final Set<String> EXTERNAL_CONTRACTS = Set.of(
            "top.egon.cola.component.common.core.pojo.PageQuery",
            "top.egon.cola.component.common.core.pojo.PageResultRecord",
            "top.egon.cola.component.common.core.pojo.ResultRecord",
            "top.egon.cola.platform.tianquan.jianshen.contract.auth.AuthorizationBootstrapView",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.FieldCatalogSnapshot",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportReceipt",
            "top.egon.cola.platform.tianquan.jianshen.contract.permission.SourceImportRequest");
    private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

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
        assertThat(apiKeys).hasSize(89);
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
        assertThat(definitions).hasSize(9).doesNotContain("*");
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
