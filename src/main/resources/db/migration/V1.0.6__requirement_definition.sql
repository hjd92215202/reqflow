ALTER TABLE req_requirement
    ADD COLUMN definition_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN definition_version BIGINT NOT NULL DEFAULT 0,
    ADD COLUMN definition_confirmed_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN definition_confirmed_by BIGINT REFERENCES sys_user(id);

-- Personal requirements have no workspace, but still need auditable definition changes.
ALTER TABLE req_activity_log ALTER COLUMN workspace_id DROP NOT NULL;
