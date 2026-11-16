package top.egon.cola.component.tianshu.admin.search;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import top.egon.cola.component.rag.chunk.RagChunkingStrategyEnum;
import top.egon.cola.component.rag.chunk.RecursiveRagChunkingStrategy;
import top.egon.cola.component.rag.model.ExtractedDocumentBO;
import top.egon.cola.component.rag.model.RagChunkBO;
import top.egon.cola.component.rag.model.RagChunkingConfigDTO;
import top.egon.cola.component.tianshu.admin.dao.DdcSiEmbeddingChunkMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiVersionMapper;
import top.egon.cola.component.tianshu.admin.dao.typehandler.DdcSiVectorTypeHandler;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.enums.DdcAiKindPersistenceEnum;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiEmbeddingChunkPO;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiLifecycleStatusEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiResourceDescriptor;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;
import top.egon.cola.component.tianshu.model.si.DdcSiSourceTypeEnum;
import top.egon.cola.component.tianshu.redis.DdcRedisKeys;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于 {@code rag-starter} 与 {@code pgvector}（1536 维 HNSW）+ PostgreSQL {@code tsvector}（GIN）
 * 的 RRF 混合检索及自动容灾降级服务实现。
 */
@Slf4j
@Service("ddcSiSemanticIndexService")
public class DdcSiSemanticIndexServiceImpl implements DdcSiSemanticIndexService {

    private static final int EXPECTED_VECTOR_DIM = 1536;
    private static final Duration LOCK_TTL = Duration.ofMinutes(2);
    private static final Duration WARN_LOG_INTERVAL = Duration.ofSeconds(30);

    private final DdcSiResourceRepository repository;
    private final DdcSiEmbeddingChunkMapper chunkMapper;
    private final DdcSiVersionMapper versionMapper;
    private final List<DdcSiContentStrategy> strategies;
    private final ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider;
    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final Executor indexExecutor;
    private final MeterRegistry meterRegistry;
    private final Clock clock;
    private final Duration embeddingTimeout;
    private final int rrfK;
    private final int candidateLimit;
    private final RecursiveRagChunkingStrategy chunkingStrategy = new RecursiveRagChunkingStrategy();
    private final AtomicLong lastDegradedWarnEpochMs = new AtomicLong(0L);

    @Autowired
    public DdcSiSemanticIndexServiceImpl(
            @Qualifier("ddcSiResourceRepository") DdcSiResourceRepository repository,
            @Qualifier("ddcSiEmbeddingChunkMapper") DdcSiEmbeddingChunkMapper chunkMapper,
            @Qualifier("ddcSiVersionMapper") DdcSiVersionMapper versionMapper,
            List<DdcSiContentStrategy> strategies,
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider,
            ObjectProvider<StringRedisTemplate> redisProvider,
            @Qualifier("siIndexTaskExecutor") ObjectProvider<Executor> indexExecutorProvider,
            ObjectProvider<MeterRegistry> meterRegistryProvider,
            @Qualifier("ddcPublishClock") Clock clock) {
        this(repository, chunkMapper, versionMapper, strategies, embeddingModelProvider, redisProvider,
                indexExecutorProvider.getIfAvailable(() -> Runnable::run),
                meterRegistryProvider.getIfAvailable(io.micrometer.core.instrument.simple.SimpleMeterRegistry::new),
                clock, Duration.ofMillis(1500), 60, 50);
    }

    public DdcSiSemanticIndexServiceImpl(
            DdcSiResourceRepository repository,
            DdcSiEmbeddingChunkMapper chunkMapper,
            DdcSiVersionMapper versionMapper,
            List<DdcSiContentStrategy> strategies,
            ObjectProvider<org.springframework.ai.embedding.EmbeddingModel> embeddingModelProvider,
            ObjectProvider<StringRedisTemplate> redisProvider,
            Executor indexExecutor,
            MeterRegistry meterRegistry,
            Clock clock,
            Duration embeddingTimeout,
            int rrfK,
            int candidateLimit) {
        this.repository = repository;
        this.chunkMapper = chunkMapper;
        this.versionMapper = versionMapper;
        this.strategies = strategies;
        this.embeddingModelProvider = embeddingModelProvider;
        this.redisProvider = redisProvider;
        this.indexExecutor = indexExecutor == null ? Runnable::run : indexExecutor;
        this.meterRegistry = meterRegistry;
        this.clock = clock == null ? Clock.systemUTC() : clock;
        this.embeddingTimeout = embeddingTimeout == null ? Duration.ofMillis(1500) : embeddingTimeout;
        this.rrfK = Math.max(1, rrfK);
        this.candidateLimit = Math.max(10, candidateLimit);
    }

    @Override
    public void scheduleReindex(Long tenantId, Long resourceId, Long versionId) {
        if (tenantId == null || resourceId == null || versionId == null) {
            return;
        }
        indexExecutor.execute(() -> reindexVersionSync(tenantId, resourceId, versionId));
    }

    @Override
    public void reindexVersionSync(Long tenantId, Long resourceId, Long versionId) {
        if (!tryAcquireLock(tenantId, versionId)) {
            log.debug("Skipping concurrent SI embedding rebuild versionId={}", versionId);
            return;
        }
        try {
            Optional<DdcSiResourceBO> resOpt = repository.findActiveById(tenantId, resourceId);
            Optional<DdcSiVersionBO> verOpt = repository.findVersionById(tenantId, versionId);
            if (resOpt.isEmpty() || verOpt.isEmpty()) {
                return;
            }
            DdcSiResourceBO resource = resOpt.get();
            DdcSiVersionBO version = verOpt.get();
            DdcSiContentStrategy strategy = selectStrategy(resource.getKind());
            String searchable = strategy.extractSearchableText(
                    resource.getKind(), version.getSpecJson(), resource.getDisplayName(), resource.getDescription());
            repository.updateSearchTsv(tenantId, resourceId, searchable);

            var embeddingModel = embeddingModelProvider.getIfAvailable();
            if (embeddingModel == null || searchable.isBlank()) {
                return;
            }

            List<RagChunkBO> chunks = chunkingStrategy.split(
                    new ExtractedDocumentBO(searchable, resource.getDisplayName(), "text/plain", Map.of()),
                    new RagChunkingConfigDTO(RagChunkingStrategyEnum.RECURSIVE, 512, 64, 1, List.of()));
            List<DdcSiEmbeddingChunkPO> prepared = new java.util.ArrayList<>(chunks.size());
            Instant now = clock.instant();
            long seq = 1L;
            for (RagChunkBO chunk : chunks) {
                float[] vector = embedWithTimeout(embeddingModel, chunk.content());
                if (vector == null || vector.length != EXPECTED_VECTOR_DIM) {
                    throw new IllegalStateException("SI embedding vector dimension mismatch; expected 1536");
                }
                DdcSiEmbeddingChunkPO po = DdcSiEmbeddingChunkPO.builder()
                        .id(now.toEpochMilli() + (seq++))
                        .tenantId(tenantId)
                        .createUserId("system:si-index")
                        .createTime(now)
                        .updateUserId("system:si-index")
                        .updateTime(now)
                        .version(0L)
                        .resourceId(resourceId)
                        .versionId(versionId)
                        .envCode(resource.getEnvCode())
                        .namespaceId(resource.getNamespaceId())
                        .kind(DdcAiKindPersistenceEnum.valueOf(resource.getKind().name()))
                        .chunkIndex(chunk.chunkIndex())
                        .chunkText(chunk.content())
                        .chunkSha256(DdcSiContentStrategy.sha256Hex(chunk.content()))
                        .embedding(vector)
                        .indexedTime(now)
                        .build();
                prepared.add(po);
            }

            chunkMapper.deleteByVersionId(tenantId, versionId);
            for (DdcSiEmbeddingChunkPO chunkPo : prepared) {
                chunkMapper.insertChunk(chunkPo, DdcSiVectorTypeHandler.toLiteral(chunkPo.getEmbedding()));
            }
            versionMapper.updateIndexStatus(tenantId, versionId, "READY", now);
            if (meterRegistry != null) {
                meterRegistry.counter("tianshu.si.index.success").increment();
            }
        } catch (Exception failure) {
            log.warn("SI embedding index rebuild failed tenantId={} resourceId={} versionId={}: {}",
                    tenantId, resourceId, versionId, failure.getMessage());
            versionMapper.updateIndexStatus(tenantId, versionId, "FAILED", clock.instant());
            if (meterRegistry != null) {
                meterRegistry.counter("tianshu.si.index.failed").increment();
            }
        } finally {
            releaseLock(tenantId, versionId);
        }
    }

    @Override
    public List<DdcSiSearchHit> hybridSearch(DdcSiSemanticSearchQuery query) {
        Long namespaceId = query.getNamespaceCode() == null || query.getNamespaceCode().isBlank()
                ? null
                : repository.resolveNamespaceId(query.getTenantId(), query.getBizCode(), query.getNamespaceCode());
        DdcAiKindPersistenceEnum persistedKind = query.getKind() == null
                ? null
                : DdcAiKindPersistenceEnum.valueOf(query.getKind().name());
        int topK = Math.min(Math.max(1, query.getTopK()), 50);

        var embeddingModel = embeddingModelProvider.getIfAvailable();
        if (embeddingModel != null) {
            try {
                float[] queryVector = embedWithTimeout(embeddingModel, query.getQueryText());
                if (queryVector != null && queryVector.length == EXPECTED_VECTOR_DIM) {
                    String vectorLiteral = DdcSiVectorTypeHandler.toLiteral(queryVector);
                    List<DdcSiEmbeddingChunkPO> rows = chunkMapper.hybridSearchRrf(
                            query.getTenantId(), query.getEnvCode(), namespaceId, persistedKind,
                            vectorLiteral, query.getQueryText(), rrfK, candidateLimit, topK);
                    return toHits(query, rows, false);
                }
            } catch (Exception failure) {
                logDegradedWarning(query.getTenantId(), query.getEnvCode(), failure);
            }
        } else {
            logDegradedWarning(query.getTenantId(), query.getEnvCode(), null);
        }

        List<DdcSiEmbeddingChunkPO> fallbackRows = chunkMapper.ftsFallbackSearch(
                query.getTenantId(), query.getEnvCode(), namespaceId, persistedKind, query.getQueryText(), topK);
        if (meterRegistry != null) {
            meterRegistry.counter("tianshu.si.search.degraded_fts").increment();
        }
        return toHits(query, fallbackRows, true);
    }

    private float[] embedWithTimeout(org.springframework.ai.embedding.EmbeddingModel model, String text) throws Exception {
        CompletableFuture<float[]> future = CompletableFuture.supplyAsync(() -> model.embed(text), indexExecutor);
        return future.get(embeddingTimeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    private DdcSiContentStrategy selectStrategy(DdcSiKindEnum kind) {
        return strategies.stream()
                .filter(s -> s.supports(kind))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No SI strategy registered for kind=" + kind));
    }

    private boolean tryAcquireLock(Long tenantId, Long versionId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return true;
        }
        Boolean acquired = redis.opsForValue().setIfAbsent(
                DdcRedisKeys.siEmbeddingLockKey(tenantId, versionId), "1", LOCK_TTL);
        return Boolean.TRUE.equals(acquired);
    }

    private void releaseLock(Long tenantId, Long versionId) {
        StringRedisTemplate redis = redisProvider.getIfAvailable();
        if (redis == null) {
            return;
        }
        try {
            redis.delete(DdcRedisKeys.siEmbeddingLockKey(tenantId, versionId));
        } catch (RuntimeException ignored) {
            // TTL covers lock cleanup
        }
    }

    private void logDegradedWarning(Long tenantId, String envCode, Exception failure) {
        long nowMs = clock.millis();
        long prev = lastDegradedWarnEpochMs.get();
        if (nowMs - prev >= WARN_LOG_INTERVAL.toMillis() && lastDegradedWarnEpochMs.compareAndSet(prev, nowMs)) {
            log.warn("SI semantic search degraded to FTS-only mode tenantId={} env={} reason={}",
                    tenantId, envCode, failure == null ? "EmbeddingModel unavailable" : failure.getMessage());
        }
    }

    private static List<DdcSiSearchHit> toHits(DdcSiSemanticSearchQuery query,
                                               List<DdcSiEmbeddingChunkPO> rows,
                                               boolean degraded) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<DdcSiSearchHit> hits = new ArrayList<>(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            DdcSiEmbeddingChunkPO row = rows.get(i);
            DdcSiKindEnum kind = row.getKind() == null ? DdcSiKindEnum.AGENT : DdcSiKindEnum.valueOf(row.getKind().name());
            double score = row.getRrfScore() == null ? 0.0d : row.getRrfScore();
            DdcSiResourceDescriptor descriptor = DdcSiResourceDescriptor.builder()
                    .tenantId(row.getTenantId() != null ? row.getTenantId() : query.getTenantId())
                    .bizCode(query.getBizCode())
                    .namespaceCode(query.getNamespaceCode())
                    .envCode(row.getEnvCode() != null ? row.getEnvCode() : query.getEnvCode())
                    .resourceId(row.getResourceId())
                    .kind(kind)
                    .resourceKey(row.getResourceKey())
                    .displayName(row.getDisplayName() != null ? row.getDisplayName() : row.getResourceKey())
                    .description(row.getDescription())
                    .status(DdcSiLifecycleStatusEnum.PUBLISHED)
                    .sourceType(DdcSiSourceTypeEnum.CONSOLE)
                    .build();
            hits.add(DdcSiSearchHit.builder()
                    .resource(descriptor)
                    .hybridScore(score)
                    .vectorScore(degraded ? 0.0d : score)
                    .ftsScore(score)
                    .matchedChunkText(row.getChunkText() != null ? row.getChunkText() : row.getDescription())
                    .degradedToFtsOnly(degraded)
                    .rank(i + 1)
                    .build());
        }
        return hits;
    }
}
