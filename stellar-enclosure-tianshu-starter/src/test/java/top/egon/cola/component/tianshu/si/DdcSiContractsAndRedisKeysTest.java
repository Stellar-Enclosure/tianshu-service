package top.egon.cola.component.tianshu.si;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiBindingStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiCatalogQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiPublicationEvent;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiReferenceTargetTypeEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSelfRegistrationRequest;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiVersionDescriptor;
import top.egon.cola.component.tianshu.redis.DdcRedisKeys;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DdcSiContractsAndRedisKeysTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void enumsResolveKnownCodesAndRejectUnknown() {
        assertThat(DdcSiKindEnum.fromCode("AGENT")).isEqualTo(DdcSiKindEnum.AGENT);
        assertThat(DdcSiKindEnum.fromCode("MCP")).isEqualTo(DdcSiKindEnum.MCP);
        assertThat(DdcSiKindEnum.fromCode("PROMPT")).isEqualTo(DdcSiKindEnum.PROMPT);
        assertThat(DdcSiKindEnum.fromCode("SKILL")).isEqualTo(DdcSiKindEnum.SKILL);
        assertThatThrownBy(() -> DdcSiKindEnum.fromCode("WORKFLOW"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(DdcSiLifecycleStatusEnum.DRAFT.isQueryableByRuntime()).isFalse();
        assertThat(DdcSiLifecycleStatusEnum.PUBLISHED.isQueryableByRuntime()).isTrue();
        assertThat(DdcSiLifecycleStatusEnum.DEPRECATED.isQueryableByRuntime()).isTrue();
        assertThat(DdcSiLifecycleStatusEnum.ARCHIVED.isQueryableByRuntime()).isFalse();

        assertThat(DdcSiBindingStatusEnum.fromCode("ACTIVE")).isEqualTo(DdcSiBindingStatusEnum.ACTIVE);
        assertThat(DdcSiSourceTypeEnum.fromCode("AUTO_REPORTED")).isEqualTo(DdcSiSourceTypeEnum.AUTO_REPORTED);
        assertThat(DdcSiReferenceTargetTypeEnum.fromCode("TIANJI_PROCESS"))
                .isEqualTo(DdcSiReferenceTargetTypeEnum.TIANJI_PROCESS);
        assertThatThrownBy(() -> DdcSiReferenceTargetTypeEnum.fromCode("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void descriptorAndEventRoundTripUtcInstantIsoZ() throws Exception {
        Instant publishedAt = Instant.parse("2026-11-16T10:15:30.123456Z");
        Instant updatedAt = Instant.parse("2026-11-16T10:20:00Z");

        DdcSiReferenceDescriptor ref = DdcSiReferenceDescriptor.builder()
                .targetType(DdcSiReferenceTargetTypeEnum.MCP)
                .targetKey("mcp.github.tools")
                .pinnedVersionNo(2)
                .required(true)
                .roleHint("tool")
                .build();

        DdcSiResourceDescriptor descriptor = DdcSiResourceDescriptor.builder()
                .tenantId(1001L)
                .bizCode("xingyuan")
                .namespaceCode("default")
                .envCode("prod")
                .resourceId(5001L)
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("agent.support.triage")
                .displayName("Support Triage Agent")
                .description("Routes support inquiries to specialists")
                .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                .activeVersionNo(3)
                .digestSha256("a".repeat(64))
                .labels(Map.of("domain", "support", "tier", "gold"))
                .references(List.of(ref))
                .specJson("{\"model\":\"gpt-6-luna\"}")
                .publishedAt(publishedAt)
                .updatedAt(updatedAt)
                .build();

        String json = objectMapper.writeValueAsString(descriptor);
        assertThat(json).contains("\"kind\":\"AGENT\"");
        assertThat(json).contains("2026-11-16T10:15:30.123456Z");
        assertThat(json).contains("2026-11-16T10:20:00Z");

        DdcSiResourceDescriptor restored = objectMapper.readValue(json, DdcSiResourceDescriptor.class);
        assertThat(restored).isEqualTo(descriptor);

        DdcSiPublicationEvent event = DdcSiPublicationEvent.builder()
                .eventId("evt-001")
                .eventType(DdcSiPublicationEvent.EventType.PUBLISHED)
                .tenantId(1001L)
                .bizCode("xingyuan")
                .namespaceCode("default")
                .envCode("prod")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("agent.support.triage")
                .activeVersionNo(3)
                .catalogRevision(42L)
                .occurredAt(publishedAt)
                .build();

        String eventJson = objectMapper.writeValueAsString(event);
        assertThat(eventJson).contains("2026-11-16T10:15:30.123456Z");
        assertThat(objectMapper.readValue(eventJson, DdcSiPublicationEvent.class)).isEqualTo(event);

        DdcSiSearchHit hit = DdcSiSearchHit.builder()
                .resource(descriptor)
                .hybridScore(0.89d)
                .vectorScore(0.91d)
                .ftsScore(0.75d)
                .matchedChunkText("Routes support inquiries")
                .degradedToFtsOnly(false)
                .rank(1)
                .build();
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(hit), DdcSiSearchHit.class)).isEqualTo(hit);

        DdcSiVersionDescriptor version = DdcSiVersionDescriptor.builder()
                .versionId(99L)
                .resourceId(5001L)
                .versionNo(3)
                .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                .digestSha256("a".repeat(64))
                .specJson("{\"model\":\"gpt-6-luna\"}")
                .changeSummary("Promote to prod")
                .sourceEnvCode("staging")
                .sourceVersionNo(2)
                .publishedBy("alice")
                .publishedAt(publishedAt)
                .build();
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(version), DdcSiVersionDescriptor.class))
                .isEqualTo(version);

        DdcSiBindingDescriptor binding = DdcSiBindingDescriptor.builder()
                .bindingId(77L)
                .tenantId(1001L)
                .bizCode("xingyuan")
                .namespaceCode("default")
                .envCode("prod")
                .appCode("tianji-agent-server")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("agent.support.triage")
                .pinnedVersionNo(3)
                .bindingStatus(DdcSiBindingStatusEnum.ACTIVE)
                .boundBy("alice")
                .updatedAt(updatedAt)
                .build();
        assertThat(objectMapper.readValue(objectMapper.writeValueAsString(binding), DdcSiBindingDescriptor.class))
                .isEqualTo(binding);
    }

    @Test
    void queryAndRegistrationValidationEnforcesBounds() {
        DdcSiCatalogQuery oversizedCatalog = DdcSiCatalogQuery.builder()
                .tenantId(1001L)
                .bizCode("xingyuan")
                .namespaceCode("default")
                .envCode("dev")
                .pageNo(0)
                .pageSize(201)
                .build();
        Set<ConstraintViolation<DdcSiCatalogQuery>> catalogViolations = validator.validate(oversizedCatalog);
        assertThat(catalogViolations).hasSizeGreaterThanOrEqualTo(2);

        DdcSiSemanticSearchQuery badSearch = DdcSiSemanticSearchQuery.builder()
                .tenantId(1001L)
                .bizCode("xingyuan")
                .namespaceCode("default")
                .envCode("dev")
                .queryText("   ")
                .topK(51)
                .build();
        assertThat(validator.validate(badSearch)).hasSizeGreaterThanOrEqualTo(2);

        DdcSiSelfRegistrationRequest badKeyReg = DdcSiSelfRegistrationRequest.builder()
                .tenantId(1001L)
                .bizCode("xingyuan")
                .namespaceCode("default")
                .envCode("dev")
                .serviceName("tianji-agent-server")
                .instanceId("inst-1")
                .kind(DdcSiKindEnum.AGENT)
                .resourceKey("INVALID KEY WITH SPACE")
                .displayName("Agent")
                .specJson("{}")
                .build();
        assertThat(validator.validate(badKeyReg)).isNotEmpty();
    }

    @Test
    void redisKeysGenerateIsolatedTenantKeysAndRejectSeparatorInjection() {
        assertThat(DdcRedisKeys.config(1L, "xingyuan", "dev", "app", "k"))
                .startsWith("tianshu:v4:tenant:1:");

        String verKey1 = DdcRedisKeys.siCatalogVersionKey(1L, "xingyuan", "default", "prod");
        String verKey2 = DdcRedisKeys.siCatalogVersionKey(2L, "xingyuan", "default", "prod");
        assertThat(verKey1)
                .startsWith(DdcRedisKeys.SI_CATALOG_VERSION_PREFIX)
                .isNotEqualTo(verKey2)
                .endsWith(":si:catalog-version");

        String entryKey = DdcRedisKeys.siCatalogEntryKey(
                1L, "xingyuan", "default", "prod", DdcSiKindEnum.AGENT, "agent.triage");
        assertThat(entryKey).contains(":si:entry:AGENT:agent.triage");

        String lockKey = DdcRedisKeys.siEmbeddingLockKey(1L, 888L);
        assertThat(lockKey).startsWith(DdcRedisKeys.SI_EMBEDDING_LOCK_PREFIX).endsWith(":si:embedding-lock:888");

        assertThatThrownBy(() -> DdcRedisKeys.siCatalogVersionKey(0L, "xingyuan", "default", "prod"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DdcRedisKeys.siCatalogEntryKey(
                1L, "xingyuan:bad", "default", "prod", DdcSiKindEnum.MCP, "mcp.one"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DdcRedisKeys.siEmbeddingLockKey(1L, 0L))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
