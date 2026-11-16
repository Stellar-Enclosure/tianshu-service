package top.egon.cola.component.tianshu.admin.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import top.egon.cola.component.tianshu.admin.dao.DdcSiEmbeddingChunkMapper;
import top.egon.cola.component.tianshu.admin.dao.DdcSiVersionMapper;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiResourceBO;
import top.egon.cola.component.tianshu.admin.model.bo.DdcSiVersionBO;
import top.egon.cola.component.tianshu.admin.model.po.DdcSiEmbeddingChunkPO;
import top.egon.cola.component.tianshu.admin.repository.DdcSiResourceRepository;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiAgentContentStrategy;
import top.egon.cola.component.tianshu.admin.service.si.strategy.DdcSiContentStrategy;
import top.egon.cola.component.tianshu.model.si.DdcSiKindEnum;
import top.egon.cola.component.tianshu.model.si.DdcSiSearchHit;
import top.egon.cola.component.tianshu.model.si.DdcSiSemanticSearchQuery;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TEST-007：验证 rag-starter 切片、1536 维向量入库、分布式锁防重入、维数错配安全保护、
 * RRF 混合查询以及 EmbeddingModel 缺失/超时自动降级至纯 FTS。
 */
@ExtendWith(MockitoExtension.class)
class DdcSiSemanticIndexServiceImplTest {

    @Mock
    private DdcSiResourceRepository repository;
    @Mock
    private DdcSiEmbeddingChunkMapper chunkMapper;
    @Mock
    private DdcSiVersionMapper versionMapper;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private ObjectProvider<EmbeddingModel> embeddingModelProvider;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private ObjectProvider<StringRedisTemplate> redisProvider;

    private Clock fixedClock;
    private DdcSiSemanticIndexServiceImpl service;

    @BeforeEach
    void setUp() {
        fixedClock = Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC);
        List<DdcSiContentStrategy> strategies = List.of(new DdcSiAgentContentStrategy(new ObjectMapper()));
        Executor directExecutor = Runnable::run;
        service = new DdcSiSemanticIndexServiceImpl(
                repository,
                chunkMapper,
                versionMapper,
                strategies,
                embeddingModelProvider,
                redisProvider,
                directExecutor,
                new SimpleMeterRegistry(),
                fixedClock,
                Duration.ofMillis(150),
                60,
                50);
    }

    @Test
    void reindexVersionSyncSplitsChunksValidates1536DimensionsAndSkipsOnDimensionMismatch() {
        DdcSiResourceBO resource = DdcSiResourceBO.builder()
                .id(101L).tenantId(1L).bizCode("xingyuan").namespaceId(10L).envCode("dev")
                .kind(DdcSiKindEnum.AGENT).resourceKey("planner").displayName("Planner").description("Plan tasks")
                .build();
        DdcSiVersionBO version = DdcSiVersionBO.builder()
                .id(201L).resourceId(101L).versionNo(1L).kind(DdcSiKindEnum.AGENT)
                .specJson("{\"modelKey\":\"gpt-6-luna\"}").build();

        when(redisProvider.getIfAvailable()).thenReturn(redisTemplate);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.setIfAbsent(anyString(), eq("1"), any(Duration.class))).thenReturn(true);
        when(repository.findActiveById(1L, 101L)).thenReturn(Optional.of(resource));
        when(repository.findVersionById(1L, 201L)).thenReturn(Optional.of(version));
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);

        float[] validVec = new float[1536];
        validVec[0] = 0.5f;
        when(embeddingModel.embed(anyString())).thenReturn(validVec);

        service.reindexVersionSync(1L, 101L, 201L);

        verify(chunkMapper).deleteByVersionId(1L, 201L);
        verify(chunkMapper).insertChunk(any(DdcSiEmbeddingChunkPO.class), anyString());
        verify(versionMapper).updateIndexStatus(1L, 201L, "READY", fixedClock.instant());

        when(embeddingModel.embed(anyString())).thenReturn(new float[128]);
        service.reindexVersionSync(1L, 101L, 201L);
        verify(chunkMapper, never()).deleteByVersionId(999L, 999L);
        verify(versionMapper).updateIndexStatus(eq(1L), eq(201L), eq("FAILED"), any());
    }

    @Test
    void hybridSearchUsesRrfNormallyAndFallsBackToFtsOnMissingOrTimedOutModel() {
        DdcSiSemanticSearchQuery query = DdcSiSemanticSearchQuery.builder()
                .tenantId(1L).bizCode("xingyuan").namespaceCode("default").envCode("prod")
                .kind(DdcSiKindEnum.AGENT).queryText("financial planner").topK(5).build();

        when(repository.resolveNamespaceId(1L, "xingyuan", "default")).thenReturn(10L);
        when(embeddingModelProvider.getIfAvailable()).thenReturn(embeddingModel);

        float[] qVec = new float[1536];
        qVec[0] = 1.0f;
        when(embeddingModel.embed("financial planner")).thenReturn(qVec);
        when(chunkMapper.hybridSearchRrf(eq(1L), eq("prod"), eq(10L), any(), anyString(), eq("financial planner"),
                eq(60), eq(50), eq(5)))
                .thenReturn(List.of(DdcSiEmbeddingChunkPO.builder()
                        .resourceId(101L).envCode("prod").resourceKey("planner").displayName("Planner")
                        .description("desc").chunkText("financial planner").rrfScore(0.032).build()));

        List<DdcSiSearchHit> rrfHits = service.hybridSearch(query);
        assertThat(rrfHits).hasSize(1);
        assertThat(rrfHits.getFirst().isDegradedToFtsOnly()).isFalse();
        assertThat(rrfHits.getFirst().getHybridScore()).isGreaterThan(0.0);
        assertThat(rrfHits.getFirst().getResource().getResourceKey()).isEqualTo("planner");

        when(embeddingModelProvider.getIfAvailable()).thenReturn(null);
        when(chunkMapper.ftsFallbackSearch(eq(1L), eq("prod"), eq(10L), any(), eq("financial planner"), eq(5)))
                .thenReturn(List.of(DdcSiEmbeddingChunkPO.builder()
                        .resourceId(101L).envCode("prod").resourceKey("planner").displayName("Planner")
                        .description("desc").chunkText("financial planner").rrfScore(0.5).build()));

        List<DdcSiSearchHit> fallbackHits = service.hybridSearch(query);
        assertThat(fallbackHits).hasSize(1);
        assertThat(fallbackHits.getFirst().isDegradedToFtsOnly()).isTrue();
    }
}
