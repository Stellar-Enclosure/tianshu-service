# Tianshu Java permission producer

`tianshu-java` owns the `xingyuan / tianshu` Java permission declarations and resource keys under `tianshu.api.` and `tianshu.field.`. Its complete build consists of `stellar-enclosure-tianshu-starter` and `stellar-enclosure-tianshu-admin`. The starter owns the shared DTO field targets; the admin owns permission definitions, HTTP resources and source messages. React may reference these FIELD/API keys, but must not redefine them.

The declarations use the shared RC annotations and `PermissionManifestProcessor`. No application startup, Spring scanning at export time, remote registration, role grants or database writes occur during compilation. The `permission-producer` Maven profile appends this processor to the existing Lombok, MapStruct and configuration processors for main compilation only.

## Source export and verification

Run a complete clean build with explicit non-secret provenance. A deployment build must use the exact reviewed clean commit; a dirty local build can establish source/build behavior only and is not deployment or activation evidence.

```sh
./mvnw -Ppermission-producer -pl :stellar-enclosure-tianshu-admin -am \
  -Dpermission.commit="$(git rev-parse HEAD)" \
  -Dpermission.buildId="<unique-build-id>" \
  -Dtest=PermissionProducerCoverageTest,DdcManagedEntryPointGuardTest,DdcAdminUiI18nCatalogTest \
  -Dsurefire.failIfNoSpecifiedTests=false clean package
```

Each successful module contains `META-INF/permissions/fragment-v1.json` with `snapshotMode=PARTIAL`. The `prepare-package` execution invokes the shared `PermissionManifestBuildCli` only after compilation and tests succeed. It uses the successfully packaged starter JAR and the admin compiled directory to aggregate the complete expected set into `target/classes/META-INF/permissions/manifest-v1.json` plus its evidence JSON. The build plugin is isolated from application dependencies; neither CLI nor processor is packaged into the executable application. Final deployment artifact SHA-256 is calculated after Spring Boot repackaging. Never promote a fragment, a failed module's old output, a missing module or an incremental subset to FULL. The common delivery pipeline owns registration and activation; this producer does not add another registration format or network client.

`PermissionProducerCoverageTest` compares all actual Spring MVC mappings with compiled API resources, follows the real Jackson request/response property graph, checks field aliases and unique targets, and resolves every label to source messages. It also verifies the isolated public login DTO. This test requires the producer profile so it reads the output of the actual compiler run. `DdcManagedEntryPointGuardTest` directly calls all 31 RPC methods without a service principal and verifies rejection before any service collaborator is reached. The separate `DdcManagedUserRuntimeTest` executes the shared compiled USER/API and field boundaries; neither test suite establishes a live SQL authority or deployed application.

## Permission ownership

The sole definitions are `permission/DdcPermissionCodes.java`:

| Code | Meaning |
| --- | --- |
| `TIANSHU_READ` | Read tenant-scoped configuration and metadata |
| `TIANSHU_WRITE` | Edit tenant-scoped configuration and metadata |
| `TIANSHU_PUBLISH` | Publish configuration and reviewed translations |
| `TIANSHU_CACHE` | Inspect and rebuild configuration caches |
| `TIANSHU_I18N_READ` | Read published tenant-scoped language bundles |
| `TIANSHU_AI_READ` | Read AI resources and history |
| `TIANSHU_AI_WRITE` | Edit AI resource drafts |
| `TIANSHU_AI_PUBLISH` | Publish or roll back AI versions and control automatic optimization |
| `TIANSHU_AI_REPORT` | Report use of authorized AI resources |

There is no wildcard permission definition and no producer-side grant to a role. FIELD declarations keep plain read and write separate; absent operations deny access and no masking policy is inferred. Server-produced IDs/timestamps in metadata request BOs have no write group. Query DTO properties require the corresponding read permission when they are bound as inputs. The shared executor applies the compiled policy; actual SQL authority and deployment activation remain separate integration gates.

## HTTP entrypoint inventory

The table lists every managed controller method. Each method has its own stable `tianshu.api.<controller>.<method>` key and source label. GET exports include the HEAD behavior supported by Spring. Exact path arrays and HTTP methods are compared against the actual framework mapping rather than normalized by the test.

| Controller | Methods | Classification / identity |
| --- | --- | --- |
| `DdcAdminUiI18nController` | `bootstrap`, `bundle`, `initialize`, `publicBundle` | `publicBundle`: PUBLIC login source projection; remaining methods PROTECTED / USER |
| `DdcAiResourceController` | `page`, `create`, `get`, `replaceDraft`, `delete`, `pageVersions`, `getVersion`, `publish`, `rollback`, `promote`, `pagePublications`, `getOptimizationPolicy`, `replaceOptimizationPolicy`, `triggerOptimizationRun`, `pageOptimizationRuns`, `getOptimizationRun`, `pageUsageFeedback` | PROTECTED / USER |
| `DdcAppController` | `list`, `page`, `detail`, `save`, `update`, `delete`, `setEnabled` | PROTECTED / USER |
| `DdcAuthBootstrapController` | `bootstrap` | PROTECTED / USER |
| `DdcCurrentPermissionController` | `current` | PUBLIC expression / verified USER only; fixed host API008 |
| `DdcBizController` | `list`, `page`, `detail`, `save`, `update`, `delete`, `setEnabled` | PROTECTED / USER |
| `DdcCacheController` | `rebuild`, `check`, `page` | PROTECTED / USER |
| `DdcConfigController` | `list`, `page`, `create`, `update`, `delete`, `publish`, `versions`, `pageVersions`, `rollback` | PROTECTED / USER |
| `DdcEnvController` | `list`, `page`, `detail`, `save`, `update`, `delete`, `setEnabled` | PROTECTED / USER |
| `DdcI18nPageController` | `page`, `create`, `get`, `sourcePage`, `draft`, `publish`, `publication`, `bundle` | PROTECTED / USER |
| `DdcI18nSourceImportController` | `importSource`, `receipt` | PROTECTED / SERVICE; TIANSHU_METADATA_IMPORT plus exact database grant |
| `DdcInstanceController` | `list`, `page` | PROTECTED / USER |
| `DdcNamespaceController` | `list`, `page`, `save`, `update`, `delete`, `setEnabled` | PROTECTED / USER |
| `DdcNamespaceEnvAppBindingController` | `list`, `page`, `create`, `update`, `delete` | PROTECTED / USER |
| `DdcPublishTaskController` | `list`, `page`, `detail`, `retry` | PROTECTED / USER |
| `DdcRegistryAdminController` | `services`, `pageServices`, `instances`, `pageInstances` | PROTECTED / USER |
| `DdcResourceMetadataController` | `search` | PROTECTED / USER |

The two source-import endpoints accept verified SERVICE identity only, bind full tenant/catalog scope, producer, issuer, client and resource, and recheck the enabled grant for writes and receipt reads. Their body DTOs are shared protocol contracts, not an editable tenant management form. `ResultRecord`, `PageResultRecord`, `PageQuery`, authorization bootstrap and shared catalog/source-import RC records are explicit externally owned protocol wrappers; the coverage test rejects new unexplained external DTO contracts.

## Fields and source messages

The current HTTP graph has 490 declared FIELD targets, including 35 properties in starter shared DTOs and five explicit PUBLIC fields in `DdcPublicI18nBundleResult`. The latter is a response-only login projection with Jackson `READ_ONLY` properties and cannot carry catalog scope, activation version, source versions or human overrides. PUBLIC only removes the field permission expression; it does not make a response property writable or bypass API, identity, tenant or binding checks. The tenant `DdcI18nBundleResult` remains a separate protected DTO.

The original 15 console catalogs now have a single source: `permission/Ddc*Messages.java` annotated with shared `@PermissionMessage`. They are compiled into the same manifest as API/FIELD labels. `DdcAdminMessageSources` lets local public login text and page metadata read those declarations without a second JSON dictionary. `DdcAdminUiI18nService.initialize()` remains a deterministic `TIANSHU_I18N_MANIFEST_REQUIRED` rejection; all tenant console bundles require an explicit imported and activated catalog version. Runtime source declarations never write human drafts or publish overrides.

## Alternative entrypoints

| Entry | Actual methods / purpose | Boundary and coverage |
| --- | --- | --- |
| `DdcRegistryRpcProvider` | registerService, heartbeatService, deregisterService, getServiceInstances, getServices | INTERNAL SERVICE protocol; signed credential, operation/scope allowlist and tenant binding; five direct missing-principal checks |
| `DdcConfigRpcProvider` | registerConfigClient, heartbeatConfigClient, offlineConfigClient, pullConfig, acknowledgePublish | INTERNAL SERVICE protocol; same identity boundary; five direct missing-principal checks |
| `DdcManagementRpcProvider` | getBiz, listBizs, getApp, listApps, findConfig, upsertConfig, deleteConfig, publishConfig, getPublishTask, retryPublishTask, getConfigClients, revokeResourceAdmission, getScopeBindings, getServiceKeys, getInstances | INTERNAL SERVICE protocol; 15 direct missing-principal checks; writes still use existing service transactions |
| `DdcAiResourceRpcProvider` | getPrompt, getSkill, reportUsage, getVersionHead, replaceSkillDraft, publishSkillDraft | INTERNAL SERVICE protocol; namespace/operation checks; six direct missing-principal checks; reportUsage validates a bounded 1..100 batch |
| `DdcPersistenceMigrationCli` / `DdcLegacyImportService` | Explicit offline topology/data migration and bounded diagnostic reports | Operator-only tool with explicit configuration; not a business USER or HTTP endpoint; never invoked by producer export or startup |
| `DdcAiOptimizationScanner`, `DdcAiJudgeScanner`, `DdcAiFeedbackRetentionScanner` | Internal leased jobs and retention | Internal job state; not a USER request adapter or a role-grant path |
| `PublishStartupRecovery`, `PublishTimeoutScanner`, `DdcLeaseExpiryScanner` | Internal recovery, timeout and lease work | Internal operation state; not a public write endpoint |
| `DdcAdminAuthenticationEntryPoint` | Fixed authentication/authorization error JSON | Protocol error projection; no business DTO or tenant secret export |

There are no separate HTTP download/export/file-stream endpoints in the current controller inventory. RPC protobuf targets are recorded as INTERNAL alternative entries, not fabricated as HTTP API mappings. The existing RPC identity is `DdcServicePrincipal` established by the signed RPC interceptor; it is not evidence of a USER JWT. No USER role or field permission may be inferred from it. Any future USER-facing adapter must preserve the verified original USER proof and invoke the shared Runtime/API/field boundary. An additional RPC/provider/controller or externally owned DTO causes coverage review; merely appearing in an inventory is not execution proof.

## Bootstrap and remaining integration gates

B0 must be an explicitly authorized clean-environment operation: create a finite catalog scope, bind the two-module `tianshu-java` owner, configure exact service principals/resources and finite registration/import/runtime grants, then register and qualify the built producer. Source-import grants are disabled by default and carry immutable identity binding; normal CI cannot create blanket grants or assign roles.

B1 runs only after the built-in permission definitions are registered. A controlled one-time administrator assigns an explicitly reviewed set of the nine permission characters to the intended role and scope. Neither registration nor source import grants these permissions. This repository's declaration work does not execute B0/B1 or fabricate a registered/active version.

The management chain now uses exact current snapshot permission codes and OAuth scopes; `CAP_*` does not grant access. The shared compiled method guard revalidates the SQL snapshot and executing build before USER APIs. I18n and AI USER service checks also invoke that guard while retaining business/app/namespace checks. RPC and internal system-actor authorization retain their existing separate identity boundaries. `TIANSHU_AI_PUBLISH` retains its existing `TIANSHU_AI_WRITE` OAuth scope upper bound.

`DdcPermissionWebConfiguration` and the existing strict I18n/AI converters use `PermissionJsonMapperFactory.forTransport`, which copies each HTTP mapper. Internal cache, persistence and RPC JSON mappers are not mutated. Spring query-object binding for config and publication filters captures exactly submitted parameters and calls the shared field guard before controller execution. The two source-import SERVICE keys remain a finite bootstrap list and still require their existing scope and SQL grant.

`GET /api/v1/authorization/effective-permissions` returns only the current verified USER snapshot for the fixed host catalog and artifact, with `Cache-Control: no-store`; query targets, SERVICE callers and missing/expired proof are rejected. The only native anonymous resource is `tianshu.api.adminUiI18n.publicBundle`: the existing PUBLIC endpoint classification, exact GET path, one locale parameter and five response-only PUBLIC fields are all required. PUBLIC declarations elsewhere do not grant anonymous access.

Deployment supplies `TIANSHU_ARTIFACT_DIGEST`, `TIANSHU_CATALOG_SCOPE_ID`, `TIANSHU_CATALOG_TENANT_ID`, `TIANSHU_CATALOG_NAMESPACE` and `DEPLOYMENT_ENV`; `system-code` and catalog application code are both `tianshu`, under `xingyuan`. Finite tenant/platform identity authority bindings use `TIANSHU_IDENTITY_TENANT_A_REGISTRATION_ID`, `TIANSHU_IDENTITY_TENANT_B_REGISTRATION_ID`, `TIANSHU_IDENTITY_PLATFORM_REGISTRATION_ID`, `TIANQUAN_SHOUBING_AUTHORITY_BASE_URI` and `TIANQUAN_SHOUBING_IDENTITY_RESOURCE_URI`. Credential provisioning stays in the existing service-client configuration. Authorization Redis and identity revocation/audit spool configuration are removed; Tianshu's own business configuration cache is retained.

PostgreSQL integration, remote receipts, actual source import, deployment qualification/activation and browser behavior remain separate validation stages. No service, database, container or browser is started by the validation commands above.

## Offline evidence for this implementation

The 2026-10-25 USER integration clean package passed 85 selected tests with no failures, errors or skips: nine actual compiled API/field/native-public/API008 tests and 76 existing affected tests. A subsequent configuration-only package passed the new host configuration test. Earlier 24/84-test producer and RPC rounds are separate, overlapping evidence and are not added to these counts.

The standard CLI produced a 440,675-byte FULL manifest containing 578 resources (88 API + 490 FIELD), 1,250 source messages and nine permission definitions. Its manifest digest is `33536b41607dc003747fbe1b7bcaf0ad0c07e16d36475e2f469a13057b1aeb65`. The final configuration package executable JAR SHA-256 is `dcbfa57c6780c597ff4db6a4c0e43b9f1a124a0fee108db67b6fb678ed5db3a8`. A Boot archive classloader plus Spring ClassPathResource read the embedded FULL manifest without initializing application classes or a Spring context; CLI/processor artifacts are absent from runtime libraries. Earlier aggregation tests rejected either missing module and proved order-independent canonical output.

Build ID `s27-m-runtime-local` records an uncommitted local source build. It is not evidence of a clean deployment commit, central registration, grants, qualification, activation or live Runtime requests. The snapshot loader and verified-token handoff in the focused tests are explicit fixtures; the actual SQL authority and token verification chain are separate owners' validation boundaries.
