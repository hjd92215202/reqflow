CREATE TABLE req_requirement_closeout (
    requirement_id BIGINT PRIMARY KEY REFERENCES req_requirement(id) ON DELETE CASCADE,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT' CHECK(status IN ('DRAFT','COMPLETE')),
    version BIGINT NOT NULL DEFAULT 0,
    content_json JSONB NOT NULL DEFAULT '{}',
    wiki_document_id BIGINT REFERENCES req_wiki_document(id) ON DELETE SET NULL,
    saved_facts_token VARCHAR(64), completed_facts_token VARCHAR(64),
    completed_at TIMESTAMPTZ, completed_by BIGINT,
    updated_at TIMESTAMPTZ NOT NULL, updated_by BIGINT NOT NULL
);
CREATE TABLE req_closeout_facts (
    requirement_id BIGINT PRIMARY KEY REFERENCES req_requirement(id) ON DELETE CASCADE,
    revision BIGINT NOT NULL DEFAULT 0
);
INSERT INTO req_closeout_facts(requirement_id) SELECT id FROM req_requirement;

CREATE FUNCTION bump_closeout_facts(req BIGINT) RETURNS void LANGUAGE plpgsql AS $$
BEGIN
    UPDATE req_closeout_facts SET revision=revision+1 WHERE requirement_id=req;
END $$;
CREATE FUNCTION closeout_requirement_changed() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN INSERT INTO req_closeout_facts(requirement_id) VALUES(NEW.id);
    ELSIF (to_jsonb(OLD)-'updated_at') IS DISTINCT FROM (to_jsonb(NEW)-'updated_at') THEN
        PERFORM bump_closeout_facts(NEW.id);
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER closeout_requirement_facts AFTER INSERT OR UPDATE ON req_requirement FOR EACH ROW EXECUTE FUNCTION closeout_requirement_changed();
CREATE FUNCTION closeout_related_changed() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE old_req BIGINT; new_req BIGINT;
BEGIN
    IF TG_OP='UPDATE' AND (to_jsonb(OLD)-'updated_at') IS NOT DISTINCT FROM (to_jsonb(NEW)-'updated_at') THEN RETURN NEW; END IF;
    IF TG_TABLE_NAME='req_sub_task' THEN
        IF TG_OP!='INSERT' THEN SELECT requirement_id INTO old_req FROM req_stage WHERE id=OLD.stage_id; END IF;
        IF TG_OP!='DELETE' THEN SELECT requirement_id INTO new_req FROM req_stage WHERE id=NEW.stage_id; END IF;
    ELSE
        IF TG_OP!='INSERT' THEN old_req:=OLD.requirement_id; END IF;
        IF TG_OP!='DELETE' THEN new_req:=NEW.requirement_id; END IF;
    END IF;
    IF old_req IS NOT NULL THEN PERFORM bump_closeout_facts(old_req); END IF;
    IF new_req IS NOT NULL AND new_req IS DISTINCT FROM old_req THEN PERFORM bump_closeout_facts(new_req); END IF;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER closeout_stage_facts AFTER INSERT OR UPDATE OR DELETE ON req_stage FOR EACH ROW EXECUTE FUNCTION closeout_related_changed();
CREATE TRIGGER closeout_task_facts AFTER INSERT OR UPDATE OR DELETE ON req_sub_task FOR EACH ROW EXECUTE FUNCTION closeout_related_changed();
CREATE TRIGGER closeout_decision_facts AFTER INSERT OR UPDATE OR DELETE ON req_decision_record FOR EACH ROW EXECUTE FUNCTION closeout_related_changed();
CREATE TRIGGER closeout_verification_facts AFTER INSERT OR UPDATE OR DELETE ON req_verification_record FOR EACH ROW EXECUTE FUNCTION closeout_related_changed();
CREATE FUNCTION closeout_wiki_changed() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE req BIGINT;
BEGIN
    IF TG_OP='UPDATE' AND (to_jsonb(OLD)-'updated_at'-'share_token') IS NOT DISTINCT FROM (to_jsonb(NEW)-'updated_at'-'share_token') THEN RETURN NEW; END IF;
    FOR req IN SELECT requirement_id FROM req_requirement_closeout WHERE wiki_document_id=OLD.id LOOP
        PERFORM bump_closeout_facts(req);
    END LOOP;
    IF TG_OP='DELETE' THEN RETURN OLD; END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER closeout_wiki_facts BEFORE UPDATE OR DELETE ON req_wiki_document FOR EACH ROW EXECUTE FUNCTION closeout_wiki_changed();
