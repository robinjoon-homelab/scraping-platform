CREATE TABLE collection_runs (
    id UUID PRIMARY KEY,
    task_id UUID NOT NULL,
    task_version BIGINT NOT NULL,
    task_display_name VARCHAR(120) NOT NULL,
    resource VARCHAR(2048) NOT NULL,
    parameters TEXT NOT NULL,
    every_seconds BIGINT NOT NULL,
    anchor_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    source_id UUID NOT NULL,
    source_display_name VARCHAR(120) NOT NULL,
    source_kind VARCHAR(20) NOT NULL,
    source_base_url VARCHAR(2048) NOT NULL,
    source_credential_ref VARCHAR(120),
    source_created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    scheduled_for TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    started_at TIMESTAMP(6) WITH TIME ZONE,
    finished_at TIMESTAMP(6) WITH TIME ZONE,
    lease_until TIMESTAMP(6) WITH TIME ZONE,
    status VARCHAR(20) NOT NULL,
    failure_reason VARCHAR(1000),
    CONSTRAINT collection_runs_task_fk FOREIGN KEY (task_id) REFERENCES collection_tasks (id),
    CONSTRAINT collection_runs_source_fk FOREIGN KEY (source_id) REFERENCES sources (id),
    CONSTRAINT collection_runs_task_slot_unique UNIQUE (task_id, scheduled_for),
    CONSTRAINT collection_runs_task_version_nonnegative CHECK (task_version >= 0),
    CONSTRAINT collection_runs_interval_range CHECK (every_seconds BETWEEN 60 AND 31536000),
    CONSTRAINT collection_runs_source_kind_supported CHECK (source_kind IN ('HTTP_JSON', 'RSS_ATOM', 'HTML')),
    CONSTRAINT collection_runs_status_supported CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT collection_runs_failure_reason_not_blank CHECK ((status = 'FAILED' AND failure_reason IS NOT NULL AND CHAR_LENGTH(TRIM(failure_reason)) > 0) OR (status <> 'FAILED' AND failure_reason IS NULL))
);

CREATE INDEX collection_runs_status_scheduled_idx ON collection_runs (status, scheduled_for);
CREATE INDEX collection_runs_status_lease_idx ON collection_runs (status, lease_until);
CREATE INDEX collection_runs_task_created_idx ON collection_runs (task_id, scheduled_for DESC, id);
