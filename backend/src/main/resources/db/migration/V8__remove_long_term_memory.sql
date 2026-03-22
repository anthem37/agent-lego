-- 移除平台「长期记忆」能力：业务表、智能体 memory_policy、向量绑定中的 MEMORY 类型。

DROP TABLE IF EXISTS lego_memory_items;

DROP INDEX IF EXISTS ux_lego_vscb_memory_scope;

DELETE
FROM lego_vector_store_collection_bindings
WHERE binding_kind = 'MEMORY';

ALTER TABLE lego_vector_store_collection_bindings DROP CONSTRAINT chk_vscb_kind_kb;
ALTER TABLE lego_vector_store_collection_bindings DROP COLUMN memory_owner_scope;
ALTER TABLE lego_vector_store_collection_bindings DROP COLUMN binding_kind;

ALTER TABLE lego_vector_store_collection_bindings
    ADD CONSTRAINT chk_vscb_kb_collection CHECK (kb_collection_id IS NOT NULL);

COMMENT
ON TABLE lego_vector_store_collection_bindings IS '物理集合级独占：每个 profile×物理 collection 至多绑定一个知识库集合';

ALTER TABLE lego_agents DROP COLUMN IF EXISTS memory_policy;
