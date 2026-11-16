CREATE TABLE collection_manual_requests (
 task_id UUID NOT NULL REFERENCES collection_tasks(id),
 request_key VARCHAR(120) NOT NULL,
 run_id UUID NOT NULL REFERENCES collection_runs(id),
 PRIMARY KEY(task_id, request_key)
);
CREATE TABLE collection_export_settings (
 task_id UUID PRIMARY KEY REFERENCES collection_tasks(id), enabled BOOLEAN NOT NULL
);
CREATE TABLE collection_export_counter (id INTEGER PRIMARY KEY, published_position BIGINT NOT NULL);
INSERT INTO collection_export_counter(id, published_position) VALUES (1, 0);
CREATE TABLE collection_export_ledger (
 publication_sequence BIGINT PRIMARY KEY,
 task_id UUID NOT NULL REFERENCES collection_tasks(id),
 record_id UUID NOT NULL UNIQUE REFERENCES collected_records(id)
);
CREATE INDEX collection_export_scope_idx ON collection_export_ledger(task_id, publication_sequence);
ALTER TABLE collected_records ADD COLUMN task_id UUID REFERENCES collection_tasks(id);
UPDATE collected_records SET task_id = (SELECT task_id FROM collection_runs WHERE collection_runs.id = collected_records.run_id);
ALTER TABLE collected_records ALTER COLUMN task_id SET NOT NULL;
ALTER TABLE collected_records DROP CONSTRAINT collected_records_revision_unique;
ALTER TABLE collected_records ADD CONSTRAINT collected_records_plan_revision_unique UNIQUE(task_id, source_id, source_record_key_hash, revision_hash);

ALTER TABLE collection_tasks ADD COLUMN resume_floor BIGINT NOT NULL DEFAULT 0;
UPDATE collection_tasks SET resume_floor = version;
