-- V1__init_schema.sql
-- Studio AI 初始化数据库架构

CREATE TABLE users (
    id VARCHAR(36) PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    email VARCHAR(200),
    role VARCHAR(50) NOT NULL DEFAULT 'USER',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    team_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE TABLE chat_sessions (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(200),
    summary TEXT,
    message_count BIGINT NOT NULL DEFAULT 0,
    last_active_at BIGINT NOT NULL,
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_session_user ON chat_sessions(user_id);
CREATE INDEX idx_session_active ON chat_sessions(user_id, last_active_at);

CREATE TABLE chat_messages (
    id VARCHAR(36) PRIMARY KEY,
    session_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(36) NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    token_count INTEGER,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_msg_session ON chat_messages(session_id);
CREATE INDEX idx_msg_session_time ON chat_messages(session_id, created_at);

CREATE TABLE kb_files (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    file_name VARCHAR(500) NOT NULL,
    original_name VARCHAR(500),
    file_type VARCHAR(100),
    file_size BIGINT,
    ingest_status VARCHAR(100) NOT NULL DEFAULT 'PENDING',
    chunk_count INTEGER,
    storage_path VARCHAR(500),
    team_id VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_kb_user ON kb_files(user_id);
CREATE INDEX idx_kb_status ON kb_files(ingest_status);

CREATE TABLE literature_docs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(500) NOT NULL,
    source VARCHAR(50),
    identifier VARCHAR(200),
    project_tag VARCHAR(100),
    extracted_text TEXT,
    storage_path VARCHAR(500),
    abstract_text TEXT,
    authors VARCHAR(500),
    year INTEGER,
    team_id VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_lit_user ON literature_docs(user_id);
CREATE INDEX idx_lit_tag ON literature_docs(project_tag);

CREATE TABLE experiment_projects (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    project_name VARCHAR(200) NOT NULL,
    description VARCHAR(1000),
    team_id VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_exp_proj_user ON experiment_projects(user_id);

CREATE TABLE experiment_records (
    id VARCHAR(36) PRIMARY KEY,
    project_id VARCHAR(36) NOT NULL REFERENCES experiment_projects(id),
    user_id VARCHAR(36) NOT NULL,
    title VARCHAR(500),
    conditions TEXT,
    conditions_structured TEXT,
    data_path VARCHAR(500),
    conclusion TEXT,
    failure_reason VARCHAR(1000),
    tags VARCHAR(2000),
    team_id VARCHAR(50),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_exp_proj ON experiment_records(project_id);
CREATE INDEX idx_exp_user ON experiment_records(user_id);

CREATE TABLE audit_logs (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    action VARCHAR(50) NOT NULL,
    resource VARCHAR(500),
    resource_id VARCHAR(200),
    details TEXT,
    ip_address VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_user ON audit_logs(user_id);
CREATE INDEX idx_audit_time ON audit_logs(created_at);
CREATE INDEX idx_audit_action ON audit_logs(action);

CREATE TABLE api_tokens (
    id VARCHAR(36) PRIMARY KEY,
    user_id VARCHAR(36) NOT NULL,
    token_hash VARCHAR(200) NOT NULL,
    name VARCHAR(100),
    expires_at TIMESTAMP WITH TIME ZONE,
    last_used_at TIMESTAMP WITH TIME ZONE,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_token_user ON api_tokens(user_id);
CREATE INDEX idx_token_value ON api_tokens(token_hash);

-- 插入默认管理员用户 (密码: admin123)
INSERT INTO users (id, username, password_hash, display_name, role, enabled)
VALUES ('00000000-0000-0000-0000-000000000001', 'admin', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Administrator', 'ADMIN', TRUE);
