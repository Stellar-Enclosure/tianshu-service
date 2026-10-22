# tianshu-service

[English](README.md) | [中文](README.zh-CN.md)

Service registry, dynamic configuration center and Prompt/Skill resource control plane for Stellar Enclosure. All first-party modules start at **0.0.1**.

## Current capabilities

- Manage businesses, applications, namespaces, environments and their bindings; maintain scoped YAML configuration resources with version history, publish and rollback.
- Distribute configuration through the ConfigData/`@DdcValue` client lifecycle, selective refresh, lease heartbeats and publication ACKs; expose task progress and retry APIs.
- Register and discover RPC/HTTP providers and internal gateways through Redis leases; expose service directories, instance snapshots and paginated management queries.
- Manage tenant-isolated Prompt text and Skill file trees, immutable published versions, rollback and cross-scope version promotion. Collect usage feedback and run configured optimization into drafts or optional publication.
- Provide published Skill workspaces and optional local authoring upload through the AI Starter; expose cache inspection/rebuild and client-instance management.

## Modules and integration

| Module | Responsibility |
|---|---|
| [Base Starter](stellar-enclosure-tianshu-starter/) | Transport-independent configuration, management and registry clients; ConfigData, refresh, ACK and lease contracts/runtime. |
| [HTTP Registration Starter](stellar-enclosure-tianshu-http-registration-starter/) | HTTP service registration, heartbeat/recovery and metadata contribution SPI. |
| [AI Starter](stellar-enclosure-tianshu-ai-starter/) | Queued usage feedback, published Skill synchronization and optional authoring directory upload. |
| [Admin](stellar-enclosure-tianshu-admin/) | Human management REST APIs, direct machine gRPC facades, PostgreSQL facts, Redis state and publication orchestration. |
| [Test](stellar-enclosure-tianshu-test/) | Sample consumer application and lifecycle/contract verification. |

Machine clients connect directly to a configured Tianshu gRPC target through the RPC Tianshu adapter owned by [Components](https://github.com/Stellar-Enclosure/Stellar-Enclosure-Components/tree/main/stellar-enclosure-component-rpc/stellar-enclosure-component-rpc-tianshu-adapter). Tianshu does not discover itself. Admin exposes human management APIs under `/api/v1/tianshu`; [tianshu-web](https://github.com/Stellar-Enclosure/tianshu-web) owns the separate console. Shoubing supplies identity/service tokens, Jianshen supplies user authorization, and Yuheng consumes registry/configuration state.

PostgreSQL stores durable facts; Redis holds caches, notifications and temporary leases. Multiple Admin instances share that logical control plane. This repository does not implement Raft, database/Redis HA, a generic distributed lock service or an embedded UI. Prompt/Skill features require tenant credentials and an explicitly prepared persistence target; see the [migration runbook](docs/tianshu-ai-migration-runbook.md).

## Build

Requires Java 21 and the included Maven Wrapper. Once the 0.0.1 dependencies are published:

```bash
./mvnw -B -ntp test-compile
```

Before the first Central publication, clone the eight Java repositories next to one another and bootstrap their artifact graph explicitly:

```bash
bash ../Stellar-Enclosure-Parent/scripts/bootstrap-workspace.sh \
  --workspace "$(cd .. && pwd)" \
  --local-repository "$HOME/.m2/repository"

# Resolve subsequent Maven commands and generator subprocesses from this same cache.
export MAVEN_ARGS="${MAVEN_ARGS:-} -Dmaven.repo.local=$HOME/.m2/repository"
```

The bootstrap installs parent/BOM models before building components and business starters in Maven reactor order. It compiles test sources and installs packages with runtime tests disabled. It never runs applications, databases, containers, browsers, or deploy.

## Publish to Maven Central

Publishes the Tianshu parent and the base, HTTP registration and AI Starters. The Admin application and integration-test module are excluded. Requires the published platform parent, Components and Shoubing Starter; the HTTP/AI Starters also require the Components RPC Tianshu adapter.

Run these commands from this repository. Configure a Central Portal token under Maven server ID `central` and a local GPG signing key; credentials stay outside Git. See the [credential setup and initial dependency order](https://github.com/Stellar-Enclosure/Stellar-Enclosure-Parent/blob/main/docs/first-release.md).

```bash
# Verify release artifacts locally; signing and upload are disabled.
./mvnw -B -ntp -Prelease -Dgpg.skip=true -DskipITs -Dinvoker.skip=true -Darchetype.test.skip=true clean verify

# Sign and publish this repository's public artifacts.
./mvnw -B -ntp -Prelease -DskipITs -Dinvoker.skip=true -Darchetype.test.skip=true clean deploy
```

Unit tests remain enabled. `release` attaches sources, Javadoc and GPG signatures, then publishes through the Central Portal. Ordinary builds and Git pushes do not publish. Each published GAV is immutable: use a new version for changed content, and use `-pl` to select only unpublished modules after a partial release. Before the first release, use the Build section's local bootstrap or publish the prerequisite artifacts first; bootstrap installation alone does not publish dependencies.

## Compatibility and publication

Java packages/classes, Bean names, configuration keys, OAuth/RPC identities, APIs, and database ownership are retained. Existing SQL and DDL history are copied byte for byte. Artifact/build versions and necessary build paths change. A successful build does not imply application startup or live infrastructure acceptance.

Git pushes run validation only. Central and npm publication are separate user actions. CI defaults to initial source bootstrap; after publication set `STELLAR_USE_SOURCE_BOOTSTRAP=false` to consume published artifacts directly. Private organizations may supply the Actions secret `STELLAR_ENCLOSURE_READ_TOKEN` with repository read access.

Original MIT/LGPL licenses and author attribution are preserved.
