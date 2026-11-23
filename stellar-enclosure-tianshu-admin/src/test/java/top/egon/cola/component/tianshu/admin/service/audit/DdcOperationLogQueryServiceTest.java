package top.egon.cola.component.tianshu.admin.service.audit;

import jakarta.validation.Validation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import top.egon.cola.component.common.core.enums.ResultCode;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPO;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPhaseEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogOutcomeEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository.PageRow;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository.WindowQuery;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcOperationLogViewConverter;
import top.egon.cola.component.tianshu.admin.model.query.DdcOperationLogPageQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogDetailVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogSummaryVO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantBindingDTO;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.error.DdcErrorStatus;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class DdcOperationLogQueryServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-23T10:00:00.123456Z");
    private final OperationLogRepository repository = mock(OperationLogRepository.class);
    private final DdcOperationLogViewConverter.Summary summary = mock(DdcOperationLogViewConverter.Summary.class);
    private final DdcOperationLogViewConverter.Detail detail = mock(DdcOperationLogViewConverter.Detail.class);
    private final ValidationUtils validation = new ValidationUtils(Validation.buildDefaultValidatorFactory().getValidator());
    private final DdcTenantRegistry tenants = mock(DdcTenantRegistry.class);

    @AfterEach void clearContext() { MDC.clear(); }

    @Test void sameAnchorDefaultsToOneDayAndNeverFetchesDetailsOrCounts() {
        var service = service(true);
        when(repository.page(any())).thenReturn(List.of());
        var result = service.page(new DdcOperationLogPageQuery());
        assertThat(result.getFrom()).isEqualTo(NOW.minusSeconds(86_400));
        assertThat(result.getTo()).isEqualTo(NOW);
        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getNextCursor()).isNull();
        var query = capturedWindow();
        assertThat(query.tenantId()).isEqualTo(23L);
        assertThat(query.fetchLimit()).isEqualTo(51);
        assertThat(query.filters()).isEmpty();
        verify(repository, never()).findDetail(any(), any(), any());
    }

    @Test void equalTimeCrossBucketsSortsBigintWithoutJavascriptPrecisionLossAndTrimsOnlyAfterMerge() {
        var service = service(true);
        var at = NOW.minusSeconds(10);
        when(repository.page(any())).thenReturn(List.of(row(9007199254740992L, at), row(3L, at.minusSeconds(1)),
                row(9007199254740993L, at), row(9007199254740994L, at)));
        var result = service.page(new DdcOperationLogPageQuery().setLimit(2));
        assertThat(result.getRecords()).extracting(DdcOperationLogSummaryVO::getEventId)
                .containsExactly("9007199254740994", "9007199254740993");
        assertThat(result.getNextCursor().beforeTime()).isEqualTo(at);
        assertThat(result.getNextCursor().beforeId()).isEqualTo("9007199254740993");
        assertThat(capturedWindow().fetchLimit()).isEqualTo(3);
    }

    @Test void anAnchoredCursorUsesTheOriginalExactInstantAndBigint() {
        var service = service(true);
        when(repository.page(any())).thenReturn(List.of());
        var input = window().setBeforeTime("2026-11-23T05:59:59.000001-04:00").setBeforeId("9223372036854775807");
        var result = service.page(input);
        assertThat(result.getFrom()).isEqualTo(Instant.parse("2026-11-23T09:00:00Z"));
        assertThat(result.getTo()).isEqualTo(NOW);
        var query = capturedWindow();
        assertThat(query.beforeTime()).isEqualTo(Instant.parse("2026-11-23T09:59:59.000001Z"));
        assertThat(query.beforeId()).isEqualTo(Long.MAX_VALUE);
    }

    @Test void reusesAllDeclaredFiltersAfterNormalizationAndValidatesTheActionRegistry() {
        var service = service(true);
        when(repository.page(any())).thenReturn(List.of());
        service.page(window().setActionCode(" TIANSHU_CONFIG_UPDATE ").setActorId(" actor ")
                .setResourceType(" CONFIG ").setResourceId(" id ").setBizCode(" business ")
                .setNamespaceCode(" namespace ").setEnv(" prod ").setAppCode(" app ").setOutcome(" SUCCEEDED "));
        assertThat(capturedWindow().filters()).containsEntry("actionCode", "TIANSHU_CONFIG_UPDATE")
                .containsEntry("actorId", "actor").containsEntry("resourceType", "CONFIG").containsEntry("resourceId", "id")
                .containsEntry("bizCode", "business").containsEntry("namespaceCode", "namespace")
                .containsEntry("env", "prod").containsEntry("appCode", "app").containsEntry("outcome", "SUCCEEDED").hasSize(9);
    }

    @Test void rejectsInvalidTimeWindowCursorPairsAndLimitsBeforeTargetAccess() {
        var service = service(true);
        for (var query : List.of(new DdcOperationLogPageQuery().setFrom("2026-11-23T09:00:00Z"),
                window().setBeforeTime("2026-11-23T09:10:00Z"),
                new DdcOperationLogPageQuery().setBeforeTime("2026-11-23T09:10:00Z").setBeforeId("1"),
                window().setFrom("2026-10-01T09:00:00Z"), window().setFrom("2026-11-24T09:00:00Z"),
                window().setBeforeTime("2026-11-22T09:10:00Z").setBeforeId("1"),
                window().setBeforeTime(NOW.toString()).setBeforeId("1"),
                window().setBeforeTime("2026-11-23T09:10:00Z").setBeforeId("9223372036854775808"),
                new DdcOperationLogPageQuery().setLimit(0), new DdcOperationLogPageQuery().setLimit(101),
                new DdcOperationLogPageQuery().setLimit(null))) {
            assertInvalid(() -> service.page(query));
        }
        verifyNoInteractions(repository);
    }

    @Test void rejectsNonRfc3339OrSubmicrosecondTimesRatherThanTruncatingInput() {
        var service = service(true);
        for (String value : List.of("2026-11-23T09:00:00", "2026-11-23", "2026-11-23T09:00:00.1234567Z",
                "2026-02-30T09:00:00Z", "2026-11-23 09:00:00Z", "2026-11-23T09:00:00Z\n")) {
            assertInvalid(() -> service.page(window().setFrom(value)));
            assertInvalid(() -> service.detail("1", value));
        }
        verifyNoInteractions(repository);
    }

    @Test void rejectsUnknownCodesControlCharactersBlankFiltersAndOverflowIds() {
        var service = service(true);
        for (var query : List.of(window().setActionCode("UNKNOWN"), window().setResourceType("UNKNOWN"),
                window().setOutcome("UNKNOWN_ENUM_VALUE"), window().setActorId(" actor\t "),
                window().setActorId("  "), window().setResourceId("\u007f"), window().setEnv("x".repeat(33)))) {
            assertInvalid(() -> service.page(query));
        }
        for (String id : List.of("0", "+1", "01", "-1", "9223372036854775808", " 1", "1\n")) {
            assertInvalid(() -> service.detail(id, NOW.toString()));
        }
        verifyNoInteractions(repository);
    }

    @Test void tenantIsReadAgainOnEveryCallAndCannotBeSuppliedInThePublicQuery() {
        var service = service(true);
        when(repository.page(any())).thenReturn(List.of());
        service.page(new DdcOperationLogPageQuery());
        MDC.put("tenantId", "24");
        when(tenants.requireEnabledTenant(24L)).thenReturn(new DdcTenantBindingDTO(24L, "tenant-24", true));
        service.page(new DdcOperationLogPageQuery());
        var captured = org.mockito.ArgumentCaptor.forClass(WindowQuery.class);
        verify(repository, org.mockito.Mockito.times(2)).page(captured.capture());
        assertThat(captured.getAllValues()).extracting(WindowQuery::tenantId).containsExactly(23L, 24L);
        assertThat(java.util.Arrays.stream(DdcOperationLogPageQuery.class.getDeclaredFields()).map(java.lang.reflect.Field::getName))
                .doesNotContain("tenantId", "offset", "pageNo");
    }

    @Test void failedBucketReturnsUnavailableInsteadOfPartialOrEmptyPage() {
        var service = service(true);
        when(repository.page(any())).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        assertThatThrownBy(() -> service.page(window())).isInstanceOfSatisfying(CommonException.class,
                failure -> assertThat(failure.getStatus()).isEqualTo(DdcErrorStatus.OPERATION_LOG_UNAVAILABLE.getStatus()));
        verifyNoInteractions(summary);
    }

    @Test void missingTargetIsUnavailableForBothQueriesWhileUnexpectedCodeBugsAreNot503() {
        var disabled = service(false);
        assertThatThrownBy(() -> disabled.page(window())).isInstanceOfSatisfying(CommonException.class,
                failure -> assertThat(failure.getCode()).isEqualTo(56998));
        assertThatThrownBy(() -> disabled.detail("1", NOW.toString())).isInstanceOfSatisfying(CommonException.class,
                failure -> assertThat(failure.getCode()).isEqualTo(56998));
        var active = service(true);
        when(repository.page(any())).thenThrow(new IllegalStateException("unexpected programming defect"));
        assertThatThrownBy(() -> active.page(window())).isInstanceOf(IllegalStateException.class);
    }

    @Test void detailUsesTenantIdAndExactInstantAndReturnsNotFoundWhenAbsent() {
        var service = service(true);
        Instant at = Instant.parse("2026-11-23T09:00:00.000001Z");
        when(repository.findDetail(23L, Long.MAX_VALUE, at)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail("9223372036854775807", "2026-11-23T05:00:00.000001-04:00"))
                .isInstanceOfSatisfying(CommonException.class, failure -> assertThat(failure.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
        verify(repository).findDetail(23L, Long.MAX_VALUE, at);
        verifyNoInteractions(detail);
    }

    @Test void detailResultIsProjectedAndDoesNotReturnThePersistenceModel() {
        var service = service(true);
        var row = OperationLogPO.builder().id(101L).tenantId(23L).occurredAt(NOW).build();
        var view = new DdcOperationLogDetailVO().setEventId("101").setOccurredAt(NOW);
        when(repository.findDetail(23L, 101L, NOW)).thenReturn(Optional.of(row));
        when(detail.toTarget(row)).thenReturn(view);
        assertThat(service.detail("101", NOW.toString())).isSameAs(view);
        verify(detail).toTarget(row);
    }

    @Test void generatedConvertersUseOnlyTheApprovedFieldsAndFenceUnknownDetails() throws Exception {
        var summaryMapper = new top.egon.cola.component.tianshu.admin.model.convertor.DdcOperationLogSummaryViewConverterImpl();
        var detailMapper = new top.egon.cola.component.tianshu.admin.model.convertor.DdcOperationLogDetailViewConverterImpl();
        var json = org.springframework.http.converter.json.Jackson2ObjectMapperBuilder.json().build();
        var summaryJson = json.valueToTree(summaryMapper.toTarget(row(Long.MAX_VALUE, NOW)));
        assertThat(summaryJson.path("eventId").isTextual()).isTrue();
        assertThat(summaryJson.path("eventId").textValue()).isEqualTo("9223372036854775807");
        assertThat(summaryJson.size()).isEqualTo(14);
        assertThat(summaryJson.has("details")).isFalse();
        assertThat(summaryJson.has("tenantId")).isFalse();
        assertThat(summaryJson.has("requestUrl")).isFalse();
        assertThat(summaryJson.has("handlerMethod")).isFalse();
        var fact = OperationLogPO.builder().id(Long.MAX_VALUE).tenantId(23L).operationId("op")
                .schemaVersion(1).sourceService("tianshu-admin").moduleCode("CONFIG").actionCode("TIANSHU_CONFIG_UPDATE")
                .resourceType("CONFIG").occurredAt(NOW).createTime(NOW.plusNanos(1_000))
                .phase(OperationLogPhaseEnum.COMMAND).outcome(OperationLogOutcomeEnum.SUCCEEDED)
                .details(json.createObjectNode().put("revision", "2").put("token", "must-not-escape")).build();
        var view = detailMapper.toTarget(fact);
        assertThat(view.getRecordedAt()).isEqualTo(NOW.plusNanos(1_000));
        assertThat(view.getDetails()).isEqualTo(json.createObjectNode().put("revision", "2"));
        view.getDetails().put("revision", "changed");
        assertThat(fact.getDetails().path("revision").textValue()).isEqualTo("2");
        assertThat(json.valueToTree(view).has("tenantId")).isFalse();
        fact.setSchemaVersion(2);
        assertThat(detailMapper.toTarget(fact).getDetails()).isEmpty();
        fact.setSchemaVersion(1).setActionCode("UNREGISTERED_ACTION");
        assertThat(detailMapper.toTarget(fact).getDetails()).isEmpty();
        fact.setActionCode("TIANSHU_CONFIG_PUBLISH").setDetails(json.createObjectNode()
                .put("publishStatus", "PUBLISHING").put("attempt", -1).put("targetRevision", "01").put("private", "hidden"));
        assertThat(detailMapper.toTarget(fact).getDetails()).isEmpty();
    }

    @Test void aRealValidationProxyAllowsNormalizedLengthsButRejectsRawControlCharacters() {
        var service = service(true);
        when(repository.page(any())).thenReturn(List.of());
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(
                        org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration.class))
                .withBean("ddcOperationLogQueryService", DdcOperationLogQueryService.class, () -> service)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var proxied = context.getBean(DdcOperationLogQueryService.class);
                    assertThat(org.springframework.aop.support.AopUtils.isAopProxy(proxied)).isTrue();
                    assertThat(proxied.page(window().setActorId(" ".repeat(512) + "actor" + " ".repeat(512))).getRecords()).isEmpty();
                    assertThatThrownBy(() -> proxied.page(window().setActorId("actor\t")))
                            .isInstanceOf(jakarta.validation.ConstraintViolationException.class);
                });
    }

    @Test void missingOrNonPositiveTrustedTenantFailsBeforeAnyTargetAccess() {
        var service = service(true);
        MDC.clear();
        assertInvalid(() -> service.page(window()));
        MDC.put("tenantId", "0");
        assertInvalid(() -> service.page(window()));
        MDC.put("tenantId", "-1");
        assertInvalid(() -> service.detail("1", NOW.toString()));
        verifyNoInteractions(repository);
    }

    private DdcOperationLogQueryService service(boolean enabled) {
        MDC.put("tenantId", "23");
        when(tenants.requireEnabledTenant(23L)).thenReturn(new DdcTenantBindingDTO(23L, "tenant-23", true));
        org.mockito.Mockito.doAnswer(invocation -> {
            PageRow row = invocation.getArgument(0);
            return new DdcOperationLogSummaryVO().setEventId(Long.toString(row.id())).setOccurredAt(row.occurredAt());
        }).when(summary).toTarget(any());
        var beans = new StaticListableBeanFactory();
        if (enabled) beans.addBean("operationLogRepository", repository);
        return new DdcOperationLogQueryService(beans.getBeanProvider(OperationLogRepository.class), summary, detail,
                validation, Clock.fixed(NOW, ZoneOffset.UTC), tenants);
    }
    private WindowQuery capturedWindow() {
        var captured = org.mockito.ArgumentCaptor.forClass(WindowQuery.class);
        verify(repository).page(captured.capture());
        return captured.getValue();
    }
    private static DdcOperationLogPageQuery window() {
        return new DdcOperationLogPageQuery().setFrom("2026-11-23T09:00:00Z").setTo(NOW.toString());
    }
    private static PageRow row(long id, Instant at) {
        return new PageRow(id, 23L, "op", at, "tianshu-admin", "CONFIG", "TIANSHU_CONFIG_UPDATE", "CONFIG", "config",
                "USER", "user-1", null, OperationLogPhaseEnum.COMMAND, OperationLogOutcomeEnum.SUCCEEDED, "/config/{id}");
    }
    private static void assertInvalid(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(CommonException.class,
                failure -> assertThat(failure.getStatus()).isEqualTo(DdcErrorStatus.INVALID_REQUEST.getStatus()));
    }
}
