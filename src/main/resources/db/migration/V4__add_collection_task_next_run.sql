ALTER TABLE collection_tasks ADD COLUMN next_run_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE collection_tasks ADD CONSTRAINT collection_tasks_next_run_requires_enabled CHECK (enabled = TRUE OR next_run_at IS NULL);

CREATE INDEX collection_tasks_due_idx ON collection_tasks (next_run_at);
