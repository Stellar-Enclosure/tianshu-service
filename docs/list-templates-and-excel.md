# 列表字段模板与 Excel 导出

天枢按可信租户、鉴神 catalog scope、页面资源和列表标识保存字段模板。系统模板和个人模板均可有多个；用户明确选择的模板优先，其次是个人默认、系统默认、列表代码默认。个人模板可以选择系统模板未显示但本人有字段读取权限的列。模板只控制列及顺序，不授予页面、导出、行数据或字段权限。

当前首个接入页面是天枢命名空间列表 `tianshu.page.namespaces / main`。系统模板维护、个人模板维护、导出分别使用 `TIANSHU_NAMESPACE_TEMPLATE_SYSTEM`、`TIANSHU_NAMESPACE_TEMPLATE_PERSONAL`、`TIANSHU_NAMESPACE_EXPORT`，都还要求 `TIANSHU_READ`。新的权限定义和页面资源必须经过已有 Manifest 注册、部署限定和显式激活，管理员还需在鉴神授予相应角色；注册和激活本身不会授予角色。命名空间列表没有组织归属字段，遇到非全租户的数据范围时列表和导出均拒绝，而不是忽略该范围。

命名空间列表现有的六个字段均使用 `TIANSHU_READ` 读取权限，因此这个示例不会单独区分各列的角色授权。其他业务需要按现有权限目录约定声明字段的读取、脱敏权限并在鉴神授予相应角色；模板和导出会按实际字段授权结果处理。

## 列表注册与权限绑定

业务应用在服务端声明 `ListDefinition` 和 `ListDefinitionBinding`，字段引用后端拥有的 FIELD 资源，列标题引用天枢多语言 key。前端 producer 在同一 catalog 的 `COMPONENT` 资源中声明 `kind=LIST`、`pageResourceKey`、`listKey`、`definitionDigest`、三个 API key、`scopePolicyDigest` 和有序列。列编码为 `三位顺序|列标识|字段资源键（多个以逗号分隔）|文案页面|文案键|默认显示（0/1）|允许导出（0/1）`；最多四组 `columns`、`columns2`、`columns3`、`columns4`，每组最多 64 列。

`definitionDigest` 由 `ListDefinition.digest()` 计算；`scopePolicyDigest` 由 `ListDefinitionBinding.scopePolicyDigest(true, permissionScopes)` 计算，映射必须与业务服务实际启用的 USER scope 策略相同。已激活的组件元数据与业务服务代码、权限映射或三个 API 绑定不一致时请求失败。更改 scope 映射或列表定义需要重新登记并激活相应 producer，不能仅修改请求体。字段/API 仍由后端 producer 拥有，前端只能引用它们。完整示例见天枢 Web 的 `tianshu.component.namespaces.main` 声明。

## 业务应用接入

通用 XLSX 组件位于 `stellar-enclosure-component-common-core` 的 `top.egon.cola.component.common.core.excel` 包；宿主需显式引入 `poi-ooxml:5.4.1`。分页查询、数据范围、授权后的字段投影和权限复核由业务服务提供。`stellar-enclosure-tianshu-list-client` 提供 `ListTemplateClient`，通过业务服务自己的 `PermissionExecutionGuard` 和已验证用户令牌调用天枢内部模板接口。客户端需要启用 USER scope 策略，并注入针对天枢资源、当前租户且只具备 `tianshu:list-template:delegate` 的 Shoubing SERVICE token 供应器。端点必须是 HTTPS，只有本机回环允许 HTTP；客户端不把令牌放入日志、浏览器或持久化数据。

天枢只接受已验证的 TENANT SERVICE；鉴神重新验证同一原始 USER 令牌并返回当前目标应用快照。天枢核对服务来源业务域、应用、环境、租户与用户的 catalog，重新校验 PAGE/API/FIELD 资源及令牌 scope，读取和写入按认证用户隔离。应用端与天枢均在操作前后复核权限版本。部署时需显式配置 Shoubing 的精确 SERVICE Grant、天枢到鉴神的目标应用 Runtime 绑定、原用户令牌 audience、producer/制品限定及元数据导入授权；模板接口不会自行创建这些授权。

业务列表应复用原有筛选、排序与数据范围查询，把实际生效模板列和 `PermissionOutputProjection` 生成的授权结果交给 XLSX 组件。派生列必须声明所有依赖字段并只使用已投影的值。导出期间权限或模板变化时中止；分批查询默认不承诺跨页数据库快照。命名空间导出默认使用当前筛选结果，也支持当前页，最多 50,000 行，并限制中间内容和最终文件大小。

列表数据接口继续返回当前用户有权限的全部字段，不接收模板参数。前端单独读取模板视图，使用 `visibleColumns` 决定显示列；切换模板后重新读取模板，列表数据无需重新查询。`mayManageSystem` 和 `mayManagePersonal` 是按当前权限计算的界面能力提示，每次维护操作仍会在服务端重新授权。

前端 codegen 生成的页面约定由业务服务在列表 API 路径下提供 `GET /list-view`、`POST /list-view/selection`、`POST /list-view/system`、`POST /list-view/personal` 和 `DELETE /list-view/{system|personal}/{id}`。业务服务应通过 `ListTemplateClient.execute` 将这些接口映射到 `LOAD`、`SELECT`、`SAVE_SYSTEM`、`SAVE_PERSONAL`、`DELETE_SYSTEM` 和 `DELETE_PERSONAL`；读取返回模板视图，保存返回 `savedTemplate`，选择和删除返回成功结果。接口 DTO、页面/字段资源、服务凭据及鉴神策略必须由业务应用注册和配置，前端生成器不会替业务应用创建这些授权或服务端接口。

## 数据库升级

新增脚本 `db/egon-mp/V20261028_001__tianshu_list_templates.sql` 是唯一的新版本；既有脚本与 checksum 不变。四个新逻辑表均使用 `STANDARD_TENANT_ID`，就绪检查验证表、列、关键约束、模板唯一索引与列表定义不可变触发器。

已有受管数据库需要在停用生产者和 worker、备份并核对目标后，以受保护的绝对路径配置显式执行 `DdcPersistenceMigrationCli upgrade-list-templates --target-config /secure/tianshu-target.yml`，再执行 `validate`。该入口只接受单 PRIMARY、LOCAL、原拓扑加四个指定路由，核验历史 checksum 和两份 DDL history；新 SQL 与旧指纹先提交，然后在同一事务提升天枢与 Outbox 指纹，支持失败后重新执行核验。旧 `upgrade-i18n` 不能用于该新版本。新库仍使用现有 `initialize` 和 `validate` 流程。

源码测试、静态迁移合同和内存 MVC 测试不等于真实 PostgreSQL、鉴神/Shoubing、网关或浏览器验收；部署前应在授权的目标环境验证真实升级、角色授权、不同租户和用户隔离、字段明文/脱敏/不可见，以及导出期间撤权。
