-- Run with the old bootstrap CLI paused. The runner owns the schema lock and this version's transaction.
-- Block concurrent legacy inserts while validating and copying the exact original TEXT.
LOCK TABLE ddc_operation_log_t0 IN SHARE ROW EXCLUSIVE MODE;

CREATE TABLE ddc_metadata_bootstrap_receipt_t0 (
    tenant_id bigint NOT NULL,
    operation_id varchar(36) NOT NULL,
    command_digest char(64) NOT NULL,
    receipt text NOT NULL,
    recorded_at timestamptz(6) NOT NULL,
    CONSTRAINT pk_tianshu_metadata_bootstrap_receipt PRIMARY KEY (tenant_id, operation_id),
    CONSTRAINT ck_metadata_bootstrap_receipt_tenant CHECK (tenant_id > 0),
    CONSTRAINT ck_metadata_bootstrap_receipt_operation CHECK
        (operation_id ~ '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'),
    CONSTRAINT ck_metadata_bootstrap_receipt_digest CHECK (command_digest::text ~ '^[0-9a-f]{64}$'),
    -- Java String.length counts supplementary Unicode characters twice; PostgreSQL length counts once.
    CONSTRAINT ck_metadata_bootstrap_receipt_length CHECK
        (char_length(receipt) + char_length(regexp_replace(receipt, U&'[\0001-\FFFF]', '', 'g')) BETWEEN 1 AND 1048576)
);

DO $pac_receipt$
DECLARE
    candidate record;
    body json;
    identity_value text;
    digest_value text;
BEGIN
    FOR candidate IN
        SELECT tenant_id, public_id, operation_type, operation_content, created_at, deleted_at
        FROM ddc_operation_log_t0
        WHERE operation_type = 'PAC_BOOTSTRAP_METADATA_B0' OR public_id LIKE 'pac-b0:%'
        ORDER BY tenant_id, public_id, id
    LOOP
        IF candidate.operation_type IS DISTINCT FROM 'PAC_BOOTSTRAP_METADATA_B0'
           OR candidate.public_id !~ '^pac-b0:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
           OR candidate.tenant_id IS NULL OR candidate.tenant_id <= 0
           OR candidate.deleted_at IS NOT NULL OR candidate.created_at IS NULL
           OR candidate.operation_content IS NULL
           -- The original CLI's JSON entry point additionally caps its UTF-8 input at one MiB.
           OR octet_length(convert_to(candidate.operation_content, 'UTF8')) > 1048576
           OR char_length(candidate.operation_content)
                + char_length(regexp_replace(candidate.operation_content, U&'[\0001-\FFFF]', '', 'g')) > 1048576
        THEN
            RAISE EXCEPTION 'TIANSHU_PAC_RECEIPT_CANDIDATE_INVALID';
        END IF;
        BEGIN
            body := candidate.operation_content::json;
        EXCEPTION WHEN invalid_text_representation THEN
            RAISE EXCEPTION 'TIANSHU_PAC_RECEIPT_JSON_INVALID';
        END;
        IF json_typeof(body) IS DISTINCT FROM 'object' THEN
            RAISE EXCEPTION 'TIANSHU_PAC_RECEIPT_JSON_INVALID';
        END IF;
        -- Match the original strict parser for arbitrary object/array shapes, without rewriting TEXT.
        IF EXISTS (
            WITH RECURSIVE containers(value, depth) AS (
                SELECT body, 1
                UNION ALL
                SELECT child.value, parent.depth + 1
                FROM containers parent
                CROSS JOIN LATERAL (
                    SELECT value FROM json_each(CASE WHEN json_typeof(parent.value) = 'object'
                        THEN parent.value ELSE '{}'::json END)
                    UNION ALL
                    SELECT value FROM json_array_elements(CASE WHEN json_typeof(parent.value) = 'array'
                        THEN parent.value ELSE '[]'::json END)
                ) child
                WHERE parent.depth <= 16 AND json_typeof(child.value) IN ('object', 'array')
            )
            SELECT 1 FROM containers
            WHERE depth > 16 OR EXISTS (
                SELECT key FROM json_each(CASE WHEN json_typeof(value) = 'object' THEN value ELSE '{}'::json END)
                GROUP BY key HAVING count(*) > 1
            )
        ) THEN
            RAISE EXCEPTION 'TIANSHU_PAC_RECEIPT_JSON_INVALID';
        END IF;
        identity_value := body ->> 'operationId';
        digest_value := body ->> 'commandDigest';
        IF json_typeof(body -> 'operationId') IS DISTINCT FROM 'string'
           OR identity_value IS DISTINCT FROM substring(candidate.public_id FROM 8)
           OR json_typeof(body -> 'commandDigest') IS DISTINCT FROM 'string'
           OR digest_value IS NULL OR digest_value !~ '^[0-9a-f]{64}$'
        THEN
            RAISE EXCEPTION 'TIANSHU_PAC_RECEIPT_IDENTITY_INVALID';
        END IF;
        INSERT INTO ddc_metadata_bootstrap_receipt_t0
            (tenant_id, operation_id, command_digest, receipt, recorded_at)
        VALUES (candidate.tenant_id, identity_value, digest_value, candidate.operation_content,
                candidate.created_at AT TIME ZONE 'UTC');
    END LOOP;
END
$pac_receipt$;
