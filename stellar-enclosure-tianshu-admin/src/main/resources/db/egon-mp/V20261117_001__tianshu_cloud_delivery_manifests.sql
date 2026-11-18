-- Freeze legacy configuration/SI publishers before installing this migration.
-- Historical NULL manifests stay readable; every subsequent INSERT/UPDATE must supply a real manifest.
ALTER TABLE ddc_config_version_t0 ADD COLUMN manifest_json jsonb;
ALTER TABLE ddc_config_version_t0
    ADD CONSTRAINT ck_config_version_manifest_shape CHECK (
        manifest_json IS NULL OR COALESCE(
            jsonb_typeof(manifest_json) = 'object'
            AND octet_length(convert_to(manifest_json::text, 'UTF8')) <= 1048576
            AND CASE
                WHEN jsonb_typeof(manifest_json -> 'schemaVersion') = 'string'
                    AND (manifest_json ->> 'schemaVersion') ~ '^[1-9][0-9]{0,18}$'
                THEN (manifest_json ->> 'schemaVersion')::numeric <= 9223372036854775807
                ELSE false
            END, false));
ALTER TABLE ddc_config_version_t0
    ADD CONSTRAINT ck_config_version_manifest_required CHECK (manifest_json IS NOT NULL) NOT VALID;

ALTER TABLE ddc_si_version_t0 ADD COLUMN dependency_manifest_json jsonb;
ALTER TABLE ddc_si_version_t0
    ADD CONSTRAINT ck_si_version_dependency_manifest_shape CHECK (
        dependency_manifest_json IS NULL OR COALESCE(
            jsonb_typeof(dependency_manifest_json) = 'object'
            AND octet_length(convert_to(dependency_manifest_json::text, 'UTF8')) <= 1048576
            AND CASE
                WHEN jsonb_typeof(dependency_manifest_json -> 'schemaVersion') = 'string'
                    AND (dependency_manifest_json ->> 'schemaVersion') ~ '^[1-9][0-9]{0,18}$'
                THEN (dependency_manifest_json ->> 'schemaVersion')::numeric <= 9223372036854775807
                ELSE false
            END, false));
ALTER TABLE ddc_si_version_t0
    ADD CONSTRAINT ck_si_version_dependency_manifest_required CHECK (dependency_manifest_json IS NOT NULL) NOT VALID;

-- Tenant-routed observations are distinct from the old instance/lease ACK archive.
CREATE TABLE ddc_config_process_receipt_t0 (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0,
    config_id bigint NOT NULL,
    config_revision bigint NOT NULL,
    scope_json jsonb NOT NULL,
    target_binding_id varchar(128) COLLATE "C" NOT NULL,
    target_revision bigint NOT NULL,
    cluster_id varchar(64) COLLATE "C" NOT NULL,
    k8s_namespace varchar(63) COLLATE "C" NOT NULL,
    workload_uid varchar(64) COLLATE "C" NOT NULL,
    pod_uid varchar(64) COLLATE "C" NOT NULL,
    container_name varchar(63) COLLATE "C" NOT NULL,
    container_id varchar(256) COLLATE "C" NOT NULL,
    boot_id varchar(64) COLLATE "C" NOT NULL,
    content_digest char(64) NOT NULL,
    applied_revision bigint,
    applied_digest char(64),
    restart_digest char(64) NOT NULL,
    result varchar(24) NOT NULL,
    reason_code varchar(64),
    reason_message varchar(1024),
    report_seq bigint NOT NULL,
    receipt_digest char(64) NOT NULL,
    reported_at timestamptz(6) NOT NULL,
    applied_at timestamptz(6),
    last_received_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_config_process_receipt PRIMARY KEY (id),
    CONSTRAINT uk_config_process_receipt_incarnation UNIQUE (
        tenant_id, config_id, config_revision, target_binding_id, target_revision,
        cluster_id, pod_uid, container_id, boot_id),
    CONSTRAINT fk_config_process_receipt_version FOREIGN KEY (tenant_id, config_id, config_revision)
        REFERENCES ddc_config_version_t0 (tenant_id, config_id, config_revision) ON DELETE RESTRICT,
    CONSTRAINT ck_config_process_receipt_id_positive CHECK (id > 0),
    CONSTRAINT ck_config_process_receipt_tenant_positive CHECK (tenant_id > 0),
    CONSTRAINT ck_config_process_receipt_version_nonnegative CHECK (version >= 0),
    CONSTRAINT ck_config_process_receipt_not_deleted CHECK (deleted_at IS NULL),
    CONSTRAINT ck_config_process_receipt_revision_positive CHECK (
        config_id > 0 AND config_revision > 0 AND target_revision > 0 AND report_seq > 0
        AND (applied_revision IS NULL OR applied_revision > 0)),
    CONSTRAINT ck_config_process_receipt_scope_shape CHECK (
        jsonb_typeof(scope_json) = 'object'
        AND octet_length(convert_to(scope_json::text, 'UTF8')) <= 8192),
    CONSTRAINT ck_config_process_receipt_identity_ascii CHECK (
        target_binding_id ~ '^[!-~]+$' AND cluster_id ~ '^[!-~]+$'
        AND workload_uid ~ '^[!-~]+$' AND pod_uid ~ '^[!-~]+$'
        AND container_id ~ '^[!-~]+$' AND boot_id ~ '^[!-~]+$'),
    CONSTRAINT ck_config_process_receipt_namespace CHECK (
        k8s_namespace ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'),
    CONSTRAINT ck_config_process_receipt_container_name CHECK (
        container_name ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'),
    CONSTRAINT ck_config_process_receipt_digests CHECK (
        content_digest ~ '^[0-9a-f]{64}$' AND restart_digest ~ '^[0-9a-f]{64}$'
        AND receipt_digest ~ '^[0-9a-f]{64}$'
        AND (applied_digest IS NULL OR applied_digest ~ '^[0-9a-f]{64}$')),
    CONSTRAINT ck_config_process_receipt_result CHECK (
        result IN ('APPLIED', 'DISABLED_APPLIED', 'REJECTED', 'RESTART_REQUIRED', 'FAILED')),
    CONSTRAINT ck_config_process_receipt_actual_pair CHECK (
        (applied_revision IS NULL) = (applied_digest IS NULL)
        AND (applied_at IS NULL OR applied_revision IS NOT NULL)),
    CONSTRAINT ck_config_process_receipt_result_shape CHECK (
        CASE WHEN result IN ('APPLIED', 'DISABLED_APPLIED') THEN
            applied_revision IS NOT NULL AND applied_revision = config_revision
            AND applied_digest = content_digest AND applied_at IS NOT NULL
            AND reason_code IS NULL AND reason_message IS NULL
        ELSE reason_code IS NOT NULL AND reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'
        END),
    CONSTRAINT ck_config_process_receipt_receive_time CHECK (last_received_at >= create_time)
);

CREATE INDEX ix_config_process_receipt_target
    ON ddc_config_process_receipt_t0 (
        tenant_id, config_id, config_revision, target_binding_id, target_revision,
        cluster_id, k8s_namespace, workload_uid, pod_uid);
CREATE INDEX ix_config_process_receipt_retention
    ON ddc_config_process_receipt_t0 (tenant_id, last_received_at, id);

-- SINGLE authority: the logical SQL table name intentionally includes its physical _t0 suffix.
-- id is the immutable durable fence. Runtime writers receive SELECT/INSERT only;
-- controlled physical archival uses a separate privileged administrative identity.
CREATE TABLE ddc_config_boot_binding_t0 (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0,
    cluster_id varchar(64) COLLATE "C" NOT NULL,
    pod_uid varchar(64) COLLATE "C" NOT NULL,
    container_name varchar(63) COLLATE "C" NOT NULL,
    container_id varchar(256) COLLATE "C" NOT NULL,
    boot_id varchar(64) COLLATE "C" NOT NULL,
    workload_uid varchar(64) COLLATE "C" NOT NULL,
    k8s_namespace varchar(63) COLLATE "C" NOT NULL,
    CONSTRAINT pk_config_boot_binding PRIMARY KEY (id),
    CONSTRAINT uk_config_boot_binding_incarnation UNIQUE (cluster_id, pod_uid, container_name, container_id),
    CONSTRAINT ck_config_boot_binding_id_positive CHECK (id > 0),
    CONSTRAINT ck_config_boot_binding_technical_tenant CHECK (tenant_id = 0),
    CONSTRAINT ck_config_boot_binding_version_nonnegative CHECK (version >= 0),
    CONSTRAINT ck_config_boot_binding_not_deleted CHECK (deleted_at IS NULL),
    CONSTRAINT ck_config_boot_binding_identity_ascii CHECK (
        cluster_id ~ '^[!-~]+$' AND pod_uid ~ '^[!-~]+$' AND container_id ~ '^[!-~]+$'
        AND boot_id ~ '^[!-~]+$' AND workload_uid ~ '^[!-~]+$'),
    CONSTRAINT ck_config_boot_binding_namespace CHECK (
        k8s_namespace ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$'),
    CONSTRAINT ck_config_boot_binding_container_name CHECK (
        container_name ~ '^[a-z0-9]([a-z0-9-]*[a-z0-9])?$')
);

CREATE FUNCTION ddc_config_boot_binding_reject_mutation() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
    RAISE EXCEPTION 'TIANSHU_CONFIG_BOOT_BINDING_IMMUTABLE' USING ERRCODE = '55000';
END;
$body$;

CREATE TRIGGER tr_ddc_config_boot_binding_immutable
    BEFORE UPDATE ON ddc_config_boot_binding_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_config_boot_binding_reject_mutation();
