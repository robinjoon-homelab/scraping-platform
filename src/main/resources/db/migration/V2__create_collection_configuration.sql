CREATE TABLE sources (
    id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    kind VARCHAR(20) NOT NULL,
    base_url VARCHAR(2048) NOT NULL,
    credential_ref VARCHAR(120),
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT sources_name_not_blank CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT sources_kind_supported CHECK (kind IN ('HTTP_JSON', 'RSS_ATOM', 'HTML')),
    CONSTRAINT sources_base_url_not_blank CHECK (CHAR_LENGTH(TRIM(base_url)) > 0),
    CONSTRAINT sources_credential_ref_not_blank CHECK (credential_ref IS NULL OR CHAR_LENGTH(TRIM(credential_ref)) > 0)
);

CREATE INDEX sources_created_at_id_idx ON sources (created_at, id);

CREATE TABLE collection_tasks (
    id UUID PRIMARY KEY,
    display_name VARCHAR(120) NOT NULL,
    source_id UUID NOT NULL,
    resource VARCHAR(2048) NOT NULL,
    parameters TEXT NOT NULL,
    every_seconds BIGINT NOT NULL,
    anchor_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT collection_tasks_source_fk FOREIGN KEY (source_id) REFERENCES sources (id),
    CONSTRAINT collection_tasks_name_not_blank CHECK (CHAR_LENGTH(TRIM(display_name)) > 0),
    CONSTRAINT collection_tasks_resource_relative CHECK (resource LIKE '/%' AND resource NOT LIKE '//%'),
    CONSTRAINT collection_tasks_interval_range CHECK (every_seconds BETWEEN 60 AND 31536000),
    CONSTRAINT collection_tasks_version_nonnegative CHECK (version >= 0)
);

CREATE INDEX collection_tasks_created_at_id_idx ON collection_tasks (created_at, id);
CREATE INDEX collection_tasks_source_id_idx ON collection_tasks (source_id);
