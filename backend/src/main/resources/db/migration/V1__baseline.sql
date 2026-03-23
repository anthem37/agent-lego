-- Agent Lego 空库唯一基线（全新部署专用）。
-- 依赖：PostgreSQL 14+；仅需 pg_trgm（长记忆模糊检索）。知识库向量存 Milvus，不使用 pgvector 扩展。

CREATE
EXTENSION IF NOT EXISTS pg_trgm;

-- ---------------------------------------------------------------------------
-- 模型与工具
-- ---------------------------------------------------------------------------
CREATE TABLE lego_models
(
    id             varchar(32) PRIMARY KEY,
    name           text         NOT NULL,
    description    text,
    provider       varchar(64)  NOT NULL,
    model_key      varchar(128) NOT NULL,
    config         jsonb        NOT NULL DEFAULT '{}'::jsonb,
    api_key_cipher text,
    base_url       text,
    created_at     timestamptz  NOT NULL DEFAULT now()
);

CREATE INDEX ix_lego_models_created_at ON lego_models (created_at DESC);
CREATE INDEX ix_lego_models_provider_model_key ON lego_models (provider, model_key);

CREATE TABLE lego_tools
(
    id            varchar(32) PRIMARY KEY,
    tool_type     varchar(32) NOT NULL,
    name          text        NOT NULL,
    definition    jsonb       NOT NULL DEFAULT '{}'::jsonb,
    tool_category varchar(32) NOT NULL DEFAULT 'ACTION',
    display_label varchar(256),
    description   text,
    created_at    timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_lego_tools_name_lower ON lego_tools (lower(name));

COMMENT
ON COLUMN lego_tools.tool_category IS '语义分类：QUERY 查询类（只读） / ACTION 操作类（默认）';
COMMENT
ON COLUMN lego_tools.display_label IS '展示名/中文名（可选）；运行时仍以 name 为键';
COMMENT
ON COLUMN lego_tools.description IS '平台侧工具说明（给人读）；模型侧说明仍可放在 definition.description';

-- ---------------------------------------------------------------------------
-- 智能体（工具多对多：lego_agent_tools）
-- ---------------------------------------------------------------------------
CREATE TABLE lego_agents
(
    id                    varchar(32) PRIMARY KEY,
    name                  text        NOT NULL,
    system_prompt         text        NOT NULL,
    model_id              varchar(32) REFERENCES lego_models (id) ON DELETE SET NULL,
    memory_policy         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    knowledge_base_policy jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at            timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_lego_agents_model_id ON lego_agents (model_id);
CREATE INDEX ix_lego_agents_kb_collection_ids_gin
    ON lego_agents USING gin ((knowledge_base_policy -> 'collectionIds'));
CREATE INDEX ix_lego_agents_created_at ON lego_agents (created_at DESC);

CREATE TABLE lego_agent_tools
(
    agent_id varchar(32) NOT NULL REFERENCES lego_agents (id) ON DELETE CASCADE,
    tool_id  varchar(32) NOT NULL REFERENCES lego_tools (id) ON DELETE RESTRICT,
    PRIMARY KEY (agent_id, tool_id)
);

COMMENT
ON TABLE lego_agent_tools IS '智能体引用的工具记录（lego_tools.id）';

CREATE INDEX ix_lego_agent_tools_tool_id ON lego_agent_tools (tool_id);

-- ---------------------------------------------------------------------------
-- 工作流
-- ---------------------------------------------------------------------------
CREATE TABLE lego_workflows
(
    id         varchar(32) PRIMARY KEY,
    name       text        NOT NULL,
    definition jsonb       NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (name)
);

CREATE TABLE lego_workflow_runs
(
    id              varchar(32) PRIMARY KEY,
    workflow_id     varchar(32) NOT NULL REFERENCES lego_workflows (id) ON DELETE CASCADE,
    status          varchar(32) NOT NULL,
    idempotency_key text UNIQUE,
    input           jsonb       NOT NULL DEFAULT '{}'::jsonb,
    output          jsonb       NOT NULL DEFAULT '{}'::jsonb,
    error           text,
    started_at      timestamptz,
    finished_at     timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_lego_workflow_runs_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_lego_workflow_runs_wf_created ON lego_workflow_runs (workflow_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 评测
-- ---------------------------------------------------------------------------
CREATE TABLE lego_evaluations
(
    id         varchar(32) PRIMARY KEY,
    agent_id   varchar(32) NOT NULL REFERENCES lego_agents (id) ON DELETE CASCADE,
    name       text        NOT NULL,
    config     jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (agent_id, name)
);

CREATE TABLE lego_evaluation_runs
(
    id            varchar(32) PRIMARY KEY,
    evaluation_id varchar(32) NOT NULL REFERENCES lego_evaluations (id) ON DELETE CASCADE,
    status        varchar(32) NOT NULL,
    input         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    metrics       jsonb       NOT NULL DEFAULT '{}'::jsonb,
    trace         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    error         text,
    started_at    timestamptz,
    finished_at   timestamptz,
    created_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_lego_evaluation_runs_status
        CHECK (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED'))
);

CREATE INDEX ix_lego_evaluation_runs_eval_created ON lego_evaluation_runs (evaluation_id, created_at DESC);

-- ---------------------------------------------------------------------------
-- 长记忆
-- ---------------------------------------------------------------------------
CREATE TABLE lego_memory_items
(
    id          varchar(32) PRIMARY KEY,
    owner_scope text        NOT NULL,
    content     text        NOT NULL,
    metadata    jsonb       NOT NULL DEFAULT '{}'::jsonb,
    embedding   jsonb,
    created_at  timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX ix_lego_memory_items_owner_scope_created_at ON lego_memory_items (owner_scope, created_at DESC);
CREATE INDEX ix_lego_memory_items_content_trgm ON lego_memory_items USING gin (content gin_trgm_ops);

-- ---------------------------------------------------------------------------
-- 知识库（元数据在 PG，向量在 Milvus）
-- ---------------------------------------------------------------------------
CREATE TABLE lego_kb_collections
(
    id                  varchar(32) PRIMARY KEY,
    name                text        NOT NULL,
    description         text,
    embedding_model_id  varchar(32) NOT NULL REFERENCES lego_models (id),
    embedding_dims      int         NOT NULL,
    vector_store_kind   varchar(32) NOT NULL DEFAULT 'MILVUS',
    vector_store_config jsonb       NOT NULL DEFAULT '{}'::jsonb,
    chunk_strategy      varchar(32) NOT NULL DEFAULT 'FIXED_WINDOW',
    chunk_params        jsonb       NOT NULL DEFAULT '{
      "maxChars": 900,
      "overlap": 120
    }'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

COMMENT
ON COLUMN lego_kb_collections.embedding_dims IS '与上游 embedding 输出及 Milvus 向量字段维度一致';
COMMENT
ON COLUMN lego_kb_collections.vector_store_kind IS 'MILVUS 等；由应用解析 vector_store_config';
COMMENT
ON COLUMN lego_kb_collections.vector_store_config IS '连接与物理 collection 等 JSON：host、port、collectionName、token 等';
COMMENT
ON COLUMN lego_kb_collections.chunk_strategy IS 'FIXED_WINDOW | PARAGRAPH | HEADING_SECTION';
COMMENT
ON COLUMN lego_kb_collections.chunk_params IS 'JSON：maxChars、overlap；HEADING_SECTION 另有 headingLevel、leadMaxChars';

CREATE INDEX ix_lego_kb_collections_created_at ON lego_kb_collections (created_at DESC);
CREATE INDEX ix_lego_kb_collections_embedding_model_id ON lego_kb_collections (embedding_model_id);

CREATE TABLE lego_kb_documents
(
    id                   varchar(32) PRIMARY KEY,
    collection_id        varchar(32) NOT NULL REFERENCES lego_kb_collections (id) ON DELETE CASCADE,
    title                text        NOT NULL,
    body                 text        NOT NULL,
    body_rich            text,
    status               varchar(32) NOT NULL DEFAULT 'PENDING',
    error_message        text,
    linked_tool_ids      jsonb       NOT NULL DEFAULT '[]'::jsonb,
    tool_output_bindings jsonb       NOT NULL DEFAULT '{
      "mappings": []
    }'::jsonb,
    metadata             jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_lego_kb_documents_status
        CHECK (status IN ('PENDING', 'READY', 'FAILED'))
);

COMMENT
ON COLUMN lego_kb_documents.body IS '用于分块、向量化与召回的 Markdown 正文';
COMMENT
ON COLUMN lego_kb_documents.body_rich IS '可选：富文本 HTML；可为空';
COMMENT
ON COLUMN lego_kb_documents.linked_tool_ids IS '本条知识关联的工具 ID（JSON 字符串数组）';
COMMENT
ON COLUMN lego_kb_documents.tool_output_bindings IS '占位符与工具 JSON 出参映射：{"mappings":[...]}';

CREATE INDEX ix_lego_kb_documents_collection_created ON lego_kb_documents (collection_id, created_at DESC);

ANALYZE;
