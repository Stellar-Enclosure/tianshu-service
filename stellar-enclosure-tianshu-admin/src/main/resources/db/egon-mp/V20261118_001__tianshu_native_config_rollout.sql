ALTER TABLE public.ddc_publish_task_t0
    ADD COLUMN platform_evidence jsonb,
    ADD CONSTRAINT ck_ddc_publish_task_platform_evidence CHECK (
        platform_evidence IS NULL OR CASE
            WHEN jsonb_typeof(platform_evidence) = 'object'
                 AND platform_evidence -> 'schemaVersion' = '1'::jsonb
                 AND jsonb_typeof(platform_evidence -> 'targets') = 'array'
            THEN jsonb_array_length(platform_evidence -> 'targets') <= 32
                AND octet_length(convert_to(platform_evidence::text, 'UTF8')) <= 65536
            ELSE false
        END
    );

CREATE SEQUENCE public.ddc_config_workload_observation_epoch AS bigint MINVALUE 1 NO CYCLE;

CREATE TABLE public.ddc_config_workload_observation_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(128) NOT NULL,
    create_time timestamptz NOT NULL,
    update_user_id varchar(128) NOT NULL,
    update_time timestamptz NOT NULL,
    deleted_at timestamp NULL,
    version bigint NOT NULL CHECK (version >= 0),
    target_binding_id varchar(128) NOT NULL,
    target_revision bigint NOT NULL CHECK (target_revision > 0),
    cluster_id varchar(128) NOT NULL,
    k8s_namespace varchar(63) NOT NULL,
    workload_uid varchar(128) NOT NULL,
    observed_at timestamptz,
    valid_until timestamptz,
    recorded_at timestamptz NOT NULL,
    view_json jsonb NOT NULL,
    CONSTRAINT pk_ddc_config_workload_observation PRIMARY KEY (id),
    CONSTRAINT uk_ddc_config_workload_observation_scope UNIQUE NULLS NOT DISTINCT
        (tenant_id, target_binding_id, target_revision, cluster_id, k8s_namespace, workload_uid, deleted_at),
    CONSTRAINT ck_ddc_config_workload_observation_view CHECK (
        jsonb_typeof(view_json) = 'object' AND octet_length(convert_to(view_json::text, 'UTF8')) <= 1048576
    ),
    CONSTRAINT ck_ddc_config_workload_observation_expiry CHECK (
        (observed_at IS NULL AND valid_until IS NULL) OR
        (observed_at IS NOT NULL AND observed_at <= recorded_at
            AND (valid_until IS NULL OR valid_until > observed_at))
    )
);
