package top.egon.cola.component.tianshu.admin.service.i18n;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.transaction.support.TransactionTemplate;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.tianshu.admin.model.bo.DdcCatalogSelectionBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcResourceMetadataProjectionBO;
import top.egon.cola.component.tianshu.admin.model.dto.DdcI18nScope;
import top.egon.cola.component.tianshu.admin.model.dto.DdcResourceMetadataQuery;
import top.egon.cola.component.tianshu.admin.model.vo.DdcPageResult;
import top.egon.cola.component.tianshu.admin.repository.DdcResourceMetadataProjectionRepository;
import top.egon.cola.component.tianshu.admin.security.management.DdcAdminCapability;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static top.egon.cola.component.tianshu.admin.service.i18n.DdcSourceImportTestFixture.*;

class DdcResourceMetadataServiceTest {
    private static final DdcI18nScope PAGE_SCOPE = new DdcI18nScope("trade", "main", "test", "order");
    private final DdcI18nScopeValidator scopes = mock(DdcI18nScopeValidator.class);
    private final DdcCatalogActivationService activations = mock(DdcCatalogActivationService.class);
    private final DdcResourceMetadataProjectionRepository repository = mock(DdcResourceMetadataProjectionRepository.class);
    private final DdcResourceMetadataService service = new DdcResourceMetadataService(scopes, activations, repository, new TransactionTemplate(new DdcMetadataTransactionTestFixture()));
    private final DdcResourceMetadataQuery filter = new DdcResourceMetadataQuery(null, null, null, null, null, null);
    private DdcResourceMetadataProjectionBO row;
    @BeforeEach void setup() {
        MDC.put("tenantId", "101");
        when(activations.resolve(SCOPE, "9")).thenReturn(new DdcCatalogSelectionBO(901L, DdcI18nBundleAssemblerTest.descriptor(),
                List.of(new DdcCatalogSelectionBO.Member("order-admin", "1", request().sourceVersion(), 701L))));
        row = DdcResourceMetadataProjectionBO.builder().tenantId(101L).catalogScopeId(501L).sourceId(701L).producerId("order-admin")
                .sourceVersion(request().sourceVersion()).metadataKind("RESOURCE").resourceType("API").definitionKey("order.read")
                .resourceId(9_007_199_254_740_993L).definitionLifecycle("ACTIVE").pageCode("orders").labelKey("title")
                .targetJson("{\"path\":\"/orders\"}").policyJson("{\"requirement\":{\"mode\":\"ALL\"}}")
                .i18nRefsJson("{\"pageCode\":\"orders\",\"labelKey\":\"title\"}").referencedPermissionCodesJson("[\"order:read\"]").build();
        when(repository.search(eq(501L), eq(901L), any(), any())).thenReturn(DdcPageResult.<DdcResourceMetadataProjectionBO>builder().records(List.of(row)).total(1L).pageNo(1).pageSize(20).build());
    }
    @AfterEach void cleanup() { MDC.clear(); }

    @Test void returnsReadOnlyPolicyAndDecimalIdsFromExactlyTheSelectedProducer() {
        var result = service.search(PAGE_SCOPE, "501", "9", filter, new PageQuery(1, 20)).getRecords().getFirst();
        assertThat(result.resourceId()).isEqualTo("9007199254740993"); assertThat(result.policy()).containsKey("requirement");
        assertThat(result.permissionCodes()).containsExactly("order:read"); assertThat(result.catalogScope()).isEqualTo(SCOPE);
        verify(repository, never()).insertAll(any());
    }
    @Test void aDifferentProducerSourceOrTenantNeverLeaksThroughTheIndex() {
        row.setSourceVersion("a".repeat(64));
        assertThatThrownBy(() -> service.search(PAGE_SCOPE, "501", "9", filter, new PageQuery(1, 20))).isInstanceOf(CommonException.class);
        row.setSourceVersion(request().sourceVersion()).setTenantId(102L);
        assertThatThrownBy(() -> service.search(PAGE_SCOPE, "501", "9", filter, new PageQuery(1, 20))).isInstanceOf(CommonException.class);
    }
    @Test void authorizationAndBoundedFiltersPrecedeProjectionReads() {
        assertThatThrownBy(() -> service.search(PAGE_SCOPE, "501", "9", filter, new PageQuery(1, 101))).isInstanceOf(CommonException.class);
        when(scopes.require(PAGE_SCOPE, DdcAdminCapability.READ)).thenThrow(new CommonException(56407, "denied", "denied"));
        assertThatThrownBy(() -> service.search(PAGE_SCOPE, "501", "9", filter, new PageQuery(1, 20))).isInstanceOf(CommonException.class);
        verifyNoInteractions(activations, repository);
    }

    @Test void oversizedResourcePageIsRejectedAsAWholeBeforeParsingAndCanBeRequestedInSmallerPages() {
        row.setPolicyJson("{\"details\":\"" + "x".repeat(200_000) + "\"}");
        when(repository.search(eq(501L), eq(901L), any(), any())).thenReturn(DdcPageResult.<DdcResourceMetadataProjectionBO>builder()
                .records(java.util.Collections.nCopies(100, row)).total(100L).pageNo(1).pageSize(100).build());
        assertThatThrownBy(() -> service.search(PAGE_SCOPE, "501", "9", filter, new PageQuery(1, 100)))
                .isInstanceOfSatisfying(CommonException.class, error -> assertThat(error.getCode()).isEqualTo(56412));
        verify(repository, never()).insertAll(any());
    }
}
