-- Agent Lego 单文件基线（空库专用）。已合并原 V1–V21 演进史；若库中已有 flyway_schema_history 旧版本，请勿替换迁移文件。
-- 依赖：PostgreSQL + pgvector 镜像或已安装 vector 扩展。

CREATE
EXTENSION IF NOT EXISTS vector;
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
    id         varchar(32) PRIMARY KEY,
    tool_type  varchar(32) NOT NULL,
    name       text        NOT NULL,
    definition jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (tool_type, name)
);

CREATE UNIQUE INDEX ux_lego_tools_name_lower ON lego_tools (lower(name));

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
-- 知识库 v3 + pgvector
-- ---------------------------------------------------------------------------
CREATE TABLE lego_kb_collections
(
    id                 varchar(32) PRIMARY KEY,
    name               text        NOT NULL,
    description        text,
    embedding_model_id varchar(32) NOT NULL REFERENCES lego_models (id),
    embedding_dims     int         NOT NULL DEFAULT 1536,
    created_at         timestamptz NOT NULL DEFAULT now(),
    updated_at         timestamptz NOT NULL DEFAULT now()
);

COMMENT
ON COLUMN lego_kb_collections.embedding_dims IS '上游 embedding 输出维度；入库向量 padding 到 3072 维';

CREATE INDEX ix_lego_kb_collections_created_at ON lego_kb_collections (created_at DESC);
CREATE INDEX ix_lego_kb_collections_embedding_model_id ON lego_kb_collections (embedding_model_id);

CREATE TABLE lego_kb_documents
(
    id            varchar(32) PRIMARY KEY,
    collection_id varchar(32) NOT NULL REFERENCES lego_kb_collections (id) ON DELETE CASCADE,
    title         text        NOT NULL,
    body          text        NOT NULL,
    status        varchar(32) NOT NULL DEFAULT 'PENDING',
    error_message text,
    metadata      jsonb       NOT NULL DEFAULT '{}'::jsonb,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_lego_kb_documents_status
        CHECK (status IN ('PENDING', 'READY', 'FAILED'))
);

CREATE INDEX ix_lego_kb_documents_collection_created ON lego_kb_documents (collection_id, created_at DESC);

CREATE TABLE lego_kb_chunks
(
    id            varchar(32) PRIMARY KEY,
    document_id   varchar(32) NOT NULL REFERENCES lego_kb_documents (id) ON DELETE CASCADE,
    collection_id varchar(32) NOT NULL REFERENCES lego_kb_collections (id) ON DELETE CASCADE,
    chunk_index   int         NOT NULL,
    content       text        NOT NULL,
    embedding_vec vector(3072),
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (document_id, chunk_index)
);

COMMENT
ON COLUMN lego_kb_chunks.embedding_vec IS 'pgvector 余弦检索；维度固定 3072，短向量零填充';

CREATE INDEX ix_lego_kb_chunks_collection_id ON lego_kb_chunks (collection_id);
CREATE INDEX ix_lego_kb_chunks_document_id ON lego_kb_chunks (document_id);

-- pgvector：在默认 8KB 页下 HNSW/IVFFlat 索引约限 2000 维；本列 vector(3072) 无法建 ANN 索引。
-- 检索仍用 SQL 余弦算子 (<=>)；数据量大时可：按 collection 过滤缩小扫描、降维后再建索引、或外置向量库。
-- ---------------------------------------------------------------------------
-- 可选种子：默认 MCP 工具（与 agentlego.mcp.server.sse-path 一致时可改 endpoint）
-- ---------------------------------------------------------------------------
INSERT INTO lego_tools (id, tool_type, name, definition, created_at)
VALUES ('1800000000001000001', 'MCP', 'mcp_local_echo',
        jsonb_build_object(
                'endpoint', 'http://127.0.0.1:8080/mcp',
                'mcpToolName', 'echo',
                'description', '默认示例：经 MCP 调用本机内置 echo。请按实际服务地址修改 endpoint。',
                'parameters', jsonb_build_object(
                        'type', 'object',
                        'properties', jsonb_build_object(
                                'content', jsonb_build_object('type', 'string', 'description', '要回显的文本')
                                      ),
                        'required', jsonb_build_array('content')
                              )
        ),
        now()) ON CONFLICT (tool_type, name) DO NOTHING;

INSERT INTO lego_tools (id, tool_type, name, definition, created_at)
VALUES ('1800000000001000002', 'MCP', 'mcp_local_now',
        jsonb_build_object(
                'endpoint', 'http://127.0.0.1:8080/mcp',
                'mcpToolName', 'now',
                'description', '默认示例：经 MCP 调用本机内置 now。请按实际服务地址修改 endpoint。',
                'parameters', jsonb_build_object(
                        'type', 'object',
                        'properties', jsonb_build_object(
                                'content', jsonb_build_object('type', 'string', 'description', '可选')
                                      ),
                        'required', '[]'::jsonb
                              )
        ),
        now()) ON CONFLICT (tool_type, name) DO NOTHING;

INSERT INTO lego_tools (id, tool_type, name, definition, created_at)
VALUES ('1800000000001000003', 'MCP', 'mcp_local_format_line',
        jsonb_build_object(
                'endpoint', 'http://127.0.0.1:8080/mcp',
                'mcpToolName', 'format_line',
                'description', '默认示例：经 MCP 调用 format_line。请按实际服务地址修改 endpoint。',
                'parameters', jsonb_build_object(
                        'type', 'object',
                        'properties', jsonb_build_object(
                                'template', jsonb_build_object('type', 'string', 'description', '含 {who}、{what}'),
                                'who', jsonb_build_object('type', 'string'),
                                'what', jsonb_build_object('type', 'string')
                                      ),
                        'required', jsonb_build_array('template', 'who')
                              )
        ),
        now()) ON CONFLICT (tool_type, name) DO NOTHING;

ANALYZE;
