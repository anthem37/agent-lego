-- 一个向量库 profile 仅允许绑定一个写入方：要么一个知识库集合，要么一个长期记忆 ownerScope（互斥）。
-- 知识库侧：同一 profile 至多一条 kb 集合（部分唯一索引）。
-- 记忆侧：lego_vector_store_memory_bindings 记录 profile -> ownerScope。

CREATE TABLE lego_vector_store_memory_bindings
(
    profile_id         varchar(32) PRIMARY KEY REFERENCES lego_vector_store_profiles (id) ON DELETE CASCADE,
    memory_owner_scope varchar(512) NOT NULL
);

COMMENT
ON TABLE lego_vector_store_memory_bindings IS '长期记忆独占绑定：一个 profile 仅服务一个 ownerScope 的向量写入/检索';
COMMENT
ON COLUMN lego_vector_store_memory_bindings.memory_owner_scope IS '与 memoryPolicy.ownerScope / CreateMemoryItemRequest.ownerScope 一致';

CREATE UNIQUE INDEX ux_lego_kb_collections_vector_store_profile_id
    ON lego_kb_collections (vector_store_profile_id) WHERE vector_store_profile_id IS NOT NULL;
