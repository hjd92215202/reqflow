CREATE TABLE req_decision_record (
    id BIGSERIAL PRIMARY KEY,
    requirement_id BIGINT NOT NULL REFERENCES req_requirement(id) ON DELETE CASCADE,
    stage_id BIGINT REFERENCES req_stage(id) ON DELETE SET NULL,
    sub_task_id BIGINT REFERENCES req_sub_task(id) ON DELETE SET NULL,
    stage_title VARCHAR(255),
    sub_task_title VARCHAR(255),
    title VARCHAR(255) NOT NULL,
    context TEXT NOT NULL,
    options_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    chosen_option TEXT,
    rationale TEXT,
    assumptions_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    confidence VARCHAR(16),
    status VARCHAR(20) NOT NULL DEFAULT 'PROPOSED',
    review_date DATE,
    ai_assistance_json JSONB,
    supersedes_decision_id BIGINT UNIQUE REFERENCES req_decision_record(id) ON DELETE SET NULL,
    superseded_by_decision_id BIGINT REFERENCES req_decision_record(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_by BIGINT NOT NULL,
    updated_by BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_decision_status CHECK (status IN ('PROPOSED', 'ACCEPTED', 'REJECTED', 'SUPERSEDED')),
    CONSTRAINT chk_decision_confidence CHECK (confidence IS NULL OR confidence IN ('LOW', 'MEDIUM', 'HIGH')),
    CONSTRAINT chk_decision_version CHECK (version >= 0)
);
CREATE INDEX idx_decision_requirement_order ON req_decision_record(requirement_id, created_at DESC, id DESC);
CREATE INDEX idx_decision_stage ON req_decision_record(stage_id);
CREATE INDEX idx_decision_task ON req_decision_record(sub_task_id);
