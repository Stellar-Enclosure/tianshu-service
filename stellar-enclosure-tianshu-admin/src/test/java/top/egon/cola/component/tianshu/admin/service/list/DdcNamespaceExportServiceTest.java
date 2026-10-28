package top.egon.cola.component.tianshu.admin.service.list;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.bo.DdcNamespaceBO;
import top.egon.cola.component.tianshu.admin.model.command.DdcNamespaceExportCommand;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcNamespaceResultConverter;
import top.egon.cola.component.tianshu.admin.model.vo.DdcI18nBundleResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcNamespaceResult;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.service.i18n.DdcAdminUiI18nService;
import top.egon.cola.component.tianshu.admin.service.metadata.DdcNamespaceService;
import top.egon.cola.component.tianshu.admin.repository.DdcOperationLogRepository;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.DataScopeDecision;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.Decision;
import top.egon.cola.platform.tianquan.jianshen.contract.authorization.SystemAuthorizationSnapshot;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.CatalogScope;
import top.egon.cola.platform.tianquan.jianshen.contract.permission.EffectivePermissionSnapshot;
import top.egon.cola.platform.tianquan.jianshen.starter.field.PermissionOutputProjection;
import top.egon.cola.platform.tianquan.jianshen.starter.permission.PermissionExecutionGuard;
import top.egon.cola.platform.tianquan.jianshen.starter.security.CurrentRbac3User;
import top.egon.cola.platform.tianquan.jianshen.starter.security.Rbac3UserDetails;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DdcNamespaceExportServiceTest {
    private final DdcNamespaceService namespaces = mock(DdcNamespaceService.class);
    private final DdcNamespaceResultConverter converter = mock(DdcNamespaceResultConverter.class);
    private final DdcListTemplateService templates = mock(DdcListTemplateService.class);
    private final DdcAdminUiI18nService i18n = mock(DdcAdminUiI18nService.class);
    private final PermissionOutputProjection projection = mock(PermissionOutputProjection.class);
    private final PermissionExecutionGuard execution = mock(PermissionExecutionGuard.class);
    private final CurrentRbac3User users = mock(CurrentRbac3User.class);
    private final DdcOperationLogRepository audit = mock(DdcOperationLogRepository.class);
    private final DdcNamespaceDataScopeGuard dataScope = new DdcNamespaceDataScopeGuard(users);
    private final DdcNamespaceExportService service = new DdcNamespaceExportService(namespaces, converter, templates, i18n, dataScope, audit, projection, execution);
    private final Rbac3UserDetails user = mock(Rbac3UserDetails.class);
    private final SystemAuthorizationSnapshot snapshot = mock(SystemAuthorizationSnapshot.class);
    private final String digest = DdcNamespaceListDefinition.DEFINITION.digest();
    private final DdcNamespaceExportCommand command = new DdcNamespaceExportCommand("trade", "needle", "FILTERED", 2, 10,
            "zh-CN", "1", digest, null, null);

    @BeforeEach void setup() throws Exception {
        var scope = new CatalogScope("10", "20", "xingyuan", "tianshu", "default", "test");
        var view = new EffectivePermissionSnapshot("https://identity.example/tenants/20", "alice", scope, "1", "2", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("tianshu", List.of("7"), List.of("7")),
                List.of("TIANSHU_NAMESPACE_EXPORT", "TIANSHU_READ"), Instant.now().plusSeconds(60));
        when(snapshot.permissionSnapshot()).thenReturn(view);
        when(snapshot.dataScopes()).thenReturn(Map.of());
        when(user.snapshot()).thenReturn(snapshot);
        when(execution.require("tianshu.api.namespace.export")).thenReturn(user);
        when(users.require()).thenReturn(user);
        var columns = DdcNamespaceListDefinition.DEFINITION.columns();
        when(templates.load(anyString(), anyString(), eq("1"), eq(false))).thenReturn(
                new DdcListTemplateService.View(digest, "1", columns, List.of(), null, List.of("bizCode", "description"), false, false));
        when(i18n.bundle("10", "1", "tianshu.namespaces", "zh-CN")).thenReturn(
                new DdcI18nBundleResult("tianshu.namespaces", "zh-CN", "zh-CN", "v1", Map.of("fields.bizCode", "业务域", "common.description", "描述")));
        var row = new DdcNamespaceBO();
        when(namespaces.page(eq("trade"), eq("needle"), any(PageQuery.class))).thenReturn(
                DdcPageResult.<DdcNamespaceBO>builder().records(List.of(row)).total(1L).pageNo(1).pageSize(500).build());
        when(converter.toTargetList(anyList())).thenReturn(List.of(new DdcNamespaceResult()));
        when(projection.project(eq("tianshu.api.namespace.export"), any())).thenReturn(
                new ObjectMapper().readTree("[{\"bizCode\":\"trade\",\"description\":\"***\",\"namespace\":\"private\"}]"));
    }

    @Test void exportsOnlyTheVisibleAuthorizedProjectionWithTheSubmittedFilters() throws Exception {
        byte[] bytes = service.export(command);
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("业务域");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("描述");
            assertThat(sheet.getRow(1).getCell(0).getStringCellValue()).isEqualTo("trade");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("***");
            assertThat(sheet.getRow(1).getLastCellNum()).isEqualTo((short) 2);
        }
        verify(namespaces).page("trade", "needle", new PageQuery(1, 500));
    }

    @Test void aChangedAuthorizationViewStopsTheFileBeforeReturn() {
        var changed = mock(SystemAuthorizationSnapshot.class);
        var newView = new EffectivePermissionSnapshot("https://identity.example/tenants/20", "alice",
                new CatalogScope("10", "20", "xingyuan", "tianshu", "default", "test"), "1", "9", "3",
                new EffectivePermissionSnapshot.ActiveRoleContext("tianshu", List.of("7"), List.of("7")),
                List.of("TIANSHU_NAMESPACE_EXPORT", "TIANSHU_READ"), Instant.now().plusSeconds(60));
        var changedUser = mock(Rbac3UserDetails.class);
        when(changed.permissionSnapshot()).thenReturn(newView);
        when(changedUser.snapshot()).thenReturn(changed);
        when(execution.require("tianshu.api.namespace.export")).thenReturn(user, user, changedUser);
        assertThatThrownBy(() -> service.export(command)).isInstanceOfSatisfying(CommonException.class,
                error -> assertThat(error.getCode()).isEqualTo(56404));
    }

    @Test void omittedRangeDefaultsToAllFilteredResults() throws Exception {
        var defaultRange = new DdcNamespaceExportCommand("trade", "needle", null, 2, 10,
                "zh-CN", "1", digest, null, null);
        service.export(defaultRange);
        verify(namespaces).page("trade", "needle", new PageQuery(1, 500));
        verify(audit).insert(argThat(log -> log.getOperationContent().contains("range=FILTERED")));
    }

    @Test void aConfiguredRestrictedDataScopeFailsClosedBeforeQuerying() {
        var restricted = new DataScopeDecision(Decision.ALLOW, "ALLOW", "20", "alice", "TIANSHU_READ", "SELF", false,
                Set.of(), false, Set.of(), false, Set.of("alice"), true, "alice", "1", 1, 2, 3, List.of(), Instant.now());
        when(snapshot.dataScopes()).thenReturn(Map.of("TIANSHU_READ", restricted));
        assertThatThrownBy(() -> service.export(command)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(namespaces);
    }
}
