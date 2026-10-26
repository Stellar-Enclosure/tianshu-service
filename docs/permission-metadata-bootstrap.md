# Explicit metadata B0 initialization

The clean-environment sequence is: prepare the existing ACTIVE IdP realm and migrate the approved schemas; run IdP B0 for the finite clients/resources/grants; run this META B0 and RBAC B0 with the same six-part catalog target; register each FULL producer and wait for matching source receipts; obtain deployment qualifications and activate the complete producer set; finally run RBAC B1 for explicit administrator permission codes.

This command establishes only the META business/environment/application/namespace/binding, the immutable catalog-scope mapping and finite RBAC-publisher source-import grants. It accepts no manifest, source message, active catalog, user role or permission grant. It is not a normal application startup hook, HTTP endpoint or ordinary CI task. `CI=true` is rejected.

The current implementation targets the repository's actual single primary `tianshu_0` and its migrated `_t0` tables. The database name, OID, owner, connecting user and `public` schema are explicit and checked. It does not infer a multi-shard route, create schemas, run migrations, repair Flyway history, or connect to Redis. Deployments with a different physical topology must use their reviewed routing-aware owner initialization; do not point this command at an arbitrary shard.

Prepare three owner-only files outside the repository: the command JSON, database connection JSON, and database password file. Use absolute paths and mode `0600` or `0400`; no group/other access and no symlink inputs. Input directories must be owned by the operator and unwritable by other users; ancestor directories must also be trusted. Reads resolve the trusted directory before opening the bounded file, so a mutable directory alias cannot redirect the privileged input. The receipt parent directory must exist and be controlled by the owner. Do not put passwords in argv, environment variables, the JDBC URL, logs or the command JSON. The password file is read exactly, so avoid adding an unintended trailing newline.

Command shape (replace every example value with the reviewed environment facts):

```json
{
  "schemaVersion": 1,
  "operationId": "ef236f7a-3840-4664-afc5-7c2690765c09",
  "target": {
    "catalogScopeId": "10001",
    "tenantId": "20001",
    "businessCode": "xingyuan",
    "applicationCode": "tianshu",
    "namespaceCode": "default",
    "environment": "test"
  },
  "connectionFile": "/absolute/private/metadata-database.json",
  "actorId": "approved-operator",
  "ddcBusinessId": "reviewed-ddc-business-public-id",
  "ddcApplicationId": "reviewed-ddc-application-public-id",
  "ids": {
    "business": "30001", "environment": "30002", "application": "30003",
    "namespace": "30004", "binding": "30005", "metadataScope": "30006", "audit": "30007"
  },
  "sourceImportGrants": [
    {
      "id": "30008", "producerId": "tianshu-java",
      "issuer": "https://idp.example/tenant",
      "clientId": "reviewed-rbac-publisher-client",
      "resourceUri": "https://metadata.example"
    }
  ]
}
```

The technical row IDs are explicitly allocated owner inputs, not inferred from a username or code. When reusing an existing target, use its actual row IDs. `ddcBusinessId` and `ddcApplicationId` are the public IDs passed to RBAC B0; they must match exactly. Each producer needs an explicit source-import grant for the **RBAC publisher identity**. That identity is distinct from the CI registration client; never derive it from a CI role or accept wildcard producers. The issuer/client/resource binding digest uses exactly the same shared canonical calculation as source-import authentication.

Database file shape:

```json
{
  "schemaVersion": 1,
  "logicalDataSource": "tianshu_0",
  "jdbcUrl": "jdbc:postgresql://db.example:5432/tianshu?sslmode=verify-full",
  "username": "approved_metadata_owner",
  "passwordFile": "/absolute/private/metadata-db-password",
  "schema": "public",
  "databaseName": "tianshu",
  "databaseOid": "<actual positive database OID>",
  "databaseOwner": "<actual database owner>"
}
```

Invoke the already verified executable once:

```bash
java -jar stellar-enclosure-tianshu-admin/target/stellar-enclosure-tianshu-admin-exec.jar \
  bootstrap-permission-metadata \
  --command-file /absolute/private/metadata-command.json \
  --receipt-file /absolute/private/metadata-receipt.json
```

The special main branch does not create a Spring application context or start a web server, RPC provider, scheduler or consumer. It validates the files and database identity, then uses one serializable local transaction with finite lock/statement timeouts. Existing business facts are checked under row locks and preserved. A mismatched ID/public identity, disabled row or incompatible scope fails; the command never overwrites or re-enables it.

The same transaction records the canonical command digest bound to the non-secret database identity and original receipt in `ddc_operation_log_t0` under `public_id = pac-b0:<operationId>`. Audit failure rolls back all creations. The local receipt is written privately and atomically after commit. If commit or local file publication has an unknown outcome, retain the same command and operation ID and rerun: the SQL receipt is authoritative and prevents duplicate work. A different command digest with the same operation ID is rejected. If a local receipt already exists but the matching SQL original is absent or differs (for example after database recovery), initialization stops before any business INSERT; it does not replay the grants. An existing receipt file for another operation is never overwritten.

The receipt contains the exact target, public business/application IDs and created/reused row/grant IDs with their binding digests. It contains no database credential. Passing unit tests proves validation, transaction orchestration, conflict/replay handling and file behavior with JDBC fixtures. Actual PostgreSQL execution and cross-center B0 → registration → activation → B1 acceptance remain an explicit environment-validation step.
