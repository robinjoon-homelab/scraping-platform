CREATE TABLE collected_records (
    id UUID PRIMARY KEY,
    source_id UUID NOT NULL,
    run_id UUID NOT NULL,
    source_record_key VARCHAR(2048) NOT NULL,
    source_record_key_hash CHAR(64) NOT NULL,
    revision_hash CHAR(64) NOT NULL,
    schema_version VARCHAR(120) NOT NULL,
    payload TEXT NOT NULL,
    collected_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    source_updated_at TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT collected_records_source_fk FOREIGN KEY (source_id) REFERENCES sources (id),
    CONSTRAINT collected_records_run_fk FOREIGN KEY (run_id) REFERENCES collection_runs (id),
    CONSTRAINT collected_records_revision_unique UNIQUE (source_id, source_record_key_hash, revision_hash),
    CONSTRAINT collected_records_source_record_key_not_blank CHECK (CHAR_LENGTH(TRIM(source_record_key)) > 0),
    CONSTRAINT collected_records_source_record_key_hash_length CHECK (CHAR_LENGTH(source_record_key_hash) = 64),
    CONSTRAINT collected_records_revision_hash_length CHECK (CHAR_LENGTH(revision_hash) = 64),
    CONSTRAINT collected_records_schema_version_not_blank CHECK (CHAR_LENGTH(TRIM(schema_version)) > 0),
    CONSTRAINT collected_records_payload_length CHECK (CHAR_LENGTH(payload) BETWEEN 1 AND 1000000)
);

CREATE INDEX collected_records_run_idx ON collected_records (run_id, collected_at, id);

CREATE TABLE collection_checkpoints (
    task_id UUID PRIMARY KEY,
    task_version BIGINT NOT NULL,
    "value" TEXT NOT NULL,
    run_id UUID NOT NULL,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT collection_checkpoints_task_fk FOREIGN KEY (task_id) REFERENCES collection_tasks (id),
    CONSTRAINT collection_checkpoints_run_fk FOREIGN KEY (run_id) REFERENCES collection_runs (id),
    CONSTRAINT collection_checkpoints_task_version_nonnegative CHECK (task_version >= 0),
    CONSTRAINT collection_checkpoints_value_length CHECK (CHAR_LENGTH("value") BETWEEN 1 AND 16384)
);
