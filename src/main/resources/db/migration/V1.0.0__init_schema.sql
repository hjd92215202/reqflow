-- ===================================================================
-- ReqFlow V1.0.0 初始数据库基线脚本
-- 严格对应当前所有 @Entity 实体定义
-- ===================================================================

-- 1. 系统用户表 (sys_user)
CREATE TABLE IF NOT EXISTS sys_user (
                                        id BIGSERIAL PRIMARY KEY,
                                        username VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    nickname VARCHAR(255),
    email VARCHAR(255),
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );

-- 2. 需求事项表 (req_requirement)
CREATE TABLE IF NOT EXISTS req_requirement (
                                               id BIGSERIAL PRIMARY KEY,
                                               title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'TODO',
    priority VARCHAR(50) DEFAULT 'MEDIUM',
    start_date DATE,
    end_date DATE,
    creator_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );

-- 3. 需求执行阶段表 (req_stage)
CREATE TABLE IF NOT EXISTS req_stage (
                                         id BIGSERIAL PRIMARY KEY,
                                         requirement_id BIGINT NOT NULL,
                                         title VARCHAR(255) NOT NULL,
    start_date DATE,
    end_date DATE,
    status VARCHAR(50) DEFAULT 'TODO',
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );
CREATE INDEX IF NOT EXISTS idx_stage_requirement_id ON req_stage(requirement_id);

-- 4. 树形工作矩阵子任务表 (req_sub_task，支持 JSONB 动态扩展列)
CREATE TABLE IF NOT EXISTS req_sub_task (
                                            id BIGSERIAL PRIMARY KEY,
                                            stage_id BIGINT NOT NULL,
                                            parent_id BIGINT,
                                            title VARCHAR(255) NOT NULL,
    assignee VARCHAR(255),
    status VARCHAR(50) DEFAULT 'TODO',
    start_date DATE,
    end_date DATE,
    custom_fields JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );
CREATE INDEX IF NOT EXISTS idx_subtask_stage_id ON req_sub_task(stage_id);
CREATE INDEX IF NOT EXISTS idx_subtask_parent_id ON req_sub_task(parent_id);

-- 5. 阶段讨论与跟进日志表 (req_discussion)
CREATE TABLE IF NOT EXISTS req_discussion (
                                              id BIGSERIAL PRIMARY KEY,
                                              stage_id BIGINT NOT NULL,
                                              user_id BIGINT NOT NULL,
                                              content TEXT NOT NULL,
                                              created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_discussion_stage_id ON req_discussion(stage_id);

-- 6. 待办事项表 (sys_todo)
CREATE TABLE IF NOT EXISTS sys_todo (
                                        id BIGSERIAL PRIMARY KEY,
                                        user_id BIGINT NOT NULL,
                                        title VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) DEFAULT 'IN_PROGRESS',
    priority VARCHAR(50) DEFAULT 'MEDIUM',
    due_date DATE,
    sub_task_id BIGINT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );
CREATE INDEX IF NOT EXISTS idx_todo_user_id ON sys_todo(user_id);

-- 7. Wiki 知识库文档表 (req_wiki_document，含 16 位安全随机分享 Token)
CREATE TABLE IF NOT EXISTS req_wiki_document (
                                                 id BIGSERIAL PRIMARY KEY,
                                                 requirement_id BIGINT,
                                                 parent_id BIGINT,
                                                 title VARCHAR(255) NOT NULL,
    content TEXT,
    tags VARCHAR(255),
    creator_id BIGINT NOT NULL,
    share_token VARCHAR(64) UNIQUE,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITHOUT TIME ZONE DEFAULT CURRENT_TIMESTAMP
    );
CREATE INDEX IF NOT EXISTS idx_wiki_requirement_id ON req_wiki_document(requirement_id);
CREATE INDEX IF NOT EXISTS idx_wiki_parent_id ON req_wiki_document(parent_id);
CREATE INDEX IF NOT EXISTS idx_wiki_share_token ON req_wiki_document(share_token);
