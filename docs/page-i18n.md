# 企业元数据中心：页面多语种

页面语言资源按可信 tenant 和 `bizCode + namespaceCode + env + appCode + pageCode` 隔离。
作用域中的实体和绑定必须存在且启用。tenant 来自认证身份；`X-Egon-Tianshu-Tenant` 只是前端的
预期身份校验头，不能用来选择其他租户。HTTP 消费端通过现有 Edge/BFF 认证通道访问。

## 管理和发布

管理入口为 Tianshu Web 的 `/metadata/i18n`。每个页面维护名称、默认语言和多语言词条表。
`pageCode` 是稳定业务标识，例如 `order.list`；保留值 `common` 用于同作用域的公共文案。
文案使用稳定 key，例如 `actions.create`。前端页面代码引用 key，翻译值由 Tianshu 管理。

一个页面的所有语言共同形成一份草稿。保存草稿不会改变已发布语言包；发布会在一个事务中替换
完整已发布快照并推进发布版本。保存和发布均必须携带最新 `expectedRevision`，避免并发编辑覆盖。
发生冲突时保留本地编辑，先查看服务器草稿再处理；不要自动重试覆盖。

初建页面允许空默认语言草稿，发布前必须至少有一条默认语言文案。默认语言必须覆盖其他语言的全部 key。
其他语言缺少某条文案时按 key 回退；管理界面中的空翻译会省略，接口不接受空字符串值。
文案支持 `{{name}}` 形式的插值，同一 key 各语言的占位符集合必须一致。文案作为普通文本渲染，
不应传给 `innerHTML` 或 `dangerouslySetInnerHTML`。

语言使用规范 BCP 47 标签（如 `zh-CN`、`en-US`）。名称、pageCode、key 最长 128 字符；
pageCode 和 key 使用由点分隔的 ASCII 字母、数字、下划线和连字符片段，禁止
`__proto__`、`constructor`、`prototype` 片段。每个页面最多 32 种语言，每种语言最多 2000 条，
单条值最多 8192 字符，整份 translations 序列化后最多 2 MiB。

## HTTP 契约

除分页外，成功响应沿用 `ResultRecord<T>`，业务对象位于 `data`。分页返回
`PageResultRecord<T>` 的 `records` 和 `page`。ID、revision 和 publishedVersion 使用十进制字符串；
尚未发布时 publishedVersion 为 null。管理响应使用 `Cache-Control: no-store`。

| 方法与路径 | 参数 / 请求体 | 结果 |
|---|---|---|
| `GET /api/v1/tianshu/i18n/pages` | 完整 scope、pageNo、pageSize | 页面摘要分页 |
| `POST /api/v1/tianshu/i18n/pages` | scope、pageCode、name、defaultLocale、translations | 新页面及草稿 |
| `GET /api/v1/tianshu/i18n/pages/{id}` | 页面 ID | 页面及草稿 |
| `PUT /api/v1/tianshu/i18n/pages/{id}/draft` | expectedRevision、name、defaultLocale、translations | 保存后的页面 |
| `POST /api/v1/tianshu/i18n/pages/{id}/publish` | expectedRevision | 发布后的页面 |
| `GET /api/v1/tianshu/i18n/bundle` | 完整 scope、pageCode、locale | 已发布、合并并回退的语言包 |

translations 的格式如下：

```json
{
  "zh-CN": {"title": "订单列表", "greeting": "你好，{{name}}"},
  "en-US": {"title": "Orders", "greeting": "Hello, {{name}}"}
}
```

消费语言包的 `data` 示例：

```json
{
  "pageCode": "order.list",
  "locale": "en-US",
  "defaultLocale": "zh-CN",
  "version": "opaque-checksum",
  "messages": {"actions.save": "Save", "title": "Orders", "greeting": "Hello, {{name}}"}
}
```

合并优先级从低到高为：公共默认语言、公共请求语言、页面默认语言、页面请求语言。
页面词条覆盖同 key 的公共词条。公共页可缺省；请求页面不存在或尚未发布时返回 404。
请求合法但尚未配置的语言时返回默认语言文案，locale 仍为请求语言，defaultLocale 标示页面默认语言。

bundle.version 是不透明内容版本，包含公共语言包变更的影响，不能当作页面发布序号。
响应支持 ETag/If-None-Match 和 `Cache-Control: private, no-cache`，每次条件请求仍须先通过身份及作用域检查。
调用方应缓存已解包的 messages；使用能处理 304 的 HTTP 客户端时，304 应复用同身份同请求的缓存响应。

## 权限与前端接入

管理读取、编辑、发布分别使用已有的 `TIANSHU_READ`、`TIANSHU_WRITE`、`TIANSHU_PUBLISH`。
语言包读取支持独立 `TIANSHU_I18N_READ`，也允许已有管理读取身份。
OAuth scope 和 Jianshen capability 必须同时具备；`CAP_*` 是服务端认证后的权限表达，
浏览器不能自行添加它来获取权限。部署时登记并授予消费 scope，对应 Edge/BFF 客户端请求该 scope。
现有默认 Edge Tianshu 登录只请求 READ，管理写入和发布需在部署配置中补齐所需 scopes。

Web SDK 提供 `createPageI18nLoader`，注入业务应用已有的认证客户端 `fetchBundle(query, {signal})`。
该客户端需发送预期租户校验头并解包 ResultRecord，浏览器继续使用服务端会话，不保存 access token。
加载器使用独立 i18next 实例，按身份、scope、页面和语言缓存，默认 TTL 为 60 秒；
下一次 load 在 TTL 过期后刷新，也可显式 `refresh: true`。没有后台轮询或推送。
更新已有 key 的翻译无须重新构建前端；新增前端控件或改用新的 key 仍需要相应前端代码。

会话或租户变化时调用 `setIdentity`，注销时清空身份或 dispose；迟到响应不能写入新身份缓存。
刷新失败可保留同身份同 scope/page/locale 的上次成功资源，或使用调用方提供的本地 fallback。
版本更新采用整包替换，已删除的 key 不会残留。具体 TypeScript 示例见
[Web SDK](https://github.com/Stellar-Enclosure/web-sdk/tree/main/packages/stellar-enclosure-web-shared)。
Tianshu 管理页的已发布预览使用同一加载器，并提供实际渲染结果和 JSON 查看。

## Tianshu 现有管理页面接入

Tianshu 管理前端的 14 条业务路由及公共组件已统一读取中心语言包，初始提供简体中文和英语。
服务端维护 15 份 catalog：`common`、`tianshu.auth`、`tianshu.bizs`、`tianshu.envs`、`tianshu.apps`、
`tianshu.namespaces`、`tianshu.bindings`、`tianshu.registry`、`tianshu.instances`、`tianshu.publishTasks`、
`tianshu.cache`、`tianshu.configs`、`tianshu.ai.resources`、`tianshu.ai.editor`、`tianshu.i18n`。
配置编辑弹窗共享 configs 包，AI 编辑子面板共享 editor 包，公共包包含导航与组件内置词条。
源文件位于 Admin 的 `src/main/resources/i18n/tianshu-admin`；不是前端静态业务词典。

| 接口 | 用途与权限 |
|---|---|
| `GET /api/v1/tianshu/i18n/public/bundle?locale=...` | 匿名只读静态登录包，不访问数据库或企业文案 |
| `GET /api/v1/tianshu/i18n/admin/bootstrap` | READ；返回固定管理台作用域、已发布支持语言和是否允许初始化 |
| `GET /api/v1/tianshu/i18n/admin/bundle?pageCode=...&locale=...` | READ；内置默认文案叠加企业已发布公共与页面文案 |
| `POST /api/v1/tianshu/i18n/admin/initialize` | 同时需要 READ、WRITE、PUBLISH；在单个事务中创建并发布缺失的 catalog 页 |

私有接口仍要求预期租户校验头，不允许请求参数改变作用域或选择其他租户。公开接口只返回
`tianshu.auth`，即使传入别的 pageCode/tenant 参数也不会改变目标。Edge 只对精确 GET 公开路径
放行并剥离身份凭据，其他管理及语言包路径继续鉴权。前端的初始化、草稿保存和发布在发送前取得
当前 BFF 会话的 CSRF 校验信息，等待期间身份变化会取消写入，不重放旧请求。

管理台作用域由 `egon.cola.component.tianshu.admin.ui-i18n` 配置，其默认值分别来自
`TIANSHU_RESOURCE_BIZ_CODE`（xingyuan）、`DEPLOYMENT_NAMESPACE`（default）、`DEPLOYMENT_ENV`（local）
和 `TIANSHU_RESOURCE_APP_CODE`（tianshu）。业务页面筛选的 app/scope 不会影响界面语言。

在元数据中心选择“查看管理台语言包”，再显式执行“初始化默认文案”。此操作只处理当前企业，
已有页面（包括未发布草稿）全部跳过；不会自动创建业务域或绑定，也不会覆盖企业定制。
作用域尚未建立或被禁用时，中心提供内置默认文案，使管理员仍能进入页面修复元数据。
数据库故障或权限拒绝不会伪装成初始化缺失。企业发布后，已发布文案优先，未配置的新 key 继续用服务端默认值。
管理台的内置词条作为版本升级的兼容回退，删除企业覆盖项会恢复内置值；普通业务页面语言包仍遵循前文的纯发布内容规则。

语言切换先加载当前可见页面的目标语言，成功后更新界面和组件 locale，失败则保留原语言及编辑缓冲。
只在浏览器存储语言偏好，不持久化企业文案。登录/退出和同企业重新登录均隔离语言缓存。
发布管理台作用域中的文案会自动刷新当前界面；其他已打开页面可点顶部刷新按钮，或在下次加载时取得新版本。
动态业务数据、用户文件内容和服务端原始诊断信息保持原样，不把这些内容当作翻译 key。

## 数据库升级与验收

本次只新增 `db/egon-mp/V20261022_001__tianshu_page_i18n.sql` 一个 DDL 版本，
创建 `ddc_i18n_page_t0`，并将其登记到 `db/egon-mp/repository-manifest.json`。
旧迁移内容保持不变。Admin 启动只检查就绪状态，不自动修改数据库。

**新建空库**沿用 [PostgreSQL DDL 初始化](tianshu-ai-migration-runbook.md#postgresql-ddl-初始化)
的显式 CLI initialize 流程，完成后使用 validate 检查；runner 从声明的新库基线开始并执行后续版本。

**已经初始化的数据库**需要使用本次专用的 `upgrade-i18n`。新增路由表会改变整个 topology fingerprint，
因此普通 initialize/validate 会保持严格校验，不能用于跳过旧指纹不一致。目标配置沿用原来的
PRIMARY、LOCAL 事务和 STRATEGY 配置，并在 sharding.tables 中新增：

```yaml
ddc_i18n_page: { type: STANDARD_TENANT_ID }
```

升级前备份目标数据库，停止旧版本 Admin；目标配置使用权限为 `0600` 的绝对路径文件。
该命令是执行后退出的迁移 CLI：

```bash
./mvnw -pl :stellar-enclosure-tianshu-admin -DskipTests \
  spring-boot:run \
  -Dspring-boot.run.main-class=top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli \
  -Dspring-boot.run.arguments="upgrade-i18n --target-config /secure/tianshu-target.yml"
```

工具从当前配置仅移除本次新增的路由表，推导原指纹；只接受匹配旧完整迁移链或声明的新库基线、
完整 checksum 和相同旧指纹的受管数据库。它先用原 DDL runner 执行本次新增 SQL，再在一个事务中
把 Tianshu 与 Outbox 两份 history 的 route_fingerprint 一起切换到新值；不修改旧 SQL、checksum、
版本或安装时间，不接受其他路由变更，也不接受两份 history 的混合指纹状态。

SQL 已提交但指纹切换尚未完成时，重新执行同一命令可继续；已经完成时只做完整核验。
提交结果未知时，工具使用新连接核验，不能证明成功则报告未知状态，不能手工补写 history。
升级成功并通过 validate 后再部署服务端、匹配的 Web SDK 和管理前端。两阶段之间不要启动新 Admin，
其就绪检查会拒绝未完成的升级状态。

人工运行验收可依次检查：新建 common 和业务页，维护两种语言，保存后验证草稿不影响读取，
发布后验证语言切换、逐 key 回退和公共词条覆盖；再验证并发编辑冲突、另一租户不可读、
公共文案发布后版本变化，以及网络故障时保留同身份的已加载文案。
单元测试和构建不能代替真实 PostgreSQL、Edge/OAuth 或浏览器运行验收。
