# Permission 元数据持久化

Permission Manifest 的来源版本、资源和权限展示索引、导入回执、激活描述符投影及机器导入授权使用 PostgreSQL。既有 `ddc_i18n_page` 继续承担人工草稿和当前人工发布状态，新增不可变人工发布历史。代码来源导入不修改人工页。

本次结构使用 `db/egon-mp/V20261023_001__tianshu_permission_metadata.sql`，在已有 managed manifest 后追加一个版本。历史 SQL、checksum 和 `freshBaselineVersion` 保持不变。完整功能必须与匹配的注册、身份和语言服务一起交付；中间构建不能承接业务流量。

## 数据与版本

| 状态 | 数据表 | 规则 |
| --- | --- | --- |
| 完整目录作用域 | `ddc_metadata_scope` | 关联本地 namespace/app/binding，可信保存 RBAC catalogScopeId；身份不可变 |
| 机器导入授权 | `ddc_source_import_grant` | 精确 issuer/clientId/Resource/scope/producer，默认禁用；启用变更必须 CAS |
| 代码来源 | `ddc_i18n_source_version` | sourceVersion 等于 manifestDigest；同内容允许被多个注册序号引用 |
| 资源与权限展示 | `ddc_resource_metadata_projection` | 七种 resource type；权限定义使用独立 metadataKind，不引入新资源类型 |
| 来源词条 | `ddc_i18n_source_message` | 指定来源的默认语言、翻译和占位符 |
| 导入成功结果 | `ddc_i18n_import_receipt` | 与源内容同事务提交，重复操作返回原结果；查询仍需当前授权 |
| 激活描述符 | `ddc_catalog_activation_projection`、`ddc_catalog_activation_producer` | 受认证读取 RBAC 的精确版本；没有 META active/latest 指针 |
| 人工编辑与发布 | `ddc_i18n_page`、`ddc_i18n_published_override` | 人工 CAS 与不可变发布历史；源导入不能顺带发布草稿 |

所有新业务表使用 `STANDARD_TENANT_ID` 路由。跨表外键包含 tenant 和必要的 catalog scope；源回执与激活成员同时绑定来源三摘要。中心 ID 与版本在接口中使用十进制字符串，数据库使用正 bigint。

不可变表拒绝 UPDATE/DELETE。机器 grant 只能修改启用状态和版本；更换主体或目标需要新的 grant。人工发布复制当前已保存草稿，在同一事务写入历史和当前发布引用；清除覆盖应发布空内容，不删除历史。

## 初始化与就绪

只支持已明确指定的清洁 PostgreSQL 目标，不转换旧混合语言页，也不自动删除本地数据。使用现有 `DdcPersistenceMigrationCli` 的 `initialize`，随后执行 `validate`。目标配置包含完整新表路由、PRIMARY/LOCAL 事务和既有 Outbox 配置。

旧 `upgrade-i18n` 仅适用于截至 `20261022_001` 的历史单表升级。新增 Permission 结构不得通过旧入口绕过新表或 route fingerprint 校验。Tianshu 的 `public.ddl_history` 和 Outbox 技术 schema 的 history 分别管理，均核验完整 topology。

应用只读就绪检查覆盖表、列、具名约束及复合 FK、索引、函数原文和已启用触发器。应用启动不创建表或修复 schema；缺少结构时拒绝就绪。

首次注册前的 B0 显式准备 IdP 机器身份/Resource Grant、RBAC catalogScope/producer、本地 biz/env/app/namespace/binding 和 exact source-import grant。默认仅授予 RBAC Outbox publisher。B0 不依赖尚未产生的管理权限，也不是服务启动副作用。注册、来源确认和激活后，B1 才为管理员赋予明确权限字符。

## 来源导入接口

| 方法与路径 | 合同 | 结果 |
| --- | --- | --- |
| `POST /internal/v1/i18n/source-imports` | 共享 `SourceImportRequest`：完整 target scope、producer/version、operationId、typed Manifest、三摘要、资源/权限中心 ID maps | `ResultRecord<SourceImportReceipt>`，成功结果和来源内容在一个事务提交 |
| `GET /internal/v1/i18n/source-imports/{operationId}` | 必填 query：catalogScopeId、tenantId、businessCode、applicationCode、namespaceCode、environment、producerId | 重新校验当前机器授权后返回原始回执，不支持只凭 operationId 查询 |

入口使用独立 Bearer SERVICE 安全链，要求 `TIANSHU_METADATA_IMPORT` OAuth Scope，并在 PostgreSQL 中精确匹配 issuer/clientId/Resource/完整 scope/producer 的启用 grant。仅接受 TENANT 服务身份；PLATFORM 和 USER 不进入该链。issuer 来自身份验证器的 `IdpAuthenticationToken.verifiedIssuer()`，不读取请求头、请求体或审计 metadata 作为 issuer 授权事实。publisher 的 sourceApp 与目标应用可以不同，访问范围由显式数据库 grant 决定。

请求使用共享 Manifest Reader、Canonicalizer 和 I18nSourceRules；META 不维护另一套 DTO 或摘要算法。解析器拒绝未知字段、重复 JSON key、隐式数字 ID 转字符串、尾随 JSON 和超深结构。正文总上限为 Manifest 16 MiB + 两份 ID map 各 2 MiB + 64 KiB 信封空间；Manifest 内部仍遵循共享深度及字段上限。

导入在同一数据库事务内锁定目标 scope、重新锁定并验证 grant、验证启用的 biz/env/app/namespace/binding，随后写来源、全部索引/词条、原始回执和业务审计。相同 operationId 与完整请求摘要重放返回原 ID、时间和计数；同 operationId 改内容为 409。相同 sourceVersion 的新受信 producerVersion 可以复用来源，但不能改变中心 ID maps。并发请求由 scope 行锁串行化，不在 PostgreSQL 唯一冲突后的失败事务内继续读取。

请求中的 tenant 仅为断言，实际租户和 actor 从已验证服务身份建立请求期上下文，结束后恢复原上下文。来源导入不依赖人工翻译 Repository，不保存 Session，也不切换 RBAC 或 META 的激活指针。未知提交结果须查询原 operationId；没有自动换 ID 的重放或跨中心事务。

结构/合同错误返回 422，内容冲突返回 409，未找到已授权范围内的回执返回 404，过大正文返回 413。身份和授权失败沿用身份链的 401/403；数据库或必要审计交接不可用时返回 503，不放行操作。

## PostgreSQL-only 集成验证

`tianshu-permission-metadata-it` 不启动数据库或容器，只连接事先准备并完成迁移的隔离 PostgreSQL。测试事务结束后回滚，验证不可变来源、跨 scope 回执 FK、grant CAS 和人工发布快照。默认单元测试不会执行这些 `*IT`。

显式启用使用以下两个非秘密环境参数：

```text
TIANSHU_PERMISSION_METADATA_IT_ENABLED=true
TIANSHU_PERMISSION_METADATA_IT_CONFIG=/secure/tianshu-metadata-it.yml
```

目标文件必须为绝对路径、普通文件且仅所有者可读写。文件提供 `jdbcUrl`、`username`、`password`、`expectedDatabase` 和 `allowTransactionalWrites: true`；凭据只从该受保护文件读取，不放在命令参数或测试日志中。测试连接后核对数据库名称和 `public` schema，不符合预期即停止。

在 `tianshu-service` 使用已配置的 JDK 25 执行：

```bash
./mvnw -pl :stellar-enclosure-tianshu-admin -am \
  -Ptianshu-permission-metadata-it verify
```

缺少显式启用条件时，集成用例记为跳过；不能将跳过或普通单元测试成功当成 PostgreSQL 验收通过。源码层的合约、Mapper 与就绪检查测试不替代真实数据库事务证据。

## 指定激活版本的读取与人工发布（S21）

`DdcCatalogActivationClient` 只访问受控 `catalog-authority.bindings` 内的 HTTPS 地址，以 TENANT SERVICE 身份和
`tianquan-jianshen:permission-catalog:read` 调用 API-006。每条 binding 同时固定完整 `CatalogScope`、读取资格对应的
`producerId`、Spring Client Credentials registration、appId 和 RBAC Resource URI。浏览器不能传入目标 URL、凭据或 producer 集合。
HTTP 不跟随重定向，连接/读取均有 5 秒上限；401 最多使该 SERVICE 凭据失效并重取一次。响应限制大小、重复字段、未知字段和数字型 ID，
再使用 RC 的 `ActivationDescriptor`、`canonicalize()` 和 `digest()` 校验；跨 scope、版本不一致、缺来源或损坏投影均拒绝。

配置示例只展示非秘密定位信息；对应 Client、授权和 B0 事实由受控初始化建立：

```yaml
egon:
  cola:
    component:
      tianshu:
        catalog-authority:
          bindings:
            "501":
              target:
                catalog-scope-id: "501"
                tenant-id: "101"
                business-code: trade
                application-code: order
                namespace-code: main
                environment: test
              producer-id: order-admin
              base-uri: https://rbac.example.test/
              resource-uri: https://rbac.example.test
              registration-id: metadata-catalog-reader
              app-id: metadata
```

首次读取指定版本时，远程获取在 SQL 事务之外进行。随后持有该 metadata scope 的行锁，验证每个已导入来源的三种摘要，
将 descriptor 和全部成员在同一 PostgreSQL 事务中持久化。成员之间重复资源/权限 Key、词条所有者冲突、页面大小写或默认语言冲突都会使该事务失败。
投影不可更新，不存在 META active/latest 指针。已投影的版本只从不可变 SQL 数据读取；它不授予用户权限，也不能代替 RBAC Runtime 对请求资格的判断。

接口合同：

| 接口 | 必要的版本上下文 | 行为 |
| --- | --- | --- |
| `GET /api/v1/tianshu/i18n/bundle` | 原 scope 参数，加 `catalogScopeId`、`catalogActivationVersion`、`pageCode`、`locale` | 指定 source 集合的运行文本，草稿不参与 |
| `GET /api/v1/tianshu/i18n/source-pages/{pageCode}` | 完整 scope 参数，加两个 catalog 字段 | 源默认/来源哈希/人工草稿/已发布覆盖对照；还没有人工页时 `id`、`revision` 为空 |
| `GET /api/v1/tianshu/i18n/pages/{id}` | `catalogActivationVersion` | 人工页面与所选 source 的差异 |
| `POST /api/v1/tianshu/i18n/pages` | 原创建字段，加 `catalogScopeId`、`catalogActivationVersion`、`reviewedSourceHashes` | 只创建人工草稿，不自动发布来源默认文本 |
| `PUT /api/v1/tianshu/i18n/pages/{id}/draft` | `expectedRevision`、`catalogActivationVersion`、`reviewedSourceHashes` | 保存人工全量覆盖草稿；允许空 map；必须审核该来源页面的完整 Key/hash 集合 |
| `POST /api/v1/tianshu/i18n/pages/{id}/publish` | `expectedRevision`、canonical UUID `operationId` | 只复制已保存草稿；返回不可变发布回执 |
| `GET /api/v1/tianshu/i18n/pages/{id}/publications/{operationId}` | 路径上的页面和操作 ID | 经重新授权查询原发布结果，用于未知结果重试 |
| `GET /api/v1/tianshu/resource-metadata` | 完整 scope 和两个 catalog 字段 | 按 producer、RESOURCE/PERMISSION、七类资源类型、生命周期、permissionCode、Key 查询，只读返回策略 |
| `GET /api/v1/tianshu/i18n/admin/bundle` | `catalogScopeId`、`catalogActivationVersion`、`pageCode`、`locale` | 管理台固定业务 scope 走同一标准 bundle 路径 |

所有 ID/revision 均为十进制字符串。旧的缺版本 bundle 请求被拒绝。`POST /admin/initialize` 返回
`TIANSHU_I18N_MANIFEST_REQUIRED`（409），不再创建或发布内置默认文本。公开登录启动文案仍只读取包内 auth 目录，不读取租户数据。

人工覆盖允许只翻译一个 locale，不要求复制源默认语言；每条覆盖必须属于所选页面，并与源 `{{name}}` 占位符集合一致。
`translations` 与 `reviewedSourceHashes` 各有 2 MiB 持久化上限，HTTP 请求总量仍受 4 MiB 限制。
保存时 source hash 不匹配与 revision 冲突均返回 409，不会自动改写人工内容。发布持有页面锁，插入不可变完整快照并推进 head、publication revision、
页面 revision 和操作审计，全部位于同一事务。重复 operationId + 同一请求摘要返回原回执；同一操作改变请求内容会冲突。

历史选择以请求 source 的每个 Key 为单位：最新发布的审核域不包含该 Key 时，说明该次发布不管理此旧 Key，旧运行版本继续使用适用历史。
审核域包含 Key 且对应 source hash 的覆盖为空时是显式清除，不复活更早译文。占位符兼容但 source hash 改变的覆盖仍可展示，同时返回待复核诊断；
占位符不兼容时查找该 source hash 最近的历史覆盖，否则使用该版本的源默认文本。新来源已经移除的 Key 永不被历史覆盖加入新 bundle。
每个 Key 按请求 locale 人工覆盖 → 请求 locale 源默认 → 页面默认 locale 人工覆盖 → 页面默认 locale 源默认 → Key 回退；page 声明优先于 common。

bundle 的 `version`/ETag 包含完整 descriptor digest（含 scope、activation 和 producer/source 集合）、page/locale，以及 page/common 的当前 publication revision。
人工发布单独发生时会改变 ETag；保存草稿不会改变它。读取在 REPEATABLE READ 事务内观察发布 head 和历史，不能看到只插入快照、尚未推进 head 的中间状态。
元数据故障只影响展示结果，不修改或放宽权限决策。

`DdcI18nBundleAssemblerTest`、`DdcCatalogActivationClientTest`、`DdcCatalogActivationServiceTest`、`DdcI18nPageServiceTest`、
`DdcResourceMetadataServiceTest` 和 XML repository 合同测试覆盖上述源选择、身份/版本绑定、人工 CAS、历史和只读边界。
`DdcI18nDraftPublishIT` 已接入 `tianshu-permission-metadata-it` profile，执行真实 PostgreSQL 历史选择 SQL、旧版本 Key 保留、空发布和陈旧 CAS，
沿用显式已有数据库配置、目标校验和每测试回滚；该 IT 不启动 PostgreSQL/容器。本轮未运行真实数据库 IT，也未进行真实 RBAC API-006 或部署验证。
正式 USER Runtime 的最终装配依赖 S20/S27，现有管理安全链的框架回归不能替代该验收。

来源合同和人工快照容量分开控制：每份合法 Manifest 仍保持 RC 的 20,000 消息/16 MiB 等既定上限，
人工 `translations` 和完整 `reviewedSourceHashes` 各自超过 2 MiB 时，在进入写事务前整体返回 413，不删词条、不写草稿、不推进 revision。
运行读取直接查询已规范化版本表，不加载 producer 的完整 Manifest。page/common 来源和历史候选均按 64 个来源 Key 分批查询；
聚合来源/历史输入分别限 16 MiB，最终 bundle/来源页面响应也限 16 MiB（响应 envelope 另计）。超限整次 413，不返回截断包。
资源索引使用独立 SQL 分页，每页最多 100 条。单次展示响应的限制不会阻止合法来源被注册、保留或参加 RBAC 激活资格判断。

`DdcI18nImportConcurrencyIT` 使用两条真实 PostgreSQL 连接验证 scope 行锁串行化、前一导入回滚后后一导入的完整写入，以及最终无残留来源/回执。
除原有 IT 开关和受保护数据库配置外，必须显式设置非秘密的 `TIANSHU_PERMISSION_METADATA_CONCURRENCY_TENANT_ID` 与
`TIANSHU_PERMISSION_METADATA_CONCURRENCY_SCOPE_ID`，指向已经由 B0 建立的专用测试 scope。测试不会创建或修改该 scope，所有导入 fixture 都回滚，
不会为清理而关闭不可变触发器。此项验证锁与回滚，不宣称覆盖真实 HTTP 导入或已提交回执重放；后两者仍需独立环境证据。
资源索引的单页 JSON 也有 16 MiB 上限，收到 413 时可缩小 pageSize 重试。
