CREATE SEQUENCE public.ddc_service_observation_epoch AS bigint MINVALUE 1 NO CYCLE;

CREATE TABLE public.ddc_service_observation_t0 (
    id bigint NOT NULL,
    tenant_id bigint NOT NULL,
    create_user_id varchar(128) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(128) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp NULL,
    version bigint NOT NULL,
    identity_tenant_id varchar(128) NOT NULL,
    binding_id varchar(128) NOT NULL,
    binding_revision bigint NOT NULL,
    binding_fingerprint varchar(71) NOT NULL,
    cluster_id varchar(128) NOT NULL,
    k8s_namespace varchar(63) NOT NULL,
    service_name varchar(63) NOT NULL,
    service_uid varchar(128) NOT NULL,
    port_name varchar(63) NOT NULL,
    service_port integer NOT NULL,
    writer_epoch bigint NOT NULL,
    report_seq bigint NOT NULL,
    observed_at timestamptz(6),
    valid_until timestamptz(6),
    recorded_at timestamptz(6) NOT NULL,
    view_json jsonb NOT NULL,
    CONSTRAINT pk_ddc_service_observation PRIMARY KEY (id),
    CONSTRAINT uk_ddc_service_observation_scope UNIQUE NULLS NOT DISTINCT
        (tenant_id, binding_id, binding_revision, deleted_at),
    CONSTRAINT ck_ddc_service_observation_positive CHECK (
        id > 0 AND tenant_id > 0 AND version >= 0 AND binding_revision > 0
        AND writer_epoch > 0 AND report_seq > 0 AND service_port BETWEEN 1 AND 65535
    ),
    CONSTRAINT ck_ddc_service_observation_identity CHECK (
        identity_tenant_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
        AND binding_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
        AND cluster_id ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
        AND service_uid ~ '^[A-Za-z0-9][A-Za-z0-9._:-]{0,127}$'
        AND k8s_namespace ~ '^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$'
        AND service_name ~ '^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$'
        AND port_name ~ '^[a-z0-9]([-a-z0-9]{0,61}[a-z0-9])?$'
        AND binding_fingerprint ~ '^sha256:[0-9a-f]{64}$'
    ),
    CONSTRAINT ck_ddc_service_observation_expiry CHECK (
        (observed_at IS NULL AND valid_until IS NULL) OR
        (observed_at IS NOT NULL AND observed_at <= recorded_at
            AND (valid_until IS NULL OR (valid_until > observed_at AND valid_until <= observed_at + INTERVAL '5 minutes')))
    ),
    CONSTRAINT ck_ddc_service_observation_view CHECK (COALESCE(
        jsonb_typeof(view_json) = 'object'
        AND octet_length(convert_to(view_json::text, 'UTF8')) <= 1048576
        AND view_json ?& ARRAY['schemaVersion','bindingFingerprint','writerEpoch','reportSeq','capturedAt','view','payloadDigest','invalidationFloor']
        AND (view_json - ARRAY['schemaVersion','bindingFingerprint','writerEpoch','reportSeq','capturedAt','view','payloadDigest','invalidationFloor']) = '{}'::jsonb
        AND view_json -> 'schemaVersion' = '"1"'::jsonb
        AND view_json ->> 'bindingFingerprint' = binding_fingerprint
        AND view_json ->> 'writerEpoch' = writer_epoch::text
        AND view_json ->> 'reportSeq' = report_seq::text
        AND jsonb_typeof(view_json -> 'view') = 'object'
        AND view_json ->> 'payloadDigest' ~ '^sha256:[0-9a-f]{64}$'
        AND jsonb_typeof(view_json -> 'capturedAt') = 'string'
        AND view_json ->> 'capturedAt' ~ 'Z$'
        AND (view_json -> 'view' ->> 'status') IN ('INITIALIZING','RELISTING','FRESH','STALE','UNAVAILABLE','IDENTITY_CONFLICT','CLOSED')
        AND jsonb_typeof(view_json -> 'view' -> 'endpoints') = 'array', false)
    )
);
