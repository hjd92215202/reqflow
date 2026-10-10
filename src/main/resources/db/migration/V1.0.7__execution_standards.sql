ALTER TABLE req_stage
    ADD COLUMN goal TEXT,
    ADD COLUMN expected_output TEXT,
    ADD COLUMN exit_criteria TEXT;

ALTER TABLE req_sub_task
    ADD COLUMN deliverable TEXT,
    ADD COLUMN completion_criteria TEXT;
