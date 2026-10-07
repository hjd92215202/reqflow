CREATE TABLE IF NOT EXISTS req_task_dependency (
    id BIGSERIAL PRIMARY KEY,
    stage_id BIGINT NOT NULL REFERENCES req_stage(id) ON DELETE CASCADE,
    predecessor_id BIGINT NOT NULL REFERENCES req_sub_task(id) ON DELETE CASCADE,
    successor_id BIGINT NOT NULL REFERENCES req_sub_task(id) ON DELETE CASCADE,
    type VARCHAR(50) NOT NULL DEFAULT 'FINISH_TO_START',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_task_dependency UNIQUE (stage_id, predecessor_id, successor_id),
    CONSTRAINT chk_task_dependency_not_self CHECK (predecessor_id <> successor_id)
);
CREATE INDEX IF NOT EXISTS idx_task_dependency_stage_id ON req_task_dependency(stage_id);
CREATE INDEX IF NOT EXISTS idx_task_dependency_predecessor_id ON req_task_dependency(predecessor_id);
CREATE INDEX IF NOT EXISTS idx_task_dependency_successor_id ON req_task_dependency(successor_id);
