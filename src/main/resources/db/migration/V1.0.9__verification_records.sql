CREATE TABLE req_verification_record (
    id BIGSERIAL PRIMARY KEY,
    requirement_id BIGINT NOT NULL REFERENCES req_requirement(id) ON DELETE CASCADE,
    stage_id BIGINT REFERENCES req_stage(id) ON DELETE SET NULL,
    sub_task_id BIGINT REFERENCES req_sub_task(id) ON DELETE SET NULL,
    stage_title VARCHAR(255), sub_task_title VARCHAR(255),
    success_criterion_id VARCHAR(64),
    criterion_snapshot TEXT NOT NULL,
    criterion_method TEXT, criterion_target TEXT,
    task_deliverable TEXT, task_completion_criteria TEXT,
    method TEXT NOT NULL, expected_result TEXT, actual_result TEXT NOT NULL,
    result_status VARCHAR(20) NOT NULL CHECK (result_status IN ('PASS','FAIL','PARTIAL','INCONCLUSIVE','WAIVED')),
    waiver_reason TEXT,
    evidence_json JSONB NOT NULL DEFAULT '[]',
    verified_by BIGINT NOT NULL, verified_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    invalidated_at TIMESTAMPTZ, invalidation_reason TEXT,
    voided_at TIMESTAMPTZ, voided_by BIGINT, void_reason TEXT,
    client_request_id VARCHAR(36) NOT NULL, request_fingerprint VARCHAR(64) NOT NULL,
    UNIQUE (requirement_id, client_request_id)
);
CREATE INDEX idx_verification_history ON req_verification_record(requirement_id, created_at DESC, id DESC);
CREATE INDEX idx_verification_stage_history ON req_verification_record(requirement_id, stage_id, created_at DESC, id DESC);
CREATE INDEX idx_verification_task_history ON req_verification_record(requirement_id, sub_task_id, created_at DESC, id DESC);
CREATE INDEX idx_verification_criterion_latest ON req_verification_record(requirement_id, success_criterion_id, created_at DESC, id DESC) WHERE invalidated_at IS NULL AND voided_at IS NULL;
CREATE INDEX idx_verification_task_latest ON req_verification_record(requirement_id, sub_task_id, created_at DESC, id DESC) WHERE invalidated_at IS NULL AND voided_at IS NULL;
ALTER TABLE req_sub_task ADD COLUMN repair_verification_id BIGINT REFERENCES req_verification_record(id) ON DELETE SET NULL;
ALTER TABLE req_sub_task ADD COLUMN repair_request_id VARCHAR(36);
CREATE UNIQUE INDEX idx_repair_request ON req_sub_task(repair_verification_id, repair_request_id) WHERE repair_verification_id IS NOT NULL;

-- Invalidation is irreversible, including deletion then reusing an identical criterion ID.
-- Triggers also cover legacy CRUD and direct task updates without relying on a client.
CREATE FUNCTION invalidate_changed_criteria() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE req_verification_record v SET invalidated_at = clock_timestamp(), invalidation_reason = '成功标准已修改或删除'
    WHERE v.requirement_id = NEW.id AND v.success_criterion_id IS NOT NULL AND v.invalidated_at IS NULL
    AND NOT EXISTS (
        SELECT 1 FROM jsonb_array_elements(COALESCE(NEW.definition_json->'successCriteria', '[]'::jsonb)) c
        WHERE c->>'id' = v.success_criterion_id
        AND btrim(COALESCE(c->>'description','')) = v.criterion_snapshot
        AND btrim(COALESCE(c->>'suggestedMethod','')) = COALESCE(v.criterion_method,'')
        AND btrim(COALESCE(c->>'targetValue','')) = COALESCE(v.criterion_target,'')
    );
    RETURN NEW;
END $$;
CREATE TRIGGER verification_criteria_changed AFTER UPDATE OF definition_json ON req_requirement
FOR EACH ROW WHEN (OLD.definition_json IS DISTINCT FROM NEW.definition_json) EXECUTE FUNCTION invalidate_changed_criteria();

CREATE FUNCTION invalidate_changed_task_verification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE req_verification_record SET invalidated_at = clock_timestamp(), invalidation_reason = '任务交付标准已变更或任务已删除'
    WHERE sub_task_id = OLD.id AND invalidated_at IS NULL;
    IF TG_OP = 'DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER verification_task_changed AFTER UPDATE OF deliverable, completion_criteria ON req_sub_task
FOR EACH ROW WHEN (OLD.deliverable IS DISTINCT FROM NEW.deliverable OR OLD.completion_criteria IS DISTINCT FROM NEW.completion_criteria) EXECUTE FUNCTION invalidate_changed_task_verification();
CREATE TRIGGER verification_task_deleted BEFORE DELETE ON req_sub_task FOR EACH ROW EXECUTE FUNCTION invalidate_changed_task_verification();
CREATE FUNCTION invalidate_deleted_stage_verification() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    UPDATE req_verification_record SET invalidated_at = clock_timestamp(), invalidation_reason = '关联阶段已删除'
    WHERE stage_id = OLD.id AND invalidated_at IS NULL;
    RETURN OLD;
END $$;
CREATE TRIGGER verification_stage_deleted BEFORE DELETE ON req_stage FOR EACH ROW EXECUTE FUNCTION invalidate_deleted_stage_verification();
