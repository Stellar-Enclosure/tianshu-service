package top.egon.cola.component.tianshu.admin.service.list;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import java.util.concurrent.TimeUnit;
import java.lang.reflect.Method;
import org.springframework.util.ReflectionUtils;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogEvent;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogRecorder;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogPhaseEnum;
import top.egon.cola.component.common.mybatis.operationlog.OperationLogOutcomeEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.core.excel.XlsxExport;
import top.egon.cola.component.tianshu.admin.model.command.DdcNamespaceExportCommand;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcNamespaceResultConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcNamespaceResult;
import top.egon.cola.component.tianshu.admin.permission.DdcPermissionCodes;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcNamespaceService;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.DataScopeDecision;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.I18nSourceRules;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionOutputProjection;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Bounded synchronous export over the same namespace search and authorized DTO projection. */
@Service("ddcNamespaceExportService")
@Slf4j
@RequiredArgsConstructor(onConstructor_ = @Autowired)
public class DdcNamespaceExportService {
    private static final Method EXPORT_METHOD = ReflectionUtils.findMethod(
            DdcNamespaceExportService.class, "export", DdcNamespaceExportCommand.class);

    private static final String EXPORT_API = "tianshu.api.namespace.export";
    private static final int MAX_ROWS = 50_000;
    private static final long MAX_BYTES = 64L * 1024 * 1024;
    private static final int BATCH_SIZE = 500;

    @Qualifier("ddcNamespaceService") private final DdcNamespaceService namespaces;
    @Qualifier("ddcNamespaceResultConverterImpl") private final DdcNamespaceResultConverter converter;
    @Qualifier("ddcListTemplateService") private final DdcListTemplateService templates;
    @Qualifier("ddcAdminUiI18nService") private final DdcAdminUiI18nService i18n;
    @Qualifier("ddcNamespaceDataScopeGuard") private final DdcNamespaceDataScopeGuard dataScope;
    @Qualifier("operationLogRecorder") private final OperationLogRecorder audit;
    private final PermissionOutputProjection projection;
    private final PermissionExecutionGuard execution;
    @Qualifier("ddcMetadataTransactionTemplate") private final TransactionTemplate auditTransaction;

    public byte[] export(DdcNamespaceExportCommand command) throws IOException {
        long started = System.nanoTime();
        Objects.requireNonNull(command, "export command");
        if (!List.of("FILTERED", "PAGE").contains(command.range()) || command.pageNo() < 1 || command.pageNo() > MAX_ROWS
                || command.pageSize() < 1 || command.pageSize() > PageQuery.MAX_PAGE_SIZE) throw invalid();
        I18nSourceRules.locale(command.locale());
        var initial = execution.require(EXPORT_API).snapshot();
        dataScope.require(initial, true);
        var view = templates.load(DdcNamespaceListDefinition.DEFINITION.pageResourceKey(),
                DdcNamespaceListDefinition.DEFINITION.listKey(), command.catalogVersion(), false);
        verifyView(command, view);
        if (view.visibleColumns().isEmpty()) throw invalid();
        var scope = initial.permissionSnapshot().catalogScope();
        Map<String, String> labels = i18n.bundle(scope.catalogScopeId(), command.catalogVersion(), "tianshu.namespaces", command.locale()).messages();
        var columnMap = view.columns().stream().collect(java.util.stream.Collectors.toMap(column -> column.columnKey(), column -> column));
        List<String> headers = view.visibleColumns().stream().map(key -> {
            var column = columnMap.get(key);
            if (column == null || !column.exportable()) throw invalid();
            return labels.getOrDefault(column.labelKey(), column.labelKey());
        }).toList();
        int pageSize = "FILTERED".equals(command.range()) ? BATCH_SIZE : command.pageSize();
        try {
            return new XlsxExport().export("Namespaces", headers, XlsxExport.Range.valueOf(command.range()), command.pageNo(),
                    new XlsxExport.Limits(pageSize, MAX_ROWS, MAX_BYTES), (page, size) -> {
                        var result = namespaces.page(command.bizCode(), command.keyword(), new PageQuery(page, size));
                        return new XlsxExport.Page<>(result.getRecords(), result.getTotal());
                    }, values -> project(values, view.visibleColumns()), () -> {
                        verifyAuthorization(initial, execution.require(EXPORT_API).snapshot());
                        verifyView(command, templates.load(DdcNamespaceListDefinition.DEFINITION.pageResourceKey(),
                                DdcNamespaceListDefinition.DEFINITION.listKey(), command.catalogVersion(), false));
                    }, count -> audit(initial, command, count, started));
        } catch (XlsxExport.ExportLimitException limit) { throw tooLarge(); }
    }

    private List<List<?>> project(List<top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceBO> batch, List<String> columns) {
        List<DdcNamespaceResult> values = converter.toTargetList(batch);
        JsonNode result = projection.project(EXPORT_API, values);
        if (!result.isArray()) throw new IllegalStateException("NAMESPACE_EXPORT_PROJECTION_INVALID");
        List<List<?>> rows = new ArrayList<>();
        for (JsonNode row : result) {
            if (!row.isObject()) throw new IllegalStateException("NAMESPACE_EXPORT_PROJECTION_INVALID");
            List<Object> cells = new ArrayList<>(columns.size());
            for (String key : columns) {
                JsonNode value = row.get(key);
                if (value == null || value.isNull()) cells.add(null);
                else if (value.isTextual()) cells.add(value.textValue());
                else if (value.isBoolean()) cells.add(value.booleanValue());
                else if (value.isNumber()) cells.add(value.decimalValue());
                else throw new IllegalStateException("NAMESPACE_EXPORT_VALUE_INVALID");
            }
            rows.add(cells);
        }
        return rows;
    }

    private void audit(SystemAuthorizationSnapshot snapshot, DdcNamespaceExportCommand command, int count, long started) {
        var scope = snapshot.permissionSnapshot().catalogScope();
        var details = JsonNodeFactory.instance.objectNode().put("range", command.range()).put("rows", count);
        if (command.templateId() != null) {
            details.put("templateId", command.templateId());
        }
        var event = OperationLogEvent.builder()
                .moduleCode("NAMESPACE").actionCode("TIANSHU_NAMESPACE_EXPORT_GENERATED").resourceType("NAMESPACE")
                .bizCode(scope.businessCode()).appCode(scope.applicationCode()).env(scope.environment())
                .namespaceCode(scope.namespaceCode()).phase(OperationLogPhaseEnum.COMMAND)
                .outcome(OperationLogOutcomeEnum.SUCCEEDED)
                .durationMs(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)).details(details).build();
        auditTransaction.executeWithoutResult(status -> audit.recordInTransaction(event, EXPORT_METHOD));
    }

    private static void verifyView(DdcNamespaceExportCommand command, DdcListTemplateService.View view) {
        if (!Objects.equals(command.definitionDigest(), view.definitionDigest())
                || !Objects.equals(command.templateId(), view.selectedTemplateId())) throw conflict();
        if (command.templateId() == null) {
            if (command.templateVersion() != null) throw conflict();
        } else if (view.templates().stream().noneMatch(template -> command.templateId().equals(template.id())
                && Objects.equals(command.templateVersion(), template.version()))) throw conflict();
    }

    private void verifyAuthorization(SystemAuthorizationSnapshot before, SystemAuthorizationSnapshot after) {
        var oldView = before.permissionSnapshot(); var newView = after.permissionSnapshot();
        if (oldView == null || newView == null || !oldView.issuer().equals(newView.issuer())
                || !oldView.subject().equals(newView.subject()) || !oldView.catalogScope().equals(newView.catalogScope())
                || !oldView.catalogVersion().equals(newView.catalogVersion()) || !oldView.authVersion().equals(newView.authVersion())
                || !oldView.policyVersion().equals(newView.policyVersion()) || !oldView.activeRoleContext().equals(newView.activeRoleContext())
                || !oldView.permissionCodes().equals(newView.permissionCodes()) || !before.resourceCodes().equals(after.resourceCodes())
                || !Objects.equals(before.policyBinding(), after.policyBinding())) throw conflict();
        dataScope.require(after, true);
        for (String permission : List.of(DdcPermissionCodes.READ, DdcPermissionCodes.NAMESPACE_EXPORT)) {
            if (!sameScope(before.dataScopes().get(permission), after.dataScopes().get(permission))) throw conflict();
        }
    }

    private static boolean sameScope(DataScopeDecision left, DataScopeDecision right) {
        if (left == null || right == null) return left == right;
        return left.decision() == right.decision() && left.scopeType().equals(right.scopeType())
                && left.allInTenant() == right.allInTenant() && left.allowedOrgIds().equals(right.allowedOrgIds())
                && left.includeOrgDescendants() == right.includeOrgDescendants() && left.allowedDeptIds().equals(right.allowedDeptIds())
                && left.includeDeptDescendants() == right.includeDeptDescendants() && left.allowedUserIds().equals(right.allowedUserIds())
                && left.includeSelf() == right.includeSelf() && Objects.equals(left.selfUserId(), right.selfUserId())
                && left.directorySnapshotVersion().equals(right.directorySnapshotVersion());
    }

    private static CommonException invalid() { return new CommonException(56401, "TIANSHU_NAMESPACE_EXPORT_INVALID", "Invalid export request"); }
    private static CommonException conflict() { return new CommonException(56404, "TIANSHU_NAMESPACE_EXPORT_CHANGED", "Authorization or template changed; reload before exporting"); }
    private static CommonException tooLarge() { return new CommonException(56412, "TIANSHU_NAMESPACE_EXPORT_TOO_LARGE", "Export exceeds its size limit"); }
}
