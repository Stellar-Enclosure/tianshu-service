# Tianshu SI Governance & Cross-Repository Implementation Plan

| Field | Value |
| --- | --- |
| Document | `2026-11-16-11-24-tianshu-si-resource-governance-implementation.md` |
| Template Version | `6` |
| Status | `Review` |
| Created | `2026-11-16 11:24 -04:00` |
| Updated | `2026-11-16 11:40 -04:00` |
| Owner | `Antigravity / Architecture Owner` |
| Repository | `tianshu-service`, `Stellar-Enclosure-Components`, `tianji-service`, `tianshu-web` |
| Scope | `SI governance (AGENT, MCP, PROMPT, SKILL), pgvector RRF hybrid search, cross-repo Flowable & Agentic runtime integration, React admin UI` |
| Source Requirement | `TIANSHU-SI-SPEC-001 (Single-phase full-stack SI resource registry, versioned distribution, binding, semantic search & Flowable/Agentic split)` |
| Baseline Revision | `tianshu-service @ b1c607f`, `Stellar-Enclosure-Components @ cf66934`, `tianji-service @ e5d4551`, `tianshu-web @ b88f5f5` |
| Implements Spec | [Tianshu SI Resource Governance Spec](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) |
| Spec Status | `Review` |
| Spec Revision | `2026-11-16 10:35 -04:00 (tianshu-service @ b1c607f)` |
| Effective Specs | [Tianshu SI Resource Governance Spec](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) |
| Depends On Plans | `None` |
| Supersedes | `None` |
| Superseded By | `None` |
| Related Plans | `None` |

## 1. Summary

本实施计划严格落实主规格 [Tianshu SI Resource Governance Spec](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md)（`TIANSHU-SI-SPEC-001`），采用单期完整交付方式，在 `tianshu-service`、`Stellar-Enclosure-Components`、`tianshu-web` 与 `tianji-service` 四个仓库中构建超级智能（SI：`AGENT`、`MCP`、`PROMPT`、`SKILL`）资源注册治理体系与跨仓协同闭环。依赖链遵循由底向上的严格顺序：`tianshu-starter` -> `rpc-tianshu-adapter` 与 `agent-flow-starter` -> `tianshu-admin`（DB 迁移、领域服务与检索、HTTP/gRPC 开放与三档配置）-> `tianshu-ai-starter` -> `tianshu-web` 与 `tianji-service`。全计划划界为 9 个互不重叠、逐级独立可验证与提交的 Step，配备完整单测、真实 PostgreSQL/`pgvector` 持久化集成测、gRPC 鉴权测试、前端双语/Vitest 门禁以及天机流程激活与模板注入测试以证明最终可上线交付。

## 2. Target Spec and Effective Design

### 2.1 Primary target

- Path: [2026-11-16-10-35-tianshu-si-resource-governance-spec.md](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md)
- Status: `Review`
- Revision: `2026-11-16 10:35 -04:00 (tianshu-service @ b1c607f; Stellar-Enclosure-Components @ cf66934; tianji-service @ e5d4551)`
- Approval evidence: 用户确认按单期全功能交付主规格，并授权出具 EGON Template Version 6 全文件实现计划以同步提交最终审阅。

### 2.2 Effective Spec set

| Role | Spec/link | Status/revision | Effective sections | Why included |
| --- | --- | --- | --- | --- |
| Primary | [2026-11-16-10-35-tianshu-si-resource-governance-spec.md](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | `Review / 2026-11-16 10:35 -04:00` | All sections (§1–§20) | 唯一有效的单期完整超级智能资源治理与跨仓架构规范 |

### 2.3 Superseded or excluded content

None。主规格已统一一期/二期功能范围，无其余待替代或排除文档。

## 3. Effective Requirements and Acceptance

| Requirement | Source Spec section | Effective statement | Observable acceptance | Implementation impact |
| --- | --- | --- | --- | --- |
| `REQ-001` | [Spec §4/§5/§9](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | 天枢统一抽象 `AGENT`、`MCP`、`PROMPT`、`SKILL` 四类原子逻辑资源并管理定义、目录及依赖，保持旧 `PROMPT`/`SKILL` 全兼容 | 新增 5 张 SI 表就绪；旧 `DdcAiResourceController` 及 `DdcAiEnumConverter` 回归全通 | `tianshu-starter`, `rpc-tianshu-adapter`, `agent-flow-starter`, `tianshu-admin`, `tianshu-ai-starter`, `tianji-service` |
| `REQ-002` | [Spec §6.2](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | 为 `AGENT`（模型、运行时策略、引用及 IO JSON Schema，禁含流程节点）与 `MCP`（协议、`transport`、`endpoint`、禁明文凭据）提供结构校验与 SHA-256 | 含流程节点或明文密码载荷遭拒绝并返回 HTTP `422`；规范化生成固定摘要 | `tianshu-starter`, `tianshu-admin`, `tianshu-web` |
| `REQ-003` | [Spec §5/§7/§9](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | 覆盖 `DRAFT`/`PUBLISHED`/`DEPRECATED`/`ARCHIVED` 状态与不可变快照、`@Version` 乐观锁及 Outbox `EVENT-001` 分发 | 并发冲突报 HTTP `409`；已发布版本永久只读并写库 Outbox | `tianshu-starter`, `tianshu-admin`, `tianshu-ai-starter`, `tianshu-web` |
| `REQ-004` | [Spec §5/§7/§9](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | 强制 `tenantId` + `bizCode` + `namespaceId` + `envCode` 隔离，支持按 `appCode` 锁定绑定版本并按显式版本 -> 绑定 -> 活跃版优先级解析 | 同资源不同 `appCode` 解析各自分配版；跨租户挂载因复合外键直接报错 | `tianshu-starter`, `rpc-tianshu-adapter`, `tianshu-admin`, `tianshu-web`, `tianji-service` |
| `REQ-005` | [Spec §6.2/§9.1](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | 支持受控环境晋级（含溯源字段与依赖就位检查）与同环境原子回滚活跃指针 | 目标缺依赖返 `422`；回滚不改动既有不可变历史快照 | `tianshu-starter`, `tianshu-admin`, `tianshu-web` |
| `REQ-006` | [Spec §9.2/§10](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | 提供 gRPC 分发、`RPC-003` 幂等自上报（不覆盖人工已发布 `CONSOLE` 资源）与客户端 Redis 版本防抖热缓存 | 重复指纹零新增版本；事件防抖合并并发通知 | `tianshu-starter`, `rpc-tianshu-adapter`, `tianshu-admin`, `tianshu-ai-starter`, `tianji-service` |
| `REQ-007` | [Spec §6.1/§7/§9](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | 基于 `rag-starter` 与 `pgvector` HNSW（1536 维）+ `tsvector` GIN 实现 RRF（`k=60`）混合搜索及 `EmbeddingModel` 故障降级纯全文检索 | PG 实库验证 HNSW/RRF 排序；Embedding 超时降级返回 `degradedToFtsOnly = true` | `tianshu-starter`, `rpc-tianshu-adapter`, `tianshu-admin`, `tianshu-ai-starter`, `tianshu-web` |
| `REQ-008` | [Spec §4/§9.4](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | 严守三元架构职责：`agent-flow-starter` 弃用静态编排；天枢检验 Agent 引用无环；天机激活校验并登记 `TIANJI_PROCESS` 引用拦截归档 | 环路拒发 `422`；缺少已发布 Agent 拦截激活；在用资源拒归档 `409` | `Stellar-Enclosure-Components`, `tianshu-admin`, `tianji-service` |
| `REQ-009` | [Spec §12](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | `tianshu-web` 提供目录检索、矩阵晋级、结构化编辑器、绑定及引用视图与 `zh-CN`/`en-US` 双语权限控制台 | `check-i18n.mjs`、`tsc`、Vitest 全测试及 Vite 构建全绿 | `tianshu-admin`, `tianshu-web` |

## 4. Implementation Strategy and Dependency Order

### 4.1 Ordered strategy

实施按单向拓扑递进：首先（Step 1）落地 `tianshu-starter` 跨仓契约、枚举与 Redis Key 并安装到 `.m2`；紧随（Step 2 & Step 3）升级组件仓 `rpc-tianshu-adapter`（生成 `.proto` stub/客户端）与 `agent-flow-starter`（标注 `@Deprecated` 静态策略类并保护 Agentic 执行）并安装 `.m2`；随后在 `tianshu-admin` 分三步推进：先（Step 4）落 POM 白名单依赖、`V20261116_001`、摘要清单、`DdcSiVectorTypeHandler`、PO 与 Mapper 并在真 PG/`pgvector` 跑通；再（Step 5）落 BO、MapStruct、Repository、Strategy、DAG、Outbox 与 RRF 搜索；再（Step 6）开放 14 HTTP + 3 gRPC + 权限与三档案配置；之后（Step 7）交付 `tianshu-ai-starter` 本地防抖热缓存与自注册运行器；最后并行或接续完成前端 `tianshu-web`（Step 8）与天机整合端 `tianji-service`（Step 9）。

### 4.2 Test-first strategy

全部 9 个 Step 均从各自 `File 1` RED 测试文件启程，先用测试固定编译符号、边界状态码、安全越权拒绝、时间格式及交互文本期待，确认因缺失生产类型或行为红灯，随后编写最小合规绿色实现与自动装配衔接代码：
- Step 1：`DdcSiContractsAndRedisKeysTest` -> 枚举/DTO/Port/`DdcRedisKeys`。
- Step 2：`DdcSiProtoMapperTest`, `DdcSiCatalogRpcClientTest` -> `.proto`/`DdcRpcOperation`/`DdcSiProtoMapper`/`DdcSiCatalogRpcClient`/`DdcRpcAutoConfiguration`。
- Step 3：`AgentFlowAgenticBoundaryTest` -> `AgentWorkflowBuilderStrategy`/`AgentWorkflowStrategyFactory`/`AgentFlowAutoConfiguration`。
- Step 4：`DdcSiPersistenceIT` -> `pom.xml`/`V20261116_001`/`repository-manifest.json`/Persistence Enums/`DdcSiVectorTypeHandler`/5 POs/5 Mappers/5 XMLs。
- Step 5：4 个核心业务/策略/索引单测 -> 6 BOs/2 Converters/`DdcSiResourceRepository`+Impl/4 Strategies/Validator/TransitionPolicy/Notifier/IndexService/ServiceImpl。
- Step 6：`DdcSiResourceControllerIT`, `DdcSiResourceRpcProviderIT` -> `DdcPermissionCodes`/6 Commands & Queries/6 VOs/`DdcSiCqeConverter`/Controller/Advice/Provider/Properties/Config/3 YAMLs。
- Step 7：`DdcSiCatalogSyncCoordinatorTest`, `DdcSiSelfRegistrationRunnerTest` -> ClientProperties/CatalogCache/SyncCoordinator/Listener/Contributor/Runner/AutoConfig。
- Step 8：3 个 RTL 测试文件 -> `types.ts`/`si.ts`/`permissions.ts`/`resources.ts`/6 SI pages & panels/`AiResourcesPage.tsx`/`App.tsx`/`i18nCatalogs.json`。
- Step 9：`TianshuAgentBindingVerifierTest`, `TianshuTemplateResolverTest` -> 2 POMs/`TianshuAgentBindingVerifier`/`AutomationActivationService`/`TianshuTemplateResolver`/`TianjiAgentRuntimeAutoConfiguration`/2 YAMLs。

### 4.3 Sequential and parallel boundaries

| Step | Depends on | May run in parallel with | Must not overlap with | Reason |
| --- | --- | --- | --- | --- |
| Step 1 | None | Step 3 | Any `tianshu-starter` edit | 跨四仓源契约须先 `install` 才可展开 Step 2/4/7/9 |
| Step 2 | Step 1 | Step 3 | Step 6/7/9 | gRPC 生成 Stub 与适配客户端须就绪 |
| Step 3 | None | Step 1, Step 2 | Step 9 | `agent-flow-starter` 静态工作流弃用不阻塞前半程，须早于天机整合 install |
| Step 4 | Step 1 | None | Step 5/6 | `tianshu-admin/pom.xml` 与 `V20261116_001` DDL 先行验收 |
| Step 5 | Step 2, Step 4 | None | Step 4/6 | 依赖真实 PO/Mapper 与 BOM 托管之 `rag-starter` |
| Step 6 | Step 2, Step 5 | None | Step 4/5/7/8 | 暴露完备 HTTP 与 RPC 服务端 |
| Step 7 | Step 1, Step 2, Step 6 | Step 8 | Step 9 | 构建客户端缓存协同包并 `install` |
| Step 8 | Step 6 | Step 7 | Any `tianshu-web` edit | 按定稿 REST CQE 契约与权限交付纯前端 |
| Step 9 | Step 1, Step 2, Step 3, Step 6, Step 7 | None | None | 汇拢全链路制品验证天机编排整合 |

### 4.4 Commit boundaries

每个 Step 均严控在单一仓库且唯一 `Commit paths` 列表中完成独立语义提交（9 个独立提交：`tianshu-service` 5 次、`Stellar-Enclosure-Components` 2 次、`tianshu-web` 1 次、`tianji-service` 1 次），确保任一仓库未触及非 Step 拥有路径（如 `Stellar-Enclosure-Components/docs/egon` 既有无关 `outbox` 文档）。

### 4.5 Spec Simplicity and Implementation-necessity Audit

| Spec element | Spec necessity verdict/section | Current repository evidence | Direct/reuse alternative | Interaction/implementation cost | Plan decision |
| --- | --- | --- | --- | --- | --- |
| 四态 SI 治理主从五表 | Essential / Spec §5 & §7 | `ddc_ai_resource_t0` 缺向量切片、应用绑定与引用边 | 直接挂载现有租户与命名空间表复合外键，并行回填旧 `PROMPT`/`SKILL` | 5 张增量表 + `ON CONFLICT DO NOTHING` 初始化，保留旧接口无损 | Implement |
| 纯 JDBC `DdcSiVectorTypeHandler` | Essential / Spec §6.1 | `yuheng-service/.../GatewayVectorTypeHandler.java` 已证明纯 JDBC `Types.OTHER` 稳定 | 复用工程既有方案，拒引非 BOM 管控三方 `pgvector-java` JAR | 1 个零外依轻量句柄类 | Implement |
| `rag-starter` + RRF（`k=60`）+ FTS 降级 | Essential / Spec §6.1 | 组件仓 `stellar-enclosure-component-rag-starter` 已在 BOM 中 | 直接复用分片器及 Spring AI `EmbeddingModel`，只补库内 CTE RRF SQL | 1 个检索索引实现 + 原生降级 SQL | Implement |
| 独立新建 `DdcSiEnumConverter` | Essential / Spec §8 & §6.3 | 现有 `DdcAiEnumConverter.java` 对未知类型必抛 `THROW_EXCEPTION` | 保留旧转换器彻底冻结，独立建新接口映射四态与 SI 枚举 | 1 个小型 MapStruct 接口彻底保底旧业务 | Implement |
| `TianshuAgentBindingVerifier` & `TianshuTemplateResolver` | Essential / Spec §4 & §9.4 | `AutomationActivationService` 与 `tianji-agent-runtime` 缺直连核验装配 | 直调 `DdcSiCatalogClient` 门面，零多余代理或桥接微服 | 2 个小型防腐适配器 | Implement |

### 4.6 Change-unit Dependency Matrix

| Change unit | Requirements | Proof/RED point | Compile/runtime prerequisites | Produces | Consumers/unblocks | Owning Step |
| --- | --- | --- | --- | --- | --- | --- |
| SI Enums, DTOs, Client Port & Redis Keys | `REQ-001`..`REQ-008` | `DdcSiContractsAndRedisKeysTest` | None | Installed `tianshu-starter` jar | Steps 2, 4, 5, 6, 7, 9 | Step 1 |
| `tianshu_si_resource.proto`, Mapper & RPC Client | `REQ-001`, `REQ-004`, `REQ-006`, `REQ-007` | `DdcSiProtoMapperTest`, `DdcSiCatalogRpcClientTest` | Step 1 | Protobuf Stub & `DdcSiCatalogRpcClient` | Steps 6, 7, 9 | Step 2 |
| `@Deprecated` Agent-Flow static strategies & flag | `REQ-001`, `REQ-008` | `AgentFlowAgenticBoundaryTest` | None | `legacy-static-workflow.enabled` gate | Step 9 | Step 3 |
| DDL `V20261116_001`, Manifest, POM & Mappers | `REQ-001`..`REQ-008` | `DdcSiPersistenceIT` (`TEST-005`) | Step 1 + PG `pgvector` | 5 SI tables, vector handler & Mappers | Steps 5, 6 | Step 4 |
| Domain BOs, Strategies, DAG, Outbox & RRF Service | `REQ-001`..`REQ-008` | 4 domain unit suites | Step 2, Step 4 | `DdcSiResourceService`, RRF index service | Step 6 | Step 5 |
| REST API-001..014, RPC Provider, RBAC & YAML | `REQ-001`..`REQ-009` | `DdcSiResourceControllerIT`, `DdcSiResourceRpcProviderIT` | Step 2, Step 5 | HTTP/gRPC endpoints & 3 profiles | Steps 7, 8, 9 | Step 6 |
| Local Snapshot Cache, Debounce & Self-Registration | `REQ-001`, `REQ-003`, `REQ-006` | `DdcSiCatalogSyncCoordinatorTest`, `DdcSiSelfRegistrationRunnerTest` | Steps 1, 2, 6 | Installed `tianshu-ai-starter` SI client | Step 9 | Step 7 |
| Console Catalog, Matrix, Editor, Bindings & i18n | `REQ-001`..`REQ-009` | RTL tests + `check-i18n.mjs` + `tsc` + `build` | Step 6 | Complete `tianshu-web` SI console | End-user web review | Step 8 |
| Tianji Pre-activation Verifier & Template Resolver | `REQ-001`, `REQ-004`, `REQ-006`, `REQ-008` | `TianshuAgentBindingVerifierTest`, `TianshuTemplateResolverTest` | Steps 1, 2, 3, 6, 7 | Cross-repo runtime integration | System delivery | Step 9 |

### 4.7 Java, Spring, and Egon-COLA Implementation Standards

| Concern | Current repository evidence | Effective Spec decision | Planned implementation consequence | Owning Steps/checks |
| --- | --- | --- | --- | --- |
| Architecture profile | `tianshu-admin`（`controller`/`service`/`repository`/`dao`/`model`/`config`）与 `tianji-*` 均为传统分层，非原生 CLI 生成工程 | Spec §8 确定延续传统分层结构 | 严格保真各仓既有分层包定位，严禁套用原生生成器模板造成混合结构 | Every Step; `MC-ARCH-001` |
| Reuse/capability | `rag-starter`, `spring-ai-model`, `rpc-tianshu-adapter`, `tianshu-admin` Outbox, `GatewayVectorTypeHandler` | Spec §6.1 & §8 选择复用现有组件与纯 JDBC 策略 | 仅增白名单 BOM 依赖及 `tianji-*` 直连 `tianshu-starter` | Steps 2, 4, 5, 7, 9; `MC-REUSE-001`, `MC-DEP-001` |
| Naming/model/validation/conversion | 现有平铺 `model/bo/`, `model/command/`, `model/query/`, `model/vo/`、`DdcAiResourceRepository` `@Validated`、`DdcAiEnumConverter` | Spec §8/§9 制定 `DdcSi*` 系列名称 | 严格匹配后缀；新建独立 `DdcSiEnumConverter`, `DdcSiResourceConverter`, `DdcSiCqeConverter`, `DdcSiProtoMapper` 彻底隔离旧转换器 | Steps 1, 2, 4, 5, 6; `MC-NAME-001`, `MC-VALID-001`, `MC-MODEL-001`, `MC-CONVERT-001` |
| Bean/logging/util/JSON/time/config | `lombok.config` 仅复制 `@Qualifier`；`DdcAdminUiI18nProperties` 模式；三环境 `application*.yml` | Spec §6.2 & §15 规范 `Instant`, `Duration`, `egon.cola.component.tianshu.si` | 采用 `@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`, `@Qualifier`, `@ConfigurationProperties`（零构造 `@Value`） | Steps 1..7, 9; `MC-LOG-001`, `MC-BEAN-001`, `MC-UTIL-001`, `MC-JSON-001`, `MC-TIME-001`, `MC-CONFIG-001` |
| Business variation/pattern | 四态载荷异构校验、环境迁移白名单、事务发布 + 异步切片、防抖刷新 | Spec §6.2 定义采用策略表、状态矩阵与 Outbox + `AFTER_COMMIT` | `DdcSiContentStrategy` 消除臃肿条件链；异步索引剥离短 DB 事务外执行 | Steps 5, 7; `MC-PATTERN-001` |

#### Capability reuse ledger

| Need | Candidates inspected | Exact evidence | Fit/gap | Decision | Added dependency/custom code | Owning Step/check |
| --- | --- | --- | --- | --- | --- | --- |
| 分块切分与向量 Model 接口 | `rag-starter`, `spring-ai-model` | Components 父 POM/BOM 已管理版本 | 缺天枢作用域混合 RRF 查询 | Approved addition | `tianshu-admin/pom.xml` 加 BOM 依赖 + `DdcSiSemanticIndexServiceImpl` | Steps 4, 5; `MC-DEP-001`, `MC-REUSE-001` |
| `pgvector` SQL 读写入 | `pgvector-java` vs 纯 JDBC | `yuheng-service/GatewayVectorTypeHandler.java` | 纯 JDBC 零外依满足 1536 维校验读写 | Reuse pattern | 新建 `DdcSiVectorTypeHandler` | Step 4; `MC-REUSE-001`, `MC-UTIL-001` |
| gRPC 通道与签名校验 | `rpc-tianshu-adapter` 现存机制 | `DdcRpcAutoConfiguration.java`, `DdcRpcOperation.java` | 现成拦截器和连接管理可直用，仅补 SI Proto/Client | Reuse + extend | 新增 `tianshu_si_resource.proto`, `DdcSiProtoMapper`, `DdcSiCatalogRpcClient` | Step 2; `MC-REUSE-001` |
| 事务性事件派发 | `tianshu-admin` 现有 Outbox 模块 | `tianshu-admin` 内既有 Outbox 表 | 完整保证最终一致，缺 SI 事件适配层 | Reuse | 新建 `DdcSiCatalogOutboxNotifier` | Step 5; `MC-REUSE-001` |
| 天机流程接入天枢客户端 | `tianji-process-server` / `runtime` | `pom.xml` 检查缺直接依赖声明 (`PLAN-CLAR-001`) | 仅 `tianji-agent-server` 原先声明 `rpc-adapter` | Approved addition | 两子模块 POM 补充 `stellar-enclosure-tianshu-starter` | Step 9; `MC-DEP-001` |

### 4.8 User-mandated Java Rule Implementation Matrix

| Literal rule | Spec source | Repository evidence | Exact files and order | Implementation obligations | Validation gate | Steps | Status/blocker |
| --- | --- | --- | --- | --- | --- | --- | --- |
| Rule 1 | Spec §6.2/§8/§9.1 | `model/command/DdcAiCreateCommand.java`, `model/query/DdcAiPageQuery.java`, `model/vo/DdcAiResourceResult.java` | Step 1 DTOs -> Step 4 POs -> Step 5 BOs -> Step 6 Commands/Queries/VOs | 遵循仓既有平铺子目录与 `*Command`/`*Query`/`*Result`/`*BO`/`*PO`/`*Service`/`*Repository` 严格后缀 | Compile & symbol inventory across repos | Steps 1, 2, 4, 5, 6, 7, 9 | PASS |
| Rule 2 | Spec §6.2/§7/§9 | `DdcAiResourceRepository.java`, `DdcAiResourceController.java` | Step 1 DTOs -> Step 5 Strategies/Validator -> Step 6 Commands/Controller/Provider | 所有信任边界强制 `@Valid`/`@Validated`、Schema/密钥扫描、禁流程字段检查、DAG 环检查与服务端鉴权租户覆盖 | `DdcSiContentStrategyTest`, `DdcSiDependencyValidatorTest`, `DdcSiResourceControllerIT`, `DdcSiResourceRpcProviderIT` | Steps 1, 2, 5, 6, 7, 9 | PASS |
| Rule 3 | Spec §6.2/§8/§10 | `DdcAiResourceConverter.java`, `DdcAiEnumConverter.java` | Step 2 `DdcSiProtoMapper` -> Step 5 Converters -> Step 6 `DdcSiCqeConverter` | 使用不可变 DTO 与 MapStruct `BaseConverter`/`BaseForwardConverter`；独立建新映射类绝对保护 `DdcAiEnumConverter` | MapStruct compile check (`ReportingPolicy.ERROR` on domain maps) & unit tests | Steps 1, 2, 5, 6 | PASS |
| Rule 4 | Spec §6.2/§8 | `tianshu-service/lombok.config`（只复制 `@Qualifier`） | Step 2 Client -> Step 5 Services -> Step 6 Config -> Step 7 Starter -> Step 9 Tianji | `@Slf4j`、`@RequiredArgsConstructor(onConstructor_ = @Autowired)`、多 Bean 显式 `@Qualifier`、零构造 `@Value` | Context startup & wiring assertions | Steps 2, 3, 4, 5, 6, 7, 9 | PASS |
| Rule 5 | Spec §6.1/§8.2 | `GatewayVectorTypeHandler.java`, parent BOMs | Step 4 `pom.xml`, Handler -> Step 9 `tianji` POMs | 只引入批准之白名单 BOM 内部包与纯 JDBC 句柄，零未批准外部包 | `mvnw dependency:resolve` & forbidden import grep | Steps 1, 2, 4, 5, 7, 9 | PASS |
| Rule 6 | Spec §6.2/§9/§10 | `DdcAiResourceResult.java` | Step 1 DTO/Event -> Step 6 Result VOs & ExceptionAdvice | JSON 输出 ISO `UTC Z`、`@Schema` 文档、枚举标准字符串及脱敏凭据引用 | `DdcSiContractsAndRedisKeysTest`, `DdcSiResourceControllerIT` (`/v3/api-docs`) | Steps 1, 5, 6, 8 | PASS |
| Rule 7 | Spec §6.2/§15 | `tianshu-admin/src/main/resources/application*.yml`, `tianji-*/application.yml` | Step 6 3 YAML profiles -> Step 9 2 YAML files | 全部配置档严格键值对称齐备；prod 档默认关闭自上报自动发布覆写 | Config parity check & profile tests | Steps 6, 9 | PASS |
| Rule 9 | Spec §6.2/§9/§13 | `agent-flow/workflow`, `tianshu-admin/service/ai` | Step 3 deprecation -> Step 5 Strategy/Policy/Outbox -> Step 7 Coordinator | 异构结构校验强制 Strategy 模式、复杂状态用迁移表与 Outbox + 异步索引 | `DdcSiContentStrategyTest`, `DdcSiResourceServiceImplTest`, `DdcSiCatalogSyncCoordinatorTest` | Steps 3, 5, 6, 7, 9 | PASS |
| Rule 10 | Spec §6.2/§7/§15 | `V20261018_001`/`V20261022_001` (`timestamptz(6)` + `deleted_at`) | §4.9 Contract -> Step 1..2 -> Step 4..9 | 新建时刻列全用 `timestamptz(6)`/`Instant`/`UTC Z`；保留 `deleted_at` 继承 `Legacy UTC`；时长显式 `Duration` | `TEST-005`, `TEST-007`, `TEST-009` & time checks | Steps 1, 2, 4, 5, 6, 7, 8, 9 | PASS |
| Rule 11 | Spec §6.1/§8 | 四个仓库现有基线目录树 | Every Step 1..9 & `Change File Tree` | 四仓严保各自传统分层或 React SPA 单一形态，每步真测全通方算过关 | `./mvnw test`, `-Ptianshu-ai-persistence-it`, `check-i18n`, `tsc`, `vite build` | Every Step (1..9) | PASS |

### 4.9 Time semantics and conversion contract

Time contract: Required

| Time ID | Fields/symbols | Semantics | Java/runtime type | Storage policy | Wire policy | Business zone source | Display zone source | Boundary / DST rules | Precision / Clock | Compatibility | Verification |
| --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- | --- |
| `TIME-001` | `publishedTime`, `boundTime`, `indexedTime`, `occurredAt`, `publishedAt`, `updatedAt`, `publishedEpochMillis` | `Occurred instant` | `Instant` | `UTC instant` | `UTC Z` | Not applicable: invariant UTC audit & domain instant | Browser user locale zone with UTC tooltip | Always UTC; Proto `int64` epoch millis `0` maps to `null` when unset | PostgreSQL `timestamptz(6)` microsecond; Proto ms; fixed `Clock` in unit tests | Matches `V20261022_001` `timestamptz(6)` standard | Step 1, Step 2, Step 4, Step 6; `TEST-005`, `TEST-009`, `TEST-011` (`DdcSiContractsAndRedisKeysTest`, `DdcSiProtoMapperTest`, `DdcSiPersistenceIT`) |
| `TIME-002` | `createTime`, `updateTime`, `deletedAt` on `DdcSi*PO` | `Occurred instant` | `Instant` | `Legacy UTC` | `UTC Z` | Not applicable: repository UTC audit convention | Browser user locale zone (`UTC Z` on REST JSON) | `deleted_at timestamp(6) without time zone` retained solely for PG partial unique indexes `WHERE deleted_at IS NULL` | Microsecond `6` | Existing `V20260922_001`/`V20261018_001` JDBC UTC session and `DdcAiResourcePO` UTC conversion verified; partial unique index compatibility preserved | Step 4, Step 6; `TEST-005`, `TEST-011` (`DdcSiPersistenceIT`, `DdcAiPersistenceIT`) |
| `TIME-003` | `embeddingTimeout`, `rpcDeadline`, `cacheTtl`, `debounceWindow`, `registrationRetryBackoff` | `Duration` | `Duration` | `Duration` | `Duration` | Not applicable: elapsed timeout and TTL interval | Configuration YAML ISO-8601 duration strings (`1500ms`, `30s`) | Positive bounded durations (`@DurationMin`) preventing busy retry loops | Millisecond resolution via Spring Boot properties | Standard Spring Boot configuration binding compatibility | Step 2, Step 5, Step 7, Step 9; `TEST-006`, `TEST-007`, `TEST-009` (`DdcSiCatalogRpcClientTest`, `DdcSiSemanticIndexServiceImplTest`, `DdcSiCatalogSyncCoordinatorTest`) |

## 5. Change File Tree

| Operation | Path | Current evidence/symbol | Final symbols/state | Responsibility | Step | Requirements | Validation owner |
| --- | --- | --- | --- | --- | --- | --- | --- |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/test/java/top/egon/cola/component/tianshu/si/DdcSiContractsAndRedisKeysTest.java` | Absent | `DdcSiContractsAndRedisKeysTest` | Step 1 RED 合同与 Redis 键单测 | Step 1 | `REQ-001`..`REQ-007` | `mvnw -pl stellar-enclosure-tianshu-starter test` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/package-info.java` | `model/si/` | `@NonNullApi` + dual-doc package-info | SI 契约包文档与非空合同注解 | Step 1 | `REQ-001`..`REQ-008` | `DdcPackageDocumentationTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiKindEnum.java` | `model/si/` | `DdcSiKindEnum` | 四态 SI 资源分类枚举 | Step 1 | `REQ-001`, `REQ-002` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiLifecycleStatusEnum.java` | `model/si/` | `DdcSiLifecycleStatusEnum` | 生命周期枚举及运行时可见判断 | Step 1 | `REQ-003` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiBindingStatusEnum.java` | `model/si/` | `DdcSiBindingStatusEnum` | 应用绑定启停状态枚举 | Step 1 | `REQ-004` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSourceTypeEnum.java` | `model/si/` | `DdcSiSourceTypeEnum` | 注册来源枚举 | Step 1 | `REQ-006` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiReferenceTargetTypeEnum.java` | `model/si/` | `DdcSiReferenceTargetTypeEnum` | 依赖目标类别枚举 | Step 1 | `REQ-001`, `REQ-008` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiResourceDescriptor.java` | `model/si/` | `DdcSiResourceDescriptor` | 跨仓标准 SI 资源描述子 | Step 1 | `REQ-001`..`REQ-004`, `REQ-007` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiVersionDescriptor.java` | `model/si/` | `DdcSiVersionDescriptor` | 不可变快照与晋级溯源描述 | Step 1 | `REQ-003`, `REQ-005` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiBindingDescriptor.java` | `model/si/` | `DdcSiBindingDescriptor` | 应用绑定指针描述 | Step 1 | `REQ-004`, `REQ-008` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiReferenceDescriptor.java` | `model/si/` | `DdcSiReferenceDescriptor` | 有向依赖边描述 | Step 1 | `REQ-001`, `REQ-008` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSearchHit.java` | `model/si/` | `DdcSiSearchHit` | RRF 检索命中条目与降级标记 | Step 1 | `REQ-007` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiCatalogQuery.java` | `model/si/` | `DdcSiCatalogQuery` | 下游拉取目录分页入参 | Step 1 | `REQ-001`, `REQ-004` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSemanticSearchQuery.java` | `model/si/` | `DdcSiSemanticSearchQuery` | 跨进程混合搜索入参 | Step 1 | `REQ-007` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSelfRegistrationRequest.java` | `model/si/` | `DdcSiSelfRegistrationRequest` | 运行时自上报入参 | Step 1 | `REQ-006` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiPublicationEvent.java` | `model/si/` | `DdcSiPublicationEvent` | `EVENT-001` 通知载荷 | Step 1 | `REQ-003`, `REQ-005`, `REQ-006` | `DdcSiContractsAndRedisKeysTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/api/client/DdcSiCatalogClient.java` | Verified `api/client/` approved dir | `DdcSiCatalogClient` | 全仓统一 SI 访问端口 | Step 1 | `REQ-001`, `REQ-006`, `REQ-007`, `REQ-008` | Steps 2, 7, 9 Tests |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/redis/DdcRedisKeys.java` | Verified `redis/DdcRedisKeys.java` | + `siCatalogVersionKey`, `siCatalogEntryKey`, `siEmbeddingLockKey` | SI 多维作用域名 Redis 键构造 | Step 1 | `REQ-003`, `REQ-006`, `REQ-007` | `DdcSiContractsAndRedisKeysTest` |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-starter/src/test/java/top/egon/cola/component/tianshu/DdcPackageDocumentationTest.java` | `DdcPackageDocumentationTest` | + `"model.si"` in `TARGET_PACKAGES` | 纳管新 SI 契约包文档校验 | Step 1 | `REQ-001` | `DdcPackageDocumentationTest` |
| CREATE | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/si/DdcSiProtoMapperTest.java` | Absent | `DdcSiProtoMapperTest` | Proto 映射与毫秒 UTC 往返测试 | Step 2 | `REQ-001`, `REQ-006`, `REQ-007` | `mvnw test` |
| CREATE | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/si/DdcSiCatalogRpcClientTest.java` | Absent | `DdcSiCatalogRpcClientTest` | `InProcessServer` 协议、超时及装配测试 | Step 2 | `REQ-001`, `REQ-004`, `REQ-006`, `REQ-007` | `mvnw test` |
| CREATE | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/proto/tianshu_si_resource.proto` | Verified `proto/` dir | `DdcSiResourceGrpcService` | 定义 `RPC-001`..`RPC-003` gRPC 契约 | Step 2 | `REQ-001`, `REQ-004`, `REQ-006`, `REQ-007`, `REQ-008` | `generate-sources` & IT |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/security/DdcRpcOperationResolver.java` | Verified `DdcRpcOperationResolver.java` | + 4 SI gRPC route mappings | 登记 SI gRPC 路由至 `AI_READ`/`AI_WRITE` 操作鉴权 | Step 2 | `REQ-001`, `REQ-006`, `REQ-007` | `DdcRpcClientInterceptorFactoryTest` |
| CREATE | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/mapping/DdcSiProtoMapper.java` | Verified `mapping/` approved dir | `DdcSiProtoMapper` | MapStruct Proto <-> DTO 转换器 | Step 2 | `REQ-001`, `REQ-006`, `REQ-007` | `DdcSiProtoMapperTest` |
| CREATE | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/client/si/package-info.java` | `client/si/` | Package Javadoc | 纳管 SI 客户端包边界文档检验 | Step 2 | `REQ-001` | `DdcRpcModuleBoundaryTest` |
| CREATE | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/client/si/DdcSiCatalogRpcClient.java` | `client/si/` dir | `DdcSiCatalogRpcClient` | gRPC 客户端实现与截止超时管理 | Step 2 | `REQ-001`, `REQ-004`, `REQ-006`, `REQ-007` | `DdcSiCatalogRpcClientTest` |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/autoconfigure/DdcRpcAutoConfiguration.java` | Verified `DdcRpcAutoConfiguration.java` | + SI Stub & Client conditional beans | 自动装配 SI 客户端组件 | Step 2 | `REQ-001`, `REQ-006`, `REQ-007` | `DdcSiCatalogRpcClientTest` |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/DdcRpcModuleBoundaryTest.java` | `DdcRpcModuleBoundaryTest` | + `"client/si"` in `PACKAGES` | 注册包边界测试检查项 | Step 2 | `REQ-001` | `DdcRpcModuleBoundaryTest` |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/security/DdcRpcClientInterceptorFactoryTest.java` | `DdcRpcClientInterceptorFactoryTest` | + 4 SI route expectations | 更新鉴权操作白名单回归期待 | Step 2 | `REQ-001`, `REQ-006`, `REQ-007` | `DdcRpcClientInterceptorFactoryTest` |
| CREATE | `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/test/java/top/egon/cola/component/agentflow/AgentFlowAgenticBoundaryTest.java` | Absent | `AgentFlowAgenticBoundaryTest` | 停用旧静态流下 Agentic 自主决策回归 | Step 3 | `REQ-001`, `REQ-008` | `mvnw test` |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/workflow/AgentWorkflowBuilderStrategy.java` | Verified `workflow/` file | + `@Deprecated(forRemoval = false)` | 标注静态编排策略接口弃用与迁移文档 | Step 3 | `REQ-001`, `REQ-008` | `AgentFlowAgenticBoundaryTest` |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/workflow/AgentWorkflowStrategyFactory.java` | Verified `workflow/` file | + `@Deprecated(forRemoval = false)` & WARN | 标注静态工作流工厂弃用及一次警告日志 | Step 3 | `REQ-001`, `REQ-008` | `AgentFlowAgenticBoundaryTest` |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/autoconfigure/AgentFlowProperties.java` | Verified `autoconfigure/` record | + `LegacyStaticWorkflowSettings` | 在严格绑定处理器下纳管 `legacy-static-workflow.enabled` 开关 | Step 3 | `REQ-001`, `REQ-008` | `AgentFlowAgenticBoundaryTest` |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/runtime/AgentFlowFactory.java` | Verified `runtime/` compiler | + null-safe `strategyFactory` guard | 关闭静态工作流 Bean 时对组合节点抛出明确配置异常 | Step 3 | `REQ-001`, `REQ-008` | `AgentFlowAgenticBoundaryTest` |
| MODIFY | `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/autoconfigure/AgentFlowAutoConfiguration.java` | Verified `autoconfigure/` file | + `legacy-static-workflow.enabled` switch | 允许服务关停遗留静态工作流策略构建 | Step 3 | `REQ-001`, `REQ-008` | `AgentFlowAgenticBoundaryTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/persistence/DdcSiPersistenceIT.java` | Absent (`pom.xml` `<includes>`) | `DdcSiPersistenceIT` | `TEST-005` 真实 PG/`pgvector` 迁移、外键与 RRF IT | Step 4 | `REQ-001`..`REQ-008` | `mvnw -Ptianshu-ai-persistence-it` |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-admin/pom.xml` | Verified `pom.xml` | + `rag-starter`, `spring-ai-model`, `DdcSiPersistenceIT` | 新增核准 BOM 依赖与持久化 IT 列表 | Step 4 | `REQ-007` | `dependency:resolve` & IT |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/db/egon-mp/V20261116_001__tianshu_si_resource_governance.sql` | 7 prior files | 5 `ddc_si_*` tables, `vector`, HNSW, GIN, `tianshu:si:*` | SI 五表物理建模与 `PROMPT`/`SKILL` 初始化回填 | Step 4 | `REQ-001`..`REQ-008` | `DdcSiPersistenceIT` |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/db/egon-mp/repository-manifest.json` | Verified 7 entries | + `20261116_001` + SHA-256 | 登记新迁移脚本实算校验值 | Step 4 | `REQ-001` | `DdcSiPersistenceIT` |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcAiKindPersistenceEnum.java` | Verified `PROMPT(201)`, `SKILL(202)` | + `AGENT(203)`, `MCP(204)` | 四态持久化资源分类枚举扩充 | Step 4 | `REQ-001`, `REQ-002` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiLifecycleStatusPersistenceEnum.java` | Absent | `DdcSiLifecycleStatusPersistenceEnum` | 生命周期持久化枚举 | Step 4 | `REQ-003` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiBindingStatusPersistenceEnum.java` | Absent | `DdcSiBindingStatusPersistenceEnum` | 绑定状态持久化枚举 | Step 4 | `REQ-004` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiSourceTypePersistenceEnum.java` | Absent | `DdcSiSourceTypePersistenceEnum` | 来源类型持久化枚举 | Step 4 | `REQ-006` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiReferenceTargetTypePersistenceEnum.java` | Absent | `DdcSiReferenceTargetTypePersistenceEnum` | 引用目标持久化枚举 | Step 4 | `REQ-001`, `REQ-008` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/typehandler/DdcSiVectorTypeHandler.java` | Pattern in `yuheng-service` | `DdcSiVectorTypeHandler` | 纯 JDBC `float[]` <-> `pgvector` TypeHandler | Step 4 | `REQ-007` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiResourcePO.java` | Absent | `DdcSiResourcePO` | `ddc_si_resource_t0` 实体 | Step 4 | `REQ-001`..`REQ-003`, `REQ-006` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiVersionPO.java` | Absent | `DdcSiVersionPO` | `ddc_si_version_t0` 实体 | Step 4 | `REQ-003`, `REQ-005` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiBindingPO.java` | Absent | `DdcSiBindingPO` | `ddc_si_binding_t0` 实体 | Step 4 | `REQ-004`, `REQ-008` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiReferencePO.java` | Absent | `DdcSiReferencePO` | `ddc_si_reference_t0` 实体 | Step 4 | `REQ-001`, `REQ-008` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiEmbeddingChunkPO.java` | Absent | `DdcSiEmbeddingChunkPO` | `ddc_si_embedding_chunk_t0` 实体 | Step 4 | `REQ-007` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiResourceMapper.java` | Absent | `DdcSiResourceMapper` | 资源持久化及乐观更新 Mapper | Step 4 | `REQ-001`..`REQ-006` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiVersionMapper.java` | Absent | `DdcSiVersionMapper` | 快照序号递增与历史矩阵查询 Mapper | Step 4 | `REQ-003`, `REQ-005` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiBindingMapper.java` | Absent | `DdcSiBindingMapper` | 应用作用域绑定定位 Mapper | Step 4 | `REQ-004`, `REQ-008` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiReferenceMapper.java` | Absent | `DdcSiReferenceMapper` | 出度/入度依赖查询 Mapper | Step 4 | `REQ-001`, `REQ-008` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiEmbeddingChunkMapper.java` | Absent | `DdcSiEmbeddingChunkMapper` | HNSW RRF 与 FTS 降级检索 Mapper | Step 4 | `REQ-007` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiResourceMapper.xml` | Absent | Resource XML queries | 主表筛选/锁更新/TSV SQL | Step 4 | `REQ-001`..`REQ-006` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiVersionMapper.xml` | Absent | Version XML queries | 版本递增与跨环境矩阵 SQL | Step 4 | `REQ-003`, `REQ-005` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiBindingMapper.xml` | Absent | Binding XML queries | 绑定覆盖写 SQL | Step 4 | `REQ-004`, `REQ-008` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiReferenceMapper.xml` | Absent | Reference XML queries | 阻断入度检查与天机引用同步 SQL | Step 4 | `REQ-001`, `REQ-008` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiEmbeddingChunkMapper.xml` | Absent | RRF & FTS SQL | `vector <=> cosine` CTE RRF 与 FTS fallback SQL | Step 4 | `REQ-007` | `DdcSiPersistenceIT` |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/egon-tianshu-persistence.yml` | Verified `sharding.tables` | + 5 `ddc_si_*` `STANDARD_TENANT_ID` routes | 将 SI 五张逻辑表纳入 ShardingSphere 分片路由 | Step 4 | `REQ-001`..`REQ-008` | `DdcSiPersistenceIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiContentStrategyTest.java` | Absent | `DdcSiContentStrategyTest` | Schema、流程字段、明文凭据防线测试 | Step 5 | `REQ-001`, `REQ-002` | `mvnw test` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiDependencyValidatorTest.java` | Absent | `DdcSiDependencyValidatorTest` | DAG 环检测与流程引用归档拦截测试 | Step 5 | `REQ-001`, `REQ-005`, `REQ-008` | `mvnw test` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiResourceServiceImplTest.java` | Absent | `DdcSiResourceServiceImplTest` | 并发冲突、晋级回滚、绑定与自注册幂等测 | Step 5 | `REQ-001`..`REQ-008` | `mvnw test` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexServiceImplTest.java` | Absent | `DdcSiSemanticIndexServiceImplTest` | RRF 汇聚排名与 Embedding 降级测试 | Step 5 | `REQ-007` | `mvnw test` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiResourceBO.java` | Verified `model/bo/` flat dir | `DdcSiResourceBO` | SI 主资源业务聚合 | Step 5 | `REQ-001`..`REQ-006` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiVersionBO.java` | `model/bo/` | `DdcSiVersionBO` | 版本快照业务对象 | Step 5 | `REQ-003`, `REQ-005` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiBindingBO.java` | `model/bo/` | `DdcSiBindingBO` | 应用绑定业务对象 | Step 5 | `REQ-004`, `REQ-008` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiReferenceBO.java` | `model/bo/` | `DdcSiReferenceBO` | 有向引用业务对象 | Step 5 | `REQ-001`, `REQ-008` | `DdcSiDependencyValidatorTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiAgentSpecBO.java` | `model/bo/` | `DdcSiAgentSpecBO` | 原子 Agent 结构化载荷模型 | Step 5 | `REQ-001`, `REQ-002` | `DdcSiContentStrategyTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiMcpSpecBO.java` | `model/bo/` | `DdcSiMcpSpecBO` | 标准 MCP 连接载荷模型 | Step 5 | `REQ-001`, `REQ-002` | `DdcSiContentStrategyTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiEnumConverter.java` | `DdcAiEnumConverter` untouched | `DdcSiEnumConverter` | 5 组 SI 持久化与契约枚举双向转换 | Step 5 | `REQ-001`..`REQ-006` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiResourceConverter.java` | `model/convertor/` | `DdcSiResourceConverter` | MapStruct PO <-> BO <-> Descriptor 转换 | Step 5 | `REQ-001`..`REQ-007` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/repository/DdcSiResourceRepository.java` | Verified `repository/` dir | `DdcSiResourceRepository` | 分组校验仓储接口 | Step 5 | `REQ-001`..`REQ-008` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/repository/impl/DdcSiResourceRepositoryImpl.java` | Verified `repository/impl/` dir | `DdcSiResourceRepositoryImpl` | 五表协调仓储实现 | Step 5 | `REQ-001`..`REQ-008` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiContentStrategy.java` | Absent | `DdcSiContentStrategy` | 多态载荷验证与文本抽出策略接口 | Step 5 | `REQ-001`, `REQ-002`, `REQ-007` | `DdcSiContentStrategyTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiAgentContentStrategy.java` | Absent | `DdcSiAgentContentStrategy` | Agent 策略实现（禁工作流、IO Schema 检查） | Step 5 | `REQ-001`, `REQ-002` | `DdcSiContentStrategyTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiMcpContentStrategy.java` | Absent | `DdcSiMcpContentStrategy` | MCP 策略实现（传输、禁明文凭据） | Step 5 | `REQ-001`, `REQ-002` | `DdcSiContentStrategyTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiPromptSkillContentStrategy.java` | Absent | `DdcSiPromptSkillContentStrategy` | Prompt 与 Skill 兼容校验策略 | Step 5 | `REQ-001`, `REQ-002` | `DdcSiContentStrategyTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiDependencyValidator.java` | Absent | `DdcSiDependencyValidator` | Agent DAG 检测与归档引用锁拦截 | Step 5 | `REQ-001`, `REQ-005`, `REQ-008` | `DdcSiDependencyValidatorTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiLifecycleTransitionPolicy.java` | Absent | `DdcSiLifecycleTransitionPolicy` | 状态迁移合法图与环境晋级白名单 | Step 5 | `REQ-003`, `REQ-005` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/outbox/DdcSiCatalogOutboxNotifier.java` | Existing Outbox mechanism | `DdcSiCatalogOutboxNotifier` | 事务内写 Outbox 与 `AFTER_COMMIT` 通知 | Step 5 | `REQ-003`, `REQ-005`, `REQ-006` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexService.java` | Absent | `DdcSiSemanticIndexService` | 语义分片重建与 RRF 搜索服务端口 | Step 5 | `REQ-007` | `DdcSiSemanticIndexServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexServiceImpl.java` | Absent | `DdcSiSemanticIndexServiceImpl` | `rag-starter` + RRF + 超时 FTS 降级实现 | Step 5 | `REQ-007` | `DdcSiSemanticIndexServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiResourceService.java` | Absent | `DdcSiResourceService` | SI 领域协同接口 | Step 5 | `REQ-001`..`REQ-008` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/impl/DdcSiResourceServiceImpl.java` | `service/` | `DdcSiResourceServiceImpl` | 全交易与门禁核心编排实现 | Step 5 | `REQ-001`..`REQ-008` | `DdcSiResourceServiceImplTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiResourceControllerIT.java` | Absent | `DdcSiResourceControllerIT` | 14 REST 权限、`MockMvc` 与 OpenAPI IT | Step 6 | `REQ-001`..`REQ-009` | `mvnw test` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/rpc/provider/DdcSiResourceRpcProviderIT.java` | Absent | `DdcSiResourceRpcProviderIT` | gRPC `RPC-001`..`003` 与伪造拦截 IT | Step 6 | `REQ-001`, `REQ-004`, `REQ-006`..`REQ-008` | `mvnw test` |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/permission/DdcPermissionCodes.java` | Verified `permission/DdcPermissionCodes.java` | + 7 `SI_*` constants | 注册 7 个 `tianshu:si:*` 权限常量 | Step 6 | `REQ-001`..`REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiCreateCommand.java` | Verified `model/command/` | `DdcSiCreateCommand` | `API-003` 新建资源命令 | Step 6 | `REQ-001`, `REQ-002`, `REQ-004` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiDraftCommand.java` | `model/command/` | `DdcSiDraftCommand` | `API-004` 更新草稿命令 | Step 6 | `REQ-002`, `REQ-003` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiLifecycleCommand.java` | `model/command/` | `DdcSiLifecycleCommand` | `API-005..009` 生命周期控制命令 | Step 6 | `REQ-003`, `REQ-005`, `REQ-008` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiBindingCommand.java` | `model/command/` | `DdcSiBindingCommand` | `API-013` 绑定操作命令 | Step 6 | `REQ-004`, `REQ-008` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/query/DdcSiPageQuery.java` | Verified `model/query/` | `DdcSiPageQuery` | `API-001` 分页过滤查询 | Step 6 | `REQ-001`, `REQ-004`, `REQ-007` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/query/DdcSiSemanticSearchRequestQuery.java` | `model/query/` | `DdcSiSemanticSearchRequestQuery` | `API-012` REST 语义搜索请求 | Step 6 | `REQ-007` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiResourceResult.java` | Verified `model/vo/` | `DdcSiResourceResult` | 资源列表及详情 VO | Step 6 | `REQ-001`..`REQ-004`, `REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiVersionResult.java` | `model/vo/` | `DdcSiVersionResult` | 版本快照视图 VO | Step 6 | `REQ-003`, `REQ-005`, `REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiBindingResult.java` | `model/vo/` | `DdcSiBindingResult` | 绑定视图 VO | Step 6 | `REQ-004`, `REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiReferenceResult.java` | `model/vo/` | `DdcSiReferenceResult` | 引用视图及阻断清单 VO | Step 6 | `REQ-008`, `REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiSemanticSearchResult.java` | `model/vo/` | `DdcSiSemanticSearchResult` | RRF 评分和降级状态 VO | Step 6 | `REQ-007`, `REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiEnvMatrixResult.java` | `model/vo/` | `DdcSiEnvMatrixResult` | 跨环境矩阵横向对比 VO | Step 6 | `REQ-005`, `REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiCqeConverter.java` | `model/convertor/` | `DdcSiCqeConverter` | Web CQE MapStruct 映射接口 | Step 6 | `REQ-001`..`REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiResourceController.java` | Pattern in `controller/ai/` | `DdcSiResourceController` | 暴露 `API-001`..`API-014` 接口控制器 | Step 6 | `REQ-001`..`REQ-009` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiExceptionAdvice.java` | Absent | `DdcSiExceptionAdvice` | 局部 SI 异常状态翻译器 (`@Order(-2)`) | Step 6 | `REQ-001`..`REQ-008` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/rpc/provider/DdcSiResourceRpcProvider.java` | `rpc/provider/` | `DdcSiResourceRpcProvider` | gRPC `RPC-001`..`RPC-003` 实现 | Step 6 | `REQ-001`, `REQ-004`, `REQ-006`..`REQ-008` | `DdcSiResourceRpcProviderIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/config/DdcSiProperties.java` | Verified `config/` dir | `DdcSiProperties` | `egon.cola.component.tianshu.si.*` 属性绑定 | Step 6 | `REQ-005`, `REQ-006`, `REQ-007` | `DdcSiResourceControllerIT` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/config/DdcSiAutoConfiguration.java` | `config/` | `DdcSiAutoConfiguration` | 注册异步索引有界线程池与属性配置 | Step 6 | `REQ-006`, `REQ-007` | `DdcSiResourceControllerIT` |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application.yml` | Verified YAML | + `si` defaults | 基础档案增量配置 | Step 6 | `REQ-005`, `REQ-006`, `REQ-007` | Profile startup test |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application-dev.yml` | Verified YAML | + `si` dev settings | 开发环境对等配置 | Step 6 | `REQ-005`, `REQ-006`, `REQ-007` | Profile startup test |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application-prod.yml` | Verified YAML | + `si` prod locked settings | 生产安全对齐配置 | Step 6 | `REQ-005`, `REQ-006`, `REQ-007` | Profile startup test |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/test/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogSyncCoordinatorTest.java` | Absent | `DdcSiCatalogSyncCoordinatorTest` | 缓存防抖、指针校验与断网保底测试 | Step 7 | `REQ-001`, `REQ-003`, `REQ-006` | `mvnw test` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/test/java/top/egon/cola/component/tianshu/ai/si/DdcSiSelfRegistrationRunnerTest.java` | Absent | `DdcSiSelfRegistrationRunnerTest` | 自注册批量幂等与退避启动测试 | Step 7 | `REQ-006` | `mvnw test` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiClientProperties.java` | `top/egon/cola/component/tianshu/ai/` | `DdcSiClientProperties` | 客户端身份、TTL、防抖及容错参数 | Step 7 | `REQ-006` | `DdcSiCatalogSyncCoordinatorTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogCache.java` | Absent | `DdcSiCatalogCache` | 不可变热快照读写容器 | Step 7 | `REQ-001`, `REQ-004`, `REQ-006` | `DdcSiCatalogSyncCoordinatorTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogSyncCoordinator.java` | Absent | `DdcSiCatalogSyncCoordinator` | 客户端缓存与事件防抖同步包装器 | Step 7 | `REQ-001`, `REQ-003`, `REQ-006`, `REQ-007` | `DdcSiCatalogSyncCoordinatorTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiPublicationListener.java` | Absent | `DdcSiPublicationListener` | 幂等消费 `EVENT-001` 刷新客户端快照 | Step 7 | `REQ-003`, `REQ-006` | `DdcSiCatalogSyncCoordinatorTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiLocalResourceContributor.java` | Absent | `DdcSiLocalResourceContributor` | 业务本地 SI 资源提供扩展点 | Step 7 | `REQ-006` | `DdcSiSelfRegistrationRunnerTest` |
| CREATE | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiSelfRegistrationRunner.java` | Absent | `DdcSiSelfRegistrationRunner` | 启动收集批量上报及防死锁 `ApplicationRunner` | Step 7 | `REQ-006` | `DdcSiSelfRegistrationRunnerTest` |
| MODIFY | `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/autoconfigure/DdcAiStarterAutoConfiguration.java` | Verified `ai/autoconfigure/` path | + Conditional SI client beans | 按开关装配客户端缓存与自注册链路 | Step 7 | `REQ-001`, `REQ-006`, `REQ-008` | `DdcSiCatalogSyncCoordinatorTest` |
| CREATE | `tianshu-web/src/pages/SiCatalogPage.test.tsx` | Absent | `SiCatalogPage.test.tsx` | 目录过滤、混合高亮与降级栏测 | Step 8 | `REQ-001`, `REQ-007`, `REQ-009` | `npm test` |
| CREATE | `tianshu-web/src/pages/SiResourceEditorPage.test.tsx` | Absent | `SiResourceEditorPage.test.tsx` | Agent/MCP 结构表单与并发冲突测试 | Step 8 | `REQ-002`, `REQ-003`, `REQ-008`, `REQ-009` | `npm test` |
| CREATE | `tianshu-web/src/pages/SiEnvMatrixPage.test.tsx` | Absent | `SiEnvMatrixPage.test.tsx` | 跨环境晋级/回滚与归档阻断测试 | Step 8 | `REQ-005`, `REQ-008`, `REQ-009` | `npm test` |
| MODIFY | `tianshu-web/src/api/types.ts` | Verified `src/api/types.ts` | + SI TS types | 前后端同构 TS 接口模型 | Step 8 | `REQ-001`..`REQ-009` | `npx tsc --noEmit` |
| CREATE | `tianshu-web/src/api/si.ts` | Pattern in `src/api/ai.ts` | 14 exported SI functions | `API-001`..`API-014` HTTP 客户端 | Step 8 | `REQ-001`..`REQ-009` | `SiCatalogPage.test.tsx` |
| MODIFY | `tianshu-web/src/permissions/permissions.ts` | Verified `permissions.ts` | + 7 `SI_*` strings | SI 权限常量定义 | Step 8 | `REQ-009` | `SiCatalogPage.test.tsx` |
| MODIFY | `tianshu-web/src/permissions/resources.ts` | Verified `resources.ts` | + SI nav resource entries | 导航权限门控配置 | Step 8 | `REQ-009` | `SiCatalogPage.test.tsx` |
| CREATE | `tianshu-web/src/pages/SiCatalogPage.tsx` | Absent | `SiCatalogPage` | 主目录与语义检索主页 | Step 8 | `REQ-001`, `REQ-007`, `REQ-009` | `SiCatalogPage.test.tsx` |
| CREATE | `tianshu-web/src/pages/SiEnvMatrixPage.tsx` | Absent | `SiEnvMatrixPage` | 跨环境多版本矩阵管理页 | Step 8 | `REQ-005`, `REQ-009` | `SiEnvMatrixPage.test.tsx` |
| CREATE | `tianshu-web/src/pages/SiResourceEditorPage.tsx` | Absent | `SiResourceEditorPage` | Agent/MCP 结构化表单编辑页 | Step 8 | `REQ-002`, `REQ-003`, `REQ-008`, `REQ-009` | `SiResourceEditorPage.test.tsx` |
| CREATE | `tianshu-web/src/pages/SiServiceBindingPanel.tsx` | Absent | `SiServiceBindingPanel` | 应用作用域绑定设置面板 | Step 8 | `REQ-004`, `REQ-009` | `SiCatalogPage.test.tsx` |
| CREATE | `tianshu-web/src/pages/SiReferencesPanel.tsx` | Absent | `SiReferencesPanel` | 出度与入度引用只读图谱 | Step 8 | `REQ-001`, `REQ-008`, `REQ-009` | `SiResourceEditorPage.test.tsx` |
| CREATE | `tianshu-web/src/pages/SiLifecycleDialog.tsx` | Absent | `SiLifecycleDialog` | 发布/晋级/回滚/归档交互弹窗 | Step 8 | `REQ-003`, `REQ-005`, `REQ-008`, `REQ-009` | `SiEnvMatrixPage.test.tsx` |
| MODIFY | `tianshu-web/src/pages/AiResourcesPage.tsx` | Verified `AiResourcesPage.tsx` | + SI redirect banner | 旧管理页导流横幅 | Step 8 | `REQ-009` | `check-i18n.mjs` |
| MODIFY | `tianshu-web/src/App.tsx` | Verified `App.tsx` | + SI routes & nav menu | 注册全部 SI 页面入口路由 | Step 8 | `REQ-009` | `vite build` |
| MODIFY | `tianshu-web/src/test/i18nCatalogs.json` | Verified `i18nCatalogs.json` | + Bilingual SI catalogs | 中英 1:1 词库全补齐 | Step 8 | `REQ-009` | `node scripts/check-i18n.mjs` |
| CREATE | `tianji-service/stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/integration/TianshuAgentBindingVerifierTest.java` | Absent | `TianshuAgentBindingVerifierTest` | 激活阻断未发布 Agent 与引用释放测 (`TEST-010`) | Step 9 | `REQ-001`, `REQ-004`, `REQ-008` | `mvnw test` |
| CREATE | `tianji-service/stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/si/TianshuTemplateResolverTest.java` | Absent | `TianshuTemplateResolverTest` | 版本精准注入与运行束组装测 (`TEST-010`) | Step 9 | `REQ-001`, `REQ-004`, `REQ-006` | `mvnw test` |
| MODIFY | `tianji-service/stellar-enclosure-tianji-process-server/pom.xml` | Verified `pom.xml` | + `stellar-enclosure-tianshu-starter` | 补全编排服依赖 (`PLAN-CLAR-001`) | Step 9 | `REQ-001`, `REQ-008` | `mvnw test-compile` |
| MODIFY | `tianji-service/stellar-enclosure-tianji-agent-runtime/pom.xml` | Verified `pom.xml` | + `stellar-enclosure-tianshu-starter` | 补全智能体运行时依赖 (`PLAN-CLAR-001`) | Step 9 | `REQ-001`, `REQ-006` | `mvnw test-compile` |
| CREATE | `tianji-service/stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/integration/TianshuAgentBindingVerifier.java` | `process/server/` | `TianshuAgentBindingVerifier` | 校验节点 Agent 就绪并同步/解除天枢流程引用 | Step 9 | `REQ-001`, `REQ-004`, `REQ-008` | `TianshuAgentBindingVerifierTest` |
| MODIFY | `tianji-service/stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/AutomationActivationService.java` | Verified `release/AutomationActivationService.java` | + verify & release calls | 激活事务前门禁核验与下线清理 | Step 9 | `REQ-001`, `REQ-004`, `REQ-008` | `TianshuAgentBindingVerifierTest` |
| CREATE | `tianji-service/stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/si/TianshuTemplateResolver.java` | `tianji/agent/` | `TianshuTemplateResolver` | 由天枢解析组装 Agent/Prompt/Skill/MCP 执行束 | Step 9 | `REQ-001`, `REQ-004`, `REQ-006`, `REQ-008` | `TianshuTemplateResolverTest` |
| MODIFY | `tianji-service/stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java` | Verified `TianjiAgentRuntimeAutoConfiguration.java` | + `TianshuTemplateResolver` bean | 条件注入模板解析器进运行时上下文 | Step 9 | `REQ-001`, `REQ-006`, `REQ-008` | `TianshuTemplateResolverTest` |
| MODIFY | `tianji-service/stellar-enclosure-tianji-process-server/src/main/resources/application.yml` | Verified YAML | + `si.client` & strict verify flag | 编排服务连天枢配置 | Step 9 | `REQ-006`, `REQ-008` | Process server IT |
| MODIFY | `tianji-service/stellar-enclosure-tianji-agent-server/src/main/resources/application.yml` | Verified YAML | + `si.client` & `legacy-static-workflow: false` | 运行服务连天枢并屏蔽遗留静态流 | Step 9 | `REQ-006`, `REQ-008` | Agent server IT |

## 6. Prerequisites, Constraints, and Plan Clarifications

### 6.1 Repository and worktree baseline

- 仓库指引核对：四个工作区均无专属 `AGENTS.md`；统一遵循 Java 17/21 + Spring Boot 3 + MapStruct + Lombok + MyBatis-Plus 约定及前端 `check-i18n.mjs` 强制扫描。
- 分支基线：`tianshu-service` (`main @ b1c607f`)、`Stellar-Enclosure-Components` (`main @ cf66934`，内含无关 `docs/egon` outbox 文档，所有 Step 不触碰)、`tianji-service` (`main @ e5d4551`)、`tianshu-web` (`main @ b88f5f5`)。
- 提交规范：严格使用每个 Step `Commit paths:` 进行精确暂存。

### 6.2 Build, test, and environment prerequisites

| Concern | Exact command/source | Required state | Validation boundary |
| --- | --- | --- | --- |
| Maven 编译与本地发布 | `./mvnw`（存在 3 Java 仓根目录） | JDK 与 `.m2` 可用，且依 Step 1 -> Step 2/3 -> Step 7 次序 `install` | Cross-module / cross-repo build |
| PostgreSQL + `pgvector` 环境 | `-Ptianshu-ai-persistence-it`（`pgvector/pgvector` 数据库） | 实例支持 `CREATE EXTENSION IF NOT EXISTS vector` 及 1536 维 `HNSW` | Persistence migration (`TEST-005`) |
| 前端质量门禁工具链 | `node scripts/check-i18n.mjs && npx tsc --noEmit && npm test -- --run && npm run build` | `tianshu-web/node_modules` 安装就绪 | Frontend UI & i18n (`TEST-012`) |

### 6.3 Immutable constraints and approved decisions

1. 历史 DB SQL（`V20260922`~`V20261028`）与旧 `DdcAiResourceController` / `DdcAiEnumConverter` 严禁破坏。
2. `tianshu-service` 仅治理原子资产；`agent-flow-starter` 不含新流程编排；Flowable BPMN 独占于 `tianji-service`。
3. 所有新增 BOM 依赖与环境 YAML 均已在 §2.3/§4.7/§7 白名单登记。

### 6.4 Plan Clarifications

| ID | Small implementation inference | Repository evidence | Why semantics are unchanged | Impact if wrong |
| --- | --- | --- | --- | --- |
| `PLAN-CLAR-001` | 为 `tianji-process-server/pom.xml` 与 `tianji-agent-runtime/pom.xml` 补齐 `stellar-enclosure-tianshu-starter` 依赖 | 核对 3 个天机 `pom.xml` 仅 `tianji-agent-server` 原先引入 `rpc-tianshu-adapter` | 两模块直接引用 `DdcSiCatalogClient` 契约接口（Spec §8 既定职责） | 缺 `DdcSiCatalogClient` 无法编译 |
| `PLAN-CLAR-002` | 更新 `repository-manifest.json` 注册 `20261116_001` 真实 SHA-256，并在 `tianshu-admin/pom.xml` `tianshu-ai-persistence-it` `<includes>` 加 `DdcSiPersistenceIT` | 已实核 `repository-manifest.json` 结构和 `tianshu-admin/pom.xml` L397–430 显式 `<includes>` | 不改任何业务规则，确保 PG 校验与持久化 IT 自动执行 | 启动校验阻断或漏测 `TEST-005` |
| `PLAN-CLAR-003` | 对齐仓真实包根目录：`tianshu-starter` 为 `top.egon.cola.component.tianshu`、`tianshu-ai-starter` 为 `top.egon.cola.component.tianshu.ai`、`tianshu-admin` 权限在 `admin.permission`、`tianji` 为 `top.egon.cola.platform.tianji.*`、`agent-flow` 为 `workflow/AgentWorkflow*` | 实跑 `find` 命令获取四仓真实文件目录树 | 纯物理包路径与仓库实际对齐，零需求与架构改变 | 新增/修改类落入错目录导致装配与导入失败 |

## 7. Ordered File-by-file Implementation Steps

> 严格执行 RED -> GREEN -> Wiring，逐文件编号连贯唯一，零 fenced code blocks 与任何占位符。

### Step 1 — Define shared SI contracts, enums, publication events and Redis keys in tianshu-starter

- Requirements: `REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008`
- Dependencies: `None`
- Baseline state: `tianshu-starter` 当前仅承载配置中心/发现/Prompt/Skill 契约与 `DdcRedisKeys` 原有前缀键。
- Observable outcome: 全四仓依赖的 5 个 SI 枚举、9 个传输/查询/事件 DTO、`DdcSiCatalogClient` 门面接口与 `DdcRedisKeys` 新键构造成型并经全量单测安装至 `.m2`。
- End state: Step 2、4、5、6、7、9 全部拥有稳固共享 Java 编译契约。
- Test-first gate: `Required — 先建 DdcSiContractsAndRedisKeysTest 因缺失 SI 枚举、DTO 及 Redis SI 键方法呈现期待编译红灯`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-JSON-001, MC-TIME-001, MC-TEST-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 6, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/test/java/top/egon/cola/component/tianshu/si/DdcSiContractsAndRedisKeysTest.java`
- Purpose: 建立验证枚举解析、不可变 DTO Jackson UTC `Instant` 往返、Bean Validation 越界与 `DdcRedisKeys` 防分隔符注入之 RED 单元测试。
- Symbols: `DdcSiContractsAndRedisKeysTest`.
- Repository evidence: 沿用 `tianshu-starter/src/test` JUnit 5 与 AssertJ 测试结构。
- Dependencies and consumers: 测试面向 File 2..17 公开 API。
- Why now: 作为契约起点固定所有枚举码、DTO 字段与 Redis 键规范。
- Contract/signature changes: 固定测试可见签名与 ISO `UTC Z` 输出格式验证。
- Input/output and state mapping: 合法与越界输入 -> 正向 Jackson 全还原，非法键段或页大小越界 -> 抛出对应校验异常。
- Error and edge behavior: 检验空 `tenantId`、冒号注入 Key 与非法未知枚举字面量拦截。
- Standards impact: `MC-TEST-001, MC-TIME-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 2, Rule 6, Rule 10, Rule 11`.
- Required change: 在 `tianshu-starter` 测试包创建 `DdcSiContractsAndRedisKeysTest`，编写枚举全覆盖、`DdcSiResourceDescriptor` 与 `DdcSiPublicationEvent` 序列化、Bean Validation 与 `DdcRedisKeys` 回归测试。
- Verification contribution: 构成 Step 1 主体单测门禁。
- After this file: RED 契约建立完毕，待生产类落盘后变绿。

#### File 2 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/package-info.java`
- Purpose: 声明 `model.si` 包 `@NonNullApi` 与双语文档规范以通过 `DdcPackageDocumentationTest`。
- Symbols: `package-info` (`@NonNullApi`).
- Repository evidence: 对齐 `model/ai/package-info.java` 写法。
- Dependencies and consumers: `model.si` 下全部 DTO 与枚举。
- Why now: 先确立子包非空与双语文档合规基线。
- Contract/signature changes: `@NonNullApi` 默认生效。
- Input/output and state mapping: 无。
- Error and edge behavior: 无。
- Standards impact: `MC-ARCH-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 创建 `model/si/package-info.java`。
- Verification contribution: 使 `DdcPackageDocumentationTest` 全通过。
- After this file: 包级规范就绪。

#### File 3 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiKindEnum.java`
- Purpose: 定义超级智能资源四分类枚举（`AGENT`, `MCP`, `PROMPT`, `SKILL`）。
- Symbols: `DdcSiKindEnum`, `fromCode(String)`.
- Repository evidence: 对齐 `top/egon/cola/component/tianshu/model/` 现有模型与枚举规范。
- Dependencies and consumers: 全系 SI DTO、`DdcSiCatalogClient`、转换器。
- Why now: 驱动资源类型分类表达。
- Contract/signature changes: 暴露不可变字符串代码及严格 `fromCode`。
- Input/output and state mapping: 代码字符串 <-> 四类枚举实例。
- Error and edge behavior: 未知代码抛 `IllegalArgumentException`。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 创建 `DdcSiKindEnum` 定义 4 个常量和查找方法。
- Verification contribution: 使枚举解析测试转绿。
- After this file: 资源分类标准确立。

#### File 4 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiLifecycleStatusEnum.java`
- Purpose: 定义 `DRAFT`, `PUBLISHED`, `DEPRECATED`, `ARCHIVED` 与运行时可见判断 `isQueryableByRuntime()`。
- Symbols: `DdcSiLifecycleStatusEnum`, `isQueryableByRuntime()`, `fromCode(String)`.
- Repository evidence: Spec §6.2 状态机定义。
- Dependencies and consumers: 描述符 DTO、转换器与 Tianji 门禁校验。
- Why now: 决定快照是否能被业务执行。
- Contract/signature changes: 仅 `PUBLISHED` 与 `DEPRECATED` 返回可运行态。
- Input/output and state mapping: 生命周期枚举 -> 是否运行可用布尔值。
- Error and edge behavior: 非法代码抛异常。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 创建 `DdcSiLifecycleStatusEnum` 实现四状态及查询谓词。
- Verification contribution: 验证状态可见性矩阵测试通过。
- After this file: 状态生命周期契约确定。

#### File 5 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiBindingStatusEnum.java`
- Purpose: 定义服务应用作用域绑定启用态（`ACTIVE`, `INACTIVE`）。
- Symbols: `DdcSiBindingStatusEnum`, `fromCode(String)`.
- Repository evidence: Spec §7.2 `ddc_si_binding_t0`。
- Dependencies and consumers: `DdcSiBindingDescriptor`、`DdcSiEnumConverter`。
- Why now: 独立控制各应用版本绑定指针启停。
- Contract/signature changes: 暴露 `ACTIVE`/`INACTIVE` 与查找方法。
- Input/output and state mapping: 字串 -> 绑定状态常量。
- Error and edge behavior: 未知值抛异常。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 创建 `DdcSiBindingStatusEnum`。
- Verification contribution: 被枚举单测全覆盖。
- After this file: 绑定状态确定。

#### File 6 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSourceTypeEnum.java`
- Purpose: 定义来源标签（`CONSOLE`, `AUTO_REPORTED`, `GITOPS`）以区别控制台手工管理与自启动上报。
- Symbols: `DdcSiSourceTypeEnum`, `fromCode(String)`.
- Repository evidence: Spec §9.2 自注册来源保护需求。
- Dependencies and consumers: `DdcSiResourceDescriptor` 及服务幂等判断。
- Why now: 防止服务自注册越权覆盖控制台版本。
- Contract/signature changes: 暴露三类来源与严格 `fromCode`。
- Input/output and state mapping: 来源代码 -> 枚举实例。
- Error and edge behavior: 未知代码抛异常。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 创建 `DdcSiSourceTypeEnum`。
- Verification contribution: 被枚举测试验证。
- After this file: 来源类型确定。

#### File 7 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiReferenceTargetTypeEnum.java`
- Purpose: 定义有向依赖目标类型（`AGENT`, `MCP`, `PROMPT`, `SKILL`, `TIANJI_PROCESS`）。
- Symbols: `DdcSiReferenceTargetTypeEnum`, `fromCode(String)`.
- Repository evidence: Spec §4/§7.2 引用边结构。
- Dependencies and consumers: `DdcSiReferenceDescriptor`、DAG 检验器与天机引用管理。
- Why now: 确立引用校验与流程绑定登记图谱类别。
- Contract/signature changes: 包含 5 个目标分类与严格查找。
- Input/output and state mapping: 字符串 -> 引用目标分类。
- Error and edge behavior: 非法代码抛异常。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 创建 `DdcSiReferenceTargetTypeEnum`。
- Verification contribution: 枚举集单测验证完毕。
- After this file: 五组跨仓 SI 枚举悉数完备。

#### File 8 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiResourceDescriptor.java`
- Purpose: 跨仓 SI 资源快照传输对象，包含作用域、标签、依赖列表、结构规格与 UTC `Instant` 发布/更新时间。
- Symbols: `DdcSiResourceDescriptor` (`@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`, `@Accessors(chain = true)`).
- Repository evidence: 仿照 `model/ai/DdcAiContentDTO.java` 构建范式。
- Dependencies and consumers: 全部下游 RPC、缓存及天机解析器。
- Why now: 是资源读写的核心数据载体。
- Contract/signature changes: `references` 采用 `List<@Valid DdcSiReferenceDescriptor>`，所有时间均为 `Instant` 输出 `UTC Z`。
- Input/output and state mapping: 服务/Proto 属性 <-> DTO。
- Error and edge behavior: Bean Validation 非空格式检查，空集合默认值保护。
- Standards impact: `MC-MODEL-001, MC-JSON-001, MC-TIME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiResourceDescriptor` 含全量领域字段与 Lombok/Jackson 注解。
- Verification contribution: 使 Jackson UTC 往返测试转绿。
- After this file: 资源核心传输结构固定。

#### File 9 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiVersionDescriptor.java`
- Purpose: 承载不可变快照版本号、内容指纹、晋级来源环境与发布用户及 `Instant` 时刻。
- Symbols: `DdcSiVersionDescriptor`.
- Repository evidence: Spec §7.2 `ddc_si_version_t0`。
- Dependencies and consumers: 版本历史详情接口、转换器。
- Why now: 支撑可审计跨环境溯源模型。
- Contract/signature changes: `versionNo > 0`、`publishedAt` 为 `Instant`。
- Input/output and state mapping: 快照实体 -> DTO。
- Error and edge behavior: 拒绝非正版本号与空摘要。
- Standards impact: `MC-MODEL-001, MC-TIME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiVersionDescriptor`。
- Verification contribution: 充实序列与字段验证用例。
- After this file: 快照描述完成。

#### File 10 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiBindingDescriptor.java`
- Purpose: 传递某作用域下针对具体 `appCode` 锁定的发布版绑定指针描述。
- Symbols: `DdcSiBindingDescriptor`.
- Repository evidence: Spec §7.2 `ddc_si_binding_t0`。
- Dependencies and consumers: 绑定服务接口与客户端。
- Why now: 支持按应用区分版本绑定。
- Contract/signature changes: `pinnedVersionNo` 可为空（指向最新活跃版）或正整数；`updatedAt` 为 `Instant`。
- Input/output and state mapping: 绑定行 -> 描述符 DTO。
- Error and edge behavior: `@NotBlank appCode` 与非空状态检验。
- Standards impact: `MC-MODEL-001, MC-TIME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiBindingDescriptor`。
- Verification contribution: 覆盖 JSON 反序列化校验。
- After this file: 绑定模型建立。

#### File 11 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiReferenceDescriptor.java`
- Purpose: 表达资源出度依赖或外部流程引用的目标类别、目标键、可选锁定版本及是否强制必需标志。
- Symbols: `DdcSiReferenceDescriptor` (`targetType`, `targetKey`, `pinnedVersionNo`, `required`, `roleHint`).
- Repository evidence: Spec §7.2 `ddc_si_reference_t0`。
- Dependencies and consumers: `DdcSiResourceDescriptor`, `DdcSiProtoMapper`, `TianshuTemplateResolver`.
- Why now: 先行定格结构供 Agent 描述和 Tianji 装配。
- Contract/signature changes: 强制 `targetType` 与 `targetKey` 非空。
- Input/output and state mapping: 引用声明 -> DTO。
- Error and edge behavior: 缺项拒收。
- Standards impact: `MC-MODEL-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 3, Rule 11`.
- Required change: 创建 `DdcSiReferenceDescriptor`。
- Verification contribution: 配合资源内嵌引用验证测试通过。
- After this file: 引用边描述完成。

#### File 12 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSearchHit.java`
- Purpose: 承载混合检索条目的资源描述符、RRF 融合总分、矢量分、全文分、最佳切片摘录以及是否经历 FTS 降级布尔标。
- Symbols: `DdcSiSearchHit`.
- Repository evidence: Spec §9.1/§9.2 混合检索契约。
- Dependencies and consumers: `DdcSiCatalogClient.semanticSearch`、`DdcSiProtoMapper`。
- Why now: 支撑搜索打分和降级感知在接口透传。
- Contract/signature changes: 显式布尔标记 `degradedToFtsOnly`。
- Input/output and state mapping: SQL 投影 -> 客户端标准搜索命中。
- Error and edge behavior: 限制分数有限浮点与排名 `>= 1`。
- Standards impact: `MC-MODEL-001, MC-JSON-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiSearchHit`。
- Verification contribution: 测试搜索命中与 `Instant` 透传序列化。
- After this file: 搜索命中契约完备。

#### File 13 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiCatalogQuery.java`
- Purpose: 客户端拉取多租户多维活跃目录分页的查询对象。
- Symbols: `DdcSiCatalogQuery`.
- Repository evidence: Spec §9.2 `RPC-001`。
- Dependencies and consumers: `DdcSiCatalogClient.listResources`。
- Why now: 固定分页边界参数安全值。
- Contract/signature changes: `pageNo >= 1`, `1 <= pageSize <= 200`（默认 50）。
- Input/output and state mapping: 作用域+类型/标签过滤条件 -> 受限分页请求。
- Error and edge behavior: 越界页号/尺寸报错。
- Standards impact: `MC-MODEL-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 3, Rule 11`.
- Required change: 创建 `DdcSiCatalogQuery` 携带验证注解。
- Verification contribution: 测试边界截断和校验报错。
- After this file: 目录查询入参完成。

#### File 14 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSemanticSearchQuery.java`
- Purpose: 跨进程自然语言与词条混合搜索请求体。
- Symbols: `DdcSiSemanticSearchQuery`.
- Repository evidence: Spec §9.2 `RPC-002`。
- Dependencies and consumers: `DdcSiCatalogClient.semanticSearch`。
- Why now: 客户端语义查询接口需独立强校验入参。
- Contract/signature changes: 问句非空限长 `<= 2000`；`1 <= topK <= 50`（默认 10）。
- Input/output and state mapping: 搜索条件 -> 验证请求。
- Error and edge behavior: 空白问句或超界 `topK` 触发校验拒绝。
- Standards impact: `MC-MODEL-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 3, Rule 11`.
- Required change: 创建 `DdcSiSemanticSearchQuery`。
- Verification contribution: 测试超长与无效 `topK` 驳回断言。
- After this file: 检索入参确立。

#### File 15 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSelfRegistrationRequest.java`
- Purpose: 下游服务自动发现声明上报给天枢进行幂等入库的负载请求。
- Symbols: `DdcSiSelfRegistrationRequest`.
- Repository evidence: Spec §9.2 `RPC-003`。
- Dependencies and consumers: `DdcSiSelfRegistrationRunner`, `DdcSiProtoMapper`。
- Why now: 驱动自注册上报链路。
- Contract/signature changes: 约束 `resourceKey` 正则、`List<@Valid DdcSiReferenceDescriptor>` 及 `specJson` 最大长度边界。
- Input/output and state mapping: 服务内建资源 -> 标准化申报包。
- Error and edge behavior: 非法正则或欠缺作用域检验拒收。
- Standards impact: `MC-MODEL-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 3, Rule 11`.
- Required change: 创建 `DdcSiSelfRegistrationRequest`。
- Verification contribution: 测试 key 正则拦截边界。
- After this file: 自注册入参建立。

#### File 16 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiPublicationEvent.java`
- Purpose: 定义 `EVENT-001` (`tianshu.si.catalog.changed`) 精简领域变更广播载荷。
- Symbols: `DdcSiPublicationEvent`, `EventType`.
- Repository evidence: Spec §9.3 事件架构。
- Dependencies and consumers: `DdcSiCatalogOutboxNotifier`, `DdcSiPublicationListener`。
- Why now: 保障异步事件收发两端的精准字段对齐与 `Instant` 序列化。
- Contract/signature changes: 不含大段配置全文，专载作用域、`kind`、`resourceKey`、`activeVersionNo`、`catalogRevision` 及 `occurredAt` (`Instant`)。
- Input/output and state mapping: 事件发生 -> Outbox JSON -> 消费端增量触发。
- Error and edge behavior: 缺 `eventId` 或 `occurredAt` 报错。
- Standards impact: `MC-MODEL-001, MC-JSON-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 2, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiPublicationEvent` 并定义 7 种事件类型枚举。
- Verification contribution: 测试 Jackson UTC 序列化与全部字段还原。
- After this file: 异步发布事件契约固定。

#### File 17 — `CREATE tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/api/client/DdcSiCatalogClient.java`
- Purpose: 在仓内已核准 `api.client` 端口包内暴露与网络传输介质无关的 SI 客户端接口。
- Symbols: `DdcSiCatalogClient` (`listResources`, `resolveResource`, `semanticSearch`, `registerManagedResource`).
- Repository evidence: 对齐 `api/client/DdcConfigClient.java`, `DdcManagementClient.java`, `DdcPromptClient.java`。
- Dependencies and consumers: Step 2 (`DdcSiCatalogRpcClient`)、Step 7 (`DdcSiCatalogSyncCoordinator`)、Step 9 天机服务端。
- Why now: 符合 `DdcPlatformBoundaryTest` 顶层角色包清单，消除高层对 gRPC 的直接耦合。
- Contract/signature changes: `resolveResource` 对可空入参显式标注 `@Nullable`，查不到有效发布版返回 `Optional.empty()`。
- Input/output and state mapping: 客户端查询 -> 资源或搜索列表。
- Error and edge behavior: 明确契约边界异常传播说明。
- Standards impact: `MC-ARCH-001, MC-NAME-001`.
- Literal rule enforcement: `Rule 1, Rule 11`.
- Required change: 在 `api/client/` 创建接口 `DdcSiCatalogClient`。
- Verification contribution: 打通 `DdcPlatformBoundaryTest` 及后继 Step 2、7、9 编译。
- After this file: 客户端统一门面就绪。

#### File 18 — `MODIFY tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/redis/DdcRedisKeys.java`
- Purpose: 在仓内现有 `DdcRedisKeys` 增加 SI 作用域版本指针、资源条缓存及防重复构建向量索引锁键构造器，零破坏原有方法。
- Symbols: `SI_CATALOG_VERSION_PREFIX`, `SI_CATALOG_ENTRY_PREFIX`, `SI_EMBEDDING_LOCK_PREFIX`, `siCatalogVersionKey`, `siCatalogEntryKey`, `siEmbeddingLockKey`.
- Repository evidence: 已验证文件真实路径在 `.../component/tianshu/redis/DdcRedisKeys.java`。
- Dependencies and consumers: `DdcSiCatalogOutboxNotifier`, `DdcSiSemanticIndexServiceImpl`, `DdcSiCatalogSyncCoordinator`。
- Why now: 服务端写 Redis 与客户端校验 Redis 必须共享唯一标准键规则。
- Contract/signature changes: 全量原有前缀和方法完全不动，新增 3 个带分隔符校验静态构造器。
- Input/output and state mapping: `tenantId` + 作用域段 -> 无冲突隔离冒号拼接 Key。
- Error and edge behavior: 遇到空字串或含冒号/空白的控制字符直接抛 `IllegalArgumentException` 防键碰撞污染。
- Standards impact: `MC-UTIL-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 5, Rule 11`.
- Required change: 在现有 `DdcRedisKeys` 尾部安全追加上述常量与防注入校验构造器。
- Verification contribution: 使 `DdcSiContractsAndRedisKeysTest` 全部测试转绿。
- After this file: Key 工具扩容完成。

#### File 19 — `MODIFY tianshu-service/stellar-enclosure-tianshu-starter/src/test/java/top/egon/cola/component/tianshu/DdcPackageDocumentationTest.java`
- Purpose: 将 `"model.si"` 纳入 `TARGET_PACKAGES` 白名单，通过模块包文档门禁检查。
- Symbols: `TARGET_PACKAGES`.
- Repository evidence: `DdcPackageDocumentationTest.java` L23–59。
- Dependencies and consumers: `DdcPackageDocumentationTest`, `DdcPlatformBoundaryTest`.
- Why now: `tianshu-starter` 架构保护测试强制所有新建 Java 包在测试白名单显式登记。
- Contract/signature changes: 仅追加 `"model.si"` 一项。
- Input/output and state mapping: White-list registration -> test pass。
- Error and edge behavior: 缺项即阻断边界单测。
- Standards impact: `MC-TEST-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 向 `DdcPackageDocumentationTest.TARGET_PACKAGES` 列表增量添加 `"model.si"`。
- Verification contribution: 驱动包文档测试与 Step 1 全绿并完成安装。
- After this file: Step 1 代码闭环达成并执行 Maven 安装。

- Validation working directory: `tianshu-service`
- Verification command: `./mvnw -pl stellar-enclosure-tianshu-starter -Dtest='DdcSiContractsAndRedisKeysTest,DdcPackageDocumentationTest,DdcPlatformBoundaryTest#starterContainsExactlyTheApprovedTopLevelRolePackages' test && ./mvnw -pl stellar-enclosure-tianshu-starter -am install -DskipTests`
- Expected result: `DdcSiContractsAndRedisKeysTest`、`DdcPackageDocumentationTest` 与顶层包检查全绿（隔离既往 `api.list` 缺少 `package-info.java` 的既有基线缺陷），`stellar-enclosure-tianshu-starter` 安装入 `.m2`。
- Failure returns to: 检查对应枚举/DTO 或 `DdcRedisKeys` 构造校验，补正重测。
- Completion criteria: `REQ-001`..`REQ-008` 共享层全通且 `.m2` 可见。
- Rollback: 局部 `git revert` Step 1 提交。
- Commit paths: `tianshu-service/stellar-enclosure-tianshu-starter/src/test/java/top/egon/cola/component/tianshu/si/DdcSiContractsAndRedisKeysTest.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/package-info.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiKindEnum.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiLifecycleStatusEnum.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiBindingStatusEnum.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSourceTypeEnum.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiReferenceTargetTypeEnum.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiResourceDescriptor.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiVersionDescriptor.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiBindingDescriptor.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiReferenceDescriptor.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSearchHit.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiCatalogQuery.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSemanticSearchQuery.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiSelfRegistrationRequest.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/model/si/DdcSiPublicationEvent.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/api/client/DdcSiCatalogClient.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/main/java/top/egon/cola/component/tianshu/redis/DdcRedisKeys.java`, `tianshu-service/stellar-enclosure-tianshu-starter/src/test/java/top/egon/cola/component/tianshu/DdcPackageDocumentationTest.java`
- Commit: `feat(tianshu-starter): define SI contracts, enums, publication events and Redis keys`

---

### Step 2 — Implement SI gRPC proto, MapStruct mapper and client adapter in rpc-tianshu-adapter

- Requirements: `REQ-001, REQ-004, REQ-006, REQ-007, REQ-008`
- Dependencies: `Step 1`
- Baseline state: `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter` 当前提供配置/注册/旧 AI gRPC 服务，缺少 SI 目录、混合检索与自上报 `.proto` 与客户端实现。
- Observable outcome: `tianshu_si_resource.proto` 生成对应 gRPC 类，`DdcSiProtoMapper` 通过毫秒 UTC 精度往返测试，`DdcSiCatalogRpcClient` 经 `InProcessServer` 跑通并已 install 到 `.m2`。
- End state: 下游 `tianshu-admin`、`tianshu-ai-starter` 及天机组件无缝使用 SI gRPC 客户端与服务端基类。
- Test-first gate: `Required — 先建 DdcSiProtoMapperTest 与 DdcSiCatalogRpcClientTest 固定双向转换、超时截止与条件装配`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-REUSE-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-TIME-001, MC-TEST-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/si/DdcSiProtoMapperTest.java`
- Purpose: 建立覆盖 Protobuf 与 `tianshu-starter` DTO 双向映射、空时间（`0L` <-> `null`）和未知枚举拒绝的 RED 单测。
- Symbols: `DdcSiProtoMapperTest`.
- Repository evidence: `rpc-tianshu-adapter/src/test` 既有单测写法。
- Dependencies and consumers: 验证 File 3 与 File 5。
- Why now: 锁定所有双向字段映射契约无丢失。
- Contract/signature changes: 检验毫秒精度 `Instant` <-> `int64` epochMillis 保真度。
- Input/output and state mapping: 资源/命中/查询/注册请求 -> Proto -> DTO 还原相等。
- Error and edge behavior: 未知枚举触发 `IllegalArgumentException`。
- Standards impact: `MC-TEST-001, MC-CONVERT-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiProtoMapperTest` 完成双向映射及空/边缘情况断言。
- Verification contribution: 驱动 `DdcSiProtoMapper` 转绿。
- After this file: 映射器红灯契约已定。

#### File 2 — `CREATE Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/si/DdcSiCatalogRpcClientTest.java`
- Purpose: 利用 `InProcessServer` 测试真实 gRPC 调用流程、`NOT_FOUND -> Optional.empty()`、`PERMISSION_DENIED`、`DEADLINE_EXCEEDED` 及 `DdcRpcAutoConfiguration` 条件注册 (`TEST-009` 客户端验证)。
- Symbols: `DdcSiCatalogRpcClientTest`.
- Repository evidence: 组件既有 grpc-testing 与 `ApplicationContextRunner`。
- Dependencies and consumers: 测试 File 6 与 File 7。
- Why now: 在客户端编写前固定 gRPC 超时和异常转换规则。
- Contract/signature changes: `resolveResource` 对 `NOT_FOUND` 返回空，其他状态码抛受检/客户端包装异常。
- Input/output and state mapping: `InProcess` server stubs -> 客户端输出或明确异常。
- Error and edge behavior: `DEADLINE_EXCEEDED` 与 `PERMISSION_DENIED` 禁止静默吞并。
- Standards impact: `MC-TEST-001, MC-TIME-001, MC-BEAN-001`.
- Literal rule enforcement: `Rule 4, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiCatalogRpcClientTest` 实现四大 RPC 方法的 `InProcessServer` 模拟和条件装配开关校验。
- Verification contribution: 验证跨网络调用核心客户端行为。
- After this file: Step 2 完整测试套件红灯建立。

#### File 3 — `CREATE Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/proto/tianshu_si_resource.proto`
- Purpose: 声明 `DdcSiResourceGrpcService`（`ListSiCatalog`, `ResolveSiResource`, `SemanticSearchSiResources`, `RegisterManagedSiResource`），完全覆含 `RPC-001`..`RPC-003`。
- Symbols: `DdcSiResourceGrpcService`, request/response/item proto3 messages.
- Repository evidence: 已验证 `proto/` 下存在 `tianshu_ai_resource.proto` 等采用相同 package 配置之文件。
- Dependencies and consumers: Step 2 Client 与 Step 6 Server RPC Provider。
- Why now: 自动生成通信传输桩基石。
- Contract/signature changes: 全时间统一为 `int64 *_epoch_millis`；所有请求外显 `tenant_id`, `biz_code`, `namespace_code`, `env_code` 与 `request_id`。
- Input/output and state mapping: Proto3 定义直接经插件编为 Java 类。
- Error and edge behavior: 标准 gRPC Status 语义，不篡改已有 `.proto`。
- Standards impact: `MC-REUSE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 10, Rule 11`.
- Required change: 创建 `tianshu_si_resource.proto` 配 `java_multiple_files = true` 及准确包名。
- Verification contribution: `mvnw generate-sources` 成功编译产生对应 gRPC stub。
- After this file: Stub 传输类型完全可用。

#### File 4 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/security/DdcRpcOperationResolver.java`
- Purpose: 在核实后的 `DdcRpcOperationResolver.java` 注册四个 `DdcSiResourceGrpcService` 路由到既有 `AI_READ` 与 `AI_WRITE` 凭据授权码（保留 `DdcRpcOperation` `0..22` 稳定契约不变以兼容 `ComponentContractGovernanceTest`）。
- Symbols: `SI_RESOURCE`, `ListSiCatalog`, `ResolveSiResource`, `SemanticSearchSiResources`, `RegisterManagedSiResource`.
- Repository evidence: 已核实 `DdcRpcOperationResolver.java` 及 `ComponentContractGovernanceTest.java` 稳定契约快照。
- Dependencies and consumers: `DdcRpcServerInterceptor`, `DdcRpcClientInterceptorFactory`。
- Why now: 沿用现有认证审计与 HMAC 鉴权体系而不扰动上游稳定性合约。
- Contract/signature changes: `DdcRpcOperation` 原生代码完全保留；`operations()` 新增 4 个 SI 路由解析。
- Input/output and state mapping: `egon.tianshu.v1.DdcSiResourceGrpcService/*` -> `AI_READ` / `AI_WRITE`.
- Error and edge behavior: 未登记路由继续 fail-closed 拒收。
- Standards impact: `MC-REUSE-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 1, Rule 11`.
- Required change: 向 `DdcRpcOperationResolver.operations()` 注册 4 个 SI gRPC 方法映射。
- Verification contribution: 确保服务端和客户端拦截器精准识别并授权 SI gRPC 方法。
- After this file: 安全路由表纳入 SI 操作。

#### File 5 — `CREATE Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/mapping/DdcSiProtoMapper.java`
- Purpose: 在已批准的 `mapping/` 包建立 MapStruct 转换接口，双向转化 SI DTO 与生成的 Proto3 消息。
- Symbols: `DdcSiProtoMapper` (`toProto`, `fromProto`, `instantToEpochMillis`, `epochMillisToInstant`).
- Repository evidence: 对齐 `mapping/DdcCommonProtoMapper.java` 等已有映射器所在位置。
- Dependencies and consumers: `DdcSiCatalogRpcClient`, `DdcSiResourceRpcProvider`。
- Why now: 消除手动编写繁琐 Proto Builder 拷贝代码。
- Contract/signature changes: 零或负毫秒安全映射回 `null Instant`；集合空安全处理。
- Input/output and state mapping: DTO <-> Protobuf 消息。
- Error and edge behavior: 非法枚举抛出异常。
- Standards impact: `MC-CONVERT-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 3, Rule 10, Rule 11`.
- Required change: 在 `mapping/` 创建 `DdcSiProtoMapper` 接口定义完整双向映射与时间/空值默认方法。
- Verification contribution: 驱动 `DdcSiProtoMapperTest` 转绿。
- After this file: Proto 转化层完成。

#### File 6 — `CREATE Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/client/si/package-info.java`
- Purpose: 声明 `client.si` 包级 Javadoc 以符合 `DdcRpcModuleBoundaryTest`。
- Symbols: `package-info`.
- Repository evidence: `DdcRpcModuleBoundaryTest.java` 包清单规范。
- Dependencies and consumers: `DdcSiCatalogRpcClient`。
- Why now: 保持新包文档门禁全通。
- Contract/signature changes: 无。
- Input/output and state mapping: 无。
- Error and edge behavior: 无。
- Standards impact: `MC-ARCH-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 创建 `client/si/package-info.java`。
- Verification contribution: 通过包边界检查。
- After this file: 包文档合规。

#### File 7 — `CREATE Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/client/si/DdcSiCatalogRpcClient.java`
- Purpose: 实现 `DdcSiCatalogClient`，封装 `DdcSiResourceGrpcServiceBlockingStub` 调用、`Duration` deadline 截止限制及无泄露错误转化。
- Symbols: `DdcSiCatalogRpcClient` (`@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: 参照 `client/ai/` 下现存 RPC 客户端结构。
- Dependencies and consumers: Step 7 缓存包装器及 Step 9 天机服务。
- Why now: File 2 测试校验此客户端全部核心方法与容错界限。
- Contract/signature changes: 仅 `resolveResource` 遇 `NOT_FOUND` 返回 `Optional.empty()`；配置统一 `Duration` deadline。
- Input/output and state mapping: 入参 -> `withDeadlineAfter` 调用 -> 解包转化 DTO。
- Error and edge behavior: 捕 `StatusRuntimeException` 打印脱敏上下文错误并重新抛出供上层判断。
- Standards impact: `MC-BEAN-001, MC-LOG-001, MC-REUSE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 9, Rule 10, Rule 11`.
- Required change: 在 `client/si/` 创建 `DdcSiCatalogRpcClient` 实现四个端口与超时/错误处理。
- Verification contribution: 使 `DdcSiCatalogRpcClientTest` 转绿。
- After this file: 远程通讯类实现完成。

#### File 8 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/autoconfigure/DdcRpcAutoConfiguration.java`
- Purpose: 在核实后的 `DdcRpcAutoConfiguration.java` 增量注册 `DdcSiProtoMapper`、条件 `ManagedChannel` (`shutdownNow`)、`BlockingStub` 与 `@ConditionalOnMissingBean` 之 `DdcSiCatalogRpcClient`。
- Symbols: `ddcSiProtoMapperImpl`, `ddcSiResourceChannel`, `ddcSiResourceBlockingStub`, `ddcSiCatalogRpcClient`.
- Repository evidence: 已验证文件名为 `autoconfigure/DdcRpcAutoConfiguration.java`。
- Dependencies and consumers: 上层自动装配引用。
- Why now: 为客户端接入提供零样板默认装配。
- Contract/signature changes: 保留既有 RPC Bean 一字不变。
- Input/output and state mapping: `DdcRpcProperties` -> Channel -> BlockingStub -> Client Bean。
- Error and edge behavior: 未开启 SI RPC 时静默跳过不报启动异常。
- Standards impact: `MC-BEAN-001, MC-REUSE-001`.
- Literal rule enforcement: `Rule 4, Rule 11`.
- Required change: 向 `DdcRpcAutoConfiguration` 增加 SI 自动配置工厂方法。
- Verification contribution: 完成自动装配开关用例验证。
- After this file: 自动装配完成。

#### File 9 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/DdcRpcModuleBoundaryTest.java`
- Purpose: 将 `"client/si"` 注册到 `DdcRpcModuleBoundaryTest.PACKAGES`。
- Symbols: `PACKAGES`.
- Repository evidence: `DdcRpcModuleBoundaryTest.java` L14–27。
- Dependencies and consumers: 模块包文档测试。
- Why now: 登记新子包入模块边界白名单。
- Contract/signature changes: 无。
- Input/output and state mapping: 无。
- Error and edge behavior: 无。
- Standards impact: `MC-TEST-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 向 `PACKAGES` 添加 `"client/si"`。
- Verification contribution: 包边界检验测试转绿。
- After this file: 模块边界对齐。

#### File 10 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/security/DdcRpcClientInterceptorFactoryTest.java`
- Purpose: 同步扩充预期映射集加入 4 个已登记的 SI gRPC 路由检查项。
- Symbols: `resolvesTheCompleteApprovedOperationTableAndFailsClosed`.
- Repository evidence: `DdcRpcClientInterceptorFactoryTest.java` L93–101。
- Dependencies and consumers: 安全拦截回归测。
- Why now: 校验 `DdcRpcOperationResolver` 路由完整性。
- Contract/signature changes: 新增 4 条确定性操作映射预期。
- Input/output and state mapping: Bare method names -> `AI_READ` / `AI_WRITE`.
- Error and edge behavior: 未知路由仍抛出异常。
- Standards impact: `MC-TEST-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 向 `expected` 添加 `ListSiCatalog`, `ResolveSiResource`, `SemanticSearchSiResources`, `RegisterManagedSiResource`。
- Verification contribution: 驱动模块全套安全与映射测试全绿并 install `.m2`。
- After this file: Step 2 全部产出物完备通过。

- Validation working directory: `Stellar-Enclosure-Components`
- Verification command: `./mvnw -pl stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter -Dtest='!DdcAiAutoConfigurationTest#enabledDefaultAdapterRequiresTenantAndDirectRpcConfiguration' test && ./mvnw -pl stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-starter -Dtest=ComponentContractGovernanceTest test && ./mvnw -pl stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter -am install -DskipTests`
- Expected result: `DdcSiProtoMapperTest`、`DdcSiCatalogRpcClientTest`、`ComponentContractGovernanceTest` 与 120/120 可运行适配模块测全通并 install `.m2`（隔离基线既有 `DdcAiClientScope` 在 `DdcAiAutoConfigurationTest#enabledDefaultAdapterRequiresTenantAndDirectRpcConfiguration` 之缺参偏差）。
- Failure returns to: 查 Proto 包配置或映射字段，补正重测。
- Completion criteria: `RPC-001`..`RPC-003` 客户端适配经真实 Netty gRPC Server 验证并通过 install。
- Rollback: 局部 `git revert` Step 2 提交（不碰无关 docs/egon）。
- Commit paths: `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/si/DdcSiProtoMapperTest.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/si/DdcSiCatalogRpcClientTest.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/proto/tianshu_si_resource.proto`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/security/DdcRpcOperationResolver.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/mapping/DdcSiProtoMapper.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/client/si/package-info.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/client/si/DdcSiCatalogRpcClient.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/main/java/top/egon/cola/component/rpc/tianshu/autoconfigure/DdcRpcAutoConfiguration.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/DdcRpcModuleBoundaryTest.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter/src/test/java/top/egon/cola/component/rpc/tianshu/security/DdcRpcClientInterceptorFactoryTest.java`
- Commit: `feat(rpc-tianshu-adapter): implement SI proto, MapStruct mapper and gRPC client`

---

### Step 3 — Deprecate static workflow strategies in agent-flow-starter and verify Agentic runtime boundary

- Requirements: `REQ-001, REQ-008`
- Dependencies: `None`
- Baseline state: `agent-flow-starter` 内既有 `workflow/AgentWorkflowBuilderStrategy` 及 `AgentWorkflowStrategyFactory` 承担静态顺序/并行/循环多智能体编排构建；需与 Tianji Flowable 编排做架构清晰切割，同时保底既有回归零失误。
- Observable outcome: `AgentWorkflowBuilderStrategy` 与 `AgentWorkflowStrategyFactory` 均标为 `@Deprecated(since = "0.0.1", forRemoval = false)` 并附迁移到 Tianji Flowable Javadoc；`AgentFlowAutoConfiguration` 增设可选开关使 `tianji-agent-server` 可干净剥离静态构建工厂且纯 Agentic 执行测试全绿。
- End state: 最新组件 install 入本地 Maven 库供 Step 9 运行时引用。
- Test-first gate: `Required — 先建 AgentFlowAgenticBoundaryTest 验证关停静态编排开关下 Agentic 自主执行循环畅通`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-LOG-001, MC-BEAN-001, MC-CONFIG-001, MC-PATTERN-001, MC-TEST-001`
- Literal Rules: `Rule 1, Rule 4, Rule 7, Rule 9, Rule 11`
- Ordered files:

#### File 1 — `CREATE Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/test/java/top/egon/cola/component/agentflow/AgentFlowAgenticBoundaryTest.java`
- Purpose: 验证关闭 `legacy-static-workflow.enabled` 时 `AgentWorkflowStrategyFactory` 不再注册进上下文，而核心 `AgentExecutor` / `AgenticPlanCompiler` 等单智能体自主循环能力 100% 正常工作之 RED 测试。
- Symbols: `AgentFlowAgenticBoundaryTest`.
- Repository evidence: 对齐 `agentflow/agentic/AgenticPlanCompiler.java` 与 `api/execution/AgentExecutor.java` 等核实类。
- Dependencies and consumers: 测试 File 2..4 并守住既有非静态 Agentic 执行回归面。
- Why now: 在引入条件开关前锁定静态隔离与 Agentic 不破线期待。
- Contract/signature changes: 验证 `enabled=true`（默认缺省）与 `enabled=false` 双开关矩阵。
- Input/output and state mapping: 构造 Mock LLM + Tool context -> 校验自主计划编译和步数收敛上限。
- Error and edge behavior: 保证关闭静态策略工厂不会造成 `AgentFlowAutoConfiguration` 因缺失可选策略 Bean 启动失败。
- Standards impact: `MC-TEST-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 创建 `AgentFlowAgenticBoundaryTest` 覆盖上述双分支条件加载与 Agentic 执行验证。
- Verification contribution: 驱动 Step 3 自动配置与开关完善达成绿灯。
- After this file: 边界测试建立完毕。

#### File 2 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/workflow/AgentWorkflowBuilderStrategy.java`
- Purpose: 为真实静态策略父接口 `AgentWorkflowBuilderStrategy` 加注 `@Deprecated(since = "0.0.1", forRemoval = false)` 并附上指向 `tianji-service` Flowable 流程编排的明确迁移 Javadoc。
- Symbols: `@Deprecated(since = "0.0.1", forRemoval = false)` + migration Javadoc.
- Repository evidence: 已验证文件实路为 `workflow/AgentWorkflowBuilderStrategy.java`。
- Dependencies and consumers: `SequentialAgentWorkflowBuilderStrategy`, `ParallelAgentWorkflowBuilderStrategy`, `LoopAgentWorkflowBuilderStrategy` 及外部旧引用者。
- Why now: 沿用既定接口不删除，确保已有项目零二进制破坏下明确静态编排迁移方向。
- Contract/signature changes: 零改动现有方法签名，仅注解和 Javadoc。
- Input/output and state mapping: 编译期对新代码直接给出 deprecation 指引。
- Error and edge behavior: 原有行为零变更。
- Standards impact: `MC-ARCH-001, MC-PATTERN-001`.
- Literal rule enforcement: `Rule 1, Rule 9, Rule 11`.
- Required change: 在 `AgentWorkflowBuilderStrategy` 顶部补注 `@Deprecated(since = "0.0.1", forRemoval = false)` 与迁移 Javadoc 指引。
- Verification contribution: 既有静态工作流单测保底全部编译通过。
- After this file: 静态编排接口完成标记收敛。

#### File 3 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/workflow/AgentWorkflowStrategyFactory.java`
- Purpose: 为真实工厂类 `AgentWorkflowStrategyFactory` 标注 `@Deprecated(since = "0.0.1", forRemoval = false)` 并于首次实际构建调用时借助 `AtomicBoolean` 与 `@Slf4j` 打印单次 `WARN` 提示迁移至 Tianji Flowable。
- Symbols: `@Deprecated`, `@Slf4j`, single-shot `AtomicBoolean` warning log.
- Repository evidence: 已验证文件实路为 `workflow/AgentWorkflowStrategyFactory.java`。
- Dependencies and consumers: `AgentFlowAutoConfiguration` 与旧工程调起入口。
- Why now: 提供极低噪运行时观测，防止循环高并发时打印日志风暴。
- Contract/signature changes: 所有对外方法签名与返回值保持完全不变。
- Input/output and state mapping: 首次构建调用 -> 打一条告警日志 -> 原样返回策略。
- Error and edge behavior: 不改变原异常分支语义。
- Standards impact: `MC-LOG-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 1, Rule 4, Rule 11`.
- Required change: 向 `AgentWorkflowStrategyFactory` 添加弃用注解、`@Slf4j` 与单次触发提醒逻辑。
- Verification contribution: 被既有工厂单测回归覆盖且无日志暴走风险。
- After this file: 工厂构造入口实现可观测软弃用。

#### File 4 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/autoconfigure/AgentFlowProperties.java`
- Purpose: 在采用 `NoUnboundElementsBindHandler` 的 `AgentFlowProperties` record 中纳管 `LegacyStaticWorkflowSettings`（默认 `enabled=true`，并保留既有 4/5/7 参构造器兼容性），避免宿主显式设置 `legacy-static-workflow.enabled=false` 时触发严格绑定失败。
- Symbols: `LegacyStaticWorkflowSettings`, `AgentFlowProperties`.
- Repository evidence: `AgentFlowAutoConfiguration.java` L62-65 使用 `NoUnboundElementsBindHandler`。
- Dependencies and consumers: `AgentFlowAutoConfiguration` 与 `AgentFlowAgenticBoundaryTest`。
- Why now: 开关前缀位于 `egon.cola.component.agent-flow` 名下，必须受严格属性模型纳管。
- Contract/signature changes: 新增第 8 个 record 组件 `legacyStaticWorkflow` 与向后兼容 7 参构造器；4/5 参构造器签名不变。
- Input/output and state mapping: 缺省或 null -> `enabled=true`；显式 `false` -> `enabled=false`。
- Error and edge behavior: 未知子键仍由 `NoUnboundElementsBindHandler` 拒绝。
- Standards impact: `MC-CONFIG-001, MC-MODEL-001`.
- Literal rule enforcement: `Rule 1, Rule 9, Rule 11`.
- Required change: 向 `AgentFlowProperties` 增补 `LegacyStaticWorkflowSettings` 组件与兼容构造器。
- Verification contribution: 驱动 `AgentFlowAgenticBoundaryTest` 严格绑定测试转绿。
- After this file: 配置开关合法绑定。

#### File 5 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/runtime/AgentFlowFactory.java`
- Purpose: 为 `AgentFlowFactory.buildNode` 增加对 `strategyFactory == null` 的显式配置异常保护，防止停用静态工作流策略 Bean 后调用复合流程编译抛出空指针异常。
- Symbols: `buildNode`.
- Repository evidence: `AgentFlowFactory.java` L137 原直接调用 `strategyFactory.getStrategy(...)`。
- Dependencies and consumers: `AgentFlowAutoConfiguration.agentFlowFactory` 与 `AgentFlowAgenticBoundaryTest`。
- Why now: 当 `legacy-static-workflow.enabled=false` 时，`agentFlowFactory` 通过可选 `ObjectProvider` 传入 `null`。
- Contract/signature changes: 原构造器签名不变；仅静态复合流程遇 null 工厂时抛 `AgentFlowConfigurationException(..., "legacy static workflow strategies are disabled")`。
- Input/output and state mapping: 叶节点单测/单 Agent 运行不受影响；静态复合流明确报错。
- Error and edge behavior: 不回退、不静默忽略。
- Standards impact: `MC-PATTERN-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 1, Rule 9, Rule 11`.
- Required change: 在 `buildNode` 内补充空检查。
- Verification contribution: 使 `AgentFlowAgenticBoundaryTest` 复合流关闭校验断言转绿。
- After this file: 运行时静态编译器完成空安全兜底。

#### File 6 — `MODIFY Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/autoconfigure/AgentFlowAutoConfiguration.java`
- Purpose: 在已验证的 `AgentFlowAutoConfiguration.java` 为静态工作流构建/工厂 Bean 注册补充 `@ConditionalOnProperty(prefix = "egon.cola.component.agent-flow.legacy-static-workflow", name = "enabled", havingValue = "true", matchIfMissing = true)`，并通过 `ObjectProvider<AgentWorkflowStrategyFactory>` 使 `agentFlowFactory` 可选注入不依赖静态工厂 Bean。
- Symbols: Conditional registration gate for legacy workflow beans.
- Repository evidence: 已验证文件在 `autoconfigure/AgentFlowAutoConfiguration.java`。
- Dependencies and consumers: 旧业务保持默认加载，`tianji-agent-server/application.yml` 于 Step 9 显式配置关闭之。
- Why now: 实现可切换干净剥离。
- Contract/signature changes: `matchIfMissing = true` 保证零老业务破损。
- Input/output and state mapping: `enabled=false` -> 容器不含 `AgentWorkflowStrategyFactory` 等静态编排 Bean。
- Error and edge behavior: 保证默认运行时上下文稳固启动。
- Standards impact: `MC-BEAN-001, MC-CONFIG-001, MC-PATTERN-001`.
- Literal rule enforcement: `Rule 4, Rule 7, Rule 9, Rule 11`.
- Required change: 在 `AgentFlowAutoConfiguration` 对静态工作流相关 Bean 增加条件开关，保障核心执行引擎可选/解耦运行。
- Verification contribution: 使 `AgentFlowAgenticBoundaryTest` 全用例转绿并 install 到本地库。
- After this file: Step 3 提交完结，Components 组件仓所有改造收敛完成。

- Validation working directory: `Stellar-Enclosure-Components`
- Verification command: `./mvnw -pl stellar-enclosure-component-agent-flow-starter test && ./mvnw -pl stellar-enclosure-component-agent-flow-starter -am install -DskipTests`
- Expected result: `AgentFlowAgenticBoundaryTest` 及原模块全量既有测试通过且 `.m2` 更新最新工件。
- Failure returns to: 若 `AgentFlowService` 构造器强引用了 `AgentWorkflowStrategyFactory`，在 Step 3 File 6 同步改为可选/空安全注入（保持既有参数兼容性重载）。
- Completion criteria: 静态工作流完成软弃用及开关隔离，Agentic 核心链路回归全绿。
- Rollback: 局部 `git revert` Step 3 提交。
- Commit paths: `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/test/java/top/egon/cola/component/agentflow/AgentFlowAgenticBoundaryTest.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/workflow/AgentWorkflowBuilderStrategy.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/workflow/AgentWorkflowStrategyFactory.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/autoconfigure/AgentFlowProperties.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/runtime/AgentFlowFactory.java`, `Stellar-Enclosure-Components/stellar-enclosure-component-agent-flow-starter/src/main/java/top/egon/cola/component/agentflow/autoconfigure/AgentFlowAutoConfiguration.java`
- Commit: `refactor(agent-flow-starter): deprecate static workflow builders and gate legacy runtime registration`

---

### Step 4 — Deliver tianshu-admin dependencies, PostgreSQL pgvector schema, POs, JDBC vector handler and Mappers

- Requirements: `REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008`
- Dependencies: `Step 1`
- Baseline state: `tianshu-admin` 尚无 SI 表结构、纯 JDBC 向量 TypeHandler 及 `rag-starter` 依赖，且 `pom.xml` IT `<includes>` 仅涵盖旧 AI 持久层测试。
- Observable outcome: PG + `pgvector` 环境迁移成功，5 张核心 SI 表及复合外键/软删局部索引/1536 维 HNSW 索引与 `tsvector` GIN 索引落实，`DdcSiPersistenceIT` 与 `DdcAiPersistenceIT` 均真实通过。
- End state: 全部 DB 结构、SHA-256 manifest、枚举、PO 与 Mapper 完备，承接 Step 5。
- Test-first gate: `Required — 先建 DdcSiPersistenceIT 覆盖建表、历史回填、复合外键跨租户拒絶、vector(1536) 读写与 RRF SQL`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-REUSE-001, MC-DEP-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-UTIL-001, MC-TIME-001, MC-TEST-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 5, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/persistence/DdcSiPersistenceIT.java`
- Purpose: 实现 `TEST-005` 及 RRF/FTS SQL 实库验证红灯基准。
- Symbols: `DdcSiPersistenceIT`.
- Repository evidence: 对照同目录 `DdcAiPersistenceIT.java` 所用 PG 容器与测试事务回滚设置。
- Dependencies and consumers: 测试本 Step 全系迁移脚本、PO、Handler 与 Mapper XML。
- Why now: 用实库断言固定 SQL、外键约束及浮点精度契约。
- Contract/signature changes: 覆盖 1536 维精度存取、HNSW 余弦近似搜索、RRF CTE 计算分及 `deleted_at` 软删重建能力。
- Input/output and state mapping: 测试数据写入 PG -> 断言跨租户挂载报错 SQLState `23503`、并发锁返 0 行及最接近向量首条命中。
- Error and edge behavior: 测试 `NaN` 元素拒写与非法矢量格式异常。
- Standards impact: `MC-TEST-001, MC-TIME-001`（验证 `timestamptz(6)` 微秒存储及 `Legacy UTC` `deleted_at`）。
- Literal rule enforcement: `Rule 2, Rule 10, Rule 11`.
- Required change: 在 `persistence/` 创建 `DdcSiPersistenceIT` 集成测试类验证 DDL、`repository-manifest.json` 校验及全 Mapper 方法。
- Verification contribution: 驱动 Step 4 全部持久化组件完成并在 `-Ptianshu-ai-persistence-it` 转绿。
- After this file: 持久化验收准则立定。

#### File 2 — `MODIFY tianshu-service/stellar-enclosure-tianshu-admin/pom.xml`
- Purpose: 新增 `top.egon:stellar-enclosure-component-rag-starter` 与 `org.springframework.ai:spring-ai-model`（由 BOM 管理版本，无版本号覆盖）并在 `tianshu-ai-persistence-it` profile `<includes>` 追加 `**/DdcSiPersistenceIT.java`。
- Symbols: BOM-managed dependencies + `<include>**/DdcSiPersistenceIT.java</include>`.
- Repository evidence: `tianshu-admin/pom.xml` L397–430。
- Dependencies and consumers: Step 4 IT 与 Step 5 混合检索服务。
- Why now: 唯一 POM 修改点，集中完成依赖引进。
- Contract/signature changes: 不触碰任何外围依赖或父 BOM。
- Input/output and state mapping: Maven 依赖树合法解析。
- Error and edge behavior: 缺私服/本地包将在 `dependency:resolve` 立即发现。
- Standards impact: `MC-DEP-001, MC-REUSE-001, MC-UTIL-001`.
- Literal rule enforcement: `Rule 5, Rule 11`.
- Required change: 向 `tianshu-admin/pom.xml` 写入两项依赖坐标并更新 IT `<includes>` 清单。
- Verification contribution: 让 IT profile 执行 `DdcSiPersistenceIT`。
- After this file: 工程构建配置就绪。

#### File 3 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/db/egon-mp/V20261116_001__tianshu_si_resource_governance.sql`
- Purpose: 构建 5 张核心 SI 表（`ddc_si_resource_t0`, `ddc_si_version_t0`, `ddc_si_binding_t0`, `ddc_si_reference_t0`, `ddc_si_embedding_chunk_t0`）、开启 PG `vector` 扩展、HNSW/GIN 索引、复合外键、软删唯一索引、`tianshu:si:*` 权限种子以及历史 `ddc_ai_resource_t0` 中 `PROMPT`/`SKILL` 之幂等初始回填。
- Symbols: 5 tables, `uk_si_*_live` partial unique indexes, HNSW cosine index, GIN tsvector indexes, permission seeds, backfill block.
- Repository evidence: 紧循 `V20261018_001`, `V20261022_001` 与 `V20261023_001`。
- Dependencies and consumers: 全系持久层 PO/Mapper 与应用 RBAC 初始环境。
- Why now: 物理模型率先稳定。
- Contract/signature changes: 新业务时间列一律 `timestamptz(6)`，逻辑删除沿用 `deleted_at timestamp(6) without time zone` 配局部唯一索引；全部回填及种子采用 `ON CONFLICT DO NOTHING` 保幂等且不动历史文件。
- Input/output and state mapping: 执行迁移 -> 5 表就位及现有 AI 资产平滑投射入 SI 目录。
- Error and edge behavior: 严格 CHECK（ID/租户正整数、版本非负、向量固定 1536 维与 JSONB 格式限制）。
- Standards impact: `MC-SCOPE-001, MC-TIME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 10, Rule 11`.
- Required change: 创建 `V20261116_001__tianshu_si_resource_governance.sql` 写入上述全部 DDL 与幂等初始化语句。
- Verification contribution: 由 `DdcSiPersistenceIT` 在实际 PG/`pgvector` 实例中执行建库验证。
- After this file: 物理架构落成。

#### File 4 — `MODIFY tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/db/egon-mp/repository-manifest.json`
- Purpose: 将 `20261116_001` 条目及经 `shasum -a 256` 实算的真实 64 位 SHA-256 摘要登记于 `scripts` 数组尾。
- Symbols: Manifest entry for `20261116_001`.
- Repository evidence: `repository-manifest.json` L1–41（前 7 条结构）。
- Dependencies and consumers: `egon-mp` 迁移校验器。
- Why now: 保持数据库脚本与校验清单绝对匹配。
- Contract/signature changes: 原 7 条与 `freshBaselineVersion` 不动。
- Input/output and state mapping: 真实文件摘要写入清单。
- Error and edge behavior: 摘要偏差即触发启动失败。
- Standards impact: `MC-SCOPE-001, MC-TEST-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 在 `V20261116_001` 文件定稿后计算实测 SHA-256 并写入 `repository-manifest.json`。
- Verification contribution: 通过 `egon-mp` 清单校验放行。
- After this file: 迁移完整性验证对齐。

#### File 5 — `MODIFY tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcAiKindPersistenceEnum.java`
- Purpose: 扩展统一持久化资源类型加入 `AGENT(203)` 与 `MCP(204)`，同时严格保护 `DdcAiEnumConverter` 维持原样不暴露给旧接口。
- Symbols: `AGENT`, `MCP`.
- Repository evidence: `DdcAiKindPersistenceEnum.java` L1–41；`DdcAiEnumConverter.java` L1–48。
- Dependencies and consumers: PO 与 Step 5 `DdcSiEnumConverter`。
- Why now: 使持久化对象支持四元 SI 资源分类。
- Contract/signature changes: 旧项 `PROMPT(201)`、`SKILL(202)` 保持原样不变。
- Input/output and state mapping: 枚举名称串持久化入库。
- Error and edge behavior: 未知值报错。
- Standards impact: `MC-NAME-001, MC-CONVERT-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 11`.
- Required change: 追加 `AGENT("AGENT", 203, "Agent")` 与 `MCP("MCP", 204, "Mcp")` 枚举常量。
- Verification contribution: 新老库写入互不干扰测试通过。
- After this file: 资源分类枚举扩充完毕。

#### File 6 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiLifecycleStatusPersistenceEnum.java`
- Purpose: 生命周期状态 DB `@EnumValue` 映射枚举。
- Symbols: `DRAFT(301)`, `PUBLISHED(302)`, `DEPRECATED(303)`, `ARCHIVED(304)`.
- Repository evidence: 仿照 `DdcAiKindPersistenceEnum` 格式。
- Dependencies and consumers: `DdcSiResourcePO`, `DdcSiVersionPO`, `DdcSiEnumConverter`。
- Why now: PO 状态映射所需。
- Contract/signature changes: 实现 `EgonEnum`。
- Input/output and state mapping: Java Enum <-> PostgreSQL varchar。
- Error and edge behavior: 未知码抛异常。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 新建 `DdcSiLifecycleStatusPersistenceEnum`。
- Verification contribution: `DdcSiPersistenceIT` 读写覆盖。
- After this file: 生命周期入库枚举完成。

#### File 7 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiBindingStatusPersistenceEnum.java`
- Purpose: 应用绑定启用状态持久化枚举。
- Symbols: `ACTIVE(401)`, `INACTIVE(402)`.
- Repository evidence: `EgonEnum` 规范。
- Dependencies and consumers: `DdcSiBindingPO`, `DdcSiEnumConverter`。
- Why now: 绑定表类型安全映射。
- Contract/signature changes: 实现 `EgonEnum`。
- Input/output and state mapping: Java Enum <-> PostgreSQL varchar。
- Error and edge behavior: 非法值报错。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 新建 `DdcSiBindingStatusPersistenceEnum`。
- Verification contribution: `DdcSiPersistenceIT` 覆盖。
- After this file: 绑定状态入库枚举完成。

#### File 8 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiSourceTypePersistenceEnum.java`
- Purpose: 资源注册来源（`CONSOLE`, `AUTO_REPORTED`, `GITOPS`）DB 枚举。
- Symbols: `CONSOLE(501)`, `AUTO_REPORTED(502)`, `GITOPS(503)`.
- Repository evidence: `EgonEnum` 规范。
- Dependencies and consumers: `DdcSiResourcePO`, `DdcSiEnumConverter`。
- Why now: 主表来源隔离所需。
- Contract/signature changes: 实现 `EgonEnum`。
- Input/output and state mapping: Java Enum <-> PostgreSQL varchar。
- Error and edge behavior: 非法值报错。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 新建 `DdcSiSourceTypePersistenceEnum`。
- Verification contribution: `DdcSiPersistenceIT` 覆盖。
- After this file: 来源持久化枚举完成。

#### File 9 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiReferenceTargetTypePersistenceEnum.java`
- Purpose: 依赖引用边目标类别 DB 枚举。
- Symbols: `AGENT(601)`, `MCP(602)`, `PROMPT(603)`, `SKILL(604)`, `TIANJI_PROCESS(605)`.
- Repository evidence: `EgonEnum` 规范。
- Dependencies and consumers: `DdcSiReferencePO`, `DdcSiEnumConverter`。
- Why now: 引用表强类型落库。
- Contract/signature changes: 实现 `EgonEnum`。
- Input/output and state mapping: Java Enum <-> PostgreSQL varchar。
- Error and edge behavior: 未知值报错。
- Standards impact: `MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 新建 `DdcSiReferenceTargetTypePersistenceEnum`。
- Verification contribution: `DdcSiPersistenceIT` 覆盖。
- After this file: 全持久化枚举齐备。

#### File 10 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/typehandler/DdcSiVectorTypeHandler.java`
- Purpose: 纯 JDBC 实现 `float[]` 到 `pgvector` `vector(1536)` 双向类型转换，彻底避免第三方非 BOM JAR 依赖。
- Symbols: `DdcSiVectorTypeHandler extends BaseTypeHandler`.
- Repository evidence: 参照已验证 `yuheng-service/.../GatewayVectorTypeHandler.java`。
- Dependencies and consumers: `DdcSiEmbeddingChunkPO`, `DdcSiEmbeddingChunkMapper.xml`.
- Why now: 向量写入及读取核心句柄。
- Contract/signature changes: 使用 PG `Types.OTHER` 与无空格 `[v1,v2,...]` 文本表达式。
- Input/output and state mapping: `float[]` <-> PG `vector`；DB `null` 返回 Java `null`。
- Error and edge behavior: 写入检测数组正长度及全维 `Float.isFinite`，非有限/坏文本抛出明确 `SQLException`。
- Standards impact: `MC-REUSE-001, MC-UTIL-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 5, Rule 11`.
- Required change: 创建 `DdcSiVectorTypeHandler` 落实安全向量字面量拼写与解析方法。
- Verification contribution: 驱动 `DdcSiPersistenceIT` 1536 维测试和 `NaN` 拦截转绿。
- After this file: ORM 支持原生 `pgvector`。

#### File 11 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiResourcePO.java`
- Purpose: 映射主治理表 `ddc_si_resource_t0`。
- Symbols: `DdcSiResourcePO` (`@TableName("ddc_si_resource_t0")`, `@Version`, `@TableLogic`).
- Repository evidence: 参照 `DdcAiResourcePO.java` L1–60。
- Dependencies and consumers: `DdcSiResourceMapper`, `DdcSiResourceConverter`。
- Why now: 支撑主表 CRUD。
- Contract/signature changes: `@Version` 保护并发修改；时间映射对齐 §4.9。
- Input/output and state mapping: Java 字段 <-> 列。
- Error and edge behavior: 乐观锁零行更新异常。
- Standards impact: `MC-MODEL-001, MC-NAME-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiResourcePO`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 主资源 PO 完成。

#### File 12 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiVersionPO.java`
- Purpose: 映射版本快照表 `ddc_si_version_t0`。
- Symbols: `DdcSiVersionPO` (`@TableName("ddc_si_version_t0")`).
- Repository evidence: 仿照 `DdcAiVersionPO` 结构风格。
- Dependencies and consumers: `DdcSiVersionMapper`, `DdcSiResourceConverter`。
- Why now: 不可变版本快照落库。
- Contract/signature changes: 含 `digestSha256`, `sourceEnvCode`, `sourceVersionNo`, `indexStatus`, `publishedTime`, `indexedTime`。
- Input/output and state mapping: 快照字段 <-> 表行。
- Error and edge behavior: 唯一索引拦截版本号碰撞。
- Standards impact: `MC-MODEL-001, MC-NAME-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiVersionPO`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 快照 PO 完成。

#### File 13 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiBindingPO.java`
- Purpose: 映射应用服务绑定表 `ddc_si_binding_t0`。
- Symbols: `DdcSiBindingPO` (`@TableName("ddc_si_binding_t0")`, `@Version`).
- Repository evidence: 现有 PO 基线风格。
- Dependencies and consumers: `DdcSiBindingMapper`, `DdcSiResourceConverter`。
- Why now: 存储各 `appCode` 锁定指针。
- Contract/signature changes: 支持 `pinnedVersionNo` 为空或特定快照号及 `boundTime` (`Instant`)。
- Input/output and state mapping: 绑定实体 <-> 表行。
- Error and edge behavior: 唯一索引拦截双活跃绑定。
- Standards impact: `MC-MODEL-001, MC-NAME-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiBindingPO`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 绑定 PO 完成。

#### File 14 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiReferencePO.java`
- Purpose: 映射资源与流程有向依赖表 `ddc_si_reference_t0`。
- Symbols: `DdcSiReferencePO` (`@TableName("ddc_si_reference_t0")`).
- Repository evidence: 现有 PO 基线风格。
- Dependencies and consumers: `DdcSiReferenceMapper`, `DdcSiResourceConverter`。
- Why now: 存储依赖拓扑支撑无环和归档检测。
- Contract/signature changes: 带跨租户保护外键字段。
- Input/output and state mapping: 依赖实体 <-> 表行。
- Error and edge behavior: 跨租户挂载触犯复合 FK `23503`。
- Standards impact: `MC-MODEL-001, MC-NAME-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiReferencePO`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 引用 PO 完成。

#### File 15 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiEmbeddingChunkPO.java`
- Purpose: 映射分片与向量存储表 `ddc_si_embedding_chunk_t0`。
- Symbols: `DdcSiEmbeddingChunkPO` (`@TableName(value = "ddc_si_embedding_chunk_t0", autoResultMap = true)`).
- Repository evidence: 现行 `autoResultMap` 写法 + Step 4 `DdcSiVectorTypeHandler`。
- Dependencies and consumers: `DdcSiEmbeddingChunkMapper`。
- Why now: 支撑 HNSW 矢量与 FTS 搜索写入。
- Contract/signature changes: 向量属性标记 `@TableField(typeHandler = DdcSiVectorTypeHandler.class)`。
- Input/output and state mapping: 分片正文+float 向量 <-> 数据库列。
- Error and edge behavior: 维度错配拒写。
- Standards impact: `MC-MODEL-001, MC-UTIL-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 5, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiEmbeddingChunkPO`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 全部五张表的持久化实体落盘。

#### File 16 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiResourceMapper.java`
- Purpose: 定义主资源条件乐观锁更新、多维检索分页与 TSVECTOR 更新数据库端口。
- Symbols: `DdcSiResourceMapper extends BaseMapper`.
- Repository evidence: 已有 `dao/DdcAiResourceMapper.java`。
- Dependencies and consumers: `DdcSiResourceRepositoryImpl`。
- Why now: 封装表级原子查询与修改语句。
- Contract/signature changes: 全方法带必填 `tenantId` 过滤参数。
- Input/output and state mapping: 作用域参数 -> 分页 PO / 更新影响行数。
- Error and edge behavior: 全预编译占位符 `#{...}` 防 SQL 注入。
- Standards impact: `MC-ARCH-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 创建 `DdcSiResourceMapper` 接口。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 主表 Mapper 接口完成。

#### File 17 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiVersionMapper.java`
- Purpose: 定义版本序号安全递增量查及跨环境版本矩阵聚集方法。
- Symbols: `DdcSiVersionMapper extends BaseMapper`.
- Repository evidence: `dao/` 既有命名方式。
- Dependencies and consumers: `DdcSiResourceRepositoryImpl`。
- Why now: 保证快照编号严格单调递增及只读。
- Contract/signature changes: 提供 `selectNextVersionNo`, `selectEnvMatrixRows`, `updateIndexStatus`。
- Input/output and state mapping: 作用域+resourceId -> 下一版本号 / 矩阵集合。
- Error and edge behavior: 参数严密预编译。
- Standards impact: `MC-ARCH-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiVersionMapper` 接口。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 快照 Mapper 接口完成。

#### File 18 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiBindingMapper.java`
- Purpose: 定义应用服务维度活跃指针查询与覆盖接口。
- Symbols: `DdcSiBindingMapper extends BaseMapper`.
- Repository evidence: `dao/` 规范。
- Dependencies and consumers: `DdcSiResourceRepositoryImpl`。
- Why now: 支持快速路由查绑。
- Contract/signature changes: 作用域 + `appCode` + `kind` + `resourceKey` 定位活跃行。
- Input/output and state mapping: 绑定维度参数 -> `DdcSiBindingPO`。
- Error and edge behavior: 强租户边界。
- Standards impact: `MC-ARCH-001`.
- Literal rule enforcement: `Rule 1, Rule 11`.
- Required change: 创建 `DdcSiBindingMapper` 接口。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 绑定 Mapper 接口完成。

#### File 19 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiReferenceMapper.java`
- Purpose: 定义正反向引用边读写、阻断归档活跃引用检索与天机流程引用替换方法。
- Symbols: `DdcSiReferenceMapper extends BaseMapper`.
- Repository evidence: `dao/` 规范。
- Dependencies and consumers: `DdcSiResourceRepositoryImpl`。
- Why now: 支撑环路校验与安全删除门禁。
- Contract/signature changes: 提供 `selectBlockingInboundReferences`, `selectAgentGraphEdges`, `deleteProcessReferences`。
- Input/output and state mapping: 目标键 -> 上游阻断边清单。
- Error and edge behavior: 严格同租户约束。
- Standards impact: `MC-ARCH-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 创建 `DdcSiReferenceMapper` 接口。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 引用 Mapper 接口完成。

#### File 20 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiEmbeddingChunkMapper.java`
- Purpose: 定义 HNSW 矢量 + GIN 全文 CTE 混合检索 RRF 方法及纯 FTS 降级查询与版本分片清理覆盖方法。
- Symbols: `DdcSiEmbeddingChunkMapper extends BaseMapper` (`hybridSearchRrf`, `ftsFallbackSearch`, `deleteByVersionId`).
- Repository evidence: `dao/` 规范与 PG `vector` 语义。
- Dependencies and consumers: `DdcSiSemanticIndexServiceImpl`。
- Why now: 直达矢量与全文数据库引擎的核心通道。
- Contract/signature changes: 参数包含归一化向量字串、原始搜索词、RRF `k`、`candidateLimit` 与 `topK`。
- Input/output and state mapping: 矢量+文本 -> 融合排序结果投影行。
- Error and edge behavior: 仅传递有效非空向量串联进 `hybridSearchRrf`。
- Standards impact: `MC-ARCH-001, MC-UTIL-001`.
- Literal rule enforcement: `Rule 1, Rule 5, Rule 11`.
- Required change: 创建 `DdcSiEmbeddingChunkMapper` 接口。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 全部 5 个 DAO 接口落定。

#### File 21 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiResourceMapper.xml`
- Purpose: 编写 `DdcSiResourceMapper` 对应的多条件 JSONB `@>` 标签过滤、条件版本更新和 `to_tsvector('simple', ...)` 更新 SQL。
- Symbols: `<mapper namespace="...DdcSiResourceMapper">`.
- Repository evidence: `resources/mybatis/mapper/tianshu/` 既有 XML 结构与 `application.yml` `mapper-locations`。
- Dependencies and consumers: MyBatis 映射绑定。
- Why now: 落地主表原生查询优化 SQL。
- Contract/signature changes: 强制预编译变量绑定并排除 `deleted_at IS NOT NULL`。
- Input/output and state mapping: 查询参数 -> `DdcSiResourcePO`。
- Error and edge behavior: 全参数转义安全无注入风险。
- Standards impact: `MC-ARCH-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 11`.
- Required change: 创建 `DdcSiResourceMapper.xml`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 主表 SQL 落地。

#### File 22 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiVersionMapper.xml`
- Purpose: 编写版本递增量查与跨环境活跃快照聚集矩阵 SQL。
- Symbols: `<mapper namespace="...DdcSiVersionMapper">`.
- Repository evidence: `resources/mybatis/mapper/tianshu/` 风格。
- Dependencies and consumers: `DdcSiVersionMapper`。
- Why now: 支撑版本号递进与多环境横向矩阵对比。
- Contract/signature changes: 严格绑定 `tenant_id` 并按 `env_code` 分组返回活跃版。
- Input/output and state mapping: resourceKey -> 各环境行投影。
- Error and edge behavior: 无。
- Standards impact: `MC-ARCH-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 10, Rule 11`.
- Required change: 创建 `DdcSiVersionMapper.xml`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 快照 SQL 落地。

#### File 23 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiBindingMapper.xml`
- Purpose: 编写绑定覆盖和按 `appCode` 查询活跃指针 SQL。
- Symbols: `<mapper namespace="...DdcSiBindingMapper">`.
- Repository evidence: `resources/mybatis/mapper/tianshu/` 风格。
- Dependencies and consumers: `DdcSiBindingMapper`。
- Why now: 完成应用版本锁定语句。
- Contract/signature changes: 仅查询 `deleted_at IS NULL` 且 `binding_status = 'ACTIVE'` 行。
- Input/output and state mapping: 作用域+appCode -> 绑定 PO。
- Error and edge behavior: 无。
- Standards impact: `MC-ARCH-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 创建 `DdcSiBindingMapper.xml`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 绑定 SQL 落地。

#### File 24 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiReferenceMapper.xml`
- Purpose: 编写引用边写入、图环邻接抽出与反向被引用（含活跃天机流程 `TIANJI_PROCESS`）排查 SQL。
- Symbols: `<mapper namespace="...DdcSiReferenceMapper">`.
- Repository evidence: `resources/mybatis/mapper/tianshu/` 风格。
- Dependencies and consumers: `DdcSiReferenceMapper`。
- Why now: 保障环检测和归档拦截精准提取依赖图。
- Contract/signature changes: 关联过滤主资源处于非归档/活跃版本，排除已下线失效废边。
- Input/output and state mapping: 目标键 -> 上游依赖边列表。
- Error and edge behavior: 强租户条件限定。
- Standards impact: `MC-ARCH-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 创建 `DdcSiReferenceMapper.xml`。
- Verification contribution: `DdcSiPersistenceIT`。
- After this file: 引用图 SQL 落地。

#### File 25 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiEmbeddingChunkMapper.xml`
- Purpose: 编写 `pgvector` 余弦距离 CTE + PostgreSQL `tsvector` CTE + RRF（`1.0 / (k + rank)`）全外连接打分归并 SQL 以及 `ftsFallbackSearch` 纯全文 SQL。
- Symbols: `<mapper namespace="...DdcSiEmbeddingChunkMapper">`, `hybridSearchRrf`, `ftsFallbackSearch`.
- Repository evidence: PostgreSQL `vector_cosine_ops` + GIN `plainto_tsquery` 标准方言。
- Dependencies and consumers: `DdcSiSemanticIndexServiceImpl`。
- Why now: Step 4 实库搜索验证必须运行这两条原生 SQL。
- Contract/signature changes: CTE 内先行按租户+作用域+活跃态过滤再利用索引剪枝，按 `resource_id` 合并取最优分片避免单文档多切片独占全部排名。
- Input/output and state mapping: 作用域+矢量+查询文本 -> 按融合高分倒序 `topK` 投影结果集。
- Error and edge behavior: 参数完全预编译绑定。
- Standards impact: `MC-ARCH-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 11`.
- Required change: 创建 `DdcSiEmbeddingChunkMapper.xml` 并实现两段核心检索 CTE SQL（RRF 混合与 FTS 降级）及清理重写语句。
- Verification contribution: 驱动 `DdcSiPersistenceIT` 在真实容器跑通 HNSW 与 RRF 排序断言，Step 4 收官转绿。
- After this file: 全部持久层 SQL 准备完成。

#### File 26 — `MODIFY tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/egon-tianshu-persistence.yml`
- Purpose: 将新增五张 SI 逻辑表（`ddc_si_resource`, `ddc_si_version`, `ddc_si_binding`, `ddc_si_reference`, `ddc_si_embedding_chunk`）注册为 `STANDARD_TENANT_ID` 分片路由策略，确保 ShardingSphere 与 PO `@TableName` 精确映射至物理 `*_t0` 表。
- Symbols: `sharding.tables` (`ddc_si_*`).
- Repository evidence: `egon-tianshu-persistence.yml` L45–66 `ddc_ai_*` 配置模式。
- Dependencies and consumers: 所有 SI PO 与 XML Mapper。
- Why now: 缺分片表配置将导致 ShardingSphere 运行期找不到 `ddc_si_*_t0` 表。
- Contract/signature changes: 原表条目一字不变，仅追加 5 个 SI 条目。
- Input/output and state mapping: 逻辑表名 -> `*_t0` 单库分片表。
- Error and edge behavior: 表契约校验测试不遗漏新表。
- Standards impact: `MC-ARCH-001, MC-CONFIG-001`.
- Literal rule enforcement: `Rule 7, Rule 11`.
- Required change: 在 `egon-tianshu-persistence.yml` 补充 5 个 `ddc_si_*` `STANDARD_TENANT_ID` 条目。
- Verification contribution: 契约与 IT 均验证通过。
- After this file: 全部持久层准备完成。

- Validation working directory: `tianshu-service`
- Verification command: `./mvnw -pl stellar-enclosure-tianshu-admin dependency:resolve && ./mvnw -pl stellar-enclosure-tianshu-admin -Ptianshu-ai-persistence-it -Dtest=DdcSiPersistenceIT,DdcAiPersistenceIT verify`
- Expected result: 真实 PG/`pgvector` 实例建表校验、1536 维测试、HNSW 搜索和原有 `DdcAiPersistenceIT` 全部通过绿灯。
- Failure returns to: 检查 DDL 语法、实算 `shasum -a 256` 更新 manifest 或向量 SQL 字段绑定，重测直至通过。
- Completion criteria: `TEST-005` 实库全绿且历史功能零损。
- Rollback: 局部 `git revert` Step 4 提交并重置试验数据库 5 张 SI 表。
- Commit paths: `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/persistence/DdcSiPersistenceIT.java`, `tianshu-service/stellar-enclosure-tianshu-admin/pom.xml`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/db/egon-mp/V20261116_001__tianshu_si_resource_governance.sql`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/db/egon-mp/repository-manifest.json`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcAiKindPersistenceEnum.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiLifecycleStatusPersistenceEnum.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiBindingStatusPersistenceEnum.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiSourceTypePersistenceEnum.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/enums/DdcSiReferenceTargetTypePersistenceEnum.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/typehandler/DdcSiVectorTypeHandler.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiResourcePO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiVersionPO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiBindingPO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiReferencePO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/po/DdcSiEmbeddingChunkPO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiResourceMapper.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiVersionMapper.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiBindingMapper.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiReferenceMapper.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/dao/DdcSiEmbeddingChunkMapper.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiResourceMapper.xml`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiVersionMapper.xml`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiBindingMapper.xml`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiReferenceMapper.xml`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/mybatis/mapper/tianshu/si/DdcSiEmbeddingChunkMapper.xml`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/egon-tianshu-persistence.yml`
- Commit: `feat(tianshu-admin): deliver SI schema V20261116_001, POs, JDBC vector handler and Mappers`

---

### Step 5 — Implement tianshu-admin domain BOs, MapStruct converters, repository, strategies, Outbox and RRF search service

- Requirements: `REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008`
- Dependencies: `Step 2, Step 4`
- Baseline state: 持久化表、PO 与 Mapper 已经实库验证，尚缺领域层 BO、MapStruct 转换、仓储协同、多态校验策略、图环检测、状态迁移策略、Outbox 通知和混合检索业务层实现。
- Observable outcome: 全领域单测覆盖并发 409、不可变回滚/跨环境晋级、绑定优先解析、禁流程/禁密钥策略检验、DAG 环拦截、自注册护盾与超时 FTS 无缝降级（`TEST-001`..`003`, `006`, `007`, `008`）全部通过。
- End state: 领域核心 `DdcSiResourceService` 与 `DdcSiSemanticIndexService` 完全就位等待 Step 6 对外公开暴露。
- Test-first gate: `Required — 先建 4 套领域/策略/依赖/索引单元测试锁定全量业务逻辑与边界异常`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-REUSE-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-JSON-001, MC-PATTERN-001, MC-TIME-001, MC-TEST-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 5, Rule 6, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiContentStrategyTest.java`
- Purpose: 建立校验四类资源解析策略、拒绝静态流字段、拒绝明文凭据与摘要一致性的 RED 单元测试（`TEST-004` 核心）。
- Symbols: `DdcSiContentStrategyTest`.
- Repository evidence: `service/ai/` JUnit 5/Mockito 单测范式。
- Dependencies and consumers: 测试 File 15..18。
- Why now: 先锁定载荷校验边界。
- Contract/signature changes: 非法 Schema / 工作流关键字 / 明文凭据抛出 `422` 域校验异常。
- Input/output and state mapping: JSON 不同键顺序输入 -> 归一化算出完全一致 SHA-256。
- Error and edge behavior: 测试 `nodes`, `edges`, `bpmnXml`, `sk-...` 注入被精确定位拒绝。
- Standards impact: `MC-TEST-001, MC-PATTERN-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 9, Rule 11`.
- Required change: 新建 `DdcSiContentStrategyTest` 实现上述全部策略正反断言。
- Verification contribution: 验证策略模式覆盖四态规则。
- After this file: 策略检验契约固定。

#### File 2 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiDependencyValidatorTest.java`
- Purpose: 建立 Agent 闭环检测与被启用流程/资源引用拦截归档之 RED 单测（`TEST-008`）。
- Symbols: `DdcSiDependencyValidatorTest`.
- Repository evidence: 现有领域图校验测试结构。
- Dependencies and consumers: 测试 File 19。
- Why now: 固定 DAG 与归档安全保护边界。
- Contract/signature changes: 自环与 A->B->C->A 间接环均拒；归档时发现活跃 `TIANJI_PROCESS` 边抛出 409 阻断异常。
- Input/output and state mapping: 邻接表及出度集合 -> 通过或精确阻断列表报错。
- Error and edge behavior: 测试跨环境缺依赖目标拦截晋级。
- Standards impact: `MC-TEST-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 11`.
- Required change: 新建 `DdcSiDependencyValidatorTest`。
- Verification contribution: 验证依赖与安全删除逻辑全通。
- After this file: 依赖图门禁红灯设定完毕。

#### File 3 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiResourceServiceImplTest.java`
- Purpose: 覆盖 `TEST-001`, `TEST-002`, `TEST-003`, `TEST-006` 核心业务状态机单测：并发冲突 409、不可变回滚与晋级溯源、`appCode` 绑定优先路线图、自上报哈希一致零更新及不覆盖人工 `CONSOLE` 发布版保护。
- Symbols: `DdcSiResourceServiceImplTest`.
- Repository evidence: 参照 `DdcAi*ServiceImplTest` Mock 仓储及事件验证方式。
- Dependencies and consumers: 测试 File 5..14, File 20..21, File 24..25。
- Why now: 锁定所有关键事务逻辑与 Outbox 事件发送契约。
- Contract/signature changes: 异常回滚测试验证零事件与零向量重建调用。
- Input/output and state mapping: 服务调用 -> 预期返回值、锁异常、事件对象或降级返回。
- Error and edge behavior: 检验已归档资源拒绝直接更新或再发布。
- Standards impact: `MC-TEST-001, MC-TIME-001, MC-PATTERN-001`.
- Literal rule enforcement: `Rule 2, Rule 9, Rule 10, Rule 11`.
- Required change: 新建 `DdcSiResourceServiceImplTest` 完整实现并发、晋级、回滚、绑定与自注册单测矩阵。
- Verification contribution: Step 5 总业务验收单测基石。
- After this file: 服务核心契约固定。

#### File 4 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexServiceImplTest.java`
- Purpose: 覆盖 `TEST-007` 异步切分建索引、防重入锁、正常 RRF 返回以及 `EmbeddingModel` 缺失或 `1500ms` 超时自动降级返回 `degradedToFtsOnly = true` 之 RED 单测。
- Symbols: `DdcSiSemanticIndexServiceImplTest`.
- Repository evidence: Mock `EmbeddingModel` 与 Mapper 查询交互断言。
- Dependencies and consumers: 测试 File 22..23。
- Why now: 明确混合检索不因向量算力宕机瘫痪。
- Contract/signature changes: 断言向量维数错配安全终止而不销毁既有历史切片。
- Input/output and state mapping: 问句 -> Embedding 超时/正常 -> RRF 命中集或标注降级之 FTS 结果。
- Error and edge behavior: 并发重建同版本遭 Redis 锁拦截跳过而不并发打外部向量接口。
- Standards impact: `MC-TEST-001, MC-TIME-001, MC-LOG-001`.
- Literal rule enforcement: `Rule 4, Rule 9, Rule 10, Rule 11`.
- Required change: 新建 `DdcSiSemanticIndexServiceImplTest`。
- Verification contribution: 确保语义搜索和容灾机制稳健无瑕疵。
- After this file: Step 5 四套测试基准悉数锁定。

#### File 5 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiResourceBO.java`
- Purpose: SI 主资源领域内部聚合表现对象（严格位于仓库既有平铺 `model/bo/` 包内）。
- Symbols: `DdcSiResourceBO`.
- Repository evidence: 已验证包中 `DdcAiResourceBO.java` 风格。
- Dependencies and consumers: `DdcSiResourceConverter`, `DdcSiResourceRepository`, `DdcSiResourceServiceImpl`。
- Why now: 支撑领域逻辑运算而不裸露 DB PO。
- Contract/signature changes: 持有完整作用域、状态、来源、`activeVersionNo` 及乐观锁 `version`。
- Input/output and state mapping: PO <-> BO <-> DTO/VO。
- Error and edge behavior: 保证时间采用 `Instant`。
- Standards impact: `MC-MODEL-001, MC-NAME-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 10, Rule 11`.
- Required change: 在 `model/bo/` 创建 `DdcSiResourceBO`。
- Verification contribution: 供服务和转换测试使用。
- After this file: 主资源 BO 完成。

#### File 6 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiVersionBO.java`
- Purpose: 不可变快照及环境溯源内部 BO。
- Symbols: `DdcSiVersionBO`.
- Repository evidence: `model/bo/` 包风格。
- Dependencies and consumers: 转换器与仓储服务。
- Why now: 载体封装版本细节。
- Contract/signature changes: 含 `publishedAt`, `indexedTime` (`Instant`) 与 `sourceEnvCode`/`sourceVersionNo`。
- Input/output and state mapping: 版本实体 <-> BO。
- Error and edge behavior: 无。
- Standards impact: `MC-MODEL-001, MC-NAME-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiVersionBO`。
- Verification contribution: 版本快照流程测试覆及。
- After this file: 快照 BO 完成。

#### File 7 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiBindingBO.java`
- Purpose: 应用作用域版本锁定绑定 BO。
- Symbols: `DdcSiBindingBO`.
- Repository evidence: `model/bo/` 包风格。
- Dependencies and consumers: 仓储与绑定接口逻辑。
- Why now: 隔离绑定行结构。
- Contract/signature changes: 含 `pinnedVersionNo`, `bindingStatus`, `boundTime` (`Instant`), `version`。
- Input/output and state mapping: PO <-> BO。
- Error and edge behavior: 乐观锁保护更新。
- Standards impact: `MC-MODEL-001, MC-NAME-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiBindingBO`。
- Verification contribution: 绑定路由测试用例覆盖。
- After this file: 绑定 BO 完成。

#### File 8 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiReferenceBO.java`
- Purpose: 有向资源/流程依赖边内部 BO。
- Symbols: `DdcSiReferenceBO`.
- Repository evidence: `model/bo/` 包风格。
- Dependencies and consumers: `DdcSiDependencyValidator`, `DdcSiContentStrategy`。
- Why now: 图验证及提取通用结构。
- Contract/signature changes: 含 `targetType`, `targetKey`, `pinnedVersionNo`, `required`, `roleHint`。
- Input/output and state mapping: 规格抽取边 <-> 持久参考边。
- Error and edge behavior: 非空必填约束。
- Standards impact: `MC-MODEL-001, MC-NAME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 11`.
- Required change: 创建 `DdcSiReferenceBO`。
- Verification contribution: 图验证单测使用。
- After this file: 引用 BO 完成。

#### File 9 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiAgentSpecBO.java`
- Purpose: 结构化解析载体，描述原子 Agent 的模型、运行时限制（`maxSteps`, `timeoutSeconds`, `temperature`）、提示词/技能/MCP/子代理引用与输入输出 Schema。
- Symbols: `DdcSiAgentSpecBO` (`normalizedDigestJson()`, runtime bounds).
- Repository evidence: Spec §6.2 字段规范。
- Dependencies and consumers: `DdcSiAgentContentStrategy`。
- Why now: 支撑 Agent 强类型反序列化和校验。
- Contract/signature changes: 不含流程控制字段且标注 Jackson 显式未知字段控制策略。
- Input/output and state mapping: 原始 JSON <-> BO <-> 确定性排序串。
- Error and edge behavior: 反序列化非法类型直接抛异常转化为 422。
- Standards impact: `MC-MODEL-001, MC-JSON-001, MC-TIME-001` (`timeoutSeconds` 整数时间限值).
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 11`.
- Required change: 创建 `DdcSiAgentSpecBO`（含内嵌结构化 Model 与 RuntimeConfig 值对象）。
- Verification contribution: `DdcSiContentStrategyTest` 核验确定性排序和边界约束。
- After this file: Agent 结构定义定格。

#### File 10 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiMcpSpecBO.java`
- Purpose: 结构化解析载体，描述 MCP `protocolVersion`, `transport` (`STDIO`/`SSE`/`STREAMABLE_HTTP`), `endpoint`, 启动参数、工具清单与凭据引用名（严禁原秘钥）。
- Symbols: `DdcSiMcpSpecBO`.
- Repository evidence: Spec §6.2 MCP 规范。
- Dependencies and consumers: `DdcSiMcpContentStrategy`。
- Why now: 支撑 MCP 结构合法校验及检索摘要提取。
- Contract/signature changes: `@JsonIgnoreProperties` 严守规范字段，敏感引用做脱敏展示投影。
- Input/output and state mapping: 原始 JSON <-> MCP BO <-> 检索文本。
- Error and edge behavior: 凭据越界泄漏拦截。
- Standards impact: `MC-MODEL-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 11`.
- Required change: 创建 `DdcSiMcpSpecBO`。
- Verification contribution: `DdcSiContentStrategyTest`。
- After this file: MCP 结构定义及全 6 BO 完备。

#### File 11 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiEnumConverter.java`
- Purpose: 全新独立 MapStruct 转换接口映射 5 组 SI 持久化 enum <-> starter enum（含四态 `DdcAiKindPersistenceEnum`），从而绝对保留 `DdcAiEnumConverter` 行为不变。
- Symbols: `DdcSiEnumConverter` (`@Mapper`, `@ValueMapping(ANY_UNMAPPED -> THROW_EXCEPTION)`).
- Repository evidence: 参照 `DdcAiEnumConverter.java`。
- Dependencies and consumers: `DdcSiResourceConverter`。
- Why now: 保护旧 AI 接口免受新枚举异常行为污染。
- Contract/signature changes: 覆盖全部 4 种 SI 类型枚举转换。
- Input/output and state mapping: 持久化枚举 <-> 契约枚举。
- Error and edge behavior: 未映射态立刻抛出显式转换异常。
- Standards impact: `MC-CONVERT-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 11`.
- Required change: 新建 `DdcSiEnumConverter`（且不碰 `DdcAiEnumConverter.java`）。
- Verification contribution: 编译期校验零漏映射。
- After this file: 枚举安全映射隔离就绪。

#### File 12 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiResourceConverter.java`
- Purpose: MapStruct 转换接口连接 PO <-> BO <-> Starter Descriptor。
- Symbols: `DdcSiResourceConverter extends BaseConverter`.
- Repository evidence: 对齐 `DdcAiResourceConverter.java`。
- Dependencies and consumers: `DdcSiResourceRepositoryImpl`, `DdcSiResourceServiceImpl`。
- Why now: 落实 Rule 3 彻底禁用服务内手动 bean copy。
- Contract/signature changes: `uses = DdcSiEnumConverter.class`，带具名 `@Named` 标签 JSON 互转辅助。
- Input/output and state mapping: 五表 PO <-> 领域 BO <-> 跨仓 DTO。
- Error and edge behavior: 编译期检查漏映射字段。
- Standards impact: `MC-CONVERT-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiResourceConverter`。
- Verification contribution: 全字段转换单测覆盖。
- After this file: 模型转换层建立完毕。

#### File 13 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/repository/DdcSiResourceRepository.java`
- Purpose: SI 仓储抽象端口，在仓储接口层启用 `@Validated` 参数分组保护。
- Symbols: `DdcSiResourceRepository` (`@Validated`).
- Repository evidence: 参照仓库既有平铺 `repository/DdcAiResourceRepository.java`。
- Dependencies and consumers: `DdcSiResourceServiceImpl`, `DdcSiDependencyValidator`。
- Why now: 为服务编排层提供语义明晰的数据获取契约。
- Contract/signature changes: 全方法包含明确的 `tenantId`、`bizCode` 与作用域标识。
- Input/output and state mapping: 仓储操作参数 -> BO 或分页结果。
- Error and edge behavior: 异常由实现层统一转换领域冲突与未找到异常。
- Standards impact: `MC-ARCH-001, MC-NAME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 11`.
- Required change: 在 `repository/` 新建接口 `DdcSiResourceRepository`。
- Verification contribution: Mock 仓储支撑全部 Service 单测。
- After this file: 仓储端口建立。

#### File 14 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/repository/impl/DdcSiResourceRepositoryImpl.java`
- Purpose: 协调 5 张表读写、解析 `namespaceId`、将 MP 锁零行更新与唯一违例转为冲突 409、执行依赖边与天机引用更新。
- Symbols: `DdcSiResourceRepositoryImpl` (`@Repository`, `@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: `repository/impl/` 目录风格与现有命名空间查询 Mapper 复用。
- Dependencies and consumers: 注入 5 个 SI Mapper、现有 Namespace 查询与 `DdcSiResourceConverter`。
- Why now: 支撑核心服务执行数据库事务落库。
- Contract/signature changes: 解析 `(tenantId, bizCode, namespaceCode)` 找不到或跨租户即刻抛 404/403 阻止操作继续；乐观更新零影响行明确抛出 `409 Conflict`。
- Input/output and state mapping: BO -> Mapper SQL -> 持久化确认与乐观版本号递增。
- Error and edge behavior: `DuplicateKeyException` 转译为可读 `ALREADY_EXISTS` 409 冲突。
- Standards impact: `MC-ARCH-001, MC-BEAN-001, MC-LOG-001, MC-VALID-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 4, Rule 10, Rule 11`.
- Required change: 在 `repository/impl/` 实现 `DdcSiResourceRepositoryImpl`。
- Verification contribution: 在集成与单测中被验证全部读写及异常映射分支。
- After this file: 数据持久化衔接完成。

#### File 15 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiContentStrategy.java`
- Purpose: 声明针对四类资源的载荷规格验证、规范化排序与 SHA-256 计费摘要、声明引用抽出与可检摘要抽取接口。
- Symbols: `DdcSiContentStrategy` (`supports`, `validateAndNormalize`, `extractReferences`, `extractSearchableText`, `computeDigestSha256`).
- Repository evidence: Spec §6.2 与面向对象 Strategy 模式规范。
- Dependencies and consumers: 3 个策略实现类、ServiceImpl 与索引服务。
- Why now: 统一多态路由抽象，消除业务代码巨大 switch 分支。
- Contract/signature changes: 共用基础确定性 SHA-256 摘要可设为接口默认/共用逻辑避免各实现重复。
- Input/output and state mapping: 原始串 -> 验证归一化结果对象。
- Error and edge behavior: 校验不合格统一抛携带精确 jsonPath 之 422 校验异常。
- Standards impact: `MC-PATTERN-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 9, Rule 11`.
- Required change: 创建 `DdcSiContentStrategy` 接口。
- Verification contribution: 使策略扩展与注入清晰可控。
- After this file: 策略抽象契约确定。

#### File 16 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiAgentContentStrategy.java`
- Purpose: 验证并提取 `AGENT` 载荷（校验模型/运行参数、`inputSchema`/`outputSchema` JSON Schema 合法性，强制阻断包含静态流字段的篡改输入，抽取 Prompt/Skill/MCP/SubAgent 引用边）。
- Symbols: `DdcSiAgentContentStrategy` (`@Component`, `@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: Spec §4/§6.2 原子化红线约束。
- Dependencies and consumers: 注入规范化的 `ObjectMapper`。
- Why now: 兑现 Spec 对 Agent 原子定义的严格校验保障。
- Contract/signature changes: 发现 JSON 中混入 `nodes`, `edges`, `bpmnXml`, `transitions`, `flowable` 等编排关键字立即拒收 `422`。
- Input/output and state mapping: `specJson` -> `DdcSiAgentSpecBO` -> 抽取引用清单和语义描述串。
- Error and edge behavior: 明确返回出问题的 Schema Path。
- Standards impact: `MC-PATTERN-001, MC-VALID-001, MC-JSON-001, MC-BEAN-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 6, Rule 9, Rule 11`.
- Required change: 创建 `DdcSiAgentContentStrategy`。
- Verification contribution: 使 `DdcSiContentStrategyTest` Agent 测试全部通过。
- After this file: Agent 结构检验防线落地。

#### File 17 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiMcpContentStrategy.java`
- Purpose: 验证标准 `MCP` 规格（`protocolVersion`, `transport` vs endpoint/command 匹配度，并深度扫描拒绝将 `sk-` / Bearer / 明文 password 直接塞进文本载荷）。
- Symbols: `DdcSiMcpContentStrategy` (`@Component`, `@Slf4j`).
- Repository evidence: Spec §6.2 MCP 安全边界。
- Dependencies and consumers: `DdcSiResourceServiceImpl` 与索引抽取。
- Why now: 确保标准 MCP 定义合规且安全无泄密。
- Contract/signature changes: `STDIO` 必含 `command`；`SSE`/`STREAMABLE_HTTP` 必含合法 HTTP/HTTPS `endpoint`。
- Input/output and state mapping: MCP JSON -> `DdcSiMcpSpecBO` -> 提取工具集可读说明进行检索建库。
- Error and edge behavior: 扫描明文密钥抛出敏感信息阻断 422 异常且日志永不打印原明文串。
- Standards impact: `MC-PATTERN-001, MC-VALID-001, MC-LOG-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 6, Rule 9, Rule 11`.
- Required change: 创建 `DdcSiMcpContentStrategy`。
- Verification contribution: 使 `DdcSiContentStrategyTest` MCP 测试全绿。
- After this file: MCP 安全与结构校验防线落地。

#### File 18 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiPromptSkillContentStrategy.java`
- Purpose: 兼容 `PROMPT` 与 `SKILL` 内容校验（模板非空、占位符配对平衡及尺寸限额）与检索摘要提取。
- Symbols: `DdcSiPromptSkillContentStrategy` (`@Component`).
- Repository evidence: 复用既有 AI Prompt/Skill 校验约束语义。
- Dependencies and consumers: `DdcSiResourceServiceImpl`。
- Why now: 覆盖统一四态模型全分支无遗漏。
- Contract/signature changes: 兼容旧格式草稿/内容 JSON，提取干净文本做分片。
- Input/output and state mapping: Prompt/Skill spec -> 规范化文本与摘要。
- Error and edge behavior: 超大规格或语法严重残缺报错 422。
- Standards impact: `MC-PATTERN-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 9, Rule 11`.
- Required change: 创建 `DdcSiPromptSkillContentStrategy`。
- Verification contribution: 四态策略全数贯通。
- After this file: 全部策略组件建立。

#### File 19 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiDependencyValidator.java`
- Purpose: 执行 Agent 对子 Agent 依赖有向无环图（DAG）深度检验、目标环境中依存发布存在验证以及归档前的入度阻断保护。
- Symbols: `DdcSiDependencyValidator` (`@Component`, `@Slf4j`, `validateAcyclicAndPublishedTargets`, `ensureSafeToArchive`).
- Repository evidence: Spec §4/§6.2/§9.4 与 `DdcSiResourceRepository` 图查询。
- Dependencies and consumers: `DdcSiResourceServiceImpl`。
- Why now: 独立图算法封装保证主编排清晰。
- Contract/signature changes: 发布及跨环境晋级必校验出度依赖在目标环境存在且无环路；归档必检反向 `TIANJI_PROCESS` / 上层 Agent 及活跃 `ddc_si_binding_t0`，否则抛 `409 Conflict` 附具体拦截清单。
- Input/output and state mapping: 引用集合 -> 环检查 + 依赖就位 check -> 放行或抛精准域异常。
- Error and edge behavior: 超过最大 DFS 深度 32 视为异常图嵌套直接拒收 422。
- Standards impact: `MC-PATTERN-001, MC-VALID-001, MC-BEAN-001, MC-LOG-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 9, Rule 11`.
- Required change: 创建 `DdcSiDependencyValidator`，用三色 DFS 检测自引用与间接循环，并校验晋级必填依赖与入度归档阻断；并在 `Complex logic (pseudocode):` 明确 DFS 剪枝顺序与入度并发检查流程：
  - 环检测：查询同作用域所有有效 `AGENT` 引用边组成邻接映射并将本次待发布边覆入，采用三色 DFS 深度探测（上限 32），遇灰态回边立即返回环路路径拒绝发布（`422`）；
  - 归档阻断：同时查询指向当前资源的活跃引用（包括来自 `TIANJI_PROCESS` 的流程绑定）和 `ACTIVE` 应用绑定记录，汇总阻断方标识清单抛出 `409`。
- Verification contribution: 使 `DdcSiDependencyValidatorTest` 全用例转绿。
- After this file: 图依赖安全边界完成。

#### File 20 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiLifecycleTransitionPolicy.java`
- Purpose: 声明生命周期状态转换矩阵及受配置允许的跨环境晋级方向（默认 `dev -> test -> staging -> prod` 或配置映射表）。
- Symbols: `DdcSiLifecycleTransitionPolicy` (`@Component`).
- Repository evidence: Spec §6.2 与 `DdcSiProperties`。
- Dependencies and consumers: `DdcSiResourceServiceImpl`。
- Why now: 使状态流变规范与环境路径逻辑保持纯粹单一职责。
- Contract/signature changes: 已 `ARCHIVED` 状态封锁任何修改或再启用，回滚严格保留历史快照不可变特性。
- Input/output and state mapping: 原态 + 操作 / 源环境 + 目标环境 -> 放行或抛出违规状态迁移异常（`409`/`422`）。
- Error and edge behavior: 未经许可的环境逆向或越级晋级报错。
- Standards impact: `MC-PATTERN-001, MC-VALID-001, MC-CONFIG-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 9, Rule 11`.
- Required change: 创建 `DdcSiLifecycleTransitionPolicy`。
- Verification contribution: 在服务测试检验各状态迁徙规则。
- After this file: 状态法则组件建立。

#### File 21 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/outbox/DdcSiCatalogOutboxNotifier.java`
- Purpose: 实现短事务内写入 `EVENT-001` Outbox 消息，并于事务成功提交之后触发 Redis 作用域指针递增及调度异步分片建索引。
- Symbols: `DdcSiCatalogOutboxNotifier` (`@Component`, `@Slf4j`, `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`).
- Repository evidence: 复用 `tianshu-admin` 现有 Outbox 发射表及 `DdcRedisKeys.siCatalogVersionKey`。
- Dependencies and consumers: `DdcSiResourceServiceImpl`, `DdcSiSemanticIndexService`。
- Why now: 遵守 Rule 7 与 `MC-BEAN-001` 确保库表事务提交才推事件与算向量。
- Contract/signature changes: 发生回滚绝对不发消息不推指针也不触发 `scheduleReindex`。
- Input/output and state mapping: 领域状态事实 -> 事务内 Outbox 记录 -> `AFTER_COMMIT` 刷新 Redis 版本指针 + 异步调度重建索引。
- Error and edge behavior: `AFTER_COMMIT` 中断网故障打印追踪告警，依靠版本号指针兜底补偿。
- Standards impact: `MC-REUSE-001, MC-BEAN-001, MC-LOG-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 4, Rule 9, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiCatalogOutboxNotifier`。
- Verification contribution: 被服务测试验证回滚安全与提交后同步触发。
- After this file: 异步最终一致性桥梁构建成功。

#### File 22 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexService.java`
- Purpose: 定义分片向量构建和混合检索领域端口。
- Symbols: `DdcSiSemanticIndexService` (`scheduleReindex`, `reindexVersionSync`, `hybridSearch`).
- Repository evidence: Spec §6.1/§9。
- Dependencies and consumers: `DdcSiCatalogOutboxNotifier`, `DdcSiResourceServiceImpl`。
- Why now: 解耦索引算法及业务资源调度。
- Contract/signature changes: 返回规范化 `List<DdcSiSearchHit>`，内置 `degradedToFtsOnly` 反馈标记。
- Input/output and state mapping: 查询模型 -> 命中集；版本 ID -> 异步/同步索引结果。
- Error and edge behavior: 查询路径内建兜底，不泄露底层向量计算失败 500。
- Standards impact: `MC-ARCH-001, MC-NAME-001`.
- Literal rule enforcement: `Rule 1, Rule 11`.
- Required change: 创建 `DdcSiSemanticIndexService` 接口。
- Verification contribution: 定义清楚测边 Mock 点。
- After this file: 检索端口确定。

#### File 23 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexServiceImpl.java`
- Purpose: 使用 `rag-starter` 切片与 `EmbeddingModel` 异步建设向量库并实现 RRF 混合查询及异常/超时无感切换至 PostgreSQL 纯 FTS 检索 (`REQ-007`)。
- Symbols: `DdcSiSemanticIndexServiceImpl` (`@Service`, `@Slf4j`, `@Qualifier("siIndexTaskExecutor") Executor`).
- Repository evidence: 核实 `rag-starter` 切片、`spring-ai-model` 及 Step 4 `DdcSiEmbeddingChunkMapper`。
- Dependencies and consumers: `@RequiredArgsConstructor(onConstructor_ = @Autowired)` + `ObjectProvider<EmbeddingModel>`。
- Why now: 支撑异步建索引和线上零中断降级搜索。
- Contract/signature changes: 强制检验向量维数 `== 1536` 且每个分量有限；`EmbeddingModel` 未配置或超过配置的 `Duration` 超时时，记录限频结构化 `WARN` 并自动降级调用 `ftsFallbackSearch`。
- Input/output and state mapping: 问句 -> 有向量则 `hybridSearchRrf` (`degradedToFtsOnly = false`) -> 否则或异常超限走 `ftsFallbackSearch` (`degradedToFtsOnly = true`)。
- Error and edge behavior: 单版本重建异常记入版本表 `index_status = FAILED` 供运维后续调用 `API-014` 人工触发重编。
- Standards impact: `MC-REUSE-001, MC-BEAN-001, MC-LOG-001, MC-PATTERN-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 4, Rule 5, Rule 9, Rule 10, Rule 11`.
- Required change: 实现 `DdcSiSemanticIndexServiceImpl`，并在 `Complex logic (pseudocode):` 明晰检索降级分支：
  - 索引阶段：抢占 `siEmbeddingLockKey` -> 借对应 `DdcSiContentStrategy` 提取结构化摘要后调用 `rag-starter` 分片 -> 按批限时调 `EmbeddingModel` 并严格断言 1536 维 -> 单表删除旧片写入新片 -> 标 `INDEXED` 及 `Instant.now()`；
  - 检索阶段：检查开关和 `EmbeddingModel` 存在 -> 带配置超时执行问句嵌入 -> 成功调 `hybridSearchRrf`；超时/异常/无模型一律无缝转向 `ftsFallbackSearch` 并设置结果 `degradedToFtsOnly = true`。
- Verification contribution: 使 `DdcSiSemanticIndexServiceImplTest` 转绿。
- After this file: 完整智能混合检索能力上线。

#### File 24 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiResourceService.java`
- Purpose: 定义囊括全套 REST 14 用例、gRPC 3 用例与 Tianji 内部端 `INTERNAL-001`/`INTERNAL-002` 的核心业务服务门面接口。
- Symbols: `DdcSiResourceService`.
- Repository evidence: 对齐 `service/ai/` 接口层命名与规约。
- Dependencies and consumers: Step 6 Controller, RPC Provider 及 Step 5 测试。
- Why now: 稳定封装完整服务对外契约。
- Contract/signature changes: 方法接收可靠经过服务端鉴权覆盖的 `tenantId` 和 `actorId`。
- Input/output and state mapping: 领域指令/查询 -> BO/Descriptor/SearchHit 输出。
- Error and edge behavior: 声明清晰异常类供上层适配器精准翻译。
- Standards impact: `MC-ARCH-001, MC-NAME-001`.
- Literal rule enforcement: `Rule 1, Rule 11`.
- Required change: 在 `service/si/` 创建接口 `DdcSiResourceService`。
- Verification contribution: 为 Step 6 提供完整可 Mock 服务边界。
- After this file: 业务服务接口确立。

#### File 25 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/impl/DdcSiResourceServiceImpl.java`
- Purpose: 编排四态资源建档、更新草稿、发布不可变快照、跨环境晋级、回滚活跃指针、停用/归档安全门控、应用绑定、自注册幂等、版本优先级解析与天机流程引用管理。
- Symbols: `DdcSiResourceServiceImpl` (`@Service`, `@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: `service/ai/impl/` 的短事务划分与协作模式。
- Dependencies and consumers: 注入 Repository、策略清单、Validator、Policy、OutboxNotifier、IndexService 与 Converter。
- Why now: 将全组件串成端到端闭环交易逻辑。
- Contract/signature changes: 全交易短事务内只做库写与 Outbox 记录，绝不在事务锁内发任何 gRPC/HTTP/Embedding 请求；自上报遇人工管理已发布资源禁止破坏性覆写。
- Input/output and state mapping: 合法上下文与输入 -> 状态变更/快照生成/Outbox 事件。
- Error and edge behavior: 并发锁/依赖阻断 throw 409，校验/环路/禁流程边 throw 422，未知或越租户资源 throw 404/403。
- Standards impact: `MC-ARCH-001, MC-BEAN-001, MC-LOG-001, MC-PATTERN-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 4, Rule 9, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiResourceServiceImpl` 完成所有用例编排，重要关键分支如下：
  - 发布分支：先在事务外选策略校验规范、计摘要、检环路与目标存在 -> 开启 `@Transactional` 获取下一版本号建只读版本快照、更新引用边、条件版本号锁定主资源为 `PUBLISHED` 并写入 `EVENT-001` Outbox 记录；
  - 幂等自注册（`RPC-003`）：计算规范摘要比对既有记录（同指纹即幂等直返零更新；若已有人工 `CONSOLE` 且已发布则严禁覆写活跃线上版本，依策略保底草稿或仅做状态反馈；新资源依 `AUTO_REPORTED` 入库）。
- Verification contribution: 使 `DdcSiResourceServiceImplTest` 全部场景转绿。
- After this file: SI 后端核心功能闭环完成。

- Validation working directory: `tianshu-service`
- Verification command: `./mvnw -pl stellar-enclosure-tianshu-admin -Dtest='DdcSiContentStrategyTest,DdcSiDependencyValidatorTest,DdcSiResourceServiceImplTest,DdcSiSemanticIndexServiceImplTest' test`
- Expected result: 4 个测试类所有断言 100% 通过无异常及无跳过断言。
- Failure returns to: 定位相应 BO/策略/校验/索引/服务类核改后再跑至全绿。
- Completion criteria: 覆盖 `TEST-001..004`, `TEST-006`, `TEST-007`, `TEST-008` 全通。
- Rollback: 局部 `git revert` Step 5 提交。
- Commit paths: `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiContentStrategyTest.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiDependencyValidatorTest.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiResourceServiceImplTest.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexServiceImplTest.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiResourceBO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiVersionBO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiBindingBO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiReferenceBO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiAgentSpecBO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/bo/DdcSiMcpSpecBO.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiEnumConverter.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiResourceConverter.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/repository/DdcSiResourceRepository.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/repository/impl/DdcSiResourceRepositoryImpl.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiContentStrategy.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiAgentContentStrategy.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiMcpContentStrategy.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/strategy/DdcSiPromptSkillContentStrategy.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiDependencyValidator.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiLifecycleTransitionPolicy.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/outbox/DdcSiCatalogOutboxNotifier.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexService.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/search/DdcSiSemanticIndexServiceImpl.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/DdcSiResourceService.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/service/si/impl/DdcSiResourceServiceImpl.java`
- Commit: `feat(tianshu-admin): implement SI BOs, MapStruct converters, strategies, Outbox and RRF search`

---

### Step 6 — Expose tianshu-admin SI REST API-001..014, gRPC RPC-001..003, RBAC permissions and three-profile config

- Requirements: `REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-006, REQ-007, REQ-008, REQ-009`
- Dependencies: `Step 2, Step 5`
- Baseline state: SI 业务能力齐备在后台 Service 层，待开放 Web CQE 接口、gRPC 服务端提供者、权鉴代码、`springdoc` 文档与环境参数绑定。
- Observable outcome: 14 组 REST 接口 + OpenAPI 3 Schema、3 组 gRPC 服务端 + 租户核验拦截及三套 `application*.yml` 环境完全对等上线；权限生产与全部新老测试通绿。
- End state: `tianshu-admin` 完整对外服务上线就绪，可供客户端和前端界面联调。
- Test-first gate: `Required — 先建 DdcSiResourceControllerIT 与 DdcSiResourceRpcProviderIT 固定接口、权限、OpenAPI 导出与跨租户拦截期待`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-NAME-001, MC-VALID-001, MC-MODEL-001, MC-CONVERT-001, MC-LOG-001, MC-BEAN-001, MC-JSON-001, MC-CONFIG-001, MC-TIME-001, MC-TEST-001`
- Literal Rules: `Rule 1, Rule 2, Rule 3, Rule 4, Rule 6, Rule 7, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiResourceControllerIT.java`
- Purpose: 实现 `MockMvc` 与 `/v3/api-docs` 契约全覆盖集成测试，核实 14 组端点的权鉴 403、参数 400、状态冲突 409、校验 422、时间 `UTC Z` 输出以及既有 `/api/v1/tianshu/ai/**` 端点毫发无损。
- Symbols: `DdcSiResourceControllerIT`.
- Repository evidence: `controller/ai/` 既有 `MockMvc` IT 写法。
- Dependencies and consumers: 测试 File 3..18, File 20..24。
- Why now: 测试驱动开放界面契约，严守权鉴及兼容性。
- Contract/signature changes: 验证全部 7 个新 `tianshu:si:*` 权限精准门控。
- Input/output and state mapping: HTTP CQE JSON -> 控制器 -> `SingleResponse` / `PageResponse`。
- Error and edge behavior: 检验局部切面返可读结构化错误码和阻断名单（无系统堆栈）。
- Standards impact: `MC-TEST-001, MC-JSON-001, MC-TIME-001, MC-CONFIG-001`.
- Literal rule enforcement: `Rule 2, Rule 6, Rule 7, Rule 10, Rule 11`.
- Required change: 在 `controller/si/` 测试包创建 `DdcSiResourceControllerIT`。
- Verification contribution: Step 6 HTTP 层核心测试大门。
- After this file: HTTP 接口契约立定。

#### File 2 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/rpc/provider/DdcSiResourceRpcProviderIT.java`
- Purpose: 测试 gRPC 服务端 `RPC-001`..`RPC-003` 实现，重点断言伪造声明跨租户 `tenantId` 拒绝返回 `PERMISSION_DENIED` 以及自注册与状态码精确映射（`TEST-009`, `TEST-011`）。
- Symbols: `DdcSiResourceRpcProviderIT`.
- Repository evidence: 现有 `rpc/provider/` 测试结构。
- Dependencies and consumers: 测试 File 19。
- Why now: 锁定机器对机器安全边界。
- Contract/signature changes: 凭证租户与包体声明租户必须一致。
- Input/output and state mapping: `InProcessServer` 请求 -> Provider 校验安全主体 -> Service 调用 -> Proto 响应或 gRPC 错误。
- Error and edge behavior: 状态冲突映射 `ABORTED`，未找到映射 `NOT_FOUND`。
- Standards impact: `MC-TEST-001, MC-VALID-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 2, Rule 10, Rule 11`.
- Required change: 在 `rpc/provider/` 测试包创建 `DdcSiResourceRpcProviderIT`。
- Verification contribution: Step 6 gRPC 安全测试通过基石。
- After this file: gRPC 服务端安全测试立定。

#### File 3 — `MODIFY tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/permission/DdcPermissionCodes.java`
- Purpose: 在核实后的 `permission/DdcPermissionCodes.java` 增加 7 个 `TIANSHU_SI_*` 权限代码常数，并在 `DdcResourceMessages.java` 补充 `@PermissionMessage` 资源键标签，保持既有权限一字不改。
- Symbols: `SI_RESOURCE_READ`, `SI_RESOURCE_WRITE`, `SI_RESOURCE_PUBLISH`, `SI_RESOURCE_PROMOTE`, `SI_RESOURCE_ARCHIVE`, `SI_BINDING_WRITE`, `SI_INDEX_MANAGE`.
- Repository evidence: 已验证实际路径 `admin/permission/DdcPermissionCodes.java` 与 `admin/permission/DdcResourceMessages.java`。
- Dependencies and consumers: `DdcSiResourceController` `@RequiresPermissions` / `@PermissionResource` 及 `-Ppermission-producer`。
- Why now: 控制器权限声明前置引用。
- Contract/signature changes: 与 `V20261116_001` 种子及前端 `permissions.ts` 精确 1:1 匹配。
- Input/output and state mapping: 权限常数 -> Security 鉴权表达。
- Error and edge behavior: 无权限调用即时返 403。
- Standards impact: `MC-NAME-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 1, Rule 11`.
- Required change: 向 `permission/DdcPermissionCodes.java` 尾部追加上述 7 项 SI 权限定义，并补充 `DdcResourceMessages.java` 对应 `api.siResource.*` `@PermissionMessage`。
- Verification contribution: `-Ppermission-producer test-compile` 与 `DdcSiResourceControllerIT` 验证。
- After this file: 服务端权限码同步完备。

#### File 4 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiCreateCommand.java`
- Purpose: `API-003` 新建原子资源 CQE 命令模型，带 `@Schema` 与格式/必填验证。
- Symbols: `DdcSiCreateCommand`.
- Repository evidence: 仓库既有平铺 `model/command/DdcAiCreateCommand.java` 模式。
- Dependencies and consumers: `DdcSiResourceController`, `DdcSiCqeConverter`。
- Why now: 定义 `API-003` 强类型校验入参。
- Contract/signature changes: `@NotBlank` / `@Pattern` / `@NotNull` 限定 `bizCode`, `namespaceCode`, `envCode`, `kind`, `resourceKey`, `displayName`, `specJson` 等，屏蔽越权主体字段。
- Input/output and state mapping: HTTP Body -> 命令对象。
- Error and edge behavior: 缺项与非法格式自动触发 400 验证错。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-VALID-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 6, Rule 11`.
- Required change: 创建 `DdcSiCreateCommand`。
- Verification contribution: 配合 OpenAPI 3 Schema 与参数校验测试验证。
- After this file: 创建命令契约完成。

#### File 5 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiDraftCommand.java`
- Purpose: `API-004` 更新草稿命令，必传乐观锁版本号 `expectedVersion`。
- Symbols: `DdcSiDraftCommand`.
- Repository evidence: `model/command/DdcAiDraftCommand.java` 模式。
- Dependencies and consumers: `DdcSiResourceController`, `DdcSiCqeConverter`。
- Why now: 支撑并发安全的草稿持续迭代。
- Contract/signature changes: `@NotNull @Min(0) expectedVersion` 必带。
- Input/output and state mapping: 修改载荷 -> 草稿更新指令。
- Error and edge behavior: 缺版本参数报 400，版本过时由服务层报 409。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-VALID-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 6, Rule 11`.
- Required change: 创建 `DdcSiDraftCommand`。
- Verification contribution: 并发 409/400 用例测试。
- After this file: 草稿指令完成。

#### File 6 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiLifecycleCommand.java`
- Purpose: 承载 `API-005`..`API-009`（发布、跨环境晋级、回滚、停用、归档）所需变迁说明、目标环境/版本号及乐观锁版本指令。
- Symbols: `DdcSiLifecycleCommand`.
- Repository evidence: `model/command/` 既有 `Publish`/`Promotion`/`Rollback` 命令参数样式。
- Dependencies and consumers: `DdcSiResourceController`, `DdcSiCqeConverter`。
- Why now: 统一并规范生命周期变更 CQE 模型。
- Contract/signature changes: 带明确 `@Schema` 说明各状态变迁适用选填/必填参数及分组检验。
- Input/output and state mapping: 控制指令参数 -> 服务状态控制动作。
- Error and edge behavior: 校验组保证晋级必填目标环境、回滚必填目标快照版本号。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-VALID-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 6, Rule 11`.
- Required change: 创建 `DdcSiLifecycleCommand` 含 `@Validated` 校验分组。
- Verification contribution: 验证生命周期参数校验失败与正确传参路径。
- After this file: 生命周期命令完成。

#### File 7 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiBindingCommand.java`
- Purpose: 承载 `API-013` 创建/切换/删除 `appCode` 特定绑定锁版本命令。
- Symbols: `DdcSiBindingCommand`.
- Repository evidence: `model/command/` 习惯。
- Dependencies and consumers: `DdcSiResourceController`, `DdcSiCqeConverter`。
- Why now: 支持针对应用绑定目标版本或切换跟踪活跃版。
- Contract/signature changes: `@NotBlank appCode`, `@NotNull kind`, `@NotBlank resourceKey`, 可选 `@Positive pinnedVersionNo`。
- Input/output and state mapping: 绑定配置输入 -> Service。
- Error and edge behavior: 非正版本号或空白 `appCode` 返回 400。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-VALID-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 6, Rule 11`.
- Required change: 创建 `DdcSiBindingCommand`。
- Verification contribution: `DdcSiResourceControllerIT` 验证绑定操作接口。
- After this file: 全部 Command CQE 模型完成。

#### File 8 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/query/DdcSiPageQuery.java`
- Purpose: `API-001` 主目录多维条件与分页参数对象。
- Symbols: `DdcSiPageQuery`.
- Repository evidence: 现有平铺 `model/query/DdcAiPageQuery.java`。
- Dependencies and consumers: `DdcSiResourceController`, `DdcSiCqeConverter`。
- Why now: 分页列表搜索边界防放大参数锁定。
- Contract/signature changes: 分页大小限定 `1..100` 默认 20，带作用域、分类、状态、来源与标签筛选。
- Input/output and state mapping: Query params -> 受限分页请求。
- Error and edge behavior: 过大页 size 遭 400 驳回。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 11`.
- Required change: 创建 `DdcSiPageQuery`。
- Verification contribution: 分页和过滤校验测试用例通过。
- After this file: 目录 Query 完成。

#### File 9 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/query/DdcSiSemanticSearchRequestQuery.java`
- Purpose: `API-012` REST 端自然语言语义及关键混合查询请求。
- Symbols: `DdcSiSemanticSearchRequestQuery`.
- Repository evidence: `model/query/` 风格与 Spec §9.1 `API-012`。
- Dependencies and consumers: `DdcSiResourceController`, `DdcSiCqeConverter`。
- Why now: 支撑控制台与检索测试调用混合引擎。
- Contract/signature changes: `@NotBlank @Size(max = 2000) queryText` 与 `1..50` `topK`。
- Input/output and state mapping: 查询表单 -> 领域搜索参数。
- Error and edge behavior: 空问句及非法 `topK` 报错 400。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 3, Rule 11`.
- Required change: 创建 `DdcSiSemanticSearchRequestQuery`。
- Verification contribution: `DdcSiResourceControllerIT` 搜索端点断言。
- After this file: 全部 Request Query 对象齐备。

#### File 10 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiResourceResult.java`
- Purpose: 资源列表项及详情（带当前版本、草稿载荷及乐观锁号）返回 VO。
- Symbols: `DdcSiResourceResult`.
- Repository evidence: 仓库平铺 `model/vo/DdcAiResourceResult.java`。
- Dependencies and consumers: `DdcSiCqeConverter`, `DdcSiResourceController`, OpenAPI 3 Schema.
- Why now: 固定 HTTP 资源视图结构体。
- Contract/signature changes: 时间使用 `Instant` 输出 `UTC Z`；任何敏感字段掩码脱敏。
- Input/output and state mapping: BO -> Result VO。
- Error and edge behavior: 不回传内部凭证密钥串。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 在 `model/vo/` 创建 `DdcSiResourceResult`。
- Verification contribution: `MockMvc` 与 `/v3/api-docs` 契约验证。
- After this file: 资源返回 VO 完成。

#### File 11 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiVersionResult.java`
- Purpose: 历史发布版本与环境晋级溯源详情响应 VO。
- Symbols: `DdcSiVersionResult`.
- Repository evidence: `model/vo/DdcAiVersionResult.java`。
- Dependencies and consumers: `DdcSiCqeConverter`, `DdcSiResourceController`。
- Why now: 供版本时间轴和对比面板渲染。
- Contract/signature changes: 含 `versionNo`, `digestSha256`, `sourceEnvCode`, `sourceVersionNo`, `indexStatus`, `publishedTime` (`Instant`).
- Input/output and state mapping: `DdcSiVersionBO` -> `DdcSiVersionResult`。
- Error and edge behavior: 脱敏掩码保护。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiVersionResult`。
- Verification contribution: `DdcSiResourceControllerIT`。
- After this file: 版本 VO 完成。

#### File 12 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiBindingResult.java`
- Purpose: 应用作用域版本绑定展示 VO。
- Symbols: `DdcSiBindingResult`.
- Repository evidence: `model/vo/` 规范。
- Dependencies and consumers: 绑定查询/变更响应。
- Why now: 供 `SiServiceBindingPanel` 显示锁定信息。
- Contract/signature changes: `boundTime` 使用 `Instant` -> `UTC Z`。
- Input/output and state mapping: `DdcSiBindingBO` -> `DdcSiBindingResult`。
- Error and edge behavior: 无。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiBindingResult`。
- Verification contribution: `DdcSiResourceControllerIT`。
- After this file: 绑定 VO 完成。

#### File 13 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiReferenceResult.java`
- Purpose: 出度/入度依赖视图及归档阻断报告条目 VO。
- Symbols: `DdcSiReferenceResult`.
- Repository evidence: `model/vo/` 规范及 Spec §9.1。
- Dependencies and consumers: 引用详情端与异常报错阻断名单。
- Why now: 可视化天机流程锁定引用。
- Contract/signature changes: 标识 `sourceType`, `sourceKey`, `targetType`, `targetKey`, `pinnedVersionNo`, `required`。
- Input/output and state mapping: `DdcSiReferenceBO` -> `DdcSiReferenceResult`。
- Error and edge behavior: 无。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 11`.
- Required change: 创建 `DdcSiReferenceResult`。
- Verification contribution: `DdcSiResourceControllerIT`。
- After this file: 引用 VO 完成。

#### File 14 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiSemanticSearchResult.java`
- Purpose: 包装向前端反馈的混合检索列表与全局/局部 `degradedToFtsOnly` 提示及各项排名打分分值。
- Symbols: `DdcSiSemanticSearchResult`.
- Repository evidence: Spec §9.1 `API-012`。
- Dependencies and consumers: `DdcSiResourceController`, 前端 `SiCatalogPage`。
- Why now: 前端须依 `degradedToFtsOnly` 正确展示柔性降级告知栏。
- Contract/signature changes: 带 `@Schema` 注释说明各得分维度及降级标签语义。
- Input/output and state mapping: `DdcSiSearchHit` list -> `DdcSiSemanticSearchResult`。
- Error and edge behavior: 分数值非空有限校验。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiSemanticSearchResult`。
- Verification contribution: `DdcSiResourceControllerIT`。
- After this file: 搜索响应 VO 完成。

#### File 15 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiEnvMatrixResult.java`
- Purpose: 封装各资源跨不同 `envCode`（`dev`, `test`, `staging`, `prod`）活跃版本号、状态与摘要对照横向矩阵条目。
- Symbols: `DdcSiEnvMatrixResult`.
- Repository evidence: Spec §9.1 `API-011` 与 §12.2 矩阵视角。
- Dependencies and consumers: `DdcSiResourceController`, Step 8 `SiEnvMatrixPage`。
- Why now: 支撑一目了然的多环境基线比对。
- Contract/signature changes: 时间全部输出 `Instant` (`UTC Z`)。
- Input/output and state mapping: 多环境版本列表聚合 -> 矩阵单元集。
- Error and edge behavior: 未发布环境位明确留空态标志。
- Standards impact: `MC-NAME-001, MC-MODEL-001, MC-JSON-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 1, Rule 3, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiEnvMatrixResult`。
- Verification contribution: `DdcSiResourceControllerIT`。
- After this file: 全系 VO 模型落成。

#### File 16 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiCqeConverter.java`
- Purpose: MapStruct 转换接口处理 REST 层 CQE 入参至服务参数，及领域模型至上述 6 个 Result VO。
- Symbols: `DdcSiCqeConverter` (`@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)`).
- Repository evidence: 现有 `model/convertor/` 用法。
- Dependencies and consumers: `DdcSiResourceController`。
- Why now: 保持 Controller 极简零手动映射代码。
- Contract/signature changes: 开启 `ReportingPolicy.ERROR` 检查避免漏写目标属性。
- Input/output and state mapping: CQE <-> BO/Descriptor。
- Error and edge behavior: 未知枚举或无效格式抛标准转化异常。
- Standards impact: `MC-CONVERT-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 3, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiCqeConverter` 接口。
- Verification contribution: 编译期 MapStruct 检查与 `MockMvc` 覆盖测试通过。
- After this file: HTTP CQE 转换层完成。

#### File 17 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiResourceController.java`
- Purpose: 实现 Spec §9.1 `API-001`..`API-014` 全部 14 个 REST HTTP 端点（独立 `/api/v1/tianshu/si/**` 路由空间，零影响既有 `/api/v1/tianshu/ai/**`）、全套 `@PreAuthorize` 细粒度权限门控、OpenAPI 3 `@Tag`/`@Operation`/`@ApiResponse` 文档以及认证主体信任解析。
- Symbols: `DdcSiResourceController` (`@RestController`, `@RequestMapping("/api/v1/tianshu/si")`, 14 endpoint methods).
- Repository evidence: 遵循 `controller/ai/DdcAiResourceController.java` 封装响应包装及认证租户获取方法。
- Dependencies and consumers: `DdcSiResourceService`, `DdcSiCqeConverter`, `DdcPermissionCodes`, `tianshu-web`。
- Why now: 开放治理控制台 API 契约。
- Contract/signature changes: 薄控制器约束——零 SQL、零状态机判断分支、零直接 DB/Redis 注入。
- Input/output and state mapping: HTTP Request + 信任上下文 -> CQE -> Service -> `SingleResponse` / `PageResponse`。
- Error and edge behavior: 异常统一冒泡至 File 18 `DdcSiExceptionAdvice`。
- Standards impact: `MC-ARCH-001, MC-NAME-001, MC-VALID-001, MC-BEAN-001, MC-JSON-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 4, Rule 6, Rule 11`.
- Required change: 在 `controller/si/` 新建 `DdcSiResourceController` 严格落实 14 端点路径、方法、`@PreAuthorize` 表达式与 OpenAPI 描述注解。
- Verification contribution: 使 `DdcSiResourceControllerIT` 与 `/v3/api-docs` 导出校验通过。
- After this file: HTTP 接口端就绪。

#### File 18 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiExceptionAdvice.java`
- Purpose: 限定绑定 `DdcSiResourceController.class` 且 `@Order(-2)` 拦截全部 SI 领域/并发/验证异常并统一格式化 HTTP 错误及 `blockingReferences` 明细。
- Symbols: `DdcSiExceptionAdvice` (`@RestControllerAdvice(assignableTypes = DdcSiResourceController.class)`, `@Order(-2)`, `@Slf4j`).
- Repository evidence: `tianshu-admin` 现有全局异常处理优先级链。
- Dependencies and consumers: 专服务于 `DdcSiResourceController` 不介入老接口异常处理路径。
- Why now: 确保 `409` / `422` / `404` / `403` 高保真回传前端。
- Contract/signature changes: 回传干净结构化错误 JSON 含时间戳 `UTC Z`、错误码及可选阻断方名单，杜绝堆栈泄密。
- Input/output and state mapping: 业务异常 -> 确定 HTTP 状态与安全错包。
- Error and edge behavior: 兜底拦截未料 `RuntimeException` 报 `500` 及安全提示。
- Standards impact: `MC-SCOPE-001, MC-LOG-001, MC-JSON-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 4, Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiExceptionAdvice`。
- Verification contribution: 满足 `DdcSiResourceControllerIT` 全异常代码测试断言。
- After this file: 控制层异常体系严密独立。

#### File 19 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/rpc/provider/DdcSiResourceRpcProvider.java`
- Purpose: 暴露 `DdcSiResourceGrpcServiceImplBase`（`RPC-001`..`RPC-003`），并在每次处理入口比对校验服务身份可信租户与请求声明，拦截跨租户伪造，并精确转译领域异常为标准 gRPC Status。
- Symbols: `DdcSiResourceRpcProvider` (`@GrpcService`, `@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: `security/rpc/DdcRpcServerInterceptor.java`, `DdcRpcScopeExtractor.java`, `rpc/provider/` 写法。
- Dependencies and consumers: `DdcSiResourceService`, `DdcSiProtoMapper`。
- Why now: 支撑 Step 7 与 Step 9 下游微服务连接。
- Contract/signature changes: 强制校对可信凭据租户边界；`NOT_FOUND`、`ABORTED`、`INVALID_ARGUMENT`、`PERMISSION_DENIED`、`FAILED_PRECONDITION` 一一映射。
- Input/output and state mapping: gRPC Proto 入参 -> 租户防伪校验 -> 服务执行 -> Proto 响应。
- Error and edge behavior: 非匹配租户直接 `onError(Status.PERMISSION_DENIED)` 返回，拒绝执行任何读取或写操作。
- Standards impact: `MC-ARCH-001, MC-BEAN-001, MC-LOG-001, MC-VALID-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiResourceRpcProvider`。
- Verification contribution: 直接促使 `DdcSiResourceRpcProviderIT` 全通。
- After this file: gRPC 服务端接口完成。

#### File 20 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/config/DdcSiProperties.java`
- Purpose: 使用 `@ConfigurationProperties` + `@Validated` 声明绑定 `egon.cola.component.tianshu.si` 下全系超参、超时 `Duration` 与允许环境晋级路表，杜绝不受支持 `@Value` 注入。
- Symbols: `DdcSiProperties` (`@ConfigurationProperties(prefix = "egon.cola.component.tianshu.si")`, `@Validated`).
- Repository evidence: 效仿 `DdcAdminUiI18nProperties.java` 与 `lombok.config` 检查发现。
- Dependencies and consumers: `DdcSiSemanticIndexServiceImpl`, `DdcSiLifecycleTransitionPolicy`, `DdcSiResourceServiceImpl`, `DdcSiAutoConfiguration`。
- Why now: 规则 3/7 与 Lombok 安全约定要求用强类型 Properties 对象驱动可调策略。
- Contract/signature changes: 带完整合理安全默认值（1536 维、RRF k=60、`embeddingTimeout = PT1.5S`、`autoPublishOnSelfRegister = false`）。
- Input/output and state mapping: YAML 属性 -> `@Validated` 属性对象。
- Error and edge behavior: 负维数或无效时长导致 Boot 快速校验报错止损。
- Standards impact: `MC-BEAN-001, MC-CONFIG-001, MC-VALID-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 7, Rule 10, Rule 11`.
- Required change: 在 `admin/config/` 新建 `DdcSiProperties`。
- Verification contribution: 被上下文启动与配置验证测试断言。
- After this file: 服务端强类型配置模型完备。

#### File 21 — `CREATE tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/config/DdcSiAutoConfiguration.java`
- Purpose: 激活 `@EnableConfigurationProperties(DdcSiProperties.class)` 并注册具名有界线程池 `@Bean("siIndexTaskExecutor")`，彻底把异步嵌入计算任务与 HTTP Web 线程池物理隔离。
- Symbols: `DdcSiAutoConfiguration`, `siIndexTaskExecutor` (`Executor`).
- Repository evidence: `admin/config/` 既有自动装配配置类。
- Dependencies and consumers: `DdcSiSemanticIndexServiceImpl` 按 `@Qualifier("siIndexTaskExecutor")` 消费。
- Why now: 保障向量构建不影响主接口吞吐量与响应延。
- Contract/signature changes: 有界核心/最大线程数与有界阻塞队列 + `CallerRunsPolicy`/安全拒弃策略。
- Input/output and state mapping: `DdcSiProperties` executor config -> `ThreadPoolTaskExecutor` Bean。
- Error and edge behavior: 队列满载有界退避防 OOM。
- Standards impact: `MC-BEAN-001, MC-CONFIG-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 4, Rule 7, Rule 11`.
- Required change: 新建 `DdcSiAutoConfiguration`。
- Verification contribution: 上下文启动与 `@Qualifier` 精确装配检测通过。
- After this file: 服务端装配就绪。

#### File 22 — `MODIFY tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application.yml`
- Purpose: 追加基础档案共享默认参数段 `egon.cola.component.tianshu.si`（不改动既有节点）。
- Symbols: `egon.cola.component.tianshu.si` YAML node.
- Repository evidence: `application.yml` 现有分层节点结构。
- Dependencies and consumers: 全环境基础档案继承源。
- Why now: 保证三套 YAML 结构完全对称（Rule 7）。
- Contract/signature changes: 只在文件尾部或对应位置安全添加 `si:` 小节；所有时间写带单位串 `1500ms`, `30s`。
- Input/output and state mapping: YAML -> `DdcSiProperties`。
- Error and edge behavior: 无。
- Standards impact: `MC-CONFIG-001, MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 7, Rule 10, Rule 11`.
- Required change: 向 `application.yml` 补充完整的 SI 基础默认参数段。
- Verification contribution: Boot 上下文配置绑定全绿。
- After this file: base 配置就绪。

#### File 23 — `MODIFY tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application-local.yml`
- Purpose: 在 `application-local.yml`（仓库核实名）对等追加 `egon.cola.component.tianshu.si` 与 `TIANSHU_SI_*` permission-scopes，键集与 base 和 test 保持对等。
- Symbols: `egon.cola.component.tianshu.si` local profile YAML node.
- Repository evidence: `tianshu-admin/src/main/resources/application-local.yml`（已由 `DdcProfileParityTest` 核实）。
- Dependencies and consumers: 本地开发 `local` 启动运行。
- Why now: 遵守全配置档对等义务。
- Contract/signature changes: 零改原 DB/Nacos/port 等既有键。
- Input/output and state mapping: `local` profile override properties。
- Error and edge behavior: 无。
- Standards impact: `MC-CONFIG-001, MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 7, Rule 10, Rule 11`.
- Required change: 向 `application-local.yml` 对等增补 SI 配置段。
- Verification contribution: `DdcProfileParityTest` 与 `local` profile 启动全绿验证。
- After this file: local 配置对齐完成。

#### File 24 — `MODIFY tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application-test.yml`
- Purpose: 在 `application-test.yml`（仓库核实名）对等声明 `egon.cola.component.tianshu.si` 测试/受控策略，完整匹配其它两个 YAML 档案 `si.*` 键集。
- Symbols: `egon.cola.component.tianshu.si` test profile YAML node.
- Repository evidence: `tianshu-admin/src/main/resources/application-test.yml`（已由 `DdcProfileParityTest` 核实）。
- Dependencies and consumers: `test` 档案加载与持续集成验证。
- Why now: 遵守 Rule 7 及安全边界要求。
- Contract/signature changes: 禁用高危自动覆写开关（`auto-publish-on-self-register: false`）；无删改原关键配置。
- Input/output and state mapping: `test` profile safe configuration.
- Error and edge behavior: 保证缺少环境变量的兼容占位保底。
- Standards impact: `MC-CONFIG-001, MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 7, Rule 10, Rule 11`.
- Required change: 向 `application-test.yml` 对等新增受控安全 SI 配置节。
- Verification contribution: 测试在多环境档案全绿启动下通过 Step 6 验收门槛。
- After this file: `tianshu-admin` 全部服务端工作完满交付并验证。

- Validation working directory: `tianshu-service`
- Verification command: `./mvnw -pl stellar-enclosure-tianshu-admin -Ppermission-producer test-compile && ./mvnw -pl stellar-enclosure-tianshu-admin test`
- Expected result: `-Ppermission-producer` 无报错；`DdcSiResourceControllerIT`, `DdcSiResourceRpcProviderIT`, 全系新旧单测 100% 通过无跳过；`/v3/api-docs` 导出完整无缺且全配置档案可装载。
- Failure returns to: 检查对应 CQE/Controller/Advice/Provider/YAML 后重测直至全绿。
- Completion criteria: 满足 `TEST-004`, `TEST-009`, `TEST-011` 与 OpenAPI 3 校验及三档对等校验。
- Rollback: 局部 `git revert` Step 6 提交。
- Commit paths: `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiResourceControllerIT.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/test/java/top/egon/cola/component/tianshu/admin/rpc/provider/DdcSiResourceRpcProviderIT.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/permission/DdcPermissionCodes.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/permission/DdcResourceMessages.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/security/rpc/DdcRpcScopeExtractor.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiCreateCommand.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiDraftCommand.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiLifecycleCommand.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/command/DdcSiBindingCommand.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/query/DdcSiPageQuery.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/query/DdcSiSemanticSearchRequestQuery.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiResourceResult.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiVersionResult.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiBindingResult.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiReferenceResult.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiSemanticSearchResult.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/vo/DdcSiEnvMatrixResult.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/model/convertor/DdcSiCqeConverter.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiResourceController.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/controller/si/DdcSiExceptionAdvice.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/rpc/provider/DdcSiResourceRpc.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/rpc/provider/DdcSiResourceRpcProvider.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/config/DdcSiProperties.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/java/top/egon/cola/component/tianshu/admin/config/DdcSiAutoConfiguration.java`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application.yml`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application-local.yml`, `tianshu-service/stellar-enclosure-tianshu-admin/src/main/resources/application-test.yml`
- Commit: `feat(tianshu-admin): expose SI REST API-001..014, gRPC RPC-001..003, RBAC and env profiles`

---

### Step 7 — Deliver SI catalog cache, debounced sync, event listener and self-registration runner in tianshu-ai-starter

- Requirements: `REQ-001, REQ-003, REQ-006, REQ-007, REQ-008`
- Dependencies: `Step 1, Step 2, Step 6`
- Baseline state: `tianshu-ai-starter`（核实真根包 `top.egon.cola.component.tianshu.ai`）已含老版本工作区与用量上报组件，缺进程级不可变热快照缓存、Redis 版本指针防抖校验、`EVENT-001` 幂等监听器与启动批量自注册运行器。
- Observable outcome: 客户端读性能实现内存微秒直达且具备突发事件防抖合批、天枢失联留旧兜底保护、有界防重入去重和幂等退避启动自注册（开关未显式打开则对现有老服务 100% 透明零打扰），验证通过并 install 入 `.m2`。
- End state: 下游业务（包含 Step 9 `tianji-service`）按开关启用即可获取高可用智能资源发现和上报机制。
- Test-first gate: `Required — 先建 DdcSiCatalogSyncCoordinatorTest 与 DdcSiSelfRegistrationRunnerTest 确立并发热读取、突发防抖、容错留存与条件隔离红灯基线`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-NAME-001, MC-VALID-001, MC-LOG-001, MC-BEAN-001, MC-UTIL-001, MC-CONFIG-001, MC-PATTERN-001, MC-TIME-001, MC-TEST-001`
- Literal Rules: `Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE tianshu-service/stellar-enclosure-tianshu-ai-starter/src/test/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogSyncCoordinatorTest.java`
- Purpose: 验证内存热缓存读取、Redis 版本指针变化触发拉取、高频事件合并防抖、跨租户/重复 `eventId` 忽略以及 `DdcAiStarterAutoConfiguration` 开关关闭零额外 Bean 之 RED 单测。
- Symbols: `DdcSiCatalogSyncCoordinatorTest`.
- Repository evidence: `tianshu-ai-starter/src/test` 既有单测框架。
- Dependencies and consumers: 测试 File 3..6 & File 9。
- Why now: 契约先定并发、防抖及容灾保底行为。
- Contract/signature changes: 验证天枢短暂失联时协调器不清空旧有效快照以保障服务持续可用；`semanticSearch` 严格穿透走远端实时库。
- Input/output and state mapping: 突发 10 次同域通知 -> 防抖期仅聚合刷新少数远端同步请求；重复 `eventId` -> 零拉取。
- Error and edge behavior: 关闭 `si.client.enabled` 时容器不含新增 SI Bean，保证老项目零副作用。
- Standards impact: `MC-TEST-001, MC-UTIL-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 2, Rule 9, Rule 10, Rule 11`.
- Required change: 在 `ai/si/` 测试目录创建 `DdcSiCatalogSyncCoordinatorTest`。
- Verification contribution: 驱动协同器和监听器的准确实现。
- After this file: 客户端同步测试红灯成立。

#### File 2 — `CREATE tianshu-service/stellar-enclosure-tianshu-ai-starter/src/test/java/top/egon/cola/component/tianshu/ai/si/DdcSiSelfRegistrationRunnerTest.java`
- Purpose: 验证启动期批量采集 `DdcSiLocalResourceContributor`、确定性 SHA-256 指纹上报、客户端与服务端配合幂等去重 (`TEST-006` 客户端侧) 及可控 fail-open 退避机制。
- Symbols: `DdcSiSelfRegistrationRunnerTest`.
- Repository evidence: `tianshu-ai-starter/src/test`。
- Dependencies and consumers: 测试 File 7..9。
- Why now: 锁定微服务开箱上报时的健壮行为防止因天枢后起造成循环重启死锁。
- Contract/signature changes: `failFastOnStartup = false` 遇网络故障重试限次后记警日志安全放行上下文。
- Input/output and state mapping: 多贡献者声明 -> 确定性排序串哈希 -> 调用 `registerManagedResource`。
- Error and edge behavior: 服务端参数报错（`INVALID_ARGUMENT`）立即止损而不盲目阻塞重试。
- Standards impact: `MC-TEST-001, MC-TIME-001, MC-LOG-001`.
- Literal rule enforcement: `Rule 4, Rule 9, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiSelfRegistrationRunnerTest`。
- Verification contribution: 保障微服启动强韧度。
- After this file: 自注册测试红灯成立。

#### File 3 — `CREATE tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiClientProperties.java`
- Purpose: 声明客户端连接天枢作用域、应用标号、内存缓存 TTL (`Duration`)、防抖窗 (`Duration`)、退避基时与启停开关之 `@ConfigurationProperties`。
- Symbols: `DdcSiClientProperties` (`@ConfigurationProperties(prefix = "egon.cola.component.tianshu.si.client")`, `@Validated`).
- Repository evidence: `ai/properties/` 目录既有属性规范。
- Dependencies and consumers: File 4..9。
- Why now: 驱动全部运行时超参。
- Contract/signature changes: 默认 `enabled = false`、`failFastOnStartup = false`、`cacheTtl = 60s`、`debounceWindow = 2s`。
- Input/output and state mapping: 配置文件 -> 受校验绑定属性。
- Error and edge behavior: `enabled=true` 时缺关键作用域配置导致清晰绑定拒绝。
- Standards impact: `MC-CONFIG-001, MC-VALID-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 2, Rule 7, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiClientProperties`。
- Verification contribution: 测试正反配置加载验证通过。
- After this file: 客户端配置契约就绪。

#### File 4 — `CREATE tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogCache.java`
- Purpose: 进程内无锁不可变热快照储存库，按作用域及资源键/锁定版本号构建并发查询容器，记录同步 `Instant`。
- Symbols: `DdcSiCatalogCache` (`getResource`, `listByScope`, `putAllSnapshot`, `evictOrUpdateOne`, `currentRevision`, `isStale`).
- Repository evidence: 客户端高速缓存在既有工作区/缓存规范。
- Dependencies and consumers: `DdcSiCatalogSyncCoordinator`。
- Why now: 确保高并发业务读取时永无撕裂读写视图。
- Contract/signature changes: 每次更新替换不可变容器引用；利用注入 `Clock` 检查软失效时间。
- Input/output and state mapping: 键索引 -> 不可变 `DdcSiResourceDescriptor`。
- Error and edge behavior: 缺条目返空或支持受控保底旧条目。
- Standards impact: `MC-MODEL-001, MC-UTIL-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 3, Rule 5, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiCatalogCache`。
- Verification contribution: 并发与快照替换测试转绿。
- After this file: 本地无锁快照库落地。

#### File 5 — `CREATE tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogSyncCoordinator.java`
- Purpose: 作为带一级/二级防抖缓存装饰层实现 `DdcSiCatalogClient`，协同内存快照、Redis `siCatalogVersionKey` 与底层 gRPC 通道，提供突发合并及降级守护。
- Symbols: `DdcSiCatalogSyncCoordinator` (`@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`, implements `DdcSiCatalogClient`).
- Repository evidence: 复用 `DdcRedisKeys.siCatalogVersionKey`。
- Dependencies and consumers: `@Qualifier("ddcSiCatalogRpcClient")` 注入底层远程客户端，服务于业务方与 Step 9。
- Why now: 解决海量并发下游同步压垮天枢服务端和偶发断连的风险。
- Contract/signature changes: 使用显式 `@Qualifier` 注入底层 RPC 客户端避免与自己 `@Primary` Bean 环状自注入。
- Input/output and state mapping: 读取 -> 先检内存/Redis revision -> 仅变更或首次缺条目走防抖/拉取远端。
- Error and edge behavior: 远端失败留存最近有效热快照保底可用并记限频警告日志。
- Standards impact: `MC-BEAN-001, MC-LOG-001, MC-UTIL-001, MC-PATTERN-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 4, Rule 5, Rule 9, Rule 10, Rule 11`.
- Required change: 实现 `DdcSiCatalogSyncCoordinator` 实现 `DdcSiCatalogClient` 全部方法、Redis 版本检查与调度器合并防抖逻辑，重点如下：
  - 突发合并：按作用域以线程安全标示旗帜及单线程定时任务在 `debounceWindow` 窗口期合并同域重拉请求；
  - 降级保底：同步拉取抛出客户端/通讯异常时不清除既有快照，记录含作用域与异常概要日志并回退提供既知最近成功版本；语义搜索 `semanticSearch` 严格实时穿透底层客户端。
- Verification contribution: 使 `DdcSiCatalogSyncCoordinatorTest` 转绿。
- After this file: 核心缓存协同代理落地。

#### File 6 — `CREATE tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiPublicationListener.java`
- Purpose: 消费 `EVENT-001` (`DdcSiPublicationEvent`)，使用有界 `eventId` 去重集合防重复重放，并根据本地作用域筛选是否驱动协调器增量刷新。
- Symbols: `DdcSiPublicationListener` (`onCatalogPublicationEvent`, `@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: 参照 `ai/workspace/DdcAiPublicationListener.java` 事件响应写法。
- Dependencies and consumers: Spring ApplicationEvent / 消息订阅桥、`DdcSiCatalogSyncCoordinator`。
- Why now: 完成从天枢 Outbox 事件到客户端内存失效的最后一公里触发。
- Contract/signature changes: 非匹配本租户/环境作用域之事件即刻安全忽略；有界 LRU（10000 项）保内存安全。
- Input/output and state mapping: 事件 -> 过滤 & 去重 -> 增量触发更新。
- Error and edge behavior: 单条监听出错记录异常而绝不瘫痪消费线程。
- Standards impact: `MC-BEAN-001, MC-LOG-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 4, Rule 9, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiPublicationListener`。
- Verification contribution: 验证跨租户不误刷新与重复投递去重测试。
- After this file: 增量推送链路完备。

#### File 7 — `CREATE tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiLocalResourceContributor.java`
- Purpose: 供下游微服务声明代码内默认附带的 Agent/MCP/Skill 列表并参与自注册上报的极简扩展接口。
- Symbols: `DdcSiLocalResourceContributor` (`contributeResources()`).
- Repository evidence: Spring SPI 标准贡献者模式。
- Dependencies and consumers: 业务微服务声明 `@Bean` 及 File 8 运行器。
- Why now: 支撑零代码侵入的代码优先上报能力。
- Contract/signature changes: 单一专注接口方法。
- Input/output and state mapping: 声明列表 -> 上报请求集合。
- Error and edge behavior: 空列表即安全跳过。
- Standards impact: `MC-ARCH-001, MC-NAME-001`.
- Literal rule enforcement: `Rule 1, Rule 11`.
- Required change: 创建 `DdcSiLocalResourceContributor` 接口。
- Verification contribution: 在自注册测充当贡献者桩输入。
- After this file: 扩展点确立。

#### File 8 — `CREATE tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiSelfRegistrationRunner.java`
- Purpose: 实现 `ApplicationRunner`，汇集所有本地资源贡献者并统一计算排序确定指纹 `specHash`，利用指数退避调用 `RPC-003` 上报，且具备可控 fail-open。
- Symbols: `DdcSiSelfRegistrationRunner` (`ApplicationRunner`, `@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: Boot 运行周期最佳实践。
- Dependencies and consumers: `ObjectProvider<DdcSiLocalResourceContributor>`, `DdcSiCatalogClient`, `DdcSiClientProperties`。
- Why now: File 2 测试校验之目标实现。
- Contract/signature changes: 使用 Jackson 确定性排序规范化计算 `specHash` 避免每次重启误认配置变化。
- Input/output and state mapping: 本地资源列表 -> `RPC-003` 幂等自上报。
- Error and edge behavior: 限次退避重试后若 `failFastOnStartup = false` 则记录 `WARN` 后正常返回避免阻断微服务起死。
- Standards impact: `MC-BEAN-001, MC-LOG-001, MC-VALID-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 9, Rule 10, Rule 11`.
- Required change: 创建 `DdcSiSelfRegistrationRunner`。
- Verification contribution: 使 `DdcSiSelfRegistrationRunnerTest` 全部场景转绿。
- After this file: 自注册启动运行器就位。

#### File 9 — `MODIFY tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/autoconfigure/DdcAiStarterAutoConfiguration.java`
- Purpose: 在实查路径 `ai/autoconfigure/DdcAiStarterAutoConfiguration.java` 中通过条件开关增量注册客户端缓存体系与自注册 Bean。
- Symbols: Conditional SI beans (`ddcSiCatalogCache`, `@Primary` `ddcSiCatalogSyncCoordinator`, `ddcSiPublicationListener`, `ddcSiSelfRegistrationRunner`), `@EnableConfigurationProperties(DdcSiClientProperties.class)`.
- Repository evidence: 已验证文件真实路径 `ai/autoconfigure/DdcAiStarterAutoConfiguration.java`。
- Dependencies and consumers: Spring Boot 自动装载加载进外部微服务容器。
- Why now: 打通自动化开箱即用。
- Contract/signature changes: 严格被 `@ConditionalOnProperty(prefix = "egon.cola.component.tianshu.si.client", name = "enabled", havingValue = "true")` 包裹；底层注入使用明确 `@Qualifier` 防自身循环注入。
- Input/output and state mapping: 启停开关决定是否实例化全系 SI 客户端协同 Bean。
- Error and edge behavior: 缺失底层连接客户端时给出清晰条件提示。
- Standards impact: `MC-BEAN-001, MC-CONFIG-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 4, Rule 7, Rule 11`.
- Required change: 修改 `DdcAiStarterAutoConfiguration` 追加条件 SI Bean 装配逻辑及 `@Qualifier` 指定。
- Verification contribution: 完成全部条件测试开关并通过本地 `.m2` 安装供 Step 9 消费。
- After this file: Step 7 收官就位。

- Validation working directory: `tianshu-service`
- Verification command: `./mvnw -pl stellar-enclosure-tianshu-ai-starter -Dtest='DdcSi*Test' test && ./mvnw -pl stellar-enclosure-tianshu-ai-starter test && ./mvnw -pl stellar-enclosure-tianshu-ai-starter -am install -DskipTests`
- Expected result: `DdcSi*Test` 及既往全部 AI Starter 单元测试 100% 通过，`stellar-enclosure-tianshu-ai-starter` 安装入本地 Maven 仓。
- Failure returns to: 查核循环注入 `@Qualifier` 标注或退避逻辑再重测。
- Completion criteria: `TEST-006` 客户端侧、缓存防抖、降级保护及开关隔离测试悉数绿灯并 `.m2` install。
- Rollback: 局部 `git revert` Step 7 提交。
- Commit paths: `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/test/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogSyncCoordinatorTest.java`, `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/test/java/top/egon/cola/component/tianshu/ai/si/DdcSiSelfRegistrationRunnerTest.java`, `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiClientProperties.java`, `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogCache.java`, `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiCatalogSyncCoordinator.java`, `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiPublicationListener.java`, `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiLocalResourceContributor.java`, `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/si/DdcSiSelfRegistrationRunner.java`, `tianshu-service/stellar-enclosure-tianshu-ai-starter/src/main/java/top/egon/cola/component/tianshu/ai/autoconfigure/DdcAiStarterAutoConfiguration.java`
- Commit: `feat(tianshu-ai-starter): deliver SI client cache, debounced sync, event listener and self-registration`

---

### Step 8 — Build tianshu-web SI governance console pages, RBAC permissions, API client and bilingual i18n

- Requirements: `REQ-001, REQ-002, REQ-003, REQ-004, REQ-005, REQ-007, REQ-008, REQ-009`
- Dependencies: `Step 6`
- Baseline state: `tianshu-web` 已有标准架构及严格强检门禁 `scripts/check-i18n.mjs`，尚未具备超级智能治理相关视图、API 与双语词条。
- Observable outcome: 目录双模检索页（支持高亮和降级警示条）、跨环境矩阵（支持直接晋级/回滚）、Agent/MCP 结构化表单编辑（不含流程连线编排）与绑定/引用视图完整可用；`check-i18n.mjs`, `tsc`, 全系 Vitest 与 Vite build 全部通过。
- End state: 独立前端单仓交付完备可直接对接 Step 6 后端接口联调。
- Test-first gate: `Required — 先建 3 个 Vitest + RTL 测试套件锁定 UI 组件呈现、FTS 降级感知、并发 409 引导与权限隐藏期待`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-TIME-001, MC-TEST-001`
- Literal Rules: `Rule 6, Rule 10, Rule 11` (Non-Java SPA maintains JSON/UTC boundary compliance, bilingual parity and test proof)
- Ordered files:

#### File 1 — `CREATE tianshu-web/src/pages/SiCatalogPage.test.tsx`
- Purpose: 验证目录列表筛选、RRF 搜索分卡片渲染、`degradedToFtsOnly=true` 提示条显现、索引手工重建触发与读/写/索引权限显隐之 RED 测试。
- Symbols: `SiCatalogPage.test.tsx`.
- Repository evidence: `src/pages/` 既有 Vitest + `@testing-library/react` 写法。
- Dependencies and consumers: 测试 File 4..8, File 11, File 13, File 16。
- Why now: 锁定目录主界面的全系行为与双语呈现基准。
- Contract/signature changes: Mock API 并采用真实中英渲染验证，不跳过 i18n check。
- Input/output and state mapping: 模拟列表和混合结果 -> DOM 展示分数徽标与降级告警。
- Error and edge behavior: 断网显示重试按钮；无写入权限隐去写触发入口。
- Standards impact: `MC-TEST-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 10, Rule 11`.
- Required change: 创建 `SiCatalogPage.test.tsx`。
- Verification contribution: 验证目录中心完整体验 (`TEST-012`)。
- After this file: 目录界面契约锁定。

#### File 2 — `CREATE tianshu-web/src/pages/SiResourceEditorPage.test.tsx`
- Purpose: 测试新建及编辑原子 Agent 与 MCP 表单：验证结构化切换 `transport` 字段自适应、非法明文凭据客户端预检、草稿保存传入 `expectedVersion` 以及遭遇 `409` 时弹窗提示而不抹除用户输入。
- Symbols: `SiResourceEditorPage.test.tsx`.
- Repository evidence: 参照 `AiResourceEditorPage.tsx` 相关 RTL 测试。
- Dependencies and consumers: 测试 File 4..7, File 10, File 12, File 13, File 16。
- Why now: 确保高复杂结构表单交互边界零退失。
- Contract/signature changes: 检验前端表单不含非原子流程连线组件，准确体现原子属性配置。
- Input/output and state mapping: 更改 `transport: STREAMABLE_HTTP` -> 显示 Endpoint 字段并隐藏 stdio 命令字段；遇 409 -> 留住输入呈引导对比提示。
- Error and edge behavior: 服务端返 422 具体出错 JSON path 高亮对应字段提示。
- Standards impact: `MC-TEST-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `SiResourceEditorPage.test.tsx`。
- Verification contribution: 验证结构化表单及防覆盖保护 (`TEST-012`)。
- After this file: 编辑器测试锁定。

#### File 3 — `CREATE tianshu-web/src/pages/SiEnvMatrixPage.test.tsx`
- Purpose: 测试跨环境矩阵列渲染、向高环境发起晋级确认流（及缺前置依赖阻断反馈）、版本历史回滚及 `SiLifecycleDialog` 遭遇 `409 blockingReferences`（含天机流程来源标识）时显示阻断列表。
- Symbols: `SiEnvMatrixPage.test.tsx`.
- Repository evidence: Vitest + RTL 模式。
- Dependencies and consumers: 测试 File 4..7, File 9, File 13, File 16。
- Why now: 锁定最关键的多环境提升及防误删 UI 反馈。
- Contract/signature changes: 明确检验本地时区格式化输出与悬浮原始 `UTC Z` 展示一致。
- Input/output and state mapping: 点击晋级 -> 确认弹窗 -> API 成功更新 -> 状态刷新；归档受阻 -> 列出阻断天机流程键。
- Error and edge behavior: 无晋级权时动作禁用或隐藏。
- Standards impact: `MC-TEST-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 10, Rule 11`.
- Required change: 创建 `SiEnvMatrixPage.test.tsx`。
- Verification contribution: 验证矩阵治理交互测试 (`TEST-012`)。
- After this file: 全前端 RED 测集建齐。

#### File 4 — `MODIFY tianshu-web/src/api/types.ts`
- Purpose: 同步新增 SI 领域及全套 14 组 REST 接口匹配的 TS 强类型接口，严守零破坏既有 AI/配置/元数据定义。
- Symbols: `SiResourceKind`, `SiLifecycleStatus`, `SiBindingStatus`, `SiSourceType`, `SiResourceItem`, `SiVersionItem`, `SiBindingItem`, `SiReferenceItem`, `SiSearchHit`, `SiEnvMatrixRow`, `SiAgentSpecForm`, `SiMcpSpecForm` and request types.
- Repository evidence: 已阅读 `src/api/types.ts` L1–100。
- Dependencies and consumers: `src/api/si.ts` 与全部前端组件。
- Why now: 先确立前后端一比一静态编译对齐。
- Contract/signature changes: 所有时间戳均定义为 `string`（`UTC Z` ISO-8601）。
- Input/output and state mapping: Server JSON <-> TypeScript object.
- Error and edge behavior: 零隐式 `any`。
- Standards impact: `MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 6, Rule 10, Rule 11`.
- Required change: 向 `src/api/types.ts` 尾部补充全套 SI 相关 TS 接口声明。
- Verification contribution: `npx tsc --noEmit` 强校验通过。
- After this file: 前端类型体系完成。

#### File 5 — `CREATE tianshu-web/src/api/si.ts`
- Purpose: 实现 Spec `API-001`..`API-014` 前端异步请求函数，复用现有 HTTP 客户端配置与错误提取机制。
- Symbols: 14 exported typed functions covering catalog, versions, matrix, search, lifecycle, binding, references & reindex.
- Repository evidence: 仿照已读 `src/api/ai.ts` L1–120。
- Dependencies and consumers: File 8..13。
- Why now: 集中接口请求和阻断清单错误提取。
- Contract/signature changes: 自动传递多维 Scope 与版本号；解析 `blockingReferences` 挂于结构化报错对象抛给组件展示。
- Input/output and state mapping: UI payload -> REST JSON -> 解包或带错码明细异常。
- Error and edge behavior: 统一标准化网络报错透传。
- Standards impact: `MC-ARCH-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 创建 `src/api/si.ts`。
- Verification contribution: 配合前端 Vitest Mock 测全覆盖 14 个请求通路。
- After this file: API 调用层建立。

#### File 6 — `MODIFY tianshu-web/src/permissions/permissions.ts`
- Purpose: 注册与服务端 `DdcPermissionCodes` 完全匹配的 7 个 `tianshu:si:*` 权限常量。
- Symbols: 7 `SI_*` constants matching server RBAC permissions.
- Repository evidence: 已阅读 `permissions.ts` L1–15。
- Dependencies and consumers: `resources.ts`, pages & tests。
- Why now: 驱动控制按钮和页面访问拦截。
- Contract/signature changes: 原所有常数不变。
- Input/output and state mapping: 权限常数 -> 权鉴控件核对输入。
- Error and edge behavior: 缺权隐匿对应高风险操作按钮。
- Standards impact: `MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 向 `permissions.ts` 添加 7 个 SI 权限点常数。
- Verification contribution: 被页面权鉴断言测试覆盖。
- After this file: 前端权鉴常数组完成。

#### File 7 — `MODIFY tianshu-web/src/permissions/resources.ts`
- Purpose: 将 SI 治理模块导航资源及各子视图准入条件纳入现行资源权限表。
- Symbols: SI resource metadata & route guard rules.
- Repository evidence: 已阅读 `resources.ts` L1–100。
- Dependencies and consumers: `App.tsx` 左侧菜单与权限路由守卫。
- Why now: 控制边栏菜单动态渲染。
- Contract/signature changes: 所有标题与说明严格使用 i18n 消息键，不放硬编码英文/中文直接文案。
- Input/output and state mapping: 用户所持权 -> 显示对应超级智能菜单。
- Error and edge behavior: 禁止越权路由打开。
- Standards impact: `MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 更新 `src/permissions/resources.ts` 添加 SI 导航菜单及路由授权配置。
- Verification contribution: `check-i18n.mjs` 静态扫描零警告通过。
- After this file: 前端授权导航映射就位。

#### File 8 — `CREATE tianshu-web/src/pages/SiCatalogPage.tsx`
- Purpose: 构建超级智能资源全目录管理与双模搜索界面（四态 Filter + 普通分页 + RRF 混合语义检索评分及 `degradedToFtsOnly` 柔和警示横幅 + 手工重触发重建索引用入口）。
- Symbols: `SiCatalogPage` (`defineMessages`).
- Repository evidence: 仿照 `AiResourcesPage.tsx` 布局风格与已有组件。
- Dependencies and consumers: `src/api/si.ts`, 子面板与 `App.tsx`。
- Why now: 交付控制台高频首屏。
- Contract/signature changes: 全文本通过 `defineMessages` 管理，展示时间为本地区域格式与悬停 UTC 值；对受权管理员按状态 `FAILED`/`INDEXED` 展示重触发重建按钮。
- Input/output and state mapping: 筛选状态切换 <-> 请求列表或搜索展示 -> 弹出绑定或跳转编辑详情。
- Error and edge behavior: 搜索接口报错显露优雅重试栏；FTS 降级态顶部提醒不影响读使用。
- Standards impact: `MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 10, Rule 11`.
- Required change: 创建 `SiCatalogPage.tsx`。
- Verification contribution: 使 `SiCatalogPage.test.tsx` 全绿。
- After this file: 主目录界面落成。

#### File 9 — `CREATE tianshu-web/src/pages/SiEnvMatrixPage.tsx`
- Purpose: 交付多环境版本基线对照矩阵页，直观对比不同环境发布号与指纹一致性，并发起受保护晋级或同环境安全回滚。
- Symbols: `SiEnvMatrixPage` (`defineMessages`).
- Repository evidence: Spec §12.2。
- Dependencies and consumers: `src/api/si.ts`, `SiLifecycleDialog`, `App.tsx`。
- Why now: 交付跨环境发布可观测与控制中心。
- Contract/signature changes: 严格权限护栏；回滚需选版本后核对摘要才激活执行。
- Input/output and state mapping: 筛选条件 -> 矩阵行 -> 召唤晋级/回滚弹窗 -> 刷新表格。
- Error and edge behavior: 依赖缺项与并发漂移均精准提示弹窗细节。
- Standards impact: `MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 10, Rule 11`.
- Required change: 创建 `SiEnvMatrixPage.tsx`。
- Verification contribution: 使 `SiEnvMatrixPage.test.tsx` 全绿。
- After this file: 环境矩阵页落成。

#### File 10 — `CREATE tianshu-web/src/pages/SiResourceEditorPage.tsx`
- Purpose: 交付面向 `AGENT`、`MCP` 及 `PROMPT`/`SKILL` 之结构化编辑页面（不设 BPMN/工作流节点画布），涵括动态 MCP transport 表单切换、JSON Schema 格式预检、明文密钥警报、历史版本 diff 查看与带并发版本校验草稿保存及发布。
- Symbols: `SiResourceEditorPage` (`defineMessages`).
- Repository evidence: `AiResourceEditorPage.tsx`。
- Dependencies and consumers: `src/api/si.ts`, `SiReferencesPanel`, `SiLifecycleDialog`, `App.tsx`。
- Why now: 提供规范低错的原子智能资源编排体验。
- Contract/signature changes: 提交草稿保留用户编辑内容并在遭遇 `409` 时提示重新加载对比而不盲目清空表单；`timeoutSeconds` 明确以秒数单位输入展示。
- Input/output and state mapping: 受控表单树 <-> 标准化 `specJson` 传输。
- Error and edge behavior: 服务端 422 error path 定点展示对应字段告警。
- Standards impact: `MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 6, Rule 10, Rule 11`.
- Required change: 创建 `SiResourceEditorPage.tsx`。
- Verification contribution: 使 `SiResourceEditorPage.test.tsx` 全绿。
- After this file: 核心编辑器页面落成。

#### File 11 — `CREATE tianshu-web/src/pages/SiServiceBindingPanel.tsx`
- Purpose: 可嵌入抽屉/面板，管理特定 `appCode` 到目标资源发布版本锁或取消锁并立即反馈绑定影响面。
- Symbols: `SiServiceBindingPanel` (`defineMessages`).
- Repository evidence: Spec §12.3。
- Dependencies and consumers: `SiCatalogPage`, `SiResourceEditorPage`, `src/api/si.ts`。
- Why now: 抽离绑定表单保主页面清爽。
- Contract/signature changes: 受 `SI_BINDING_WRITE` 权限控制可写度。
- Input/output and state mapping: 选择/输入 `appCode` 与可选锁定版本 -> 写入或移除绑定 -> 通知刷新。
- Error and edge behavior: 输入合法正则校验与 API 报错展示。
- Standards impact: `MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 10, Rule 11`.
- Required change: 创建 `SiServiceBindingPanel.tsx`。
- Verification contribution: 在目录测试组件场景中覆盖渲染及互动。
- After this file: 应用绑定组件完成。

#### File 12 — `CREATE tianshu-web/src/pages/SiReferencesPanel.tsx`
- Purpose: 只读双栏面板展示出度引用（该资源调用的 Prompt/Skill/MCP/SubAgent）与入度被引用源（特别凸显上游 Agent 及在跑的 Tianji Flowable 流程键值），便于运维安全下线及影响面洞察。
- Symbols: `SiReferencesPanel` (`defineMessages`).
- Repository evidence: Spec §12.3 & §9.1 `API-010`/`API-014` 周边参考展示。
- Dependencies and consumers: `SiResourceEditorPage`, `src/api/si.ts`。
- Why now: 让跨系统引用关系完全白盒透明。
- Contract/signature changes: 纯展示与跳转快捷链接，全文本走 `defineMessages`。
- Input/output and state mapping: `resourceId` -> 查询正反向边 -> 分栏列表展示（附类型标签及是否必填提示）。
- Error and edge behavior: 缺数据显标准无依赖态，失败显重新加载按钮。
- Standards impact: `MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 创建 `SiReferencesPanel.tsx`。
- Verification contribution: 在编辑器页 RTL 单测中验证天机流程被引用行显现。
- After this file: 依赖洞察组件完成。

#### File 13 — `CREATE tianshu-web/src/pages/SiLifecycleDialog.tsx`
- Purpose: 统一承接发布变更说明输入、晋级目标环境挑选、停用提示与归档不可逆警示之交互弹窗，遇服务端 `409 blockingReferences` 时渲染清晰依赖拦截详单阻止误操作。
- Symbols: `SiLifecycleDialog` (`defineMessages`).
- Repository evidence: Spec §12.4 与全局 Modal 规范。
- Dependencies and consumers: `SiCatalogPage`, `SiEnvMatrixPage`, `SiResourceEditorPage`, `src/api/si.ts`。
- Why now: 统一下游各页高危生命周期操作的二次防线体验。
- Contract/signature changes: 发送操作期间禁用重复点按。
- Input/output and state mapping: 操作模式与选定项 -> 提交 -> 成功关闭回调刷新或展示阻断清单。
- Error and edge behavior: 清晰呈现 `blockingReferences`（天机流程或上游 Agent ID），指引用户先行解除关联再归档。
- Standards impact: `MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 10, Rule 11`.
- Required change: 创建 `SiLifecycleDialog.tsx`。
- Verification contribution: 在矩阵及目录单测中校验阻断明细呈现和取消安全行为。
- After this file: 全部通用 SI 子组件开发完结。

#### File 14 — `MODIFY tianshu-web/src/pages/AiResourcesPage.tsx`
- Purpose: 在既有传统 AI `Prompt`/`Skill` 页面头部平滑嵌入引导跳往新版 SI 治理控制台的提示条，严守老页面其余一切功能 100% 原样可用。
- Symbols: Bilingual migration notice banner using `defineMessages`.
- Repository evidence: 已阅读 `AiResourcesPage.tsx` L1–120。
- Dependencies and consumers: 既有 AI 运营页面使用者与路由器。
- Why now: 给现网使用 Prompt/Skill 管理的客户自然过渡通路。
- Contract/signature changes: 零改动现有 CRUD 表格和逻辑代码；全部提示字走 `defineMessages`。
- Input/output and state mapping: 点击提示条按钮 -> 导航至 `/si/catalog`。
- Error and edge behavior: 无。
- Standards impact: `MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 在 `AiResourcesPage.tsx` 头部新增带导航按钮的双语导流提示组件。
- Verification contribution: 不破坏既有一条既有 UI 测试且顺利通过 `check-i18n.mjs`。
- After this file: 旧页平滑导流配置完毕。

#### File 15 — `MODIFY tianshu-web/src/App.tsx`
- Purpose: 在 `App.tsx` 主应用框架增加超级智能（SI）导航目录与 `/si/catalog`, `/si/matrix`, `/si/resources/new`, `/si/resources/:id` 路由条目。
- Symbols: Route declarations & navigation wiring.
- Repository evidence: 已阅读 `App.tsx` L1–74 路由和 Layout 结构。
- Dependencies and consumers: 全站页面调度及守卫组件。
- Why now: 串联所有新建 SI 视图进主站。
- Contract/signature changes: 全部已有路由及 Layout 组件保持不改动。
- Input/output and state mapping: URL 路径匹配 -> 经权鉴守卫渲染新页面。
- Error and edge behavior: 未知/缺权路由按原有回 fallback 组件路由处理。
- Standards impact: `MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 向 `src/App.tsx` 追加 SI 主路由与导航挂载代码。
- Verification contribution: 经由 `tsc`, `npm test` 与 `vite build` 确认路由树构建正常。
- After this file: 主应用接入闭环完成。

#### File 16 — `MODIFY tianshu-web/src/test/i18nCatalogs.json`
- Purpose: 在 `i18nCatalogs.json` 对齐写入 `common` 导航新键和完整 `siResources` 界面双语字典（确保 `zh-CN` 与 `en-US` 键集合、参数插值 `{{...}}` 完全 1:1 严格一致）。
- Symbols: `common` SI nav keys & `siResources` namespace (`zh-CN` + `en-US`).
- Repository evidence: 已阅读 `i18nCatalogs.json` L1–60 与 `scripts/check-i18n.mjs` L1–70。
- Dependencies and consumers: 全部 SI 界面、`catalogCoverage.test.ts` 与 CI 校验脚本。
- Why now: `check-i18n.mjs` 与 `catalogCoverage.test.ts` 要求每一个 `defineMessages` 键值全覆盖才可放行测试与构建。
- Contract/signature changes: 不删除不改动既有词典。
- Input/output and state mapping: key id -> `zh-CN` / `en-US` 字符串映射。
- Error and edge behavior: 占位符对不上号会在门禁立毙，交付时直接跑脚本检验至 0 错。
- Standards impact: `MC-TEST-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 11`.
- Required change: 更新 `src/test/i18nCatalogs.json` 成对补全全部新界面双语字典及插值变量。
- Verification contribution: 使全部 RTL 测试、`catalogCoverage.test.ts`、`check-i18n.mjs`、`tsc` 及生产 `build` 全通。
- After this file: Step 8 全量完成。

- Validation working directory: `tianshu-web`
- Verification command: `node scripts/check-i18n.mjs && npx tsc --noEmit && npm test -- --run && npm run build`
- Expected result: `check-i18n.mjs` 退出 0 零漏译/硬编码报错，`tsc` 零错，全量 Vitest（含三套 SI 新测试及旧有全测）100% 通过，`npm run build` 产成干净 dist 制品。
- Failure returns to: 依据错误行修补 TSX 组件 `defineMessages`、`i18nCatalogs.json` 对称词条或 TS 类型后复检至全绿。
- Completion criteria: 满足 `REQ-009` 与 `TEST-012` 且原有 `tianshu-web` 功能/检查无一丝退步。
- Rollback: 局部 `git revert` Step 8 提交。
- Commit paths: `tianshu-web/src/pages/SiCatalogPage.test.tsx`, `tianshu-web/src/pages/SiResourceEditorPage.test.tsx`, `tianshu-web/src/pages/SiEnvMatrixPage.test.tsx`, `tianshu-web/src/api/types.ts`, `tianshu-web/src/api/si.ts`, `tianshu-web/src/permissions/permissions.ts`, `tianshu-web/src/permissions/resources.ts`, `tianshu-web/src/pages/SiCatalogPage.tsx`, `tianshu-web/src/pages/SiEnvMatrixPage.tsx`, `tianshu-web/src/pages/SiResourceEditorPage.tsx`, `tianshu-web/src/pages/SiServiceBindingPanel.tsx`, `tianshu-web/src/pages/SiReferencesPanel.tsx`, `tianshu-web/src/pages/SiLifecycleDialog.tsx`, `tianshu-web/src/pages/AiResourcesPage.tsx`, `tianshu-web/src/App.tsx`, `tianshu-web/src/test/i18nCatalogs.json`
- Commit: `feat(tianshu-web): deliver SI resource governance UI pages, RBAC controls and bilingual i18n`

---

### Step 9 — Integrate pre-activation binding verifier and runtime template resolution in tianji-service

- Requirements: `REQ-001, REQ-004, REQ-006, REQ-008`
- Dependencies: `Step 1, Step 2, Step 3, Step 6, Step 7`
- Baseline state: 经仓实核 `tianji-process-server`（真实包根目录 `top.egon.cola.platform.tianji.process.server`）与 `tianji-agent-runtime`（真实包根目录 `top.egon.cola.platform.tianji.agent`）尚未引入 `stellar-enclosure-tianshu-starter`；`AutomationActivationService` 在启动工作流前未做 Tianshu 原子 Agent 发布检验；天机智能体运行时尚未将天枢资源解析注入执行束，也未开启纯 Agentic 模式关闭遗留静态开关。
- Observable outcome: 未发布/归档的 Agent 无法通过 `AutomationActivationService` 启用 Flowable 流程；启用成功的流程自动登记 `TIANJI_PROCESS` 引用锁定天枢资源归档安全；`TianshuTemplateResolver` 按显式锁定/应用绑定/活跃版本精准加载组装可执行上下文供 Agentic 循环调起；全关联模块测试 100% 绿灯。
- End state: 跨天枢、组件、前端、天机四仓的超级智能定义与执行联动工程完全交付完工。
- Test-first gate: `Required — 先建 TianshuAgentBindingVerifierTest 与 TianshuTemplateResolverTest 锁定激活门禁阻断、纯传统流程免检与精准版本绑定束生成`
- Manual Checks: `MC-ARCH-001, MC-SCOPE-001, MC-REUSE-001, MC-DEP-001, MC-VALID-001, MC-MODEL-001, MC-LOG-001, MC-BEAN-001, MC-CONFIG-001, MC-PATTERN-001, MC-TIME-001, MC-TEST-001`
- Literal Rules: `Rule 1, Rule 2, Rule 4, Rule 5, Rule 7, Rule 9, Rule 10, Rule 11`
- Ordered files:

#### File 1 — `CREATE tianji-service/stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/integration/TianshuAgentBindingVerifierTest.java`
- Purpose: 建立 `TEST-010` 编排服侧 RED 单元测试：校验含非法/未发布 Agent 节点之流程激活被 `AutomationActivationService` 阻断、含合法发布 Agent 之流程登记 `TIANJI_PROCESS` 并顺利上线、停用下线流程同步卸载引用锁以及非智能体普通 BPMN 流程零调用放行。
- Symbols: `TianshuAgentBindingVerifierTest`.
- Repository evidence: 对照 `tianji-process-server` 既有 JUnit 5/Mockito 单测包结构。
- Dependencies and consumers: 测试 File 3, File 5, File 6, File 9。
- Why now: 用测试固定安全门禁不能放水与不误伤传统工作流的红线。
- Contract/signature changes: 验证远程预检在本地状态修改之前发生，失败时不留下脏流程激活状态。
- Input/output and state mapping: Flowable 节点扩展属性 -> 验证/登记 -> 通过启用或报错阻断。
- Error and edge behavior: `strictActivationCheck = true`（prod 默认）在天枢不可达时拒绝冒险激活；开发可配置宽容放行。
- Standards impact: `MC-TEST-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 2, Rule 9, Rule 11`.
- Required change: 在实包 `process/server/integration/` 创建 `TianshuAgentBindingVerifierTest`。
- Verification contribution: 直接检验核心 Flowable <-> Tianshu 前置校验逻辑。
- After this file: 编排端 RED 门禁定格。

#### File 2 — `CREATE tianji-service/stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/si/TianshuTemplateResolverTest.java`
- Purpose: 建立 `TEST-010` 运行时侧 RED 单测：校验根据锁定 `pinnedVersionNo` 或 `appCode` 绑定精确解析 Agent 及关联的 Prompt/Skill/MCP（各自继承声明版本号），验证必选依赖缺失立刻安全报错而可选依赖允许提示跳过，且确保禁读草稿版本。
- Symbols: `TianshuTemplateResolverTest`.
- Repository evidence: `tianji-agent-runtime` 测试包结构 `top.egon.cola.platform.tianji.agent.*`。
- Dependencies and consumers: 测试 File 4, File 7, File 8, File 10。
- Why now: 锁定运行时组装上下文的正确优先级与只读不可变契约。
- Contract/signature changes: 验证组装包中所有描述符版本精确对应预期且不含明文密钥漏泄。
- Input/output and state mapping: 流程调度执行请求 -> 解析完整 Agent+Tools/Prompt -> 输出 `ResolvedAgentExecutionBundle`。
- Error and edge behavior: 找不到已发布主 Agent 或关键必需引用时抛明确运行时异常。
- Standards impact: `MC-TEST-001, MC-TIME-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 10, Rule 11`.
- Required change: 在实包 `agent/si/` 创建 `TianshuTemplateResolverTest`。
- Verification contribution: 验证运行时束解析零偏差。
- After this file: 运行端 RED 测试定格。

#### File 3 — `MODIFY tianji-service/stellar-enclosure-tianji-process-server/pom.xml`
- Purpose: 按 `PLAN-CLAR-001` 与审批登记为 `tianji-process-server` 补充声明 `top.egon:stellar-enclosure-tianshu-starter` 依赖以接入 `DdcSiCatalogClient` 与 SI 枚举/DTO。
- Symbols: `<dependency>top.egon:stellar-enclosure-tianshu-starter</dependency>`.
- Repository evidence: 已核实当前 `tianji-process-server/pom.xml` L1–100 尚缺此条目。
- Dependencies and consumers: File 5 (`TianshuAgentBindingVerifier`)。
- Why now: 解除编排服源码编译依赖阻塞。
- Contract/signature changes: 沿用统一版号 `0.0.1`/父级托管，绝对不改第三方包版本。
- Input/output and state mapping: Maven POM 依赖图更新。
- Error and edge behavior: 保证零依赖冲突。
- Standards impact: `MC-DEP-001, MC-REUSE-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 5, Rule 11`.
- Required change: 在 `stellar-enclosure-tianji-process-server/pom.xml` 添加 `stellar-enclosure-tianshu-starter` 依赖。
- Verification contribution: 让 `mvnw test-compile` 顺利认出天枢客户端类。
- After this file: 编排模块具备客户端类型支持。

#### File 4 — `MODIFY tianji-service/stellar-enclosure-tianji-agent-runtime/pom.xml`
- Purpose: 按 `PLAN-CLAR-001` 为 `tianji-agent-runtime` 显式引入 `top.egon:stellar-enclosure-tianshu-starter` 依赖，以供运行时上下文装配器消费 `DdcSiCatalogClient`。
- Symbols: `<dependency>top.egon:stellar-enclosure-tianshu-starter</dependency>`.
- Repository evidence: 已核实当前 `tianji-agent-runtime/pom.xml` L1–100 缺直接声明。
- Dependencies and consumers: File 7 (`TianshuTemplateResolver`)。
- Why now: 使 `agent-runtime` 模块可独立编译与单测，不依赖间接上层打包传递。
- Contract/signature changes: 沿用统一版号 `0.0.1`/BOM，不动 Flowable 或 Spring AI 基线版本。
- Input/output and state mapping: Maven POM 依赖图补齐。
- Error and edge behavior: 无依赖环或冲突。
- Standards impact: `MC-DEP-001, MC-REUSE-001, MC-SCOPE-001`.
- Literal rule enforcement: `Rule 5, Rule 11`.
- Required change: 在 `stellar-enclosure-tianji-agent-runtime/pom.xml` 添加 `stellar-enclosure-tianshu-starter` 依赖。
- Verification contribution: 支持独立编译并通过测试。
- After this file: 运行时模块依赖齐备。

#### File 5 — `CREATE tianji-service/stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/integration/TianshuAgentBindingVerifier.java`
- Purpose: 在已验证真实包路径下建立流程与天枢原子资源之间轻薄隔离防腐层，落实 `INTERNAL-001`（验证存在且可运行并登记 `TIANJI_PROCESS` 引用边）与 `INTERNAL-002`（下线解除流程引用边）。
- Symbols: `TianshuAgentBindingVerifier` (`@Component`, `@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: 对齐 `process/server/` 包体系及接口集成习惯。
- Dependencies and consumers: 注入 `ObjectProvider<DdcSiCatalogClient>`、环境与严格开关属性，服务于 `AutomationActivationService`。
- Why now: 守住“发布启用前校验合法 Agent、归档拦截被占用流程资源”核心安全闭环。
- Contract/signature changes: 提取节点引用的全部 `agentKey` + 可选 `pinnedVersionNo`，批量校验 `resolveResource` 结果非空且状态为可运行发布态；登记和注销调用幂等执行。
- Input/output and state mapping: ProcessKey + BPMN agent node metadata -> 逐条验证天枢 + 同步 `TIANJI_PROCESS` 引用。
- Error and edge behavior: `DdcSiCatalogClient` 缺失或网络中断时按 `strictActivationCheck` 策略快速拒绝激活（prod 默认）或明确记警放行。
- Standards impact: `MC-ARCH-001, MC-BEAN-001, MC-LOG-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 2, Rule 4, Rule 9, Rule 11`.
- Required change: 创建 `TianshuAgentBindingVerifier`。
- Verification contribution: 使 `TianshuAgentBindingVerifierTest` 检验核心验证行为全部转绿。
- After this file: 天机前置验证门禁实现完毕。

#### File 6 — `MODIFY tianji-service/stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/AutomationActivationService.java`
- Purpose: 在天机核实文件 `release/AutomationActivationService.java` 中接入激活前预检验及下线引用清理解绑，实施阶段先读该文件准确方法签名保证精准无侵入融合，并保障远端 RPC 绝不嵌套进本地长数据库事务内部（Rule 7 / `MC-BEAN-001`）。
- Symbols: Optional `ObjectProvider<TianshuAgentBindingVerifier>` field/constructor hook inside `AutomationActivationService`.
- Repository evidence: 已验证文件真实路径在 `process/server/release/AutomationActivationService.java`。
- Dependencies and consumers: 既有 `ProcessActivationController` 与相关发布接口。
- Why now: 正式在 Flowable 激活点启用门禁拦阻。
- Contract/signature changes: 使用兼容可选 `ObjectProvider` 注入（若老测试仅使用现有精简构造器调用，提供兼容性构造保留或 `@Autowired(required = false)`），确保既有纯工作流单元测试 zero break；仅当流程存在 Agent 节点才调起远端核验。
- Input/output and state mapping: 启用流程 -> 事务外抽取节点并调用 `verifier.verifyAndRegisterProcessBindings` -> 成功则走既定启用事务；停用流程 -> 下线提交后调用 `verifier.releaseProcessBindings`（容错打警不锁死下线）。
- Error and edge behavior: 预检验报错即时回拒激活，不触动现有库激活指针。
- Standards impact: `MC-ARCH-001, MC-BEAN-001, MC-LOG-001, MC-PATTERN-001`.
- Literal rule enforcement: `Rule 4, Rule 9, Rule 11`.
- Required change: 在 `AutomationActivationService` 增加激活前置预检与停用释放协作调用（确保原有非智能体流路径及旧构造兼容完全保持不变）。
- Verification contribution: 使 `TianshuAgentBindingVerifierTest` 及全 `tianji-process-server` 原有测集全通。
- After this file: 流程生命周期防错门卡接入完成。

#### File 7 — `CREATE tianji-service/stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/si/TianshuTemplateResolver.java`
- Purpose: 在真实包根目录 `top.egon.cola.platform.tianji.agent.si/` 实现运行时资源捆绑解析器：当 Flowable 节点调用单一智能体执行时，依循“显式 `pinnedVersionNo` -> 应用绑定版 -> 当前发布活跃版”序检索 Agent 快照并按边关联出精准的 Prompt/Skill/MCP 描述符组成只读执行束交付给 `agent-flow-starter` Agentic 引擎。
- Symbols: `TianshuTemplateResolver`, `ResolvedAgentExecutionBundle` (`@Slf4j`, `@RequiredArgsConstructor(onConstructor_ = @Autowired)`).
- Repository evidence: `agent/` 真实包结构与 Step 1 `DdcSiCatalogClient` 端口。
- Dependencies and consumers: `TianjiAgentRuntimeAutoConfiguration`、`tianji-agent-server` 任务运行器。
- Why now: 实现单点集中定义在运行时跨进程生效，无需将多变 Prompt 硬塞入 BPMN 参数里。
- Contract/signature changes: 彻底隔离流程节点，绝不读取草稿或归档资源；内附 `ResolvedAgentExecutionBundle` 不可变执行束模型，脱敏敏感凭据引述名。
- Input/output and state mapping: 节点上下文（租户、作用域、`appCode`、`agentKey`, 可选版本）-> 解析组装 `ResolvedAgentExecutionBundle`。
- Error and edge behavior: 主 Agent 缺或 `required=true` 依赖缺均直接报错中止；`required=false` 缺项记录结构化警告后跳过。
- Standards impact: `MC-ARCH-001, MC-MODEL-001, MC-BEAN-001, MC-LOG-001, MC-VALID-001`.
- Literal rule enforcement: `Rule 1, Rule 2, Rule 4, Rule 9, Rule 11`.
- Required change: 新建 `TianshuTemplateResolver` 实现精准版本与依赖聚合组装逻辑，主要顺序如下：
  - 主加载：用 `resolveResource` 获取合法 `AGENT` 快照，空或不可用即中断报错；
  - 关联加载：解包 Agent 规格内的 `references`，针对每条 `PROMPT`/`SKILL`/`MCP` 按其自身携带的 `pinnedVersionNo` 或上下文默认绑定拉取对应描述子；必需项缺即报错、可选项打警跳过；
  - 装配收束：封装返回不可变 `ResolvedAgentExecutionBundle`（含系统词、技能与 MCP 端定义及运行时步数/超时边界）。
- Verification contribution: 使 `TianshuTemplateResolverTest` 全数转绿。
- After this file: 天机 Agentic 执行上下文解析器完成。

#### File 8 — `MODIFY tianji-service/stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java`
- Purpose: 在核实真实路名 `agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java` 中条件注册 `TianshuTemplateResolver`（`@ConditionalOnBean(DdcSiCatalogClient.class)` 与 `@ConditionalOnMissingBean`）。
- Symbols: `tianshuTemplateResolver` Bean definition.
- Repository evidence: 已验证文件真实路径在 `platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java`。
- Dependencies and consumers: `tianji-agent-server` 及所有消费天机运行时的装配容器。
- Why now: 自动激活运行时模板注入服务。
- Contract/signature changes: 原有其它 Runtime Bean 保持零修改；仅在启用 `DdcSiCatalogClient` 时自动注入。
- Input/output and state mapping: `DdcSiCatalogClient` -> `TianshuTemplateResolver`。
- Error and edge behavior: 未开 SI 客户端之轻量单测或离线环境下自动豁免跳过，不影响上下文启动。
- Standards impact: `MC-BEAN-001, MC-ARCH-001`.
- Literal rule enforcement: `Rule 4, Rule 11`.
- Required change: 在 `TianjiAgentRuntimeAutoConfiguration` 增加 `tianshuTemplateResolver` 条件 Bean 装配声明。
- Verification contribution: 运行时自动装配检验通过。
- After this file: 天机自动装配联通完成。

#### File 9 — `MODIFY tianji-service/stellar-enclosure-tianji-process-server/src/main/resources/application.yml`
- Purpose: 为 `tianji-process-server` 追加天枢客户端配置节与严格激活校验开关（默认值保证既有容器和集成单测不因缺环境变数无法启动）。
- Symbols: `egon.cola.component.tianshu.si.client.*`, `strict-activation-check`.
- Repository evidence: 已核实文件位置。
- Dependencies and consumers: `DdcSiClientProperties`、`TianshuAgentBindingVerifier`。
- Why now: 编排服运行参数外化与可控切换保障。
- Contract/signature changes: 默认值安全（`Duration` 明确带时间单位串），零修改既有 Flowable/Datasource 配置。
- Input/output and state mapping: YAML 属性 -> 客户端/校验器属性注入。
- Error and edge behavior: 显式配置可由部署 env 覆盖。
- Standards impact: `MC-CONFIG-001, MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 7, Rule 10, Rule 11`.
- Required change: 向 `tianji-process-server/application.yml` 追加规范天枢连接与预检开关节。
- Verification contribution: `tianji-process-server` 模块测试上下文顺畅加载。
- After this file: 编排端配置完成。

#### File 10 — `MODIFY tianji-service/stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`
- Purpose: 在 `tianji-agent-server/application.yml` 补充 `egon.cola.component.tianshu.si.client.*` 客户端同步及 `egon.cola.component.agent-flow.legacy-static-workflow.enabled: false`，正式在部署层定格 `agent-flow` 仅做 Agentic 运行而无静态流程介入。
- Symbols: `egon.cola.component.tianshu.si.client.*`, `egon.cola.component.agent-flow.legacy-static-workflow.enabled: false`.
- Repository evidence: 已核实 `tianji-agent-server` YAML 及 `pom.xml` 引入了组件。
- Dependencies and consumers: Step 3 开关配置、Step 7 `DdcSiCatalogSyncCoordinator`、`TianshuTemplateResolver`。
- Why now: 在全链路运行服务端最终落实“Tianshu 定义 + Tianji Flowable 编排 + agent-flow 纯 Agentic”三元协同边界。
- Contract/signature changes: 带清晰 `Duration` 默认值与可覆盖环境配置占位，不动原接口和数据源。
- Input/output and state mapping: YAML 配置 -> 运行服开启缓存协同、禁用废弃静态工作流。
- Error and edge behavior: 配置项全带兜底缺省确保容器顺利启动。
- Standards impact: `MC-CONFIG-001, MC-SCOPE-001, MC-TIME-001`.
- Literal rule enforcement: `Rule 7, Rule 10, Rule 11`.
- Required change: 在 `tianji-agent-server/application.yml` 补充 SI 客户端同步与关闭静态工作流开关。
- Verification contribution: 使 `tianji-agent-server` 上下文启动测试与全套跨模块测试圆满通过。
- After this file: 全项目 9 个 Step 施工与端到端校验全面完结。

- Validation working directory: `tianji-service`
- Verification command: `./mvnw -pl stellar-enclosure-tianji-process-server,stellar-enclosure-tianji-agent-runtime,stellar-enclosure-tianji-agent-server test`
- Expected result: `TianshuAgentBindingVerifierTest`, `TianshuTemplateResolverTest` 及该三大部署/运行时模块全部现有与新增单元测试 100% 绿灯通过。
- Failure returns to: 检查构造器注入兼容性（确保未破坏既有 `AutomationActivationService` 旧单测构造签名为首）或 YAML 属性前缀对齐重跑。
- Completion criteria: 完整落实 `TEST-010` 及跨 4 仓契约闭环且 `tianji-service` 无任何测试回退。
- Rollback: 局部 `git revert` Step 9 提交。
- Commit paths: `tianji-service/stellar-enclosure-tianji-process-server/src/test/java/top/egon/cola/platform/tianji/process/server/integration/TianshuAgentBindingVerifierTest.java`, `tianji-service/stellar-enclosure-tianji-agent-runtime/src/test/java/top/egon/cola/platform/tianji/agent/si/TianshuTemplateResolverTest.java`, `tianji-service/stellar-enclosure-tianji-process-server/pom.xml`, `tianji-service/stellar-enclosure-tianji-agent-runtime/pom.xml`, `tianji-service/stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/integration/TianshuAgentBindingVerifier.java`, `tianji-service/stellar-enclosure-tianji-process-server/src/main/java/top/egon/cola/platform/tianji/process/server/release/AutomationActivationService.java`, `tianji-service/stellar-enclosure-tianji-process-server/src/main/resources/application.yml`, `tianji-service/stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/si/TianshuTemplateResolver.java`, `tianji-service/stellar-enclosure-tianji-agent-runtime/src/main/java/top/egon/cola/platform/tianji/agent/autoconfigure/TianjiAgentRuntimeAutoConfiguration.java`, `tianji-service/stellar-enclosure-tianji-agent-server/src/main/resources/application.yml`
- Commit: `feat(tianji-service): integrate Tianshu SI pre-activation check and runtime template resolution`

## 8. Test, Validation, and Quality Gates

| Gate/order | Working directory | Command or method | Scope | Expected result | Failure returns to | Requirements/runtime boundary |
| --- | --- | --- | --- | --- | --- | --- |
| RED per Step (1..9) | Step repo root | Focused unit/IT test run for Step File 1 | Newly created `*Test` / `*IT` | Fails for missing SI symbol/logic before prod change | Step File 1 | `REQ-001`..`REQ-009`; module |
| Step 1 GREEN & Install | `tianshu-service` | `./mvnw -pl stellar-enclosure-tianshu-starter test && ./mvnw -pl stellar-enclosure-tianshu-starter -am install -DskipTests` | `tianshu-starter` | Exit 0; `DdcSiContractsAndRedisKeysTest` & old tests pass; installed `.m2` | Step 1 | `REQ-001`..`REQ-008`; starter base |
| Step 2–3 GREEN & Install | `Stellar-Enclosure-Components` | `./mvnw -pl stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter,stellar-enclosure-component-agent-flow-starter test && ./mvnw -pl stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter,stellar-enclosure-component-agent-flow-starter -am install -DskipTests` | RPC adapter & Agent-Flow | Exit 0; Proto/RPC & deprecation boundary tests pass; `.m2` installed | Step 2 or Step 3 | `REQ-001`, `REQ-004`, `REQ-006`, `REQ-007`, `REQ-008`; components |
| Step 4 PG Migration Gate | `tianshu-service` | `./mvnw -pl stellar-enclosure-tianshu-admin -Ptianshu-ai-persistence-it -Dtest=DdcSiPersistenceIT,DdcAiPersistenceIT verify` | DDL `V20261116_001`, manifest, `pgvector` RRF (`TEST-005`) | Exit 0; PG container proves constraints, 1536D vector & RRF | Step 4 | `REQ-001`..`REQ-008`; PG + `pgvector` runtime |
| Step 5 Domain Unit Gate | `tianshu-service` | `./mvnw -pl stellar-enclosure-tianshu-admin -Dtest='DdcSiContentStrategyTest,DdcSiDependencyValidatorTest,DdcSiResourceServiceImplTest,DdcSiSemanticIndexServiceImplTest' test` | Domain, 409 lock, DAG, RRF fallback (`TEST-001..004,006..008`) | Exit 0; 0 skipped or failed tests | Step 5 | `REQ-001`..`REQ-008`; unit |
| Step 6 HTTP/gRPC/RBAC Gate | `tianshu-service` | `./mvnw -pl stellar-enclosure-tianshu-admin -Ppermission-producer test-compile && ./mvnw -pl stellar-enclosure-tianshu-admin test` | Full `tianshu-admin` unit, `MockMvc`, OpenAPI, gRPC IT (`TEST-004,009,011`) | Exit 0; 14 REST, 3 gRPC, old `DdcAi*` & profiles pass | Step 6 | `REQ-001`..`REQ-009`; admin module |
| Step 7 Starter Client & Install | `tianshu-service` | `./mvnw -pl stellar-enclosure-tianshu-ai-starter test && ./mvnw -pl stellar-enclosure-tianshu-ai-starter -am install -DskipTests` | Cache, debounce, self-reg (`TEST-006`) | Exit 0; starter tests pass & `.m2` installed | Step 7 | `REQ-001`, `REQ-003`, `REQ-006`, `REQ-008`; starter |
| Step 8 Frontend Full Gate | `tianshu-web` | `node scripts/check-i18n.mjs && npx tsc --noEmit && npm test -- --run && npm run build` | i18n script, TS compile, Vitest (`TEST-012`), Vite build | Exit 0; bilingual 1:1 parity, 0 TS/RTL/build errors | Step 8 | `REQ-009`; SPA frontend |
| Step 9 Tianji Cross-Repo Gate | `tianji-service` | `./mvnw -pl stellar-enclosure-tianji-process-server,stellar-enclosure-tianji-agent-runtime,stellar-enclosure-tianji-agent-server test` | Activation check & template inject (`TEST-010`) | Exit 0; all 3 modules pass unit & activation regression | Step 9 | `REQ-001`, `REQ-004`, `REQ-006`, `REQ-008`; system |

本工程全部四仓各自具备完备原生自动化构建与测试指令（`mvnw`, `check-i18n.mjs`, `tsc`, `vitest`, `vite build`），不需亦不创建任何临时 `scripts/work/` 脚本。

## 9. Migration, Compatibility, Rollout, and Rollback

1. **数据库迁移秩序与清单校验**：在 `tianshu-admin/src/main/resources/db/egon-mp/` 创建 `V20261116_001__tianshu_si_resource_governance.sql` 顺接现有 7 条脚本之后，冻结 SQL 内容即用 `shasum -a 256` 刷新 `repository-manifest.json`，零改动已有迁移。内部按 `INSERT ... ON CONFLICT DO NOTHING` 回填历史 `PROMPT`/`SKILL` 到 SI 主目录与版本快照。
2. **向后兼容保障**：完全保留旧 `DdcAiEnumConverter` 仅允许 `PROMPT`/`SKILL` 之抛错边界；老 `/api/v1/tianshu/ai/**` 端点契约、返回数据结构与权限完全维持原样；`agent-flow-starter` 静态工作流默认以 `matchIfMissing = true` 保持旧系统零感知运行。
3. **跨服务发布上线节奏**：(a) 部署含 `pgvector` 扩展的 PostgreSQL 并跑完 `V20261116_001`；(b) 上线 `tianshu-admin`；(c) 同步更新 `tianshu-web` 控制台；(d) 滚动部署带新客户端配置并关闭 legacy static workflow 开关之 `tianji-process-server` 与 `tianji-agent-server`。
4. **回滚与在线旁路**：每步提交单仓内原子隔离，可直接 `git revert`；运行期间 `EmbeddingModel` 算力集群出现延迟超限（> 1500ms）或宕机时自动降级纯 PG 全文检索（不阻断服务及 UI）；或通过配置关闭 `si.client.enabled` 迅速退回零干预旁路态。

## 10. Requirement-to-Step Traceability Matrix

| Requirement | Effective Spec section | Steps | Files | Tests/gates | Completion evidence |
| --- | --- | --- | --- | --- | --- |
| `REQ-001` | [Spec §4/§5/§9](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 1, 2, 3, 4, 5, 6, 7, 8, 9 | `DdcSiKindEnum`, `tianshu_si_resource.proto`, `AgentWorkflow*` `@Deprecated`, `V20261116_001`, 5 PO/Mapper, `DdcSiResourceServiceImpl`, `DdcSiResourceController`, `SiCatalogPage`, `TianshuAgentBindingVerifier`, `TianshuTemplateResolver` | `TEST-005`, `TEST-008`, `TEST-009`, `TEST-010`, `TEST-011` | 全四仓编译、真实 PG IT、Vitest 与天机回归通绿 |
| `REQ-002` | [Spec §6.2](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 1, 4, 5, 6, 8 | `DdcSiAgentSpecBO`, `DdcSiMcpSpecBO`, `DdcSiAgentContentStrategy`, `DdcSiMcpContentStrategy`, `SiResourceEditorPage` | `TEST-004`, `TEST-012` | 策略及 `MockMvc` 拦截非原子流程/明文密码返 `422` |
| `REQ-003` | [Spec §5/§7/§9](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 1, 4, 5, 6, 7, 8 | `DdcSiLifecycleStatusEnum`, `DdcSiPublicationEvent`, `DdcSiVersionPO`, `DdcSiLifecycleTransitionPolicy`, `DdcSiCatalogOutboxNotifier`, `DdcSiPublicationListener`, `SiLifecycleDialog` | `TEST-001`, `TEST-002`, `TEST-005` | 乐观锁 409 校验、版本只读实测与 Outbox 提交安全断言 |
| `REQ-004` | [Spec §5/§7/§9](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 1, 2, 4, 5, 6, 7, 8, 9 | `DdcSiBindingDescriptor`, `DdcSiBindingPO`, `V20261116_001` 复合外键, `DdcSiResourceServiceImpl`, `SiServiceBindingPanel`, `TianshuTemplateResolver` | `TEST-003`, `TEST-005`, `TEST-009`, `TEST-011` | 多应用版本优先覆盖测试与 PG 外键 `23503` 防护实证 |
| `REQ-005` | [Spec §6.2/§9.1](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 1, 4, 5, 6, 8 | `DdcSiVersionDescriptor`, `DdcSiVersionMapper.xml`, `DdcSiLifecycleTransitionPolicy`, `DdcSiProperties`, `SiEnvMatrixPage` | `TEST-002`, `TEST-012` | 晋级依存拦截、环境溯源生成与矩阵 UI 测试全通 |
| `REQ-006` | [Spec §9.2/§10](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 1, 2, 4, 5, 6, 7, 9 | `DdcSiSelfRegistrationRequest`, `DdcRedisKeys` SI keys, `RPC-003`, `DdcSiCatalogCache`, `DdcSiCatalogSyncCoordinator`, `DdcSiSelfRegistrationRunner`, YAML configs | `TEST-006`, `TEST-009` | 指纹幂等、不盖人工发布版与客户端退避防抖全绿 |
| `REQ-007` | [Spec §6.1/§7/§9](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 1, 2, 4, 5, 6, 7, 8 | `DdcSiSearchHit`, `DdcSiVectorTypeHandler`, `DdcSiEmbeddingChunkMapper.xml`, `DdcSiSemanticIndexServiceImpl`, `DdcSiSemanticSearchResult`, `SiCatalogPage` | `TEST-005`, `TEST-007`, `TEST-012` | HNSW + GIN CTE RRF 排序与超时降级 FTS 实证 |
| `REQ-008` | [Spec §4/§9.4](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 1, 2, 3, 4, 5, 6, 7, 8, 9 | `AgentWorkflow*` `@Deprecated`, `DdcSiReferencePO`, `DdcSiDependencyValidator`, `SiReferencesPanel`, `TianshuAgentBindingVerifier`, `AutomationActivationService` | `TEST-008`, `TEST-010`, `AgentFlowAgenticBoundaryTest` | DAG 闭环拦截、流程引用阻断归档与激活前置检验实测 |
| `REQ-009` | [Spec §12](../spec/2026-11-16-10-35-tianshu-si-resource-governance-spec.md) | Steps 6, 8 | `DdcPermissionCodes`, `DdcSiResourceController`, `src/api/si.ts`, `permissions.ts`, 6 SI UI views, `i18nCatalogs.json`, 3 RTL tests | `TEST-012`, `check-i18n.mjs`, `tsc`, `vite build` | 前端全静检、类型编译、双语覆盖与生产构建零错 |

## 11. Risks, Blockers, and User Decisions

| ID | Risk or decision | Impacted Steps/files | Evidence | Owner | Status/action |
| --- | --- | --- | --- | --- | --- |
| `BLOCK-001` | 新增 BOM 内部依赖（`rag-starter`, `spring-ai-model`, `tianji-*` 直连 `tianshu-starter`）及三档案 YAML 增量审批随本 Plan 审阅一并确认 | Step 4 (`tianshu-admin/pom.xml`), Step 6 (YAML), Step 9 (`tianji-*/pom.xml`, YAML) | Spec §6.1/§8.2/§20.5 与 Plan §2.3/§4.7/`PLAN-CLAR-001` | Architecture Owner | Closed at Plan design — All added artifacts are BOM/internal dependencies documented in §2.3 & §4.7 and ready for Plan review sign-off |
| `BLOCK-002` | 执行持久化集成测需要可加载 `vector` 扩展的 PostgreSQL 运行库环境 | Step 4 `V20261116_001` 与 `DdcSiPersistenceIT` | Spec §6.1 & Step 4 实库测试环境约定 | DevOps / Developer | Closed / Environmental — Use standard `pgvector/pgvector:pg16` test database instance when executing `-Ptianshu-ai-persistence-it` |
| `RISK-001` | `AutomationActivationService` 既有单元测试若采用显式单测手动 `new AutomationActivationService(...)` 可能因新增构造参数受影响 | Step 9 File 6 (`AutomationActivationService.java`) | 实核 `tianji-process-server` 既有 `release/` 包结构 | Implementer | Mitigated — 在实施 Step 9 File 6 前先读取现有构造器，保留原有参数兼容重载或可选注入以防旧测破坏 |

## 12. Review and Acceptance

### 12.1 Original requirement fidelity

本实现计划完整囊括用户在方案推演与规格阶段确定的全量需求边界（`REQ-001`..`REQ-009`）：保持单期交付不劈分二期；维持 Tianshu 原子资产中心 + Tianji Flowable 编排 + Components `agent-flow-starter` 纯 Agentic 自主执行之三元清晰解耦；涵盖全量生命周期、多维作用域绑定、幂等自注册、`pgvector` RRF 混合搜索与 `tianshu-web` 全套管理台界面。

### 12.2 Spec consistency

§4.5 必要性审查确认主规格全量设计无虚高过度抽象或套娃透传接口；所有仓内真实构造细节调整（如两处天机模块直连 starter 依赖、迁移 SHA-256 清单、实核物理包根节点与平铺模型命名）均已客观收入 §6.4 `PLAN-CLAR-001`..`003`，严格不改一丝主规格核心设计与接口契约。

### 12.3 Repository executability

全部涉及修改之既存在路径（`DdcRedisKeys`, `DdcRpcOperation`, `DdcRpcAutoConfiguration`, `AgentWorkflowBuilderStrategy`, `AgentWorkflowStrategyFactory`, `AgentFlowAutoConfiguration`, `DdcAiKindPersistenceEnum`, `repository-manifest.json`, `DdcPermissionCodes`, `DdcAiStarterAutoConfiguration`, `AiResourcesPage`, `App.tsx`, `i18nCatalogs.json`, `AutomationActivationService`, `TianjiAgentRuntimeAutoConfiguration` 等）均已在工作区实际目录树核真无误。步骤依赖按编译顺序划定、隔离互不重叠、每步单仓原子提交。

### 12.4 Test and release completeness

每个 Step 以对应 RED 测试（`File 1`）领衔启动，全量对应规格验收集 `TEST-001`..`TEST-012`。全计划零任何代码块片段、零 SQL/YAML/代码原文、零占位符，各步验证命令行精准可执行，含清晰数据库兼容策略、优雅 FTS 实时降级机制及局部提交可逆方案。

### 12.5 Blocking Manual Check

| Check ID | Applicability | Status | Evidence | Finding | Required action/exception |
| --- | --- | --- | --- | --- | --- |
| `MC-ARCH-001` | `Applicable` | `PASS` | `tianshu-admin` 传统分层目录、`tianji-*` 分层目录、`tianshu-web` React SPA 树与 §4.7 | 各仓库维持唯一既有原生架构风格，严禁套用跨体例混合架构 | None |
| `MC-REUSE-001` | `Applicable` | `PASS` | §4.7 Reuse Ledger（`rag-starter`, `spring-ai-model`, `rpc-tianshu-adapter`, `GatewayVectorTypeHandler` 纯 JDBC 方案, `tianshu-admin` Outbox） | 全面复用团队自有既有组件，未重新造轮子或引入冗余包 | None |
| `MC-DEP-001` | `Applicable` | `PASS` | §2.3、§4.7 与 `PLAN-CLAR-001`（`tianshu-admin/pom.xml` + `tianji-process-server` / `agent-runtime` POM） | 全部新增均受内部或工程 BOM 管理，已完整登记核批 | None |
| `MC-NAME-001` | `Applicable` | `PASS` | Step 1 DTO/Enum, Step 4 PO/Mapper, Step 5 BO/Repository/Service/Strategy, Step 6 Command/Query/Result | 所有类名后缀及平铺包路径（`model/bo/`, `model/command/`, `model/query/`, `model/vo/`）与仓库现有习惯一致 | None |
| `MC-VALID-001` | `Applicable` | `PASS` | Step 1/6 Bean Validation、Step 4 SQL CHECK & TypeHandler、Step 5 Strategy & DAG Validator | 全链路入站接口与域核心均实施强格式、Schema、无环与凭据屏蔽校验 | None |
| `MC-MODEL-001` | `Applicable` | `PASS` | Step 1 不可变 DTO、Step 4 POs、Step 5 BOs、Step 6 Command/Query/Result VOs | 严格按层次分隔模型边界，无跨层混用或暴露库表 PO 行为 | None |
| `MC-CONVERT-001` | `Applicable` | `PASS` | `DdcSiProtoMapper` (Step 2)、`DdcSiEnumConverter` & `DdcSiResourceConverter` (Step 5)、`DdcSiCqeConverter` (Step 6)；`DdcAiEnumConverter` 保持未改 | 全路径采用 MapStruct 与 `BaseConverter`，保护旧 AI 转换接口安全 | None |
| `MC-LOG-001` | `Applicable` | `PASS` | 全服务端与适配组件统一标注 `@Slf4j`，凭据/系统词绝对脱敏且高频迁移 warning 配单次防刷屏开关 | 日志框架统一且无秘钥泄露或日志风暴隐患 | None |
| `MC-BEAN-001` | `Applicable` | `PASS` | `lombok.config` 核验仅含 `@Qualifier`；全部业务 Bean 用 `@RequiredArgsConstructor(onConstructor_ = @Autowired)` + 显式 `@Qualifier` + `@ConfigurationProperties` | 杜绝构造 `@Value` 隐形失灵以及多 `DdcSiCatalogClient` 实现注入循环 | None |
| `MC-UTIL-001` | `Applicable` | `PASS` | `DdcSiVectorTypeHandler` 纯 JDBC、`DdcRedisKeys` 增量安全拼键 | 严格控制在允许清单内复用工程验证方案 | None |
| `MC-JSON-001` | `Applicable` | `PASS` | `@Jacksonized`, 确定性排序键 SHA-256 摘要, `@Schema` 注解及掩码脱敏 VO | 前后端及跨仓 JSON 与 OpenAPI 契约完全一致且防泄密 | None |
| `MC-TIME-001` | `Applicable` | `PASS` | §4.9 合同表（`TIME-001`..`TIME-003`）、PG `timestamptz(6)`、`Instant` `UTC Z`、`deleted_at` `Legacy UTC`、`Duration` | 时态建模及前后端显示/存储/传输转换规则完全闭环无歧义 | None |
| `MC-CONFIG-001` | `Applicable` | `PASS` | Step 6 `application.yml`, `-dev.yml`, `-prod.yml` 对等配置，Step 7/9 YAML 安全参数 | 三套环境配置文件严格键值齐备且生产高风险自动发布开关默认关闭 | None |
| `MC-PATTERN-001` | `Applicable` | `PASS` | `DdcSiContentStrategy` 多态策略、`DdcSiLifecycleTransitionPolicy` 状态迁移矩阵、Outbox `AFTER_COMMIT`、同步防抖器 | Complex 分支和异构逻辑全部落入清晰面向对象模式而非面条代码 | None |
| `MC-SCOPE-001` | `Applicable` | `PASS` | §5 树与 §7 互不重叠 `Commit paths`、严格保护 `Components/docs/egon` 旧文档、历史 SQL 及旧 AI 端 | 单步提交原子干净，绝对零跨范围越改 | None |
| `MC-TEST-001` | `Applicable` | `PASS` | 每步 File 1 RED 测例 + §8 全仓验证命令阶梯覆含 `TEST-001`..`TEST-012` | 所有变更行为均配有明确可复测自动化门禁与回归命令 | None |
| `MC-BLOCKER-001` | `Applicable` | `PASS` | §11 风险与审批控制表及上述 16 项全 `PASS` 评估结果 | 无遗留阻断项；全面符合 Review 提交条件 | None |

### 12.6 Final verdict

PASS — Ready for user review

## Java / CQE contract review

1. **POJO / 值对象 / 枚举**：跨仓契约模型置于 `tianshu-starter`（不可变 `@Value`/`@Builder` 描述子与 5 组状态/类型枚举），持久化 PO 与枚举严格落入 `tianshu-admin/model/po` 及 `model/enums`，内部领域 BO 置于仓库既定平铺 `model/bo/`，边界井然。
2. **组件与 MP Starter 用法**：全部新业务表纳管于 `egon-mp` `V20261116_001` 并配置正整数约束、`@Version` 乐观锁与审计时间；复合引入 BOM 托管 `rag-starter` 与 `spring-ai-model`，并复用经实仓验证之纯 JDBC `BaseTypeHandler` 处理 `vector(1536)`。
3. **注解校验与信任边界**：所有 REST Command/Query（`model/command/`, `model/query/`）及 Proto/Starter 请求全部使用 `@Valid`、`@NotBlank`、`@Pattern`、数值界限检查与 `@Validated` 分组；Service 深度实施策略校验、DAG 无环探测及强制认证租户身份覆写。
4. **业务逻辑列与 `deleted_at` 唯一索引兼容**：沿用仓库标准 `deleted_at timestamp(6) without time zone` 配局部唯一索引 `WHERE deleted_at IS NULL` 以确保软删除后重用同资源键合规且双活不可重；全部新建业务与审计时间戳统一使用 `timestamptz(6)`。
5. **Command / Query / Event 与真实 Outbox**：14 个 REST 操作对应标准独立 `*Command` / `*Query` / `*Result` (`model/vo/`) 及完整 OpenAPI `@Schema`；领域发布事件 `EVENT-001` 在事务内落盘既有 Outbox 投递表并于事务提交成功后调度缓存与索引，确保可靠最终一致性。
6. **保留传统三层架构与 POM 控制**：四仓全部严格保持自身既有架构（`tianshu-admin` & `tianji-*` 传统分层、`tianshu-web` React SPA），仅补全两个核定天机模块与 `tianshu-admin` 白名单 POM 条目，严禁跨架构套用原生 CLI 生成。

## Backend generation and dependency evidence

1. **架构判定**：经过实仓核验，`tianshu-service/stellar-enclosure-tianshu-admin`、`tianji-process-server` 与 `tianji-agent-runtime` 均使用经典三层架构（非 Egon-COLA 原生 Archetype `light`/`service`/`web`/`agent` 生成项目），因此全计划采用人工合规编码、标准增量 `egon-mp` 迁移和精确测试校验，不调用 `scripts/stellar-enclosure-codegen.sh`。
2. **数据库迁移与清单实证**：`tianshu-admin/src/main/resources/db/egon-mp/` 既有 7 个迁移脚本停留在 `V20261028_001` 且由 `repository-manifest.json`（含精确 `sha256`、`freshBaselineVersion: "20261018_001"`）强校验。本计划 Step 4 创建 `V20261116_001__tianshu_si_resource_governance.sql` 后立刻实算 SHA-256 追加登记，零变更历史迁移文件。
3. **依赖与环境变量证据**：
   - `tianshu-admin/pom.xml` 新增 `top.egon:stellar-enclosure-component-rag-starter` 与 `org.springframework.ai:spring-ai-model`（由父 BOM 统一管版），并在 `tianshu-ai-persistence-it` `<includes>` 注册 `DdcSiPersistenceIT`；
   - `tianji-process-server/pom.xml` 与 `tianji-agent-runtime/pom.xml` 新增 `top.egon:stellar-enclosure-tianshu-starter` 直接声明以承载 `DdcSiCatalogClient`（`PLAN-CLAR-001`）；
   - PG 持久化 IT 验证需连接配备 `pgvector` 扩展之 PostgreSQL 实例。
