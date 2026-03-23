-- 记忆策略一等实体：lego_memory_policies；智能体 memory_policy_id；条目 policy_id 归属策略。

CREATE TABLE lego_memory_policies
(
    id                      varchar(32) PRIMARY KEY,
    name                    text        NOT NULL,
    description             text,
    owner_scope             text        NOT NULL,
    top_k                   int         NOT NULL DEFAULT 5,
    write_back              boolean     NOT NULL DEFAULT false,
    write_back_on_duplicate varchar(16) NOT NULL DEFAULT 'skip',
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT chk_lego_memory_policies_top_k CHECK (top_k >= 1 AND top_k <= 32),
    CONSTRAINT chk_lego_memory_policies_wb_dup CHECK (write_back_on_duplicate IN ('skip', 'upsert')),
    CONSTRAINT ux_lego_memory_policies_owner_scope UNIQUE (owner_scope)
);

CREATE INDEX ix_lego_memory_policies_created_at ON lego_memory_policies (created_at DESC);

COMMENT
ON TABLE lego_memory_policies IS '记忆策略：owner_scope 隔离数据面；检索/写回参数；智能体引用本表 id';

-- A) 由现有条目推导策略（每个 owner_scope 一条）
INSERT INTO lego_memory_policies (id, name, description, owner_scope, top_k, write_back, write_back_on_duplicate,
                                  created_at, updated_at)
SELECT substr(md5(t.owner_scope), 1, 32),
       '迁移：' || t.owner_scope,
       '由 lego_memory_items.owner_scope 迁移',
       t.owner_scope,
       5,
       false,
       'skip',
       now(),
       now()
FROM (SELECT DISTINCT owner_scope FROM lego_memory_items) AS t
WHERE NOT EXISTS (SELECT 1 FROM lego_memory_policies p WHERE p.owner_scope = t.owner_scope);

-- B) 条目挂策略 id，去掉 owner_scope
ALTER TABLE lego_memory_items
    ADD COLUMN policy_id varchar(32) NULL REFERENCES lego_memory_policies (id) ON DELETE CASCADE;

UPDATE lego_memory_items mi
SET policy_id = p.id FROM lego_memory_policies p
WHERE p.owner_scope = mi.owner_scope;

ALTER TABLE lego_memory_items
    ALTER COLUMN policy_id SET NOT NULL;

DROP INDEX IF EXISTS ix_lego_memory_items_owner_scope_created_at;

ALTER TABLE lego_memory_items
DROP
COLUMN owner_scope;

CREATE INDEX ix_lego_memory_items_policy_created_at ON lego_memory_items (policy_id, created_at DESC);

-- C) 由智能体 JSON 补建策略（仅 memory_policy 中仍有 ownerScope 时）
INSERT INTO lego_memory_policies (id, name, description, owner_scope, top_k, write_back, write_back_on_duplicate,
                                  created_at, updated_at)
SELECT DISTINCT
ON (trim (a.memory_policy ->> 'ownerScope'))
    substr(md5(trim (a.memory_policy ->> 'ownerScope')), 1, 32),
    '迁移：' || trim (a.memory_policy ->> 'ownerScope'),
    '由 lego_agents.memory_policy 迁移',
    trim (a.memory_policy ->> 'ownerScope'),
    COALESCE ((a.memory_policy ->> 'topK'):: int, 5),
    COALESCE ((a.memory_policy ->> 'writeBack')::boolean, false),
    CASE
    WHEN lower (trim (coalesce (a.memory_policy ->> 'writeBackOnDuplicate', 'skip'))) = 'upsert' THEN 'upsert'
    ELSE 'skip'
END
,
    now(),
    now()
FROM lego_agents a
WHERE a.memory_policy ->> 'ownerScope' IS NOT NULL
  AND trim(a.memory_policy ->> 'ownerScope') <> ''
  AND NOT EXISTS (SELECT 1
                  FROM lego_memory_policies p
                  WHERE p.owner_scope = trim(a.memory_policy ->> 'ownerScope'))
ORDER BY trim(a.memory_policy ->> 'ownerScope'), a.created_at DESC;

-- D) 用智能体 JSON 覆盖策略参数（同 owner_scope 取最新一条智能体）
UPDATE lego_memory_policies p
SET top_k                   = s.top_k,
    write_back              = s.write_back,
    write_back_on_duplicate = s.wdup,
    updated_at              = now() FROM (SELECT DISTINCT ON (trim(a.memory_policy ->> 'ownerScope')) trim(a.memory_policy ->> 'ownerScope') AS os,
                         COALESCE((a.memory_policy ->> 'topK')::int, 5)                             AS top_k,
                         COALESCE((a.memory_policy ->> 'writeBack')::boolean, false)                AS write_back,
                         CASE
                             WHEN lower(trim(coalesce(
                                     a.memory_policy ->> 'writeBackOnDuplicate',
                                     'skip'))) = 'upsert' THEN 'upsert'
                             ELSE 'skip' END                                                       AS wdup
      FROM lego_agents a
      WHERE a.memory_policy ->> 'ownerScope' IS NOT NULL
        AND trim(a.memory_policy ->> 'ownerScope') <> ''
      ORDER BY trim(a.memory_policy ->> 'ownerScope'), a.created_at DESC) AS s
WHERE p.owner_scope = s.os;

-- E) 智能体改为引用策略 id
ALTER TABLE lego_agents
    ADD COLUMN memory_policy_id varchar(32) NULL REFERENCES lego_memory_policies (id) ON DELETE SET NULL;

UPDATE lego_agents a
SET memory_policy_id = p.id FROM lego_memory_policies p
WHERE trim (a.memory_policy ->> 'ownerScope') = p.owner_scope
  AND a.memory_policy ->> 'ownerScope' IS NOT NULL
  AND trim (a.memory_policy ->> 'ownerScope') <> '';

ALTER TABLE lego_agents
DROP
COLUMN memory_policy;

CREATE INDEX ix_lego_agents_memory_policy_id ON lego_agents (memory_policy_id);
