package top.egon.cola.component.tianshu.admin.permission;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import top.egon.cola.component.common.desensitize.strategy.SensitiveStrategyRegistry;
import top.egon.cola.component.tianshu.admin.config.DdcAiWebConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcI18nWebConfiguration;
import top.egon.cola.component.tianshu.admin.config.DdcPermissionWebConfiguration;
import top.egon.cola.component.tianshu.admin.controller.config.DdcConfigController;
import top.egon.cola.component.tianshu.admin.model.command.DdcAiDraftCommand;
import top.egon.cola.component.tianshu.admin.model.command.DdcI18nDraftCommand;
import top.egon.cola.component.tianshu.admin.model.dto.DdcConfigCreateRequest;
import top.egon.cola.component.tianshu.admin.model.vo.DdcConfigVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.service.config.DdcConfigService;
import top.egon.cola.component.tianshu.admin.service.publish.DdcPublishService;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.*;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.*;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.annotation.PermissionResource;
import top.egon.cola.platform.tianquan.jianshen.starter.autoconfigure.Rbac3StarterProperties;
import top.egon.cola.platform.tianquan.jianshen.starter.authorization.AuthorizationService;
import top.egon.cola.platform.tianquan.jianshen.starter.cache.SingleFlightSnapshotLoader;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionFieldBindingGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionJsonMapperFactory;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionPolicyRegistry;
import top.egon.cola.platform.tianquan.jianshen.starter.security.*;
import top.egon.cola.platform.tianquan.shoubing.contract.AuthenticationContext;
import top.egon.cola.platform.tianquan.shoubing.contract.IdentityPrincipal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Actual compiled API/field executors and host converters; SQL loading is an explicit fixture, never a database claim. */
@EnabledIfSystemProperty(named = "permission.producer.enabled", matches = "true")
class DdcManagedUserRuntimeTest {
    private static final Instant NOW = Instant.parse("2026-10-25T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String ARTIFACT = "a".repeat(64);
    private static final CatalogScope SCOPE = new CatalogScope("10", "20", "xingyuan", "tianshu", "default", "test");
    private static final IdentityPrincipal USER = new IdentityPrincipal("30", "20", "40", Set.of("https://api.example/tianshu"),
            NOW.minusSeconds(10), NOW.plusSeconds(300), AuthenticationContext.of("PASSWORD", NOW.minusSeconds(20)));

    @AfterEach void clear() {
        SecurityContextHolder.clearContext();
        org.springframework.web.context.request.RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void fieldCatalogReportsActualAdapterInputsAndRejectsUnverifiedOrForeignQueries() throws Exception {
        var fixture = fixture(Set.of(), Set.of());
        var registry = new PermissionPolicyRegistry(fixture.manifest(), ARTIFACT, CLOCK, List.of(SCOPE));
        var view = new top.egon.cola.platform.tianquan.jianshen.starter.permission.CurrentEffectivePermissionView(new CurrentRbac3User(), registry, CLOCK);
        var fields = new top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionFieldCatalog(registry, view, new CurrentRbac3User());
        var adapter = new org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter();
        adapter.setMessageConverters(List.of(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(new ObjectMapper().findAndRegisterModules())));
        var routes = new org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping();
        var beans = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        beans.addBean("adapter", adapter);
        beans.addBean("routes", routes);
        var controller = new top.egon.cola.component.tianshu.admin.controller.DdcFieldCatalogController(fields,
                beans.getBeanProvider(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.class),
                beans.getBeanProvider(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping.class));
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/authorization/field-catalog");
        request.addParameter("keys", "tianshu.field.DdcConfigQueryRequest.bizCode", "tianshu.field.DdcConfigVO.id");
        assertThatThrownBy(() -> controller.current(request)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier.set(request, "verified-user-fixture");
        var response = controller.current(request);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().data().catalogScope()).isEqualTo(SCOPE);
        assertThat(response.getBody().data().fields().get("tianshu.field.DdcConfigQueryRequest.bizCode").inputBinding()).isTrue();
        assertThat(response.getBody().data().fields().get("tianshu.field.DdcConfigVO.id").inputBinding()).isFalse();
        request.addParameter("tenantId", "other");
        assertThatThrownBy(() -> controller.current(request)).isInstanceOf(IllegalArgumentException.class);
        request.removeParameter("tenantId");
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> controller.current(request)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    @Test void everyManagedUserEntryRejectsMissingIdentityBeforeItsBody() throws Exception {
        var fixture = fixture(Set.of(), Set.of());
        SecurityContextHolder.clearContext();
        var manager = new Rbac3MethodAuthorizationManager(mock(AuthorizationService.class), fixture.execution());
        var interceptor = new AuthorizationManagerBeforeMethodInterceptor(new StaticMethodMatcherPointcut() {
            @Override public boolean matches(Method method, Class<?> targetClass) { return manager.supports(method, targetClass); }
        }, manager);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        var environment = new org.springframework.core.env.StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("runtime-test",
                Map.of("egon.cola.platform.tianquan.jianshen.enabled", "true")));
        scanner.setEnvironment(environment);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int count = 0;
        for (var candidate : scanner.findCandidateComponents("top.egon.cola.component.tianshu.admin.controller")) {
            var type = Class.forName(candidate.getBeanClassName());
            var proxy = new ProxyFactory(mock(type, CALLS_REAL_METHODS));
            proxy.setProxyTargetClass(true); proxy.addAdvisor(interceptor);
            Object target = proxy.getProxy();
            for (var method : type.getDeclaredMethods()) {
                var resource = method.getAnnotation(PermissionResource.class);
                if (resource == null || resource.key().equals("tianshu.api.adminUiI18n.publicBundle")) continue;
                Object[] arguments = Arrays.stream(method.getParameterTypes()).map(parameter -> {
                    if (parameter == int.class) return (Object) 1;
                    if (parameter == long.class) return (Object) 1L;
                    if (parameter == boolean.class) return (Object) false;
                    return null;
                }).toArray();
                assertThatThrownBy(() -> method.invoke(target, arguments)).as(resource.key())
                        .isInstanceOf(InvocationTargetException.class).hasCauseInstanceOf(AuthenticationCredentialsNotFoundException.class);
                count++;
            }
        }
        assertThat(count).isEqualTo(88);
        verifyNoInteractions(fixture.loader());
    }

    @Test void currentSqlViewAndOAuthScopeBothBoundTheCompiledApiExpression() throws Exception {
        var fixture = fixture(Set.of(DdcPermissionCodes.READ), Set.of(DdcPermissionCodes.READ));
        assertThat(fixture.execution().require("tianshu.api.config.list").permissions()).contains(DdcPermissionCodes.READ);
        when(fixture.loader().load(USER)).thenReturn(snapshot(fixture.manifest(), Set.of(), ARTIFACT));
        assertThatThrownBy(() -> fixture.execution().require("tianshu.api.config.list")).isInstanceOf(AuthorizationDeniedException.class);
        assertThatThrownBy(() -> snapshot(fixture.manifest(), Set.of("*"), ARTIFACT))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("permissionCodes");
        authenticate(snapshot(fixture.manifest(), Set.of(DdcPermissionCodes.READ), ARTIFACT), Set.of(DdcPermissionCodes.WRITE));
        when(fixture.loader().load(USER)).thenReturn(snapshot(fixture.manifest(), Set.of(DdcPermissionCodes.READ), ARTIFACT));
        assertThatThrownBy(() -> fixture.execution().require("tianshu.api.config.list")).isInstanceOf(AuthorizationDeniedException.class);
    }

    @Test void executingArtifactMustMatchTheCurrentActivation() throws Exception {
        var fixture = fixture(Set.of(DdcPermissionCodes.READ), Set.of(DdcPermissionCodes.READ));
        when(fixture.loader().load(USER)).thenReturn(snapshot(fixture.manifest(), Set.of(DdcPermissionCodes.READ), "b".repeat(64)));
        assertThatThrownBy(() -> fixture.execution().require("tianshu.api.config.list"))
                .isInstanceOf(RuntimeException.class).hasMessage("AUTH_DEPLOYMENT_BINDING_MISMATCH");
    }

    @Test void normalHttpMapperCapturesNullUnknownAndReadonlyFieldsWithoutMutatingInternalMapper() throws Exception {
        var fixture = fixture(Set.of(DdcPermissionCodes.WRITE), Set.of(DdcPermissionCodes.WRITE));
        var internal = new ObjectMapper().findAndRegisterModules();
        var converter = new MappingJackson2HttpMessageConverter(internal);
        fixture.web().extendMessageConverters(List.of(converter));
        var http = converter.getObjectMapper();
        assertThat(http).isNotSameAs(internal);
        assertThat(internal.getRegisteredModuleIds()).doesNotContain("Rbac3FieldJacksonModule");
        assertThat(http.readValue("{\"content\":null}", DdcConfigCreateRequest.class).getContent()).isNull();
        assertThatThrownBy(() -> http.readValue("{\"ownerId\":null}", DdcConfigCreateRequest.class))
                .hasStackTraceContaining("FIELD_BINDING_UNKNOWN_OR_DUPLICATE");
        assertThatThrownBy(() -> http.readValue("{\"id\":\"42\"}", DdcConfigVO.class)).hasStackTraceContaining("FIELD_ACCESS_DENIED");
        var output = new DdcConfigVO().setId("42").setContent("private-config");
        assertThat(http.readTree(http.writeValueAsString(output)).has("content")).isFalse();
        SecurityContextHolder.clearContext();
        assertThat(internal.readTree(internal.writeValueAsString(output)).get("content").asText()).isEqualTo("private-config");
        assertThat(internal.readValue("{\"content\":\"cache-value\"}", DdcConfigVO.class).getContent()).isEqualTo("cache-value");
    }

    @Test void strictTranslationAndAiConvertersRetainTheirPoliciesAndTranslationMapKeys() throws Exception {
        var fixture = fixture(Set.of(DdcPermissionCodes.WRITE), Set.of(DdcPermissionCodes.WRITE));
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        new DdcI18nWebConfiguration().ddcI18nWebConfigurer(Jackson2ObjectMapperBuilder.json(), fixture.factory()).extendMessageConverters(converters);
        var translations = (MappingJackson2HttpMessageConverter) converters.getFirst();
        String body = """
                {"expectedRevision":"0","name":"页面","defaultLocale":"zh-CN","translations":{"zh-CN":{"menu.title":"原始文案"}},
                 "catalogActivationVersion":"1","reviewedSourceHashes":{"menu.title":"source-hash"}}
                """;
        var input = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        input.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        var command = (DdcI18nDraftCommand) translations.read(DdcI18nDraftCommand.class, input);
        assertThat(command.translations()).containsEntry("zh-CN", Map.of("menu.title", "原始文案"));
        authenticate(snapshot(fixture.manifest(), Set.of(DdcPermissionCodes.WRITE), ARTIFACT), Set.of(DdcPermissionCodes.READ));
        var denied = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        denied.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        assertThatThrownBy(() -> translations.read(DdcI18nDraftCommand.class, denied)).hasStackTraceContaining("FIELD_ACCESS_DENIED");
        new DdcAiWebConfiguration().ddcAiWebConfigurer(Jackson2ObjectMapperBuilder.json(), fixture.factory()).extendMessageConverters(converters);
        var ai = (MappingJackson2HttpMessageConverter) converters.getFirst();
        assertThatThrownBy(() -> ai.getObjectMapper().readValue("{\"draft\":null}", DdcAiDraftCommand.class))
                .hasStackTraceContaining("FIELD_ACCESS_DENIED");
    }

    @Test void bootMvcFirstReadersAndWriterKeepFieldPoliciesAndRawTransportOrder() throws Exception {
        var fixture = fixture(Set.of(DdcPermissionCodes.WRITE, DdcPermissionCodes.AI_WRITE),
                Set.of(DdcPermissionCodes.WRITE, DdcPermissionCodes.AI_WRITE));
        var properties = new org.springframework.boot.env.YamlPropertySourceLoader().load("application",
                new org.springframework.core.io.ClassPathResource("application.yml"));
        String preferred = properties.stream().map(value -> value.getProperty("spring.http.converters.preferred-json-mapper"))
                .filter(Objects::nonNull).map(Object::toString).findFirst().orElseThrow();
        String codecsPreferred = properties.stream().map(value -> value.getProperty("spring.http.codecs.preferred-json-mapper"))
                .filter(Objects::nonNull).map(Object::toString).findFirst().orElseThrow();
        assertThat(preferred).isEqualTo("jackson2");
        assertThat(codecsPreferred).isEqualTo("jackson2");
        new org.springframework.boot.test.context.runner.WebApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration.class,
                        org.springframework.boot.jackson2.autoconfigure.Jackson2AutoConfiguration.class,
                        org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration.class,
                        org.springframework.boot.webmvc.autoconfigure.WebMvcAutoConfiguration.class))
                .withPropertyValues("spring.http.converters.preferred-json-mapper=" + preferred,
                        "spring.http.codecs.preferred-json-mapper=" + codecsPreferred)
                .withBean(PermissionJsonMapperFactory.class, fixture::factory)
                .withBean(DdcPermissionWebConfiguration.class, fixture::web)
                .withUserConfiguration(DdcAiWebConfiguration.class, DdcI18nWebConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getServletContext()).isInstanceOf(org.springframework.mock.web.MockServletContext.class);
                    var converters = context.getBean(org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter.class)
                            .getMessageConverters();
                    var normal = firstJsonReader(converters, DdcConfigCreateRequest.class);
                    var ai = firstJsonReader(converters, DdcAiDraftCommand.class);
                    var translations = firstJsonReader(converters, DdcI18nDraftCommand.class);
                    var writer = converters.stream().filter(value -> value.canWrite(DdcConfigVO.class, MediaType.APPLICATION_JSON))
                            .findFirst().orElseThrow();
                    assertThat(writer).isSameAs(normal);
                    assertThat(ai).isNotSameAs(normal);
                    assertThat(translations).isNotSameAs(normal).isNotSameAs(ai);
                    for (var json : List.of(normal, ai, translations)) {
                        assertThat(json.getObjectMapper().getRegisteredModuleIds()).contains("Rbac3FieldJacksonModule");
                    }
                    var shared = context.getBean(ObjectMapper.class);
                    assertThat(normal.getObjectMapper()).isNotSameAs(shared);
                    assertThat(shared.getRegisteredModuleIds()).doesNotContain("Rbac3FieldJacksonModule");
                    assertThat(((DdcConfigCreateRequest) normal.read(DdcConfigCreateRequest.class, jsonInput("{\"content\":null}"))).getContent()).isNull();
                    assertThatThrownBy(() -> normal.read(DdcConfigCreateRequest.class, jsonInput("{\"ownerId\":null}")))
                            .hasStackTraceContaining("FIELD_BINDING_UNKNOWN_OR_DUPLICATE");
                    String aiBody = "{\"expectedRevision\":\"0\",\"displayName\":\"Draft\",\"description\":\"\",\"draft\":null}";
                    assertThat(((DdcAiDraftCommand) ai.read(DdcAiDraftCommand.class, jsonInput(aiBody))).getExpectedRevision()).isEqualTo("0");
                    String translationBody = """
                            {"expectedRevision":"0","name":"页面","defaultLocale":"zh-CN","translations":{"zh-CN":{"menu.title":"原始文案"}},
                             "catalogActivationVersion":"1","reviewedSourceHashes":{"menu.title":"source-hash"}}
                            """;
                    assertThat(((DdcI18nDraftCommand) translations.read(DdcI18nDraftCommand.class, jsonInput(translationBody))).translations())
                            .containsEntry("zh-CN", Map.of("menu.title", "原始文案"));
                    authenticate(snapshot(fixture.manifest(), Set.of(DdcPermissionCodes.WRITE, DdcPermissionCodes.AI_WRITE), ARTIFACT),
                            Set.of(DdcPermissionCodes.READ));
                    assertThatThrownBy(() -> ai.read(DdcAiDraftCommand.class, jsonInput(aiBody))).hasStackTraceContaining("FIELD_ACCESS_DENIED");
                    assertThatThrownBy(() -> translations.read(DdcI18nDraftCommand.class, jsonInput(translationBody)))
                            .hasStackTraceContaining("FIELD_ACCESS_DENIED");
                    var output = new org.springframework.mock.http.MockHttpOutputMessage();
                    normal.write(new DdcConfigVO().setContent("private-config"), MediaType.APPLICATION_JSON, output);
                    assertThat(shared.readTree(output.getBodyAsBytes()).has("content")).isFalse();
                    assertRawTransportOrder(converters, normal);
                });
    }

    @Test void independentI18nReaderRetainsBundleFieldsAndCannotReadManagementFields() throws Exception {
        var fixture = fixture(Set.of(DdcPermissionCodes.I18N_READ), Set.of(DdcPermissionCodes.I18N_READ));
        assertThatCode(() -> fixture.execution().require("tianshu.api.i18nPage.bundle")).doesNotThrowAnyException();
        assertThatThrownBy(() -> fixture.execution().require("tianshu.api.adminUiI18n.bundle"))
                .isInstanceOf(AuthorizationDeniedException.class);
        var mapper = fixture.factory().forTransport(new ObjectMapper());
        var bundle = new DdcI18nBundleResult("orders", "zh-CN", "zh-CN", "v1", Map.of("menu.title", "菜单"));
        assertThat(mapper.readTree(mapper.writeValueAsString(bundle)).get("messages").get("menu.title").asText()).isEqualTo("菜单");
        assertThat(mapper.readTree(mapper.writeValueAsString(new DdcConfigVO().setContent("private"))).has("content")).isFalse();
    }

    @Test void springQueryBindingRejectsUnknownAndUnauthorisedSubmittedFieldsBeforeBusiness() throws Exception {
        var fixture = fixture(Set.of(DdcPermissionCodes.READ), Set.of(DdcPermissionCodes.READ));
        var registrations = new TestInterceptors(); fixture.web().addInterceptors(registrations);
        HandlerInterceptor interceptor = registrations.values().getFirst();
        var service = mock(DdcConfigService.class);
        var controller = new DdcConfigController(service, mock(DdcPublishService.class));
        var mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller)
                .addInterceptors(interceptor).build();
        assertThatThrownBy(() -> mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tianshu/configs")
                .param("ownerId", "unexpected"))).hasStackTraceContaining("FIELD_BINDING_UNDECLARED");
        verifyNoInteractions(service);
        when(service.list(any())).thenReturn(List.of());
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tianshu/configs")
                .param("bizCode", "retail")).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        verify(service).list(argThat(value -> "retail".equals(value.getBizCode())));
    }

    @Test void hostPermissionViewUsesOnlyTheVerifiedCurrentUserAndRejectsQueryTargets() throws Exception {
        var fixture = fixture(Set.of(), Set.of());
        var registry = new PermissionPolicyRegistry(fixture.manifest(), ARTIFACT, CLOCK, List.of(SCOPE));
        var view = new top.egon.cola.platform.tianquan.jianshen.starter.permission.CurrentEffectivePermissionView(new CurrentRbac3User(), registry, CLOCK);
        var controller = new top.egon.cola.component.tianshu.admin.controller.DdcCurrentPermissionController(view);
        var request = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/v1/authorization/effective-permissions");
        assertThatThrownBy(() -> controller.current(request)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        top.egon.cola.platform.tianquan.shoubing.starter.security.VerifiedUserTokenCarrier.set(request, "verified-user-fixture");
        var response = controller.current(request);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody().data().catalogScope()).isEqualTo(SCOPE);
        assertThat(response.getBody().data().permissionCodes()).isEmpty();
        request.addParameter("subject", "another-user");
        assertThatThrownBy(() -> controller.current(request)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(fixture.loader());
    }

    @Test void onlyTheReviewedLoginTextBoundaryAndReadOnlyPublicProjectionAllowAnonymousAccess() throws Exception {
        var manifest = manifest();
        String key = "tianshu.api.adminUiI18n.publicBundle";
        var registry = new PermissionPolicyRegistry(manifest, ARTIFACT, CLOCK, List.of(SCOPE), Set.of(), Set.of(key));
        var endpoints = new top.egon.cola.component.tianshu.admin.security.management.DdcSourceImportSecurityConfiguration().idpEndpointAuthenticationPolicy();
        var anonymous = new top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionAnonymousBoundary(registry, () -> endpoints);
        var loader = mock(SingleFlightSnapshotLoader.class);
        var users = new CurrentRbac3User();
        var policy = new Rbac3StarterProperties.UserScopePolicyProperties();
        var fields = new PermissionFieldBindingGuard(registry, users, () -> null, policy,
                top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionDenialAudit.UNOBSERVED, anonymous);
        var factory = new PermissionJsonMapperFactory(users, SensitiveStrategyRegistry.defaults(), registry, fields);
        var execution = new PermissionExecutionGuard(registry, users, loader, null, policy,
                top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionDenialAudit.UNOBSERVED, anonymous);
        var manager = new Rbac3MethodAuthorizationManager(mock(AuthorizationService.class), execution);
        var interceptor = new AuthorizationManagerBeforeMethodInterceptor(new StaticMethodMatcherPointcut() {
            @Override public boolean matches(Method method, Class<?> targetClass) { return manager.supports(method, targetClass); }
        }, manager);
        var service = mock(top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nService.class);
        var controller = new top.egon.cola.component.tianshu.admin.controller.i18n.DdcAdminUiI18nController(service,
                new top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nCatalog(
                        new top.egon.cola.component.tianshu.admin.service.i18n.DdcI18nValidation()));
        var proxy = new ProxyFactory(controller); proxy.setProxyTargetClass(true); proxy.addAdvisor(interceptor);
        var mockMvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(proxy.getProxy())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(factory.forTransport(new ObjectMapper()))).build();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tianshu/i18n/public/bundle").param("locale", "en-US"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.pageCode").value("tianshu.auth"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.data.catalogScope").doesNotExist());
        assertThatThrownBy(() -> mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/tianshu/i18n/public/bundle")
                .param("locale", "en-US").param("tenantId", "20"))).hasStackTraceContaining("AuthenticationCredentialsNotFoundException");
        assertThatThrownBy(() -> execution.require("tianshu.api.authorization.current"))
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        verifyNoInteractions(loader, service);
    }

    private Fixture fixture(Set<String> permissions, Set<String> scopes) throws Exception {
        var manifest = manifest();
        var registry = new PermissionPolicyRegistry(manifest, ARTIFACT, CLOCK, List.of(SCOPE));
        var policy = new Rbac3StarterProperties.UserScopePolicyProperties();
        policy.setEnabled(true);
        Map<String, Set<String>> mapping = new HashMap<>();
        manifest.permissionDefinitions().forEach(value -> mapping.put(value.code(), Set.of(value.code())));
        mapping.put(DdcPermissionCodes.AI_PUBLISH, Set.of(DdcPermissionCodes.AI_WRITE));
        policy.setPermissionScopes(mapping);
        var loader = mock(SingleFlightSnapshotLoader.class);
        var snapshot = snapshot(manifest, permissions, ARTIFACT);
        when(loader.load(USER)).thenReturn(snapshot);
        authenticate(snapshot, scopes);
        var users = new CurrentRbac3User();
        var fields = new PermissionFieldBindingGuard(registry, users, () -> null, policy);
        var factory = new PermissionJsonMapperFactory(users, SensitiveStrategyRegistry.defaults(), registry, fields);
        var execution = new PermissionExecutionGuard(registry, users, loader, null, policy);
        return new Fixture(manifest, loader, execution, factory, new DdcPermissionWebConfiguration(factory, execution, fields));
    }

    private PermissionManifest manifest() throws Exception {
        var json = new ObjectMapper();
        List<PermissionManifest> fragments = new ArrayList<>();
        for (var path : List.of(Path.of("target/classes/META-INF/permissions/fragment-v1.json"),
                Path.of("../stellar-enclosure-tianshu-starter/target/classes/META-INF/permissions/fragment-v1.json"))) {
            fragments.add(new PermissionManifestReader().read(json.readValue(Files.readString(path), new TypeReference<Map<String, Object>>() { })));
        }
        var admin = fragments.getFirst(); var producer = admin.producer();
        // Test-local module union exercises executors; the real prepare-package CLI proves deployable FULL aggregation separately.
        return new PermissionManifest(admin.schemaVersion(), admin.application(),
                new PermissionManifest.Producer(producer.id(), producer.kind(), PermissionManifest.SnapshotMode.FULL,
                        producer.ownedKeyPrefixes(), producer.expectedModules(), producer.expectedModules(), false), admin.artifact(),
                fragments.stream().flatMap(value -> value.permissionDefinitions().stream()).toList(), List.of(),
                fragments.stream().flatMap(value -> value.resources().stream()).toList(), List.of(),
                fragments.stream().flatMap(value -> value.messages().stream()).toList());
    }

    private SystemAuthorizationSnapshot snapshot(PermissionManifest manifest, Set<String> permissions, String artifact) {
        var digests = new PermissionManifestCanonicalizer().digests(manifest);
        var view = new EffectivePermissionSnapshot("https://idp.example/tenants/20", USER.subject(), SCOPE, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("tianshu", List.of("50"), List.of("50")),
                permissions.stream().sorted().toList(), NOW.plusSeconds(30));
        return new SystemAuthorizationSnapshot("20", USER.subject(), "60", "tianshu", 2, 3, List.of("50"), null, null,
                permissions, manifest.resources().stream().map(PermissionManifest.ResourceDefinition::key).collect(Collectors.toSet()),
                Map.of(), Map.of(), "checksum", NOW, view.snapshotExpiresAt(), view,
                new RuntimePolicyBinding("tianshu-java", "1", artifact, digests.policyDigest(), digests.manifestDigest()));
    }

    private void authenticate(SystemAuthorizationSnapshot snapshot, Set<String> scopes) {
        SecurityContextHolder.getContext().setAuthentication(new Rbac3AuthenticationToken(new Rbac3UserDetails(USER, snapshot),
                scopes.stream().map(value -> new SimpleGrantedAuthority("SCOPE_" + value)).toList()));
    }

    private record Fixture(PermissionManifest manifest, SingleFlightSnapshotLoader loader, PermissionExecutionGuard execution,
                           PermissionJsonMapperFactory factory, DdcPermissionWebConfiguration web) { }

    private MappingJackson2HttpMessageConverter firstJsonReader(List<HttpMessageConverter<?>> converters, Class<?> type) {
        var reader = converters.stream().filter(value -> value.canRead(type, MediaType.APPLICATION_JSON)).findFirst().orElseThrow();
        assertThat(reader).isInstanceOf(MappingJackson2HttpMessageConverter.class);
        return (MappingJackson2HttpMessageConverter) reader;
    }

    private MockHttpInputMessage jsonInput(String body) {
        var input = new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8));
        input.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return input;
    }

    private void assertRawTransportOrder(List<HttpMessageConverter<?>> converters, MappingJackson2HttpMessageConverter json) throws Exception {
        var string = converters.stream().filter(value -> value.canWrite(String.class, MediaType.APPLICATION_JSON)).findFirst().orElseThrow();
        var bytes = converters.stream().filter(value -> value.canWrite(byte[].class, MediaType.APPLICATION_OCTET_STREAM)).findFirst().orElseThrow();
        var stream = converters.stream().filter(value -> value.canWrite(org.springframework.core.io.InputStreamResource.class,
                MediaType.APPLICATION_OCTET_STREAM)).findFirst().orElseThrow();
        assertThat(string).isInstanceOf(org.springframework.http.converter.StringHttpMessageConverter.class);
        assertThat(bytes).isInstanceOf(org.springframework.http.converter.ByteArrayHttpMessageConverter.class);
        assertThat(stream).isInstanceOf(org.springframework.http.converter.ResourceHttpMessageConverter.class);
        assertThat(converters.indexOf(bytes)).isLessThan(converters.indexOf(string));
        assertThat(converters.indexOf(string)).isLessThan(converters.indexOf(stream));
        assertThat(converters.indexOf(stream)).isLessThan(converters.indexOf(json));
        var raw = new org.springframework.mock.http.MockHttpOutputMessage();
        ((org.springframework.http.converter.StringHttpMessageConverter) string).write("{\"ok\":true}", MediaType.APPLICATION_JSON, raw);
        assertThat(raw.getBodyAsString()).isEqualTo("{\"ok\":true}");
        var binary = new org.springframework.mock.http.MockHttpOutputMessage();
        ((org.springframework.http.converter.ByteArrayHttpMessageConverter) bytes).write(new byte[] {1, 2, 3}, MediaType.APPLICATION_OCTET_STREAM, binary);
        assertThat(binary.getBodyAsBytes()).containsExactly(1, 2, 3);
        var streamed = new org.springframework.mock.http.MockHttpOutputMessage();
        ((org.springframework.http.converter.ResourceHttpMessageConverter) stream).write(new org.springframework.core.io.InputStreamResource(
                new java.io.ByteArrayInputStream(new byte[] {4, 5, 6})), MediaType.APPLICATION_OCTET_STREAM, streamed);
        assertThat(streamed.getBodyAsBytes()).containsExactly(4, 5, 6);
    }

    private static final class TestInterceptors extends InterceptorRegistry {
        List<HandlerInterceptor> values() { return getInterceptors().stream().map(HandlerInterceptor.class::cast).toList(); }
    }
}
