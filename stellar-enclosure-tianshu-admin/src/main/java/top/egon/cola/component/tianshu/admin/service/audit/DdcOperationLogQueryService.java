package top.egon.cola.component.tianshu.admin.service.audit;

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.groups.Default;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.core.enums.ResultCode;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.validation.ValidationUtils;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogOutcomeEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository.PageRow;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRepository.WindowQuery;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcOperationLogViewConverter;
import top.egon.cola.component.tianshu.admin.model.query.DdcOperationLogPageQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogDetailVO;
import top.egon.cola.component.tianshu.admin.model.vo.DdcOperationLogPageResult;
import top.egon.cola.component.tianshu.admin.outbox.DdcOperationLogOutboxAdapter;
import top.egon.cola.component.tianshu.admin.security.tenancy.DdcTenantRegistry;
import top.egon.cola.component.tianshu.error.DdcErrorStatus;

import java.sql.SQLException;
import java.sql.SQLRecoverableException;
import java.sql.SQLTransientException;
import java.sql.SQLNonTransientConnectionException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/** Read-only application query; tenant identity is never accepted from the public model. */
@Slf4j
@Service("ddcOperationLogQueryService")
@Validated
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcOperationLogQueryService {
    private static final java.util.regex.Pattern OFFSET_TIME = java.util.regex.Pattern.compile(
            "[0-9]{4}-[0-9]{2}-[0-9]{2}[Tt][0-9]{2}:[0-9]{2}:[0-9]{2}(?:\\.[0-9]{1,6})?(?:[Zz]|[+-][0-9]{2}:[0-9]{2})");
    private static final Comparator<PageRow> NEWEST_FIRST = Comparator.comparing(PageRow::occurredAt)
            .thenComparing(PageRow::id).reversed();
    @Qualifier("operationLogRepository") private final ObjectProvider<OperationLogRepository> repository;
    @Qualifier("ddcOperationLogSummaryViewConverterImpl") private final DdcOperationLogViewConverter.Summary summary;
    @Qualifier("ddcOperationLogDetailViewConverterImpl") private final DdcOperationLogViewConverter.Detail detail;
    @Qualifier("egonColaValidationUtils") private final ValidationUtils validation;
    @Qualifier("egonColaMybatisPlusClock") private final Clock clock;
    @Qualifier("ddcTenantRegistry") private final DdcTenantRegistry tenants;

    public DdcOperationLogPageResult page(@Valid @NotNull DdcOperationLogPageQuery query) {
        if (query == null) throw invalid();
        var normalized = query.toBuilder().actionCode(filter(query.getActionCode())).actorId(filter(query.getActorId()))
                .resourceType(filter(query.getResourceType())).resourceId(filter(query.getResourceId()))
                .bizCode(filter(query.getBizCode())).namespaceCode(filter(query.getNamespaceCode()))
                .env(filter(query.getEnv())).appCode(filter(query.getAppCode())).outcome(filter(query.getOutcome())).build();
        try { validation.validate(normalized, Default.class, DdcOperationLogPageQuery.Normalized.class); }
        catch (ConstraintViolationException invalid) { throw invalid(); }
        Instant to = normalized.getTo() == null ? clock.instant().truncatedTo(ChronoUnit.MICROS) : instant(normalized.getTo());
        Instant from = normalized.getFrom() == null ? to.minus(Duration.ofDays(1)) : instant(normalized.getFrom());
        Instant before = normalized.getBeforeTime() == null ? null : instant(normalized.getBeforeTime());
        Long beforeId = normalized.getBeforeId() == null ? null : positiveId(normalized.getBeforeId());
        Map<String, String> filters = filters(normalized);
        long tenant = trustedTenant();
        var window = new WindowQuery(tenant, from, to, before, beforeId, normalized.getLimit() + 1, filters);
        try { validation.validate(window, top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups.Query.class); }
        catch (ConstraintViolationException invalid) { throw invalid(); }
        var target = target();
        var rows = new ArrayList<>(read(() -> target.page(window)));
        for (var row : rows) {
            if (row == null || !Long.valueOf(tenant).equals(row.tenantId()) || row.id() == null || row.id() <= 0
                    || row.occurredAt() == null || row.occurredAt().isBefore(from) || !row.occurredAt().isBefore(to)) {
                throw new IllegalStateException("OPERATION_LOG_PAGE_IDENTITY_MISMATCH");
            }
        }
        rows.sort(NEWEST_FIRST);
        boolean hasMore = rows.size() > normalized.getLimit();
        var selected = rows.subList(0, Math.min(rows.size(), normalized.getLimit()));
        DdcOperationLogPageResult.CursorVO next = hasMore
                ? new DdcOperationLogPageResult.CursorVO(selected.getLast().occurredAt(), Long.toString(selected.getLast().id())) : null;
        return DdcOperationLogPageResult.builder().from(from).to(to).records(selected.stream().map(summary::toTarget).toList()).nextCursor(next).build();
    }

    public DdcOperationLogDetailVO detail(
            @NotBlank @Pattern(regexp = "[1-9][0-9]{0,18}") String eventId,
            @NotBlank @Size(max = 64) @Pattern(regexp = "[^\\x00-\\x1F\\x7F]*") String occurredAt) {
        long id = positiveId(eventId);
        Instant at = instant(occurredAt);
        long tenant = trustedTenant();
        var target = target();
        var fact = read(() -> target.findDetail(tenant, id, at)).orElseThrow(() -> new CommonException(ResultCode.NOT_FOUND));
        if (!Long.valueOf(tenant).equals(fact.getTenantId()) || !Long.valueOf(id).equals(fact.getId())
                || !at.equals(fact.getOccurredAt())) throw new IllegalStateException("OPERATION_LOG_DETAIL_IDENTITY_MISMATCH");
        return detail.toTarget(fact);
    }

    private Map<String, String> filters(DdcOperationLogPageQuery query) {
        if (query.getActionCode() != null && !DdcOperationLogOutboxAdapter.actions().containsKey(query.getActionCode())) throw invalid();
        if (query.getResourceType() != null && DdcOperationLogOutboxAdapter.actions().values().stream()
                .noneMatch(action -> action.resourceType().equals(query.getResourceType()))) throw invalid();
        if (query.getOutcome() != null && java.util.Arrays.stream(OperationLogOutcomeEnum.values())
                .noneMatch(outcome -> outcome.getMessage().equals(query.getOutcome()))) throw invalid();
        var filters = new LinkedHashMap<String, String>();
        put(filters, "actionCode", query.getActionCode()); put(filters, "actorId", query.getActorId());
        put(filters, "resourceType", query.getResourceType()); put(filters, "resourceId", query.getResourceId());
        put(filters, "bizCode", query.getBizCode()); put(filters, "namespaceCode", query.getNamespaceCode());
        put(filters, "env", query.getEnv()); put(filters, "appCode", query.getAppCode()); put(filters, "outcome", query.getOutcome());
        return Map.copyOf(filters);
    }
    private static void put(Map<String, String> filters, String key, String value) { if (value != null) filters.put(key, value); }
    private static String filter(String value) {
        if (value == null) return null;
        if (value.chars().anyMatch(character -> character < 32 || character == 127)) throw invalid();
        String result = value.strip();
        if (result.isEmpty()) throw invalid();
        return result;
    }
    private static Instant instant(String value) {
        if (value == null || value.length() > 64 || !OFFSET_TIME.matcher(value).matches()) throw invalid();
        try { return OffsetDateTime.parse(value).toInstant(); }
        catch (DateTimeException invalid) { throw invalid(); }
    }
    private static long positiveId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) throw invalid();
        try { return Long.parseLong(value); }
        catch (NumberFormatException invalid) { throw invalid(); }
    }
    private long trustedTenant() {
        final Long tenant;
        try { tenant = EgonColaTenantIdProvider.currentTenantId(); }
        catch (IllegalStateException missing) { throw invalid(); }
        if (tenant == null || tenant <= 0) throw invalid();
        try { tenants.requireEnabledTenant(tenant); }
        catch (IllegalArgumentException disabled) { throw invalid(); }
        return tenant;
    }
    private OperationLogRepository target() {
        OperationLogRepository target = read(repository::getIfAvailable);
        if (target == null) throw new CommonException(DdcErrorStatus.OPERATION_LOG_UNAVAILABLE);
        return target;
    }
    private <T> T read(Supplier<T> read) {
        try { return read.get(); }
        catch (RuntimeException failure) {
            if (!unavailable(failure)) throw failure;
            log.warn("Operation log target read unavailable; failureType={}", failure.getClass().getSimpleName());
            throw new CommonException(DdcErrorStatus.OPERATION_LOG_UNAVAILABLE, failure);
        }
    }
    private static boolean unavailable(Throwable failure) {
        Set<Throwable> seen = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        for (Throwable cause = failure; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof DataAccessResourceFailureException || cause instanceof TransientDataAccessException
                    || cause instanceof RecoverableDataAccessException || cause instanceof SQLTransientException
                    || cause instanceof SQLRecoverableException || cause instanceof SQLNonTransientConnectionException) return true;
            if (cause instanceof SQLException sql && sql.getSQLState() != null
                    && (sql.getSQLState().startsWith("08") || sql.getSQLState().startsWith("53")
                    || sql.getSQLState().startsWith("57"))) return true;
        }
        return false;
    }
    private static CommonException invalid() { return new CommonException(DdcErrorStatus.INVALID_REQUEST); }
}
