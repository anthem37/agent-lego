-- 记忆策略向量链路：引用公共向量库 profile + 合并后的 vector_store_config（与知识库集合一致）；物理集合独占绑定 memory_policy_id。

ALTER TABLE lego_memory_policies
    ADD COLUMN vector_store_profile_id varchar(32) NULL REFERENCES lego_vector_store_profiles (id),
    ADD COLUMN vector_store_config_json jsonb NOT NULL DEFAULT '{}'::jsonb,
    ADD COLUMN vector_min_score double precision NOT NULL DEFAULT 0.15
        CHECK (vector_min_score >= 0.0 AND vector_min_score <= 1.0);

COMMENT
ON COLUMN lego_memory_policies.vector_store_profile_id IS 'VECTOR/HYBRID 时引用的公共向量库；与 vector_store_config_json 合并后供 Milvus/Qdrant';
COMMENT
ON COLUMN lego_memory_policies.vector_store_config_json IS '合并后的外置库配置（与 KB 一致，通常含 collectionName）；连接信息来自 profile';
COMMENT
ON COLUMN lego_memory_policies.vector_min_score IS '向量检索最小相似度阈值（与 Kb 召回一致，越大越严）';

ALTER TABLE lego_vector_store_collection_bindings
    ADD COLUMN memory_policy_id varchar(32) NULL REFERENCES lego_memory_policies (id) ON DELETE CASCADE;

ALTER TABLE lego_vector_store_collection_bindings
DROP
CONSTRAINT chk_vscb_kb_collection;

ALTER TABLE lego_vector_store_collection_bindings
    ADD CONSTRAINT chk_vscb_kb_or_memory CHECK (
        (kb_collection_id IS NOT NULL AND memory_policy_id IS NULL)
            OR (kb_collection_id IS NULL AND memory_policy_id IS NOT NULL)
        );

CREATE UNIQUE INDEX ux_lego_vscb_memory_policy ON lego_vector_store_collection_bindings (memory_policy_id) WHERE memory_policy_id IS NOT NULL;

COMMENT
ON COLUMN lego_vector_store_collection_bindings.memory_policy_id IS '独占物理集合的记忆策略（与 kb_collection_id 二选一）';
