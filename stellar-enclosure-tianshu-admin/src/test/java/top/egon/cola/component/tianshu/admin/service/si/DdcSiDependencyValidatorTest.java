package top.egon.cola.component.tianshu.admin.service.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import top.egon.cola.component.common.core.exception.CommonException;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiBindingBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiReferenceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * TEST-008：验证 Agent 有向无环图（自环/间接环/深度超限）、目标环境已发布检查，
 * 以及归档前来自上游 Agent 或 Tianji Flowable 流程与活跃绑定的阻断校验。
 */
@ExtendWith(MockitoExtension.class)
class DdcSiDependencyValidatorTest {

    @Mock
    private DdcSiResourceRepository repository;

    private DdcSiDependencyValidator validator;

    @BeforeEach
    void setUp() {
        validator = new DdcSiDependencyValidator(repository, new ObjectMapper());
    }

    @Test
    void rejectsDirectSelfCycleBeforeLockingOrReservingAVersion() {
        assertThatThrownBy(() -> validator.freezePublication(resource(), new DdcSiVersionBO(), null,
                List.of(edge(DdcSiReferenceTargetTypeEnum.AGENT, "agent-a", DdcSiReferenceTargetTypeEnum.AGENT, "agent-a")),
                List.of())).isInstanceOf(CommonException.class).hasMessageContaining("itself");
        org.mockito.Mockito.verifyNoInteractions(repository);
    }

    @Test
    void requiresAllDependenciesToBePublishedInTargetScope() {
        when(repository.findByBusinessKey(1L, "xingyuan", 10L, "prod", "app-a", DdcSiKindEnum.MCP, "github-mcp"))
                .thenReturn(Optional.of(DdcSiResourceBO.builder().id(55L).resourceKey("github-mcp")
                        .lifecycleStatus(DdcSiLifecycleStatusEnum.DRAFT).build()));
        assertThatThrownBy(() -> validator.freezePublication(resource(), new DdcSiVersionBO(), null,
                List.of(edge(DdcSiReferenceTargetTypeEnum.AGENT, "agent-a", DdcSiReferenceTargetTypeEnum.MCP, "github-mcp")),
                List.of())).isInstanceOf(CommonException.class).hasMessageContaining("not published");
    }

    private static DdcSiResourceBO resource() {
        return DdcSiResourceBO.builder().id(100L).tenantId(1L).bizCode("xingyuan").namespaceId(10L)
                .envCode("prod").appCode("app-a").kind(DdcSiKindEnum.AGENT).resourceKey("agent-a").build();
    }

    @Test
    void blocksArchiveWhenInboundTianjiProcessOrActiveAppBindingExists() {
        DdcSiResourceBO resource = DdcSiResourceBO.builder()
                .id(100L)
                .tenantId(1L)
                .envCode("prod")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("approval-agent")
                .build();

        when(repository.findBlockingInboundReferences(1L, "prod", DdcSiKindEnum.AGENT, "approval-agent"))
                .thenReturn(List.of(DdcSiReferenceBO.builder()
                        .sourceType(DdcSiReferenceTargetTypeEnum.TIANJI_PROCESS)
                        .sourceKey("loan-approval-flow")
                        .targetType(DdcSiReferenceTargetTypeEnum.AGENT)
                        .targetKey("approval-agent")
                        .build()));
        when(repository.findBindingsByResourceId(1L, 100L)).thenReturn(List.of(
                DdcSiBindingBO.builder().appCode("tianji-agent-server").status(DdcSiBindingStatusEnum.ACTIVE).build()));

        assertThatThrownBy(() -> validator.ensureSafeToArchive(resource))
                .isInstanceOf(CommonException.class)
                .satisfies(ex -> assertThat(((CommonException) ex).getCode()).isEqualTo(56409))
                .hasMessageContaining("loan-approval-flow")
                .hasMessageContaining("tianji-agent-server");

        when(repository.findBlockingInboundReferences(1L, "prod", DdcSiKindEnum.AGENT, "approval-agent"))
                .thenReturn(List.of());
        when(repository.findBindingsByResourceId(1L, 100L)).thenReturn(List.of());
        assertThatCode(() -> validator.ensureSafeToArchive(resource)).doesNotThrowAnyException();
    }

    private static DdcSiReferenceBO edge(DdcSiReferenceTargetTypeEnum srcType, String srcKey,
                                         DdcSiReferenceTargetTypeEnum targetType, String targetKey) {
        return DdcSiReferenceBO.builder()
                .sourceType(srcType)
                .sourceKey(srcKey)
                .targetType(targetType)
                .targetKey(targetKey)
                .required(true)
                .build();
    }
}
