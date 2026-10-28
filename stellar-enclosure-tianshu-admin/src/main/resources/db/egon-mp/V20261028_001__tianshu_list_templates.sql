-- List definitions and presentation templates are scoped to the authenticated catalog.
CREATE TABLE ddc_list_definition_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    activation_version bigint NOT NULL CHECK (activation_version > 0),
    producer_id varchar(128) NOT NULL,
    producer_version bigint NOT NULL CHECK (producer_version > 0),
    artifact_digest char(64) NOT NULL CHECK (artifact_digest ~ '^[0-9a-f]{64}$'),
    page_resource_key varchar(200) NOT NULL,
    list_key varchar(128) NOT NULL,
    definition_digest char(64) NOT NULL CHECK (definition_digest ~ '^[0-9a-f]{64}$'),
    definition_json text NOT NULL CHECK (jsonb_typeof(definition_json::jsonb) = 'object'
        AND octet_length(definition_json) <= 1048576),
    created_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_list_definition PRIMARY KEY (id),
    CONSTRAINT uk_list_definition_identity UNIQUE (tenant_id, catalog_scope_id, activation_version, page_resource_key, list_key),
    CONSTRAINT fk_list_definition_activation FOREIGN KEY (tenant_id, catalog_scope_id, activation_version)
        REFERENCES ddc_catalog_activation_projection_t0 (tenant_id, catalog_scope_id, activation_version) ON DELETE RESTRICT
);

CREATE TRIGGER tr_ddc_list_definition_immutable
    BEFORE UPDATE OR DELETE ON ddc_list_definition_t0
    FOR EACH ROW EXECUTE FUNCTION ddc_metadata_reject_mutation();

CREATE TABLE ddc_list_template_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    page_resource_key varchar(200) NOT NULL,
    list_key varchar(128) NOT NULL,
    template_type varchar(16) NOT NULL CHECK (template_type IN ('SYSTEM', 'PERSONAL')),
    owner_issuer varchar(2048) NOT NULL,
    owner_subject varchar(256) NOT NULL,
    owner_digest char(64) NOT NULL CHECK (owner_digest ~ '^[0-9a-f]{64}$'),
    template_name varchar(128) NOT NULL CHECK (btrim(template_name) <> ''),
    is_default boolean NOT NULL DEFAULT false,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by varchar(256) NOT NULL,
    created_at timestamptz(6) NOT NULL,
    updated_by varchar(256) NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    deleted_at timestamptz(6),
    CONSTRAINT pk_list_template PRIMARY KEY (id),
    CONSTRAINT uk_list_template_owner UNIQUE (tenant_id, catalog_scope_id, id),
    CONSTRAINT uk_list_template_list UNIQUE (tenant_id, catalog_scope_id, page_resource_key, list_key, id),
    CONSTRAINT fk_list_template_scope FOREIGN KEY (tenant_id, catalog_scope_id)
        REFERENCES ddc_metadata_scope_t0 (tenant_id, catalog_scope_id) ON DELETE RESTRICT,
    CONSTRAINT ck_list_template_owner CHECK ((template_type = 'SYSTEM' AND owner_issuer = '' AND owner_subject = '')
        OR (template_type = 'PERSONAL' AND btrim(owner_issuer) <> '' AND btrim(owner_subject) <> ''))
);

CREATE UNIQUE INDEX uk_list_template_name ON ddc_list_template_t0
    (tenant_id, catalog_scope_id, page_resource_key, list_key, template_type, owner_digest, lower(template_name))
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uk_list_template_default ON ddc_list_template_t0
    (tenant_id, catalog_scope_id, page_resource_key, list_key, template_type, owner_digest)
    WHERE deleted_at IS NULL AND is_default;

CREATE TABLE ddc_list_template_column_t0 (
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    template_id bigint NOT NULL CHECK (template_id > 0),
    column_key varchar(128) NOT NULL,
    sort_order integer NOT NULL CHECK (sort_order >= 0 AND sort_order < 256),
    CONSTRAINT pk_list_template_column PRIMARY KEY (tenant_id, template_id, column_key),
    CONSTRAINT uk_list_template_column_order UNIQUE (tenant_id, template_id, sort_order),
    CONSTRAINT fk_list_template_column_template FOREIGN KEY (tenant_id, catalog_scope_id, template_id)
        REFERENCES ddc_list_template_t0 (tenant_id, catalog_scope_id, id) ON DELETE RESTRICT
);

CREATE TABLE ddc_list_selection_t0 (
    id bigint NOT NULL CHECK (id > 0),
    tenant_id bigint NOT NULL CHECK (tenant_id > 0),
    catalog_scope_id bigint NOT NULL CHECK (catalog_scope_id > 0),
    page_resource_key varchar(200) NOT NULL,
    list_key varchar(128) NOT NULL,
    owner_issuer varchar(2048) NOT NULL CHECK (btrim(owner_issuer) <> ''),
    owner_subject varchar(256) NOT NULL CHECK (btrim(owner_subject) <> ''),
    owner_digest char(64) NOT NULL CHECK (owner_digest ~ '^[0-9a-f]{64}$'),
    template_id bigint NOT NULL CHECK (template_id > 0),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_list_selection PRIMARY KEY (id),
    CONSTRAINT uk_list_selection_owner UNIQUE (tenant_id, catalog_scope_id, page_resource_key, list_key, owner_digest),
    CONSTRAINT fk_list_selection_template FOREIGN KEY (tenant_id, catalog_scope_id, page_resource_key, list_key, template_id)
        REFERENCES ddc_list_template_t0 (tenant_id, catalog_scope_id, page_resource_key, list_key, id) ON DELETE RESTRICT
);
