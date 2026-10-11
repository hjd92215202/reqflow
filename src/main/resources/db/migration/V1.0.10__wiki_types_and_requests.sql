ALTER TABLE req_wiki_document ADD COLUMN document_type VARCHAR(32)
    CHECK (document_type IN ('GENERAL','TECHNICAL_DESIGN','PITFALL','RETROSPECTIVE','CHANGELOG','EXPERIMENT','PRACTICE','OTHER'));
ALTER TABLE req_wiki_document ADD COLUMN client_request_id VARCHAR(36);
ALTER TABLE req_wiki_document ADD COLUMN request_fingerprint VARCHAR(64);
CREATE UNIQUE INDEX idx_wiki_create_request ON req_wiki_document(creator_id, client_request_id)
    WHERE client_request_id IS NOT NULL;
