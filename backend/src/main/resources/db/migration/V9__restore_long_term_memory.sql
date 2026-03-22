-- 恢复平台「长期记忆」MVP：条目表 + 智能体 memory_policy（关键词 / pg_trgm；语义检索可后续扩展）。

CREATE TABLE IF NOT EXISTS lego_memory_items
(
    id
    varchar
(
    32
) PRIMARY KEY,
    owner_scope text NOT NULL,
    content text NOT NULL,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    embedding jsonb,
    created_at timestamptz NOT NULL DEFAULT now
(
),
    updated_at timestamptz
    );

CREATE INDEX IF NOT EXISTS ix_lego_memory_items_owner_scope_created_at
    ON lego_memory_items (owner_scope, created_at DESC);

CREATE INDEX IF NOT EXISTS ix_lego_memory_items_content_trgm
    ON lego_memory_items USING gin (content gin_trgm_ops);

COMMENT
ON TABLE lego_memory_items IS '长期记忆条目：按 owner_scope 隔离；检索 MVP 为 ILIKE / pg_trgm';

ALTER TABLE lego_agents
    ADD COLUMN IF NOT EXISTS memory_policy jsonb NOT NULL DEFAULT '{}'::jsonb;

COMMENT
ON COLUMN lego_agents.memory_policy IS '长期记忆策略 JSON：ownerScope、topK、writeBack、writeBackOnDuplicate 等';
