CREATE TABLE IF NOT EXISTS req_workspace (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    description TEXT,
    owner_id BIGINT NOT NULL REFERENCES sys_user(id),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_workspace_owner_id ON req_workspace(owner_id);

CREATE TABLE IF NOT EXISTS req_project (
    id BIGSERIAL PRIMARY KEY,
    workspace_id BIGINT NOT NULL REFERENCES req_workspace(id),
    name VARCHAR(255) NOT NULL,
    identifier VARCHAR(10) NOT NULL,
    description TEXT,
    lead_id BIGINT,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_project_workspace_identifier UNIQUE (workspace_id, identifier)
);
CREATE INDEX IF NOT EXISTS idx_project_workspace_id ON req_project(workspace_id);

ALTER TABLE req_requirement
    ADD COLUMN IF NOT EXISTS project_id BIGINT REFERENCES req_project(id);
CREATE INDEX IF NOT EXISTS idx_requirement_project_id ON req_requirement(project_id);

INSERT INTO req_workspace (name, description, owner_id)
SELECT COALESCE(NULLIF(nickname, ''), username) || ' 的工作空间', '个人工作空间', id
FROM sys_user user_row
WHERE NOT EXISTS (
    SELECT 1 FROM req_workspace workspace WHERE workspace.owner_id = user_row.id
);

INSERT INTO req_project (workspace_id, name, identifier, description, status)
SELECT workspace.id, '默认项目', 'DEFAULT', '用于收纳尚未细分的需求', 'ACTIVE'
FROM req_workspace workspace
WHERE NOT EXISTS (
    SELECT 1
    FROM req_project project
    WHERE project.workspace_id = workspace.id AND project.identifier = 'DEFAULT'
);

UPDATE req_requirement requirement
SET project_id = project.id
FROM req_project project
JOIN req_workspace workspace ON workspace.id = project.workspace_id
WHERE requirement.project_id IS NULL
  AND requirement.creator_id = workspace.owner_id
  AND project.identifier = 'DEFAULT';
