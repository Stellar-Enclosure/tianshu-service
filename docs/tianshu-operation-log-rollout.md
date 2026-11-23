# 天枢系统操作日志发布、回退与验收手册

本手册对应已批准的 MP-Ext 分片操作日志 Spec、Plan Steps 3–7。当前记录 G07 本机隔离验证，`productionAccepted=false`。本机进程、原生 PostgreSQL、受管 IT 与制品对照的证据按下文分别列出；生产容量、真实 IAM 和 Kubernetes 仍须各自验收。

首次发布保留 `ddc_operation_log` 的物理表和原路由，普通旧历史不复制到新审计库。PAC 回执只迁入 `ddc_metadata_bootstrap_receipt_t0`。物理退役是 Step 8 的独立门，本次没有 DROP、修改已应用 SQL/history、自动重驱 DEAD 或 Rust/JNI 改动。

**封存制品与独立发布。** 部署者必须同时固定 Components、Tianshu Admin/Publisher、Web、权限 manifest 的提交、不可变版本、制品摘要和构建 ID。现有 `0.0.1`/snapshot 本机隔离构建是验收输入，不是覆盖制品仓同 GAV 的授权。正式发行仍按各仓 README/BOM 的不可变坐标规则发布。

| 用途 | 提交 / 制品 | SHA256 / 状态 |
| --- | --- | --- |
| 兼容回退 Admin/Publisher/新 PAC CLI | Tianshu `a3a82428819b21dc8eaea6cd75004e83a5ec4333`；本机 `backout-artifacts/tianshu-cli-compatible-85511-exec.jar` | `85511dfd6f93b6e951a67d9e3157345c3af00e9336912ef2c585d49fab5f91b3` |
| 新采集及查询 Admin/Publisher | Tianshu `0c6be1f94a252e72d6e644411d8ee476587a733a`；本机 `g07-artifacts/stellar-enclosure-tianshu-admin-exec.jar` | `0ec57492e05c8019442fe03c805e2ccfe12f45a6cd9dd0aeef3e0c4aa0a1b2ca`；随同目录 `artifact-evidence.json`、`manifest-v1.json` |
| 来源技术 Outbox | 已提交 `d3f5f9c` 的 pre-coordination 兼容制品 | 两个 JAR 均实际内嵌 `20260924_001`、`20261027_001`；不可擅自换成增加 queue 表的版本 |
| 原版负例 | Tianshu `56bafdae6da26f3f59304ec8367fb1cb1f6e486d`，独立临时 worktree 构建 | 仅用于 V001 前生成旧 PAC 和 V001 后拒绝 extra history；不是回退制品 |
| Web | `e97849d7f60823bd8f4964c2dad35fe97579de33`；`step6-web-e97849d/artifact-record.json` | artifactDigest `5c9a0d9d6cf72dbda558c15277aaf69e407b59728ad40148193c9119c455eb1a`；manifest `a80720184e3b9d8469c29097a5aab4f3ea6524612cfa1f37b3c4abe5a5fdbb71`；旧兼容后端期间不暴露新查询入口 |

两份业务 manifest 均包含 12 个逻辑脚本，末项是 `V20261123_001__tianshu_metadata_bootstrap_receipt.sql`。旧 raw manifest 为 11 个逻辑脚本；本次 fresh-baseline 隔离来源实际 `public.ddl_history` 为 8→9 行，不能把逻辑清单数写成实际行数。两份来源 Outbox history 均必须保持原 2 行。这与目标审计库每个 slot 的独立 `V20261123_001` 是三个不同的版史维度，不能相互代替。

**真实入口与进程边界。** 以下命令直接使用封存可执行 JAR，`PropertiesLauncher` 从同一 JAR 加载原 Migration CLI，无需替换其中 class、SQL、配置资源或 manifest。

```bash
# Admin HTTP 入口。绝对路径 private.properties 由发布系统/隔离夹具提供。
java -jar "$TIANSHU_JAR" --spring.config.additional-location="file:$PRIVATE_PROPERTIES"

# 独立 Publisher：必须是第一个参数，不是 Admin 的一个普通 profile。
java -jar "$TIANSHU_JAR" --config-publisher \
  --spring.config.additional-location="file:$PRIVATE_PROPERTIES"

# 原封存 JAR 的受管数据库安装/验证；现有库日常只调用 validate。
java -Dloader.main=top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli \
  -cp "$TIANSHU_JAR" org.springframework.boot.loader.launch.PropertiesLauncher \
  validate --target-config "$PRIVATE_SOURCE_YAML"

# PAC 命令、连接文件、密码文件和输出目录必须 owner-only；同 operationId 原样重试。
java -jar "$TIANSHU_JAR" bootstrap-permission-metadata \
  --command-file "$PRIVATE_COMMAND_JSON" --receipt-file "$PRIVATE_RECEIPT_FILE"
```

Admin 的启动不等于认证 HTTP 命令通过。Publisher 的进程存活不等于领取或提交任务。Migration CLI 通过能证明该封存制品看到了真实业务与 Outbox 版史和约束，不能单独证明 Admin/Publisher 的业务路径。

Admin 必须保留实际 Shoubing/Jianshen filters、当前权限激活与 app admission、租户映射和安全审计交付。审计 page 要新 READ，detail 要 READ+DETAIL；两者沿用 OAuth `TIANSHU_READ` scope，普通配置 READ 不获得审计权限。`X-Egon-Tianshu-Tenant` 只是已认证租户的预期核对，不是租户来源。

Publisher 仍使用原源事务管理器、租户注册、受校验的 publication policy 和 Kubernetes API TLS/CA/token 文件。测试 static machine ID 仅在 local/test、单个 loopback PRIMARY 来源、loopback HTTPS API、合法 ID 下允许，不能复制到生产。原生 Redis、静态 ID 或本机 HTTPS stub 必须明确标为替代依赖；stub 不能成为真实 IAM、Kubernetes 发布或生产租约已验收的证据。

**实际开关与目标身份。** 环境键来自随JAR封存的`egon-tianshu-persistence.yml`，不在启动时另写一套逻辑路由。

| 运行角色/阶段 | `TIANSHU_OPERATION_LOG_ENABLED` | `TIANSHU_OPERATION_LOG_TARGET_ENABLED` | 其他要求 |
| --- | --- | --- | --- |
| Admin关闭新采集、继续排空/查询 | false | true | 目标就绪、原audit Handler可消费；polling保持启用 |
| Admin正常采集 | true | true | 先确认目标就绪，再填可信`TIANSHU_OPERATION_LOG_HTTP_ORIGIN`；enabled=true而target=false启动拒绝 |
| Admin全部关闭（仅预检/回退前基线） | false | false | 不具有新目标接收或查询能力，不能用来排空已有audit |
| 新Publisher产生执行审计 | true | false | `--config-publisher`为非Servlet入口，只产源Outbox；角色边界强制target=false，不建目标读池 |
| 旧兼容Admin/Publisher | 不支持新采集 | 不支持新目标 | 新审计积压原样留存，恢复接收由新Admin完成 |

Admin消费/查询运行身份设置`TIANSHU_OPERATION_LOG_AUTO_MIGRATE=false`。`TIANSHU_OPERATION_LOG_DDL_ENABLED`本身不是只读readiness开关：当前Initializer在auto-migrate=false时直接跳过受管迁移。只由单独受管部署作业以DBA/迁移身份同时设`DDL_ENABLED=true`和`AUTO_MIGRATE=true`，逐slot前向执行两份受管manifest、检查副本回放并准备当前月及后两月，再退出迁移身份；应用运行阶段另核对实际拓扑/版史、结构和调用结果。不得把DBA账号留在应用连接池，或令运行账号自行建表。`TIANSHU_OPERATION_LOG_MAX_AGE`必须填经审核的正Duration（本机功能验证P90D仅为夹具值），同时满足允许迟到、重投、修复窗与事实/receipt保留策略。

四个物理连接分别由`TIANSHU_OPERATION_LOG_SHARD_0_PRIMARY_JDBC_URL`、`TIANSHU_OPERATION_LOG_SHARD_0_REPLICA_JDBC_URL`、`TIANSHU_OPERATION_LOG_SHARD_1_PRIMARY_JDBC_URL`、`TIANSHU_OPERATION_LOG_SHARD_1_REPLICA_JDBC_URL`配置，各自同前缀`_USERNAME`/`_PASSWORD`。用户名必须是有fact/receipt SELECT+INSERT、无UPDATE/DELETE/TRUNCATE/DDL的运行身份；migration job用其独立身份。四URL必须是两个不同PRIMARY及各自真实streaming REPLICA，不能重复primary伪装replica，也不因副本异常改成强制主读。

`TIANSHU_OPERATION_LOG_HTTP_ORIGIN`是运维固定的公开origin及可选prefix，例如`https://console.example.test/platform`。只允许http(s)、host、合法公开路径prefix；不带userinfo/query/fragment、点段或编码绕过。实际日志URL由它与Spring已匹配route及经审查的公开ID组合，避免采信请求Host、Forwarded或原始query。网关实际公开prefix必须与此配置对齐；本机没有认证HTTP写成功证据，不能据此验收生产网关prefix。

**本机安全夹具。** 受管入口是 `DdcOperationLogCutoverIT#physicalAuditAndFailureRecovery`，以 opt-in 环境变量启用；缺省不会连接数据库。它只接受明确的本机隔离 source，核对 PRIMARY、owner 和数据库名。传入 `jdbc:postgresql://127.0.0.1:61116/postgres` 时创建并清理自己的随机库；传入已有 `tianshu_operation_log_cutover_<32 hex>` 来源时验证并复用，保留历史及事实供跨制品检查。保留库 `tianshu_step3_backout_6lguz5vc` 不属于该范围。

跨制品演练另用独立的随机来源库，先通过原生 PostgreSQL 管理连接创建并记录数据库 OID/owner，再给封存 CLI 的私有 YAML 和 PAC connection file 指向该库。不要复用单兼容制品预检库，也不要倒改已追加的 history。来源密码、JWT、原 PAC 正文、连接文件和业务 payload 只放在 mode 0700 目录下的 mode 0600 文件，不进入 Git 或普通日志。实施时使用的候选脚本仅编排下文真实入口，收证后删除；持续可复现行为保存在受管 IT 和这些原入口命令中。

**正式混合迁移及同库回退。** 本次 Step 6 的真实提交已固定为上表 SHA；正式发布应再次核对其可达提交及 artifact record。G07 IT 接受该严格命名、已有的来源库时验证 identity 并保留它，来源 URL 为 `/postgres` 时则由 IT 创建和清理自己的库。对四个审计目标串行执行故障用例，禁止同时跑两套重置/暂停回放的用例。

1. 准备一个全新、单独拥有的随机来源库。记录 raw、兼容、新 Admin/Publisher、Web/权限产物摘要。用 raw 56bafda 的 CLI 在旧 11 项逻辑清单对应的实际业务史上生成真实 PAC，另放一条普通旧日志。夹具可以把同一合法 JSON 回执格式化为 CRLF/空白原文，提前记录其 UTF-8 SHA256；这只是本机测试数据，不改原 CLI 或 SQL。
2. 用兼容制品原 Migration CLI 前向追加 V001。核对原 history 的完整行（本次 fresh baseline 为原 8 行）、原来源 fingerprint、Outbox 2 条 history 均不变，PAC 专表为原正文及原 UTC 时间，普通旧行不进入新事实库。原 56bafda JAR 的 validate 应拒绝 extra history；兼容 JAR 的真实 validate/PAC 重放通过。
3. 在同一来源库启动新代码的实际 Admin 和 Publisher 路径，先记录采集关闭基线，再启用采集。通过已验证的配置命令/发布受理及实际 Publisher 结果产生 COMMAND/EXECUTION。确认每个事件的原 eventId、operationId、目标 slot/bucket/month、receipt/fact 和源 ACK；不能只插一条目标 SQL 代替完整投递。
4. 暂停新源产生。保存来源 pending/retry/processing 数量、最老 next_attempt_at、messageId、attempt 和 lease。保持新 Admin 的目标接收能力，已确认的消息可以排空；故障注入的剩余消息原样保留。DEAD 留给运维修复，不在本次自动重驱。
5. 退出新业务生产实例，使用 a3 兼容 Admin/Publisher 回退。在同库 validate 与 PAC CLI 返回相同原字节；原普通行摘要保持不变。实际证明旧 worker 的 claim scope 不包含 `tianshu.operation-log`，保留审计积压的 messageId/status/attempt/lease，不接受“没有注册 handler 所以一定不抢”的推测。
6. 再退出旧生产实例，按相同就绪门恢复新实例。重投保留原事件，核对 receipt/fact 幂等、ACK、积压下降、同 operationId 的独立执行事实。回退期间旧行为产生的普通日志不补入新审计库。

```bash
# 只有本次明确的新空库才允许 initialize；现有库只前向安装经审核的新版本。
java -Dloader.main=top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli \
  -cp "$RAW_PRE_PAC_JAR" org.springframework.boot.loader.launch.PropertiesLauncher \
  initialize --target-config "$PRIVATE_SOURCE_YAML"
# 使用上文 bootstrap-permission-metadata 原入口生成并保存旧 PAC，另记原正文 SHA256。
# 兼容制品将同一库前向追加 V001，再 validate；不得覆写旧 SQL/history。
java -Dloader.main=top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli \
  -cp "$COMPATIBLE_JAR" org.springframework.boot.loader.launch.PropertiesLauncher \
  initialize --target-config "$PRIVATE_SOURCE_YAML"

# 受管真实 IT；SOURCE_URL 明确指向本次独立源库，凭据由私有环境注入。
export TIANSHU_OPERATION_LOG_IT_ENABLED=true
export TIANSHU_OPERATION_LOG_IT_SOURCE_URL="$PRIVATE_ISOLATED_SOURCE_URL"
# 同时提供 Plan 的四个 EGON_MP_PG_SHARD_*_URL、USER/PASSWORD；先核对两PRIMARY及各自REPLICA。
# 审计运行角色另注入 TIANSHU_OPERATION_LOG_IT_RUNTIME_USER/PASSWORD。
./mvnw -B -ntp -Dmaven.repo.local="$EGON_AUDIT_PLAN_M2" \
  -pl stellar-enclosure-tianshu-admin -am -Dsurefire.failIfNoSpecifiedTests=false \
  -Dtest=DdcOperationLogCutoverIT test

# 每个 after-new / rollback-old / reenable-new 截点均实际执行，输出到不同的私有日志文件。
java -Dloader.main=top.egon.cola.component.tianshu.admin.migration.DdcPersistenceMigrationCli \
  -cp "$CHECKPOINT_JAR" org.springframework.boot.loader.launch.PropertiesLauncher \
  validate --target-config "$PRIVATE_SOURCE_YAML"
java -jar "$CHECKPOINT_JAR" bootstrap-permission-metadata \
  --command-file "$PRIVATE_COMMAND_JSON" --receipt-file "$NEW_PRIVATE_RECEIPT_FILE"
```

PAC CLI 输出文件比数据库原 receipt 正文多一个 CLI framing LF。比较时只移除这一个封装 LF，再按 UTF-8 原字节核对；不要 `trim`、重新解析编码 JSON 或删除正文自带的空白。本次旧正文包含内部 CRLF 和末尾空白，专表、旧表与重放输出去掉单一 framing LF 后 SHA 一致。


若完整 HTTP 入口被某个宿主前提阻断，记录真实启动命令、非零退出、最内层原因及清洗过的日志。允许用封存 JAR 的 validate/PAC CLI 加已授权的最小本机运行夹具继续收集对应证据，但必须逐项标明替代了什么；缺失的 HTTP、IAM/app admission、Publisher 真实入口或外部 Kubernetes 接受不能写成 PASS。不得为了绿色而关闭实际安全约束、改已有制品字节或增加未经授权的部署前提。

**双主双副本与观测。** 由实际 G07 受管 IT/经授权操作收集以下证据。审计目标须由两个独立 PRIMARY 和各自 streaming REPLICA 构成；四个 URL 同指一库或主库不能通过。

| 检查 | 必须保留的证据 |
| --- | --- |
| 角色与权限 | 每连接 current_database、inet_server_addr/port、pg_is_in_recovery；管理/迁移角色与 append/read 角色区别。未用受限角色验证时明确列为未证 |
| 物理落点 | 每组两个实际父桶及 UTC 月叶、receipt、索引；当前月和后两月的分区准备。一个事件只属于自己的主组/桶/月 |
| 复制与读侧 | 每组 primary WAL LSN 与对应 replay LSN；暂停、不可见、恢复后可见的时序。正常查询走配置副本；不通过强制主读制造成功 |
| ACK 窗口 | 目标提交后、源 ACK 前故障；原 messageId/eventId 重投，事实和 receipt 不多写，最终 ACK 正确 |
| 目标隔离 | 目标断连/超时后源命令仍按 Outbox 合同提交，积压增加、没有假 SUCCEEDED；恢复后同事件收敛 |
| 冲突与月份 | 同 ID 异 payload/异月冲突不得覆盖；原 occurredAt 和微秒不因重试漂移；缺分区失败而非跳月 |
| 回退 | 同来源 OID 的 old→new→old→new 制品检查点、双 history 完整行、原 PAC SHA、原普通行 SHA、原审计队列水位 |

在来源组使用以下只读 SQL；SQL 不输出 payload、headers、原回执正文或秘密。报告中的高基数 ID 只作受控逐条核对，不能用作 metric labels。

```sql
SELECT * FROM public.ddl_history ORDER BY version;
SELECT * FROM egon_outbox.ddl_history ORDER BY version;
SELECT tenant_id, operation_id, command_digest, recorded_at,
       encode(sha256(convert_to(receipt, 'UTF8')), 'hex') AS receipt_sha256
FROM public.ddc_metadata_bootstrap_receipt_t0 ORDER BY tenant_id, operation_id;
SELECT channel, status, count(*) AS message_count,
       min(next_attempt_at) AS oldest_next_attempt_at, max(locked_until) AS latest_lease
FROM egon_outbox.egon_cola_outbox_message GROUP BY channel, status ORDER BY channel, status;
SELECT message_id, status, attempt_count, locked_by, locked_until
FROM egon_outbox.egon_cola_outbox_message
WHERE channel='tianshu.operation-log' ORDER BY message_id;
```

**容量和启用门。** 采集和目标接收是两个生命周期：关闭新采集不应关闭既有审计队列的接收能力。发布前须填实际保留时长、允许迟到/重投/运维修复窗、峰值和热点租户分布、典型及上限详情、源/目标池和超时预算。没有经过审核的预算与可复现运行结果，保持采集未启用。

| 指标 | 同负载基线及开启后都要测 | 发布阈值 |
| --- | --- | --- |
| 源命令 p99 / 错误率 | 配置写入、发布受理，均衡与热点租户 | 发布方预先确认，不能由测试后选择 |
| 审计提交 p99 / 每事件 SQL | receipt+fact、首次和重复投递、源 ACK 窗口 | 发布方确认 |
| WAL / 磁盘 / 索引 / pool wait | 按主组、桶、UTC 月；实际 payload 和索引占用 | DBA 确认 |
| 积压量 / 最老年龄 / 恢复速率 | 故障持续、恢复、切回；不能只报告空闲队列 | 运维确认 |
| 副本 lag / 查询 p99 | 两组分别记录；窗口、limit、热点过滤和游标 | 读侧预算确认 |
| 保留边界 | 事实与 receipt 生命周期、重投和修复窗 | 至少覆盖最长允许重投/修复窗 |

列表最长 31×24h，limit 最大 100，每次最多本租户两桶、每桶 limit+1，不加 COUNT。详情按 tenant/id/occurredAt 定位。不要把压测用采样结果或本机 2×2 结果换算为生产容量承诺。

维护按真实物理桶和 UTC 月执行。receipt 按 create_time/tenant/id 小批游标且每批最多 1000；只有对应事实已按授权退役且允许重投窗结束，才允许清理该时间段的 receipt。业务 API 不提供操作日志更新、软删除或恢复。保留时长不能小于修复与重投需要。

**独立退役门。** 保留旧物理表和路由是首次发布允许的最终状态。Step 8 前必须证明旧 Admin/Publisher/CLI 全部退出、回退窗关闭、两个实际 history/fingerprint 均接受候选删除后的路由、PAC 同 key 可重读、无旧表运行读写/导入/任务引用。`previous-route-exclusions` 的加表过渡能力不是删除路由授权。缺少任何证据时不创建/应用 V002、不删除旧实现，不把此门误记为阻断已通过的首次切新。真实删除后优先前向修复。

**本机已执行证据。** 2026-11-23，在 `/private/tmp/tianshu-operation-log-g07-ewg7zg60` 单兼容制品预检：封存85511的 initialize、validate、PAC CLI 均 exit 0，真实业务history9行（manifest12个逻辑脚本）与Outbox2行history通过。补全loopback HTTPS CA/token、SHA固定publication policy、逐项verified binding与local static ID后，真实 `--config-publisher` 入口 Started（5.87s），metadata validator记录相同来源fingerprint；polling=false，未把启动记为实际任务/审计投递成功。初次Admin入口因缺IAM配置exit1，补全service-token、OAuth provider issuer-uri和本机HTTPS discovery后，原85511 Admin已实际Started且存活检查通过；没有关闭真实IAM或app admission换取启动。HTTPS stub只提供discovery/空JWKS，对token/业务授权返回503，不代表真实IAM认证命令通过。

来源的运行模式与路由失败也实际验证过：对单兼容预检库只调用封存85511 CLI validate，非法mode以及合法新增SINGLE路由但与原fingerprint不符两种配置均非零退出；两份history、PAC、普通行与queue before/after完全一致。没有用改写history或扩大受信route的方式使其通过。本机首次扩registry时tenant20身份与verified binding不一致，实际新/旧Publisher均因`TIANSHU_CONFIG_PUBLICATION_TARGET_UNVERIFIED`拒启；只修私有夹具恢复原identity后重跑，生产代码/制品未改。

目标两主DDL的真实故障/重试已由根组执行：T0提交/T1连接故障、坏资源SHA拒绝、T1实际commit后响应丢失并以新连接确认、两副本回放均PASS，见 `/tmp/egon-operation-log-execution.lKrE3g/step7-real-audit-ddl-native-retry.log`。此项不替代应用链路或生产容量。

目标初始audit_test是superuser，仅用于隔离迁移和诊断，不能据此声称append/read分权。根组随后用额外测试专用受限角色在四个物理连接验证：无superuser/DDL/history权限，fact与receipt可SELECT/INSERT，UPDATE/DELETE/TRUNCATE均拒绝；副本连接同时受物理只读约束，见`/tmp/egon-operation-log-execution.lKrE3g/g07-audit-runtime-acl.txt`。受管IT以该受限角色实际投递和查询。本机物理ACL证明已通过；生产datasource.username、migration owner及实际GRANT仍须发布方核验。

另一份授权构建的a3 permission-producer备选为 `ca49f515e8444fbfd8de1b7437a0481d91af6885d02ab6d327d6ebf692c97220`，buildId=operation-log-backout-a3-20261123，独立封存未替换85511。原85511自身在JAR根META-INF/permissions中已有a3绑定manifest（83adf8acf797489e682d4b12051c92e0f7edef01f8b241571c5456928f5c4b83），不因最初仅查BOOT-INF/classes而误判其缺少权限元数据。

**同库实际入口与组件链记录。** 正式 Web 提交已固定后，B 使用独立 source `tianshu_operation_log_cutover_99e2ffa0f8034edf94723b29e6cf641e`。raw 56bafda真实CLI生成PAC，兼容85511前向追加V001；实际business history8→9，原8行和Outbox2行逐行不变，来源fingerprint始终为`f95fc4b7789531bb807ec9f61fb1992a0b89bfe4048c61c46b2d18e345b4e16d`。旧表中 ID 为 999 的一条普通日志保留。PAC正文SHA为`57e68248148dbe024245e72d49f43b9b05b7ae0ecfdbe15c219c6c84af6a2469`，原版在V001后真实拒绝extra history，兼容和新CLI validate/PAC均通过。

本机封存入口按 old Admin→new Admin→new Publisher→old Publisher→old Admin→new Admin 串行启动，6次均Started；4次Admin的`GET /actuator/health/readiness`均200。protected GET均503，日志为`IdpObservedVerificationComponent: Strict usage handoff unavailable`，因为HTTPS stub未实现真实安全观察交付。保留了真实IAM及app admission约束，这部分没有认证业务HTTP成功证据。两份publisher在该启动预检中polling=false，实际任务处理另按下面截点记录。

受管`DdcOperationLogCutoverIT`在独立自清来源和上述保留B来源分别运行，各1 test、0 failure、0 skip，均BUILD SUCCESS。该运行使用生产Service、MP、Outbox、Admin audit Handler及Query，受限目标账号覆盖两主各两桶和UTC月、微秒与源事务xmin、停止采集后排空、目标约束拒绝与其他组成功、目标commit响应丢失、源ACK回滚及原messageId租约恢复、真实副本pause/resume、连接工厂实际read失败且不回退primary、异月冲突、下月合法fact/receipt和有限metric。故障使用命名JDBC/事务切面；不把它写成断开PG服务或封存JAR执行了这些注入。源实际水位为8条audit SUCCEEDED和9条config-rollout PENDING，原普通/PAC未改变。证据见 `/tmp/tianshu-step7-a-runtime-early-fault.log` 与 `/tmp/tianshu-step7-b-shared-source.log`。

随后封存新Publisher在同一source开启采集及polling，实际执行前述9条rollout。9个业务task终态为FAILED、failureStage/reasonCode为WORKLOAD_RECONCILE（审计details.publishStatus/publishStage均为FAILED）；9条技术rollout均SUCCEEDED，并新增9条`sourceService=tianshu-config-publisher`、`phase=EXECUTION`、`outcome=FAILED`审计消息，停进程后真实保留PENDING。这个结果证明实际reconcile和终态审计事务，不代表本机Kubernetes发布成功。

回退85511 Publisher保持polling=true、fixed-delay=200ms，实际Started后连续观察15秒，随后退出进程。全部17条审计（8 SUCCEEDED、9 PENDING）的messageId/status/attempt_count/locked_by/locked_until逐条完全不变，两份history、PAC SHA、原普通行SHA也完全不变。证据在私有B目录的`actual-old-publisher-filter-proof.json`和`actual-old-filter2-{before,after}-snapshot.json`。这是实际旧进程的领取范围证据，不只是推断没有Handler。

旧Admin也用85511真实入口开启polling=true/fixed-delay=200ms，Started后观察15秒，readiness200；停稳后同样逐条核对17条audit/9条PENDING的领取字段、history、PAC与普通行SHA完全不变，证据为`actual-old-admin-filter-{before,after}-snapshot.json`。新接收恢复使用最终生产类构建的真实AdminDispatcher受限账号上下文，9条原messageId全部PENDING→SUCCEEDED；逐条按原tenant/eventId/微秒occurredAt调用QueryService detail，且物理fact=1、receipt=1，覆盖两主四桶。证据为`new-audit-drain-evidence.json`及`/tmp/tianshu-step7-b-new-drain.log`。该drain是受管IT同runner的临时编译入口，不冒充封存Admin JAR通过了认证HTTP；封存JAR启动/readiness与该调用级恢复证据分别记录。当前没有生产站点、真实IAM授权或Kubernetes接受结果。容量预算仍为空，本机功能与故障恢复不能关闭生产启用门。

排空后，封存新 Admin 以独立受限运行账号、已启用目标、关闭采集及自动 DDL 连通四个物理数据源，实际启动并返回 readiness HTTP 200。该验证关闭轮询，调用级排空证据独立保存。见 `final-new-target-ready-new-admin-startup.json`。

最终保留源水位为17条audit SUCCEEDED、9条技术rollout SUCCEEDED（对应9个业务FAILED终态），pending/retry/processing audit均为0。最终新JAR再次validate和PAC重放，原8条business history完整前缀、原2条Outbox history、旧表普通日志 ID 999 的正文 SHA及原PAC正文SHA保持不变。保留来源OID与所有私有before/after报告，未降版数据库、未覆写历史、未删除原普通物理表。各阶段的证据清单和最终稳定性汇总位于私有B目录`cross-artifact-final-evidence.json`。

| 验收层次 | 本次结论 | 尚未覆盖 |
| --- | --- | --- |
| 封存CLI/版史/PAC原字节 | 旧→新→旧→新同库PASS；原版extra history拒绝 | 生产站点的版史与路由仍须逐库核对 |
| 封存Admin/Publisher入口 | 实际进程启动、Admin readiness200、两种旧worker不领取9个真实pending audit | 真实IAM认证/app admission成功与网关公开prefix |
| 新采集及投递恢复 | 真生产Service/Outbox/Handler/Query、原消息恢复及两主两副本PASS | 真实跨网络服务故障及生产吞吐预算 |
| Publisher真实执行审计 | 封存新Publisher实际9个FAILED终态+9个EXECUTION持久消息，最终各一fact/receipt | Kubernetes实际接受/完成发布 |
| ACL及DDL | 独立受限账号的实际投递/查询、物理权限拒绝与DBA故障重试PASS | 生产凭据、schema owner与GRANT复核 |
| 上线与旧表退役 | 保持关闭生产启用、保留旧表/原路由 | 已批准容量预算、真实部署验收、独立Step8退役门 |

恢复后的封存0ec新Admin另以受限运行账号连接四个真实物理目标，`enabled=false/target.enabled=true`、`auto-migrate=false`实际Started并readiness200。此轮polling=false以避免与恢复探针竞争，证明目标接收装配和停产配置可启动；前述逐消息排空由实际Dispatcher单独证明。protectedGET仍为strict-usage503。所有拥有的Java、Redis与HTTPS夹具进程均已退出，副本暂停由IT finally恢复，来源与目标数据保留供复核。候选repo脚本已删除，提交只含受管IT与本手册。
