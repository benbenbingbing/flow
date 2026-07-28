ALTER TABLE workflow_schema_change
  DROP INDEX uk_schema_change_hash,
  ADD COLUMN active_hash CHAR(64) DEFAULT NULL AFTER ddl_hash,
  ADD KEY idx_schema_change_hash (ddl_hash);

UPDATE workflow_schema_change
SET active_hash = ddl_hash
WHERE status IN ('PENDING', 'RUNNING');

ALTER TABLE workflow_schema_change
  ADD UNIQUE KEY uk_schema_change_active_hash (active_hash);
