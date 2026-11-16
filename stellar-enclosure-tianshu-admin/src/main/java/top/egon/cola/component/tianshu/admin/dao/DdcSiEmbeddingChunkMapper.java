package top.egon.cola.component.tianshu.admin.dao;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.springframework.validation.annotation.Validated;
import top.egon.cola.component.common.mybatis.business.EgonColaTenantIdProvider;
import top.egon.cola.component.common.mybatis.extension.EgonColaMapper;
import top.egon.cola.component.common.mybatis.model.EgonColaModelValidationGroups;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiKindPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiEmbeddingChunkPO;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** SI 语义分片及 pgvector HNSW + GIN tsvector RRF 混合检索 Mapper。 */
@Mapper
@Validated
public interface DdcSiEmbeddingChunkMapper extends EgonColaMapper<DdcSiEmbeddingChunkPO> {

    @Override
    default DdcSiEmbeddingChunkPO selectActiveById(Serializable id) {
        return selectActiveById(EgonColaTenantIdProvider.currentTenantId(), requireInternalId(id));
    }

    @Override
    default List<DdcSiEmbeddingChunkPO> selectActiveByIds(Collection<? extends Serializable> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        List<Long> internalIds = new ArrayList<>(ids.size());
        ids.forEach(id -> internalIds.add(requireInternalId(id)));
        return selectActiveByIds(EgonColaTenantIdProvider.currentTenantId(), internalIds);
    }

    @Override
    default int restoreVersionedById(DdcSiEmbeddingChunkPO chunk) {
        throw new UnsupportedOperationException("SI_CHUNK_RESTORE_DISABLED");
    }

    @Validated(EgonColaModelValidationGroups.Query.class)
    DdcSiEmbeddingChunkPO selectActiveById(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("id") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long id);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiEmbeddingChunkPO> selectActiveByIds(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("ids") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            Collection<@Positive(groups = EgonColaModelValidationGroups.Query.class) Long> ids);

    int insertChunk(
            @Param("chunk") @NotNull DdcSiEmbeddingChunkPO chunk,
            @Param("embeddingLiteral") @NotBlank String embeddingLiteral);

    int deleteByVersionId(
            @Param("tenantId") @NotNull @Positive Long tenantId,
            @Param("versionId") @NotNull @Positive Long versionId);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiEmbeddingChunkPO> hybridSearchRrf(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("envCode") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 32, groups = EgonColaModelValidationGroups.Query.class) String envCode,
            @Param("namespaceId") Long namespaceId,
            @Param("kind") DdcAiKindPersistenceEnum kind,
            @Param("queryVectorLiteral") @NotBlank(groups = EgonColaModelValidationGroups.Query.class) String queryVectorLiteral,
            @Param("queryText") @NotBlank(groups = EgonColaModelValidationGroups.Query.class) String queryText,
            @Param("rrfK") @Min(value = 1, groups = EgonColaModelValidationGroups.Query.class) int rrfK,
            @Param("candidateLimit") @Min(value = 1, groups = EgonColaModelValidationGroups.Query.class) int candidateLimit,
            @Param("topK") @Min(value = 1, groups = EgonColaModelValidationGroups.Query.class) int topK);

    @Validated(EgonColaModelValidationGroups.Query.class)
    List<DdcSiEmbeddingChunkPO> ftsFallbackSearch(
            @Param("tenantId") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Positive(groups = EgonColaModelValidationGroups.Query.class) Long tenantId,
            @Param("envCode") @NotNull(groups = EgonColaModelValidationGroups.Query.class)
            @Size(max = 32, groups = EgonColaModelValidationGroups.Query.class) String envCode,
            @Param("namespaceId") Long namespaceId,
            @Param("kind") DdcAiKindPersistenceEnum kind,
            @Param("queryText") @NotBlank(groups = EgonColaModelValidationGroups.Query.class) String queryText,
            @Param("topK") @Min(value = 1, groups = EgonColaModelValidationGroups.Query.class) int topK);

    private static Long requireInternalId(Serializable id) {
        if (!(id instanceof Long value) || value <= 0) {
            throw new IllegalArgumentException("ID_MUST_BE_POSITIVE_LONG");
        }
        return value;
    }
}
