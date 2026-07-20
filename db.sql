-- 1. 创建子任务表 (增加负责人字段)
CREATE TABLE req_sub_task (
    id SERIAL PRIMARY KEY,
    requirement_id INT REFERENCES req_requirement(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    assignee VARCHAR(50),               -- 负责人（支持手动填写文本）
    is_completed BOOLEAN DEFAULT FALSE,  -- 是否完成
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sub_task_req_id ON req_sub_task(requirement_id);

-- 2. 创建讨论/进展记录表
CREATE TABLE req_discussion (
    id SERIAL PRIMARY KEY,
    requirement_id INT REFERENCES req_requirement(id) ON DELETE CASCADE,
    user_id INT REFERENCES sys_user(id),
    content TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_discussion_req_id ON req_discussion(requirement_id);