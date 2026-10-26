-- Permission metadata sources and independent human translation publication.
-- Owner: tianshu-service. Intended deployment: an explicitly initialized clean PostgreSQL target.
-- All JSON IDs/revisions are decimal strings. The corresponding database columns use bigint.
-- No Session, token vault, RBAC active pointer, role grant, or Redis state is introduced.

CREATE TABLE ddc_metadata_scope_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    biz_code varchar(128) NOT NULL,
    namespace_id bigint NOT NULL CHECK (namespace_id > 0),
    namespace_code varchar(128) NOT NULL,
    env_code varchar(32) NOT NULL,
    app_id bigint NOT NULL CHECK (app_id > 0),
    app_code varchar(128) NOT NULL,
    binding_id bigint NOT NULL CHECK (binding_id > 0),
    CONSTRAINT pk_metadata_scope PRIMARY KEY (id),
    CONSTRAINT uk_metadata_scope_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_metadata_scope_catalog UNIQUE (tenant_id, catalog_scope_id),
    CONSTRAINT uk_metadata_scope_target UNIQUE (tenant_id, biz_code, namespace_code, env_code, app_code),
    CONSTRAINT uk_metadata_scope_page_target UNIQUE (tenant_id, catalog_scope_id, biz_code, namespace_id, env_code, app_code),
    CONSTRAINT fk_metadata_scope_namespace FOREIGN KEY (tenant_id, namespace_id)
        REFERENCES ddc_namespace_t0 (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_metadata_scope_app FOREIGN KEY (tenant_id, app_id)
        REFERENCES ddc_app_t0 (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_metadata_scope_binding FOREIGN KEY (tenant_id, binding_id)
        REFERENCES ddc_namespace_env_app_t0 (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_metadata_scope_identity CHECK (btrim(biz_code) <> '' AND btrim(namespace_code) <> ''
        AND btrim(env_code) <> '' AND btrim(app_code) <> ''),
    CONSTRAINT ck_metadata_scope_immutable CHECK (version = 0 AND deleted_at IS NULL)
);

-- B0 checks exact business/app/namespace/environment/binding identity and enabled status
-- while holding the same scope rows used by DdcI18nScopeValidator. Foreign IDs remain local to META.
-- catalog_scope_id is an authenticated RBAC projection; there is intentionally no cross-database FK.

CREATE TABLE ddc_source_import_grant_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    producer_id varchar(128) NOT NULL,
    issuer_uri varchar(2048) NOT NULL,
    client_id varchar(200) NOT NULL,
    resource_uri varchar(2048) NOT NULL,
    principal_binding_digest char(64) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    CONSTRAINT pk_source_import_grant PRIMARY KEY (id),
    CONSTRAINT uk_source_import_grant_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_source_import_grant_target UNIQUE (tenant_id, catalog_scope_id, producer_id, id),
    CONSTRAINT uk_source_import_grant_identity UNIQUE (tenant_id, catalog_scope_id, producer_id, principal_binding_digest),
    CONSTRAINT fk_source_import_grant_scope FOREIGN KEY (tenant_id, catalog_scope_id)
        REFERENCES ddc_metadata_scope_t0 (tenant_id, catalog_scope_id) ON DELETE RESTRICT,
    CONSTRAINT ck_source_import_grant_digest CHECK (principal_binding_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_source_import_grant_strings CHECK (btrim(producer_id) <> '' AND btrim(issuer_uri) <> ''
        AND btrim(client_id) <> '' AND btrim(resource_uri) <> ''),
    CONSTRAINT ck_source_import_grant_live CHECK (deleted_at IS NULL)
);

-- The digest indexes long issuer/resource URIs without exceeding PostgreSQL btree row limits.
-- Authorization MUST compare the exact stored issuer/client/resource/producer after digest lookup.
-- Request and receipt reads recheck SERVICE identity, endpoint scope, grant enabled/version and live scope.
-- SELECT FOR SHARE on the grant serializes the write boundary with enabled/version changes.

CREATE TABLE ddc_i18n_source_version_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    producer_id varchar(128) NOT NULL,
    schema_version varchar(32) NOT NULL,
    source_version char(64) NOT NULL,
    policy_digest char(64) NOT NULL,
    metadata_digest char(64) NOT NULL,
    manifest_digest char(64) NOT NULL,
    projection_digest char(64) NOT NULL,
    canonical_manifest_json text NOT NULL,
    resource_id_map_json text NOT NULL,
    permission_id_map_json text NOT NULL,
    resource_count integer NOT NULL CHECK (resource_count >= 0),
    permission_count integer NOT NULL CHECK (permission_count >= 0),
    message_count integer NOT NULL CHECK (message_count >= 0),
    CONSTRAINT pk_i18n_source_version PRIMARY KEY (id),
    CONSTRAINT uk_i18n_source_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_i18n_source_version_identity UNIQUE (tenant_id, catalog_scope_id, producer_id, source_version),
    CONSTRAINT uk_i18n_source_version_owner UNIQUE (tenant_id, catalog_scope_id, id, producer_id, source_version),
    CONSTRAINT uk_i18n_source_version_receipt UNIQUE (tenant_id, catalog_scope_id, id, producer_id, source_version, policy_digest, metadata_digest),
    CONSTRAINT fk_i18n_source_version_scope FOREIGN KEY (tenant_id, catalog_scope_id)
        REFERENCES ddc_metadata_scope_t0 (tenant_id, catalog_scope_id) ON DELETE RESTRICT,
    CONSTRAINT ck_i18n_source_version_digests CHECK (source_version = manifest_digest
        AND source_version ~ '^[0-9a-f]{64}$' AND policy_digest ~ '^[0-9a-f]{64}$'
        AND metadata_digest ~ '^[0-9a-f]{64}$' AND projection_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_i18n_source_version_json CHECK (
        jsonb_typeof(canonical_manifest_json::jsonb) = 'object' AND octet_length(canonical_manifest_json) <= 16777216
        AND jsonb_typeof(resource_id_map_json::jsonb) = 'object' AND octet_length(resource_id_map_json) <= 2097152
        AND jsonb_typeof(permission_id_map_json::jsonb) = 'object' AND octet_length(permission_id_map_json) <= 2097152),
    CONSTRAINT ck_i18n_source_version_immutable CHECK (version = 0 AND deleted_at IS NULL)
);

-- producerVersion is a registration revision, not the immutable content identity.
-- Different authenticated registration operations may reference the same exact source content;
-- their producerVersion is recorded in receipts and immutable activation members.

CREATE TABLE ddc_resource_metadata_projection_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    source_id bigint NOT NULL CHECK (source_id > 0),
    producer_id varchar(128) NOT NULL,
    source_version char(64) NOT NULL,
    metadata_kind varchar(16) NOT NULL,
    definition_key varchar(200) NOT NULL,
    resource_id bigint,
    permission_id bigint,
    resource_type varchar(16),
    definition_lifecycle varchar(16) NOT NULL,
    page_code varchar(128) NOT NULL,
    label_key varchar(128) NOT NULL,
    description_key varchar(128),
    target_json text NOT NULL,
    policy_json text NOT NULL,
    i18n_refs_json text NOT NULL,
    referenced_permission_codes_json text NOT NULL,
    CONSTRAINT pk_resource_metadata_projection PRIMARY KEY (id),
    CONSTRAINT uk_resource_metadata_projection_tenant UNIQUE (tenant_id, id),
    CONSTRAINT uk_resource_metadata_projection_key UNIQUE (tenant_id, source_id, metadata_kind, definition_key),
    CONSTRAINT fk_resource_metadata_projection_source FOREIGN KEY
        (tenant_id, catalog_scope_id, source_id, producer_id, source_version)
        REFERENCES ddc_i18n_source_version_t0 (tenant_id, catalog_scope_id, id, producer_id, source_version) ON DELETE RESTRICT,
    CONSTRAINT ck_resource_metadata_projection_kind CHECK (
        (metadata_kind = 'RESOURCE' AND resource_id IS NOT NULL AND resource_id > 0 AND permission_id IS NULL
            AND resource_type IS NOT NULL AND resource_type IN ('APP','MENU','PAGE','COMPONENT','BUTTON','API','FIELD'))
        OR (metadata_kind = 'PERMISSION' AND permission_id IS NOT NULL AND permission_id > 0 AND resource_id IS NULL AND resource_type IS NULL
            AND length(definition_key) <= 128)),
    CONSTRAINT ck_resource_metadata_projection_lifecycle CHECK (definition_lifecycle IN ('ACTIVE','DEPRECATED')),
    CONSTRAINT ck_resource_metadata_projection_json CHECK (
        jsonb_typeof(target_json::jsonb) = 'object' AND octet_length(target_json) <= 65536
        AND jsonb_typeof(policy_json::jsonb) = 'object' AND octet_length(policy_json) <= 262144
        AND jsonb_typeof(i18n_refs_json::jsonb) = 'object' AND octet_length(i18n_refs_json) <= 65536
        AND jsonb_typeof(referenced_permission_codes_json::jsonb) = 'array'
        AND octet_length(referenced_permission_codes_json) <= 262144),
    CONSTRAINT ck_resource_metadata_projection_immutable CHECK (version = 0 AND deleted_at IS NULL)
);

CREATE INDEX ix_resource_metadata_scope_type ON ddc_resource_metadata_projection_t0
    (tenant_id, catalog_scope_id, resource_type, source_id, definition_key);
CREATE INDEX ix_resource_metadata_permission_refs ON ddc_resource_metadata_projection_t0
    USING gin ((referenced_permission_codes_json::jsonb));
CREATE UNIQUE INDEX uk_resource_metadata_key_case ON ddc_resource_metadata_projection_t0
    (tenant_id, source_id, metadata_kind, lower(definition_key));
-- definition_lifecycle is a historical projection, never a mutable authorization decision.
-- Current RBAC disable/retire state always overrides the activated definition at authorization.

CREATE TABLE ddc_i18n_source_message_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    source_id bigint NOT NULL CHECK (source_id > 0),
    producer_id varchar(128) NOT NULL,
    source_version char(64) NOT NULL,
    page_code varchar(128) NOT NULL,
    message_key varchar(128) NOT NULL,
    default_locale varchar(64) NOT NULL,
    source_hash char(64) NOT NULL,
    defaults_json text NOT NULL,
    placeholders_json text NOT NULL,
    CONSTRAINT pk_i18n_source_message PRIMARY KEY (id),
    CONSTRAINT uk_i18n_source_message_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_i18n_source_message_key UNIQUE (tenant_id, source_id, page_code, message_key),
    CONSTRAINT fk_i18n_source_message_source FOREIGN KEY
        (tenant_id, catalog_scope_id, source_id, producer_id, source_version)
        REFERENCES ddc_i18n_source_version_t0 (tenant_id, catalog_scope_id, id, producer_id, source_version) ON DELETE RESTRICT,
    CONSTRAINT ck_i18n_source_message_key CHECK (page_code ~ '^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$'
        AND message_key ~ '^[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*$'),
    CONSTRAINT ck_i18n_source_message_hash CHECK (source_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_i18n_source_message_defaults CHECK (jsonb_typeof(defaults_json::jsonb) = 'object'
        AND (defaults_json::jsonb ? default_locale)
        AND jsonb_typeof(defaults_json::jsonb -> default_locale) = 'string'
        AND octet_length(defaults_json) <= 2097152
        AND jsonb_typeof(placeholders_json::jsonb) = 'array' AND octet_length(placeholders_json) <= 65536),
    CONSTRAINT ck_i18n_source_message_immutable CHECK (version = 0 AND deleted_at IS NULL)
);

CREATE INDEX ix_i18n_source_message_page ON ddc_i18n_source_message_t0
    (tenant_id, catalog_scope_id, page_code, source_id);
CREATE UNIQUE INDEX uk_i18n_source_message_key_case ON ddc_i18n_source_message_t0
    (tenant_id, source_id, lower(page_code), lower(message_key));

CREATE TABLE ddc_i18n_import_receipt_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    operation_id varchar(128) NOT NULL,
    producer_id varchar(128) NOT NULL,
    producer_version bigint NOT NULL CHECK (producer_version > 0),
    source_id bigint NOT NULL CHECK (source_id > 0),
    source_version char(64) NOT NULL,
    manifest_digest char(64) NOT NULL,
    policy_digest char(64) NOT NULL,
    metadata_digest char(64) NOT NULL,
    request_digest char(64) NOT NULL,
    grant_id bigint NOT NULL CHECK (grant_id > 0),
    grant_version bigint NOT NULL CHECK (grant_version >= 0),
    publisher_issuer varchar(2048) NOT NULL,
    publisher_client_id varchar(200) NOT NULL,
    response_json text NOT NULL,
    CONSTRAINT pk_i18n_import_receipt PRIMARY KEY (id),
    CONSTRAINT uk_i18n_import_receipt_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_i18n_import_receipt_operation UNIQUE (tenant_id, catalog_scope_id, producer_id, operation_id),
    CONSTRAINT fk_i18n_import_receipt_source FOREIGN KEY
        (tenant_id, catalog_scope_id, source_id, producer_id, source_version, policy_digest, metadata_digest)
        REFERENCES ddc_i18n_source_version_t0 (tenant_id, catalog_scope_id, id, producer_id, source_version, policy_digest, metadata_digest) ON DELETE RESTRICT,
    CONSTRAINT fk_i18n_import_receipt_grant FOREIGN KEY (tenant_id, catalog_scope_id, producer_id, grant_id)
        REFERENCES ddc_source_import_grant_t0 (tenant_id, catalog_scope_id, producer_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_i18n_import_receipt_digests CHECK (source_version = manifest_digest
        AND source_version ~ '^[0-9a-f]{64}$' AND policy_digest ~ '^[0-9a-f]{64}$'
        AND metadata_digest ~ '^[0-9a-f]{64}$' AND request_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_i18n_import_receipt_json CHECK (jsonb_typeof(response_json::jsonb) = 'object'
        AND octet_length(response_json) <= 2097152),
    CONSTRAINT ck_i18n_import_receipt_immutable CHECK (version = 0 AND deleted_at IS NULL)
);

CREATE INDEX ix_i18n_import_receipt_source ON ddc_i18n_import_receipt_t0
    (tenant_id, catalog_scope_id, producer_id, producer_version);
-- Only committed success creates a receipt. API-010 never returns credentials or authorizes a stale grant.
-- request_digest includes the typed Manifest, assigned ID maps, complete target and producerVersion.
-- Reusing operationId with different content conflicts; same-content retry returns response_json unchanged.

CREATE TABLE ddc_catalog_activation_projection_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    activation_version bigint NOT NULL CHECK (activation_version > 0),
    descriptor_digest char(64) NOT NULL,
    descriptor_json text NOT NULL,
    producer_count integer NOT NULL CHECK (producer_count > 0),
    authority_resource_uri varchar(2048) NOT NULL,
    CONSTRAINT pk_catalog_activation_projection PRIMARY KEY (id),
    CONSTRAINT uk_catalog_activation_projection_tenant UNIQUE (tenant_id, id),
    CONSTRAINT uk_catalog_activation_projection_version UNIQUE (tenant_id, catalog_scope_id, activation_version),
    CONSTRAINT uk_catalog_activation_projection_owner UNIQUE (tenant_id, catalog_scope_id, id),
    CONSTRAINT fk_catalog_activation_projection_scope FOREIGN KEY (tenant_id, catalog_scope_id)
        REFERENCES ddc_metadata_scope_t0 (tenant_id, catalog_scope_id) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_activation_projection_digest CHECK (descriptor_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_catalog_activation_projection_json CHECK (jsonb_typeof(descriptor_json::jsonb) = 'object'
        AND octet_length(descriptor_json) <= 16777216),
    CONSTRAINT ck_catalog_activation_projection_immutable CHECK (version = 0 AND deleted_at IS NULL)
);

CREATE TABLE ddc_catalog_activation_producer_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    activation_projection_id bigint NOT NULL CHECK (activation_projection_id > 0),
    producer_id varchar(128) NOT NULL,
    producer_version bigint NOT NULL CHECK (producer_version > 0),
    source_id bigint NOT NULL CHECK (source_id > 0),
    source_version char(64) NOT NULL,
    policy_digest char(64) NOT NULL,
    metadata_digest char(64) NOT NULL,
    CONSTRAINT pk_catalog_activation_producer PRIMARY KEY (id),
    CONSTRAINT uk_catalog_activation_producer_tenant UNIQUE (tenant_id, id),
    CONSTRAINT uk_catalog_activation_producer_member UNIQUE (tenant_id, activation_projection_id, producer_id),
    CONSTRAINT fk_catalog_activation_producer_descriptor FOREIGN KEY (tenant_id, catalog_scope_id, activation_projection_id)
        REFERENCES ddc_catalog_activation_projection_t0 (tenant_id, catalog_scope_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_catalog_activation_producer_source FOREIGN KEY
        (tenant_id, catalog_scope_id, source_id, producer_id, source_version, policy_digest, metadata_digest)
        REFERENCES ddc_i18n_source_version_t0 (tenant_id, catalog_scope_id, id, producer_id, source_version, policy_digest, metadata_digest) ON DELETE RESTRICT,
    CONSTRAINT ck_catalog_activation_producer_digest CHECK (policy_digest ~ '^[0-9a-f]{64}$' AND metadata_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_catalog_activation_producer_immutable CHECK (version = 0 AND deleted_at IS NULL)
);

-- Descriptor + all members are committed together after authenticated API-006 validation.
-- No current/active/latest flag exists in META. Runtime requests select the RBAC-authorized exact version.
-- The selected set must have one owner per resource, permission and page/message Key;
-- candidate versions or historical owner transfers may coexist without changing the selected set.

-- Reuse the existing page table exclusively as human draft/current publication state.
-- This alteration intentionally requires a clean target: no legacy content migration or data deletion.
ALTER TABLE ddc_i18n_page_t0
    ADD COLUMN catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    ADD COLUMN draft_activation_version bigint NOT NULL CHECK (draft_activation_version > 0),
    ADD COLUMN draft_source_hashes text NOT NULL DEFAULT '{}',
    ADD COLUMN current_override_id bigint,
    ADD COLUMN published_source_hashes text,
    ADD COLUMN published_draft_revision bigint,
    ADD COLUMN published_operation_id varchar(128),
    ADD COLUMN published_at timestamptz(6),
    ADD CONSTRAINT uk_i18n_page_catalog_key UNIQUE (tenant_id, catalog_scope_id, page_code),
    ADD CONSTRAINT uk_i18n_page_catalog_id UNIQUE (tenant_id, catalog_scope_id, id),
    ADD CONSTRAINT uk_i18n_page_catalog_identity UNIQUE (tenant_id, catalog_scope_id, id, page_code),
    ADD CONSTRAINT fk_i18n_page_metadata_scope FOREIGN KEY
        (tenant_id, catalog_scope_id, biz_code, namespace_id, env_code, app_code)
        REFERENCES ddc_metadata_scope_t0 (tenant_id, catalog_scope_id, biz_code, namespace_id, env_code, app_code) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_i18n_page_draft_activation FOREIGN KEY (tenant_id, catalog_scope_id, draft_activation_version)
        REFERENCES ddc_catalog_activation_projection_t0 (tenant_id, catalog_scope_id, activation_version) ON DELETE RESTRICT,
    ADD CONSTRAINT ck_i18n_page_source_hashes CHECK (jsonb_typeof(draft_source_hashes::jsonb) = 'object'
        AND octet_length(draft_source_hashes) <= 2097152
        AND (published_source_hashes IS NULL OR (jsonb_typeof(published_source_hashes::jsonb) = 'object'
            AND octet_length(published_source_hashes) <= 2097152))),
    ADD CONSTRAINT ck_i18n_page_publication_context CHECK (
        (published_version IS NULL AND current_override_id IS NULL AND published_source_hashes IS NULL
            AND published_draft_revision IS NULL AND published_operation_id IS NULL AND published_at IS NULL)
        OR (published_version IS NOT NULL AND published_version > 0 AND current_override_id IS NOT NULL
            AND current_override_id > 0 AND published_source_hashes IS NOT NULL
            AND published_draft_revision IS NOT NULL AND published_draft_revision >= 0
            AND published_operation_id IS NOT NULL AND published_at IS NOT NULL)),
    ADD CONSTRAINT ck_i18n_page_managed_live CHECK (deleted_at IS NULL);
-- default_locale/published_default_locale are review context. Source-selected default locale owns runtime fallback.
-- The existing optimistic version changes only on human save/publish. source-import does not write this table.

CREATE TABLE ddc_i18n_published_override_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    page_id bigint NOT NULL CHECK (page_id > 0),
    page_code varchar(128) NOT NULL,
    publication_revision bigint NOT NULL CHECK (publication_revision > 0),
    draft_revision bigint NOT NULL CHECK (draft_revision >= 0),
    based_on_activation_version bigint NOT NULL CHECK (based_on_activation_version > 0),
    publish_operation_id varchar(128) NOT NULL,
    request_digest char(64) NOT NULL,
    default_locale varchar(64) NOT NULL,
    translations_json text NOT NULL,
    reviewed_source_hashes_json text NOT NULL,
    published_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_i18n_published_override PRIMARY KEY (id),
    CONSTRAINT uk_i18n_published_override_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_i18n_published_override_revision UNIQUE (tenant_id, catalog_scope_id, page_id, publication_revision),
    CONSTRAINT uk_i18n_published_override_operation UNIQUE (tenant_id, catalog_scope_id, page_id, publish_operation_id),
    CONSTRAINT uk_i18n_published_override_head UNIQUE (tenant_id, catalog_scope_id, page_id, id, publication_revision),
    CONSTRAINT fk_i18n_published_override_page FOREIGN KEY (tenant_id, catalog_scope_id, page_id, page_code)
        REFERENCES ddc_i18n_page_t0 (tenant_id, catalog_scope_id, id, page_code) ON DELETE RESTRICT,
    CONSTRAINT fk_i18n_published_override_activation FOREIGN KEY (tenant_id, catalog_scope_id, based_on_activation_version)
        REFERENCES ddc_catalog_activation_projection_t0 (tenant_id, catalog_scope_id, activation_version) ON DELETE RESTRICT,
    CONSTRAINT ck_i18n_published_override_digest CHECK (request_digest ~ '^[0-9a-f]{64}$'),
    CONSTRAINT ck_i18n_published_override_json CHECK (jsonb_typeof(translations_json::jsonb) = 'object'
        AND octet_length(translations_json) <= 2097152
        AND jsonb_typeof(reviewed_source_hashes_json::jsonb) = 'object'
        AND octet_length(reviewed_source_hashes_json) <= 2097152),
    CONSTRAINT ck_i18n_published_override_immutable CHECK (version = 0 AND deleted_at IS NULL)
);

ALTER TABLE ddc_i18n_page_t0
    ADD CONSTRAINT fk_i18n_page_current_override FOREIGN KEY
        (tenant_id, catalog_scope_id, id, current_override_id, published_version)
        REFERENCES ddc_i18n_published_override_t0 (tenant_id, catalog_scope_id, page_id, id, publication_revision)
        ON DELETE RESTRICT;
CREATE INDEX ix_i18n_published_override_history ON ddc_i18n_published_override_t0
    (tenant_id, catalog_scope_id, page_id, publication_revision DESC);
-- Publish locks the human page/CAS, inserts an immutable full override snapshot, and updates the page
-- current_override_id/publication fields in the same transaction. An empty override map is a valid clear.
-- A failed/unknown publish can query its stable publish_operation_id before retrying; source imports never publish.

-- Immutable rows are insert-only, including their base lifecycle columns.
-- No retention/deletion job is introduced in this scope; historical versions remain available for rollback/audit.
CREATE FUNCTION ddc_metadata_reject_mutation() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
    RAISE EXCEPTION 'TIANSHU_METADATA_IMMUTABLE: %', TG_TABLE_NAME USING ERRCODE = '55000';
END;
$body$;

CREATE TRIGGER tr_ddc_metadata_scope_immutable
    BEFORE UPDATE OR DELETE ON ddc_metadata_scope_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE TRIGGER tr_ddc_i18n_source_version_immutable
    BEFORE UPDATE OR DELETE ON ddc_i18n_source_version_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE TRIGGER tr_ddc_resource_metadata_projection_immutable
    BEFORE UPDATE OR DELETE ON ddc_resource_metadata_projection_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE TRIGGER tr_ddc_i18n_source_message_immutable
    BEFORE UPDATE OR DELETE ON ddc_i18n_source_message_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE TRIGGER tr_ddc_i18n_import_receipt_immutable
    BEFORE UPDATE OR DELETE ON ddc_i18n_import_receipt_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE TRIGGER tr_ddc_catalog_activation_projection_immutable
    BEFORE UPDATE OR DELETE ON ddc_catalog_activation_projection_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE TRIGGER tr_ddc_catalog_activation_producer_immutable
    BEFORE UPDATE OR DELETE ON ddc_catalog_activation_producer_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE TRIGGER tr_ddc_i18n_published_override_immutable
    BEFORE UPDATE OR DELETE ON ddc_i18n_published_override_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE FUNCTION ddc_source_import_grant_guard() RETURNS trigger LANGUAGE plpgsql AS $body$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'TIANSHU_SOURCE_IMPORT_GRANT_DELETE_FORBIDDEN' USING ERRCODE = '55000';
    END IF;
    IF (NEW.id, NEW.tenant_id, NEW.catalog_scope_id, NEW.producer_id, NEW.issuer_uri, NEW.client_id,
        NEW.resource_uri, NEW.principal_binding_digest, NEW.create_user_id, NEW.create_time)
        IS DISTINCT FROM
       (OLD.id, OLD.tenant_id, OLD.catalog_scope_id, OLD.producer_id, OLD.issuer_uri, OLD.client_id,
        OLD.resource_uri, OLD.principal_binding_digest, OLD.create_user_id, OLD.create_time)
        OR NEW.version <> OLD.version + 1 THEN
        RAISE EXCEPTION 'TIANSHU_SOURCE_IMPORT_GRANT_IDENTITY_OR_VERSION_CONFLICT' USING ERRCODE = '55000';
    END IF;
    RETURN NEW;
END;
$body$;
CREATE TRIGGER tr_source_import_grant_guard
    BEFORE UPDATE OR DELETE ON ddc_source_import_grant_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_source_import_grant_guard();

-- Human publication must point at exactly the immutable snapshot copied in the same transaction.
-- Draft-only saves cannot alter publication content, and publication cannot smuggle unsaved edits.
CREATE FUNCTION ddc_i18n_page_publication_guard() RETURNS trigger LANGUAGE plpgsql AS $body$
DECLARE
    publication ddc_i18n_published_override_t0%ROWTYPE;
BEGIN
    IF TG_OP = 'UPDATE' THEN
        IF (NEW.id, NEW.tenant_id, NEW.catalog_scope_id, NEW.biz_code, NEW.namespace_id, NEW.env_code,
            NEW.app_code, NEW.page_code, NEW.create_user_id, NEW.create_time)
            IS DISTINCT FROM
           (OLD.id, OLD.tenant_id, OLD.catalog_scope_id, OLD.biz_code, OLD.namespace_id, OLD.env_code,
            OLD.app_code, OLD.page_code, OLD.create_user_id, OLD.create_time)
            OR NEW.version <> OLD.version + 1 THEN
            RAISE EXCEPTION 'TIANSHU_I18N_PAGE_IDENTITY_OR_VERSION_CONFLICT' USING ERRCODE = '55000';
        END IF;
        IF OLD.current_override_id IS NOT NULL AND NEW.current_override_id IS NULL THEN
            RAISE EXCEPTION 'TIANSHU_I18N_PUBLICATION_CLEAR_REQUIRES_EMPTY_SNAPSHOT' USING ERRCODE = '55000';
        END IF;
    END IF;
    IF NEW.current_override_id IS NOT NULL THEN
        SELECT * INTO publication FROM ddc_i18n_published_override_t0
            WHERE tenant_id = NEW.tenant_id AND catalog_scope_id = NEW.catalog_scope_id
              AND page_id = NEW.id AND id = NEW.current_override_id;
        IF NOT FOUND OR publication.publication_revision IS DISTINCT FROM NEW.published_version
            OR publication.translations_json::jsonb IS DISTINCT FROM NEW.published_translations::jsonb
            OR publication.default_locale IS DISTINCT FROM NEW.published_default_locale
            OR publication.reviewed_source_hashes_json::jsonb IS DISTINCT FROM NEW.published_source_hashes::jsonb
            OR publication.draft_revision IS DISTINCT FROM NEW.published_draft_revision
            OR publication.publish_operation_id IS DISTINCT FROM NEW.published_operation_id
            OR publication.published_at IS DISTINCT FROM NEW.published_at THEN
            RAISE EXCEPTION 'TIANSHU_I18N_PUBLICATION_SNAPSHOT_MISMATCH' USING ERRCODE = '55000';
        END IF;
        IF TG_OP = 'UPDATE' AND NEW.current_override_id IS DISTINCT FROM OLD.current_override_id THEN
            IF NEW.published_version <> COALESCE(OLD.published_version, 0) + 1
                OR publication.draft_revision <> OLD.version
                OR publication.based_on_activation_version <> OLD.draft_activation_version
                OR publication.default_locale IS DISTINCT FROM OLD.default_locale
                OR publication.translations_json::jsonb IS DISTINCT FROM OLD.draft_translations::jsonb
                OR publication.reviewed_source_hashes_json::jsonb IS DISTINCT FROM OLD.draft_source_hashes::jsonb
                OR NEW.draft_translations::jsonb IS DISTINCT FROM OLD.draft_translations::jsonb
                OR NEW.draft_source_hashes::jsonb IS DISTINCT FROM OLD.draft_source_hashes::jsonb
                OR NEW.draft_activation_version IS DISTINCT FROM OLD.draft_activation_version
                OR NEW.default_locale IS DISTINCT FROM OLD.default_locale THEN
                RAISE EXCEPTION 'TIANSHU_I18N_PUBLICATION_REQUIRES_CURRENT_DRAFT' USING ERRCODE = '55000';
            END IF;
        END IF;
    END IF;
    RETURN NEW;
END;
$body$;
CREATE TRIGGER tr_i18n_page_publication_guard
    BEFORE INSERT OR UPDATE ON ddc_i18n_page_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_i18n_page_publication_guard();
