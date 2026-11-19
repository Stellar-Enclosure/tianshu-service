package top.egon.cola.component.tianshu.admin.openapi;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirementEntry;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;
import top.egon.cola.component.tianshu.admin.internal.controller.DdcAiInternalController;
import top.egon.cola.component.tianshu.admin.internal.controller.DdcConfigDeliveryController;
import top.egon.cola.component.tianshu.api.ai.DdcAiHttpContract;
import top.egon.cola.component.tianshu.model.config.DdcConfigReceiptCommand;
import top.egon.cola.component.tianshu.model.config.DdcConfigSnapshotDTO;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.HandlerMethod;
import top.egon.cola.component.yuheng.openapi.annotation.EgonApiCatalog;
import top.egon.cola.component.yuheng.openapi.annotation.EgonGatewayPolicy;
import top.egon.cola.component.yuheng.openapi.config.GatewayOpenApiProperties;
import top.egon.cola.component.yuheng.openapi.customizer.EgonOperationCustomizer;

import java.util.List;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DdcPublishedOpenApiContractTest {

    @Test
    void allPublishedHandlersCanBeCustomizedForTheDeclaredGroup() throws Exception {
        GatewayOpenApiProperties properties = new GatewayOpenApiProperties();
        properties.setPublishedGroups(List.of("tianshu"));
        OperationCustomizer customizer = new EgonOperationCustomizer(properties);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        int operations = 0;
        for (var bean : scanner.findCandidateComponents("top.egon.cola.component.tianshu.admin.controller")) {
            String name = bean.getBeanClassName();
            Class<?> controller = Class.forName(name);
            Object instance = mock(controller);
            assertThat(controller.getAnnotation(EgonApiCatalog.class).interfaceGroupCode())
                    .as("published Group of %s", name).isEqualTo("tianshu");
            for (var method : controller.getDeclaredMethods()) {
                if (AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class) == null) {
                    continue;
                }
                Operation annotation = AnnotatedElementUtils.findMergedAnnotation(method, Operation.class);
                assertThat(annotation).as("OpenAPI operation for %s", method).isNotNull();
                if (annotation.hidden()) {
                    continue;
                }
                var operation = new io.swagger.v3.oas.models.Operation().operationId(annotation.operationId());
                customizer.customize(operation, new HandlerMethod(instance, method));
                assertThat(operation.getExtensions()).containsKey("x-egon");
                var extension = (java.util.Map<?, ?>) operation.getExtensions().get("x-egon");
                var catalog = (java.util.Map<?, ?>) extension.get("catalog");
                assertThat(catalog.get("interfaceGroupCode")).isEqualTo("tianshu");
                operations++;
            }
        }
        assertThat(operations).isGreaterThan(20);
    }

    @Test
    void internalAiOperationsAreOutsideThePublishedTianshuGroup() {
        Class<DdcAiInternalController> controller = DdcAiInternalController.class;
        assertThat(controller.getAnnotation(EgonApiCatalog.class)).isNull();

        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
        assertThat(scanner.findCandidateComponents("top.egon.cola.component.tianshu.admin.controller"))
                .noneMatch(bean -> controller.getName().equals(bean.getBeanClassName()));

        var methods = Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> AnnotatedElementUtils.hasAnnotation(method, RequestMapping.class))
                .toList();
        assertThat(methods).hasSize(6).allSatisfy(method -> {
            assertThat(method.getAnnotation(EgonGatewayPolicy.class)).isNotNull();
            assertThat(method.getAnnotation(EgonGatewayPolicy.class).exposure())
                    .isEqualTo(EgonGatewayPolicy.Exposure.INTERNAL);
            assertThat(method.getAnnotation(Operation.class)).isNotNull();
        });
        assertThat(methods).flatExtracting(method -> Arrays.asList(
                        AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class).path()))
                .containsExactlyInAnyOrder(DdcAiHttpContract.PROMPTS_PATH, DdcAiHttpContract.SKILLS_PATH,
                        DdcAiHttpContract.VERSION_HEAD_PATH, DdcAiHttpContract.SKILL_DRAFT_PATH,
                        DdcAiHttpContract.SKILL_PUBLICATIONS_PATH, DdcAiHttpContract.USAGE_REPORTS_PATH);
    }

    @Test
    void internalConfigContractUsesTwoSimultaneousProofsAndStringRevision() throws Exception {
        Class<DdcConfigDeliveryController> controller = DdcConfigDeliveryController.class;
        assertThat(controller.getAnnotation(EgonApiCatalog.class)).isNull();
        var commonResponses = controller.getAnnotation(ApiResponses.class);
        assertThat(commonResponses.value()).extracting(ApiResponse::responseCode)
                .contains("400", "401", "403", "409", "503");
        var handlers = Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(Operation.class)).toList();
        assertThat(handlers).hasSize(2);
        assertThat(handlers).extracting(method -> method.getAnnotation(Operation.class).operationId())
                .containsExactlyInAnyOrder("configSnapshot", "reportConfigReceipt");
        for (var method : handlers) {
            var operation = method.getAnnotation(Operation.class);
            assertThat(operation.security()).hasSize(1);
            var both = operation.security()[0];
            assertThat(both.name()).isEqualTo("tianshuConfigServiceBearer");
            assertThat(both.combine()).extracting(SecurityRequirementEntry::name)
                    .containsExactly("tianshuConfigWorkloadToken");
            assertThat(operation.parameters()).extracting(io.swagger.v3.oas.annotations.Parameter::name)
                    .contains("X-Config-Boot-Id", "X-Egon-Workload-Token");
        }
        assertThat(DdcConfigSnapshotDTO.class.getMethod("getReleaseRevision").getReturnType()).isEqualTo(String.class);
        assertThat(DdcConfigReceiptCommand.class.getMethod("getReportSeq").getReturnType()).isEqualTo(String.class);

        var profile = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml")).getFirst();
        assertThat(profile.getProperty("springdoc.group-configs[0].group")).isEqualTo("tianshu");
        assertThat(profile.getProperty("springdoc.group-configs[0].paths-to-match[0]")).isEqualTo("/api/v1/tianshu/**");
        assertThat(profile.getProperty("springdoc.group-configs[0].paths-to-match[1]")).isNull();
    }

}
