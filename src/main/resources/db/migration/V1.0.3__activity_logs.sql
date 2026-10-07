CREATE TABLE IF NOT EXISTS req_activity_log (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES req_workspace(id),
    requirement_id BIGINT,
    user_id BIGINT NOT NULL REFERENCES sys_user(id),
    user_name VARCHAR(255) NOT NULL,
    target_type VARCHAR(50) NOT NULL,
    target_id BIGINT NOT NULL,
    action_type VARCHAR(50) NOT NULL,
    summary VARCHAR(1000) NOT NULL,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_activity_workspace_created
    ON req_activity_log(workspace_id, created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_activity_requirement_created
    ON req_activity_log(requirement_id, created_at DESC, id DESC);
