-- SI unified governance schema: AGENT, MCP, PROMPT, SKILL catalog, immutable snapshots,
-- app bindings, directed references (DAG + Tianji process), and pgvector HNSW + FTS hybrid search.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ddc_si_resource_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    biz_code varchar(128) NOT NULL,
    namespace_id bigint NOT NULL CHECK (namespace_id > 0),
    env_code varchar(32) NOT NULL,
    app_code varchar(128) NOT NULL,
    kind varchar(16) NOT NULL CHECK (kind IN ('AGENT', 'MCP', 'PROMPT', 'SKILL')),
    resource_key varchar(128) COLLATE "C" NOT NULL,
    display_name varchar(128) NOT NULL,
    description varchar(2048) NOT NULL DEFAULT '',
    tags_json text NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(tags_json::jsonb) = 'array' AND octet_length(tags_json) <= 65536),
    lifecycle_status varchar(16) NOT NULL CHECK (lifecycle_status IN ('DRAFT', 'PUBLISHED', 'DEPRECATED', 'ARCHIVED')),
    source_type varchar(32) NOT NULL CHECK (source_type IN ('CONSOLE', 'AUTO_REPORTED', 'GITOPS')),
    spec_json text NOT NULL CHECK (jsonb_typeof(spec_json::jsonb) = 'object' AND octet_length(spec_json) <= 1048576),
    content_sha256 char(64) NOT NULL CHECK (content_sha256 ~ '^[0-9a-f]{64}$'),
    last_version_no bigint NOT NULL DEFAULT 0 CHECK (last_version_no >= 0),
    current_version_id bigint,
    published_time timestamptz(6),
    search_tsv tsvector,
    CONSTRAINT pk_si_resource PRIMARY KEY (id),
    CONSTRAINT uk_si_resource_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_si_resource_kind UNIQUE (tenant_id, id, kind),
    CONSTRAINT fk_si_resource_namespace FOREIGN KEY (tenant_id, namespace_id)
        REFERENCES ddc_namespace_t0 (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT ck_si_resource_key CHECK (
        octet_length(resource_key) BETWEEN 1 AND 128
        AND resource_key ~ '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$')
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_si_resource_live
    ON ddc_si_resource_t0 (tenant_id, biz_code, namespace_id, env_code, app_code, kind, resource_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_si_resource_scope
    ON ddc_si_resource_t0 (tenant_id, env_code, namespace_id, kind, lifecycle_status, update_time DESC, id DESC)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_si_resource_tags_gin
    ON ddc_si_resource_t0 USING gin ((tags_json::jsonb))
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_si_resource_fts
    ON ddc_si_resource_t0 USING gin (search_tsv)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS ddc_si_version_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    resource_id bigint NOT NULL CHECK (resource_id > 0),
    version_no bigint NOT NULL CHECK (version_no > 0),
    kind varchar(16) NOT NULL CHECK (kind IN ('AGENT', 'MCP', 'PROMPT', 'SKILL')),
    spec_json text NOT NULL CHECK (jsonb_typeof(spec_json::jsonb) = 'object' AND octet_length(spec_json) <= 1048576),
    digest_sha256 char(64) NOT NULL CHECK (digest_sha256 ~ '^[0-9a-f]{64}$'),
    source_env_code varchar(32),
    source_version_no bigint CHECK (source_version_no IS NULL OR source_version_no > 0),
    change_note varchar(512) NOT NULL DEFAULT '',
    index_status varchar(16) NOT NULL DEFAULT 'PENDING' CHECK (index_status IN ('PENDING', 'READY', 'FAILED')),
    published_time timestamptz(6) NOT NULL,
    indexed_time timestamptz(6),
    CONSTRAINT pk_si_version PRIMARY KEY (id),
    CONSTRAINT uk_si_version_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT uk_si_version_owner UNIQUE (tenant_id, resource_id, id),
    CONSTRAINT uk_si_version_no UNIQUE (tenant_id, resource_id, version_no),
    CONSTRAINT fk_si_version_resource FOREIGN KEY (tenant_id, resource_id, kind)
        REFERENCES ddc_si_resource_t0 (tenant_id, id, kind) ON DELETE RESTRICT
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 'ddc_si_resource_t0'::regclass AND conname = 'fk_si_resource_current_version') THEN
        ALTER TABLE ddc_si_resource_t0
            ADD CONSTRAINT fk_si_resource_current_version
            FOREIGN KEY (tenant_id, id, current_version_id)
            REFERENCES ddc_si_version_t0 (tenant_id, resource_id, id)
            ON DELETE RESTRICT DEFERRABLE INITIALLY DEFERRED;
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS ddc_si_binding_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    biz_code varchar(128) NOT NULL,
    namespace_id bigint NOT NULL CHECK (namespace_id > 0),
    env_code varchar(32) NOT NULL,
    app_code varchar(128) NOT NULL,
    kind varchar(16) NOT NULL CHECK (kind IN ('AGENT', 'MCP', 'PROMPT', 'SKILL')),
    resource_id bigint NOT NULL CHECK (resource_id > 0),
    resource_key varchar(128) COLLATE "C" NOT NULL,
    pinned_version_no bigint CHECK (pinned_version_no IS NULL OR pinned_version_no > 0),
    binding_status varchar(16) NOT NULL CHECK (binding_status IN ('ACTIVE', 'INACTIVE')),
    bound_time timestamptz(6) NOT NULL,
    CONSTRAINT pk_si_binding PRIMARY KEY (id),
    CONSTRAINT uk_si_binding_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_si_binding_namespace FOREIGN KEY (tenant_id, namespace_id)
        REFERENCES ddc_namespace_t0 (tenant_id, id) ON DELETE RESTRICT,
    CONSTRAINT fk_si_binding_resource FOREIGN KEY (tenant_id, resource_id, kind)
        REFERENCES ddc_si_resource_t0 (tenant_id, id, kind) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_si_binding_live
    ON ddc_si_binding_t0 (tenant_id, biz_code, namespace_id, env_code, app_code, kind, resource_key)
    WHERE deleted_at IS NULL AND binding_status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS ddc_si_reference_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    source_resource_id bigint CHECK (source_resource_id IS NULL OR source_resource_id > 0),
    source_version_no bigint CHECK (source_version_no IS NULL OR source_version_no > 0),
    source_type varchar(32) NOT NULL CHECK (source_type IN ('AGENT', 'MCP', 'PROMPT', 'SKILL', 'TIANJI_PROCESS')),
    source_key varchar(128) COLLATE "C" NOT NULL,
    target_kind varchar(16) NOT NULL CHECK (target_kind IN ('AGENT', 'MCP', 'PROMPT', 'SKILL')),
    target_key varchar(128) COLLATE "C" NOT NULL,
    target_version_no bigint CHECK (target_version_no IS NULL OR target_version_no > 0),
    env_code varchar(32) NOT NULL,
    namespace_id bigint CHECK (namespace_id IS NULL OR namespace_id > 0),
    CONSTRAINT pk_si_reference PRIMARY KEY (id),
    CONSTRAINT uk_si_reference_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_si_reference_source_resource FOREIGN KEY (tenant_id, source_resource_id)
        REFERENCES ddc_si_resource_t0 (tenant_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_si_reference_live
    ON ddc_si_reference_t0 (tenant_id, env_code, source_type, source_key, target_kind, target_key)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_si_reference_target
    ON ddc_si_reference_t0 (tenant_id, env_code, target_kind, target_key)
    WHERE deleted_at IS NULL;

CREATE TABLE IF NOT EXISTS ddc_si_embedding_chunk_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    create_user_id varchar(512) NOT NULL,
    create_time timestamptz(6) NOT NULL,
    update_user_id varchar(512) NOT NULL,
    update_time timestamptz(6) NOT NULL,
    deleted_at timestamp(6) without time zone,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    resource_id bigint NOT NULL CHECK (resource_id > 0),
    version_id bigint NOT NULL CHECK (version_id > 0),
    env_code varchar(32) NOT NULL,
    namespace_id bigint NOT NULL CHECK (namespace_id > 0),
    kind varchar(16) NOT NULL CHECK (kind IN ('AGENT', 'MCP', 'PROMPT', 'SKILL')),
    chunk_index integer NOT NULL CHECK (chunk_index >= 0),
    chunk_text text NOT NULL,
    chunk_sha256 char(64) NOT NULL CHECK (chunk_sha256 ~ '^[0-9a-f]{64}$'),
    embedding vector(1536) NOT NULL,
    search_tsv tsvector NOT NULL,
    indexed_time timestamptz(6) NOT NULL,
    CONSTRAINT pk_si_embedding_chunk PRIMARY KEY (id),
    CONSTRAINT uk_si_embedding_chunk_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_si_embedding_chunk_version FOREIGN KEY (tenant_id, resource_id, version_id)
        REFERENCES ddc_si_version_t0 (tenant_id, resource_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_si_embedding_chunk_live
    ON ddc_si_embedding_chunk_t0 (tenant_id, version_id, chunk_index)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_si_embedding_chunk_hnsw
    ON ddc_si_embedding_chunk_t0 USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

CREATE INDEX IF NOT EXISTS ix_si_embedding_chunk_fts
    ON ddc_si_embedding_chunk_t0 USING gin (search_tsv)
    WHERE deleted_at IS NULL;

-- Idempotent initial backfill of existing PROMPT and SKILL resources and versions from ddc_ai_resource_t0 / ddc_ai_version_t0.
INSERT INTO ddc_si_resource_t0 (
    id, tenant_id, create_user_id, create_time, update_user_id, update_time, deleted_at, version,
    biz_code, namespace_id, env_code, app_code, kind, resource_key, display_name, description,
    tags_json, lifecycle_status, source_type, spec_json, content_sha256, last_version_no,
    current_version_id, published_time, search_tsv
)
SELECT
    r.id,
    r.tenant_id,
    r.create_user_id,
    r.create_time,
    r.update_user_id,
    r.update_time,
    r.deleted_at,
    r.version,
    r.biz_code,
    r.namespace_id,
    r.env_code,
    r.app_code,
    r.kind,
    r.resource_key,
    r.display_name,
    r.description,
    '[]',
    CASE WHEN r.current_version_id IS NOT NULL THEN 'PUBLISHED' ELSE 'DRAFT' END,
    'CONSOLE',
    CASE
        WHEN r.kind = 'PROMPT' THEN jsonb_build_object('promptText', coalesce(r.draft_prompt, ''))::text
        ELSE jsonb_build_object('kind', 'SKILL', 'resourceKey', r.resource_key)::text
    END,
    encode(sha256(convert_to(
        CASE
            WHEN r.kind = 'PROMPT' THEN jsonb_build_object('promptText', coalesce(r.draft_prompt, ''))::text
            ELSE jsonb_build_object('kind', 'SKILL', 'resourceKey', r.resource_key)::text
        END, 'UTF8')), 'hex'),
    r.last_version_no,
    NULL,
    CASE WHEN r.current_version_id IS NOT NULL THEN r.update_time ELSE NULL END,
    to_tsvector('simple', coalesce(r.resource_key, '') || ' ' || coalesce(r.display_name, '') || ' ' || coalesce(r.description, ''))
FROM ddc_ai_resource_t0 r
WHERE r.kind IN ('PROMPT', 'SKILL')
ON CONFLICT (id) DO NOTHING;

INSERT INTO ddc_si_version_t0 (
    id, tenant_id, create_user_id, create_time, update_user_id, update_time, deleted_at, version,
    resource_id, version_no, kind, spec_json, digest_sha256, source_env_code, source_version_no,
    change_note, index_status, published_time, indexed_time
)
SELECT
    v.id,
    v.tenant_id,
    v.create_user_id,
    v.create_time,
    v.update_user_id,
    v.update_time,
    v.deleted_at,
    v.version,
    v.resource_id,
    v.version_no,
    v.kind,
    CASE
        WHEN v.kind = 'PROMPT' THEN jsonb_build_object('promptText', coalesce(v.prompt_text, ''))::text
        ELSE jsonb_build_object('kind', 'SKILL', 'contentBytes', v.content_bytes, 'nodeCount', v.node_count)::text
    END,
    v.content_sha256,
    NULL,
    NULL,
    v.change_note,
    'PENDING',
    v.create_time,
    NULL
FROM ddc_ai_version_t0 v
JOIN ddc_si_resource_t0 r ON r.tenant_id = v.tenant_id AND r.id = v.resource_id
WHERE v.kind IN ('PROMPT', 'SKILL')
ON CONFLICT (id) DO NOTHING;

UPDATE ddc_si_resource_t0 r
SET current_version_id = a.current_version_id
FROM ddc_ai_resource_t0 a
WHERE a.id = r.id
  AND a.tenant_id = r.tenant_id
  AND a.current_version_id IS NOT NULL
  AND r.current_version_id IS NULL
  AND EXISTS (
      SELECT 1 FROM ddc_si_version_t0 v
      WHERE v.tenant_id = r.tenant_id AND v.resource_id = r.id AND v.id = a.current_version_id
  );
