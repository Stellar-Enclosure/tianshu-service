-- Tenant-scoped page language drafts and atomic publication snapshots.
CREATE TABLE ddc_i18n_page_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    biz_code varchar(128) NOT NULL,
    namespace_id bigint NOT NULL,
    env_code varchar(32) NOT NULL,
    app_code varchar(128) NOT NULL,
    page_code varchar(128) NOT NULL,
    name varchar(128) NOT NULL,
    default_locale varchar(64) NOT NULL,
    draft_translations text NOT NULL,
    published_default_locale varchar(64),
    published_translations text,
    published_version bigint,
    CONSTRAINT pk_i18n_page PRIMARY KEY (id),
    CONSTRAINT uk_i18n_page_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_i18n_page_namespace FOREIGN KEY (tenant_id, namespace_id)
        REFERENCES ddc_namespace_t0 (tenant_id, id),
    CONSTRAINT ck_i18n_page_code CHECK (page_code ~ '^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$'),
    CONSTRAINT ck_i18n_page_draft CHECK (jsonb_typeof(draft_translations::jsonb) = 'object'
        AND octet_length(draft_translations) <= 2097152),
    CONSTRAINT ck_i18n_page_published CHECK (
        (published_version IS NULL AND published_default_locale IS NULL AND published_translations IS NULL)
        OR (published_version IS NOT NULL AND published_version > 0 AND published_default_locale IS NOT NULL AND published_translations IS NOT NULL
            AND jsonb_typeof(published_translations::jsonb) = 'object'
            AND octet_length(published_translations) <= 2097152))
);
CREATE UNIQUE INDEX uk_i18n_page_lifecycle ON ddc_i18n_page_t0
    (tenant_id, biz_code, namespace_id, env_code, app_code, page_code, deleted_at);
CREATE UNIQUE INDEX uk_i18n_page_live ON ddc_i18n_page_t0
    (tenant_id, biz_code, namespace_id, env_code, app_code, page_code) WHERE deleted_at IS NULL;
