package top.egon.cola.component.tianshu.admin.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import top.egon.cola.component.common.core.pojo.PageQuery;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.tianshu.admin.dao.DdcInstanceMapper;
import top.egon.cola.component.tianshu.admin.model.convertor.DdcInstanceConverterImpl;
import top.egon.cola.component.tianshu.admin.model.po.DdcInstancePO;
import top.egon.cola.component.tianshu.admin.repository.impl.DdcInstanceRepositoryImpl;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DdcInstancePersistenceContractTest {
    private final DdcInstanceMapper mapper = mock(DdcInstanceMapper.class);
    private final DdcInstanceRepositoryImpl repository = new DdcInstanceRepositoryImpl(mapper, new DdcInstanceConverterImpl());
    @BeforeEach void bindContext() {
        EgonColaTenantIdProvider.initialize("tenantId");
        MDC.put("tenantId", "42"); MDC.put("userId", "user:reader");
    }
    @AfterEach void clearContext() { MDC.clear(); }

    @Test void historyReadsAlwaysSupplyTheActualTenantAndPreserveOldValues() {
        DdcInstancePO row = DdcInstancePO.builder().id(7L).tenantId(42L).instanceId("old-instance")
                .status("ONLINE").leaseId("old-lease").build();
        when(mapper.selectByInstanceId(42L, "old-instance")).thenReturn(row);
        var result = repository.findHistoricalByInstanceId("old-instance").orElseThrow();
        assertThat(result.getStatus()).isEqualTo("ONLINE"); assertThat(result.getLeaseId()).isEqualTo("old-lease");
        verify(mapper).selectByInstanceId(42L, "old-instance"); verifyNoMoreInteractions(mapper);
    }

    @Test void historyScopeAndPageExecuteOnlyTenantBoundSelects() {
        when(mapper.selectByScope(42L, "biz", "prod", "app")).thenReturn(List.of());
        assertThat(repository.findHistoricalByScope("biz", "prod", "app")).isEmpty();
        when(mapper.selectPage(42L, "biz", "prod", "app", 10, 10)).thenReturn(List.of());
        when(mapper.countPage(42L, "biz", "prod", "app")).thenReturn(21L);
        var page = repository.findHistoricalByScope("biz", "prod", "app", new PageQuery(2, 10));
        assertThat(page.getTotal()).isEqualTo(21); assertThat(page.getPageNo()).isEqualTo(2);
        verify(mapper).selectByScope(42L, "biz", "prod", "app");
        verify(mapper).selectPage(42L, "biz", "prod", "app", 10, 10);
        verify(mapper).countPage(42L, "biz", "prod", "app"); verifyNoMoreInteractions(mapper);
    }

    @Test void archivePortAndImplementationExposeNoMutationOrInheritedMpCrud() {
        assertThat(Arrays.stream(DdcInstanceRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .containsOnly("findHistoricalByInstanceId", "findHistoricalByScope");
        assertThat(DdcInstanceRepositoryImpl.class.getSuperclass()).isEqualTo(Object.class);
        assertThat(Arrays.stream(DdcInstanceRepositoryImpl.class.getMethods()).map(java.lang.reflect.Method::getName))
                .doesNotContain("save", "update", "updateById", "remove", "removeById", "insert", "markOfflineIfLeaseMatches", "markResourceAdmissionOffline");
    }

    @Test void absentOrTechnicalTenantCannotReadAnotherTenantsArchive() {
        MDC.clear();
        assertThatThrownBy(() -> repository.findHistoricalByInstanceId("instance")).isInstanceOf(IllegalStateException.class);
        MDC.put("tenantId", "0");
        assertThatThrownBy(() -> repository.findHistoricalByScope("biz", "prod", "app"))
                .hasMessage("TENANT_ID_MUST_BE_POSITIVE");
        verifyNoInteractions(mapper);
    }
}
