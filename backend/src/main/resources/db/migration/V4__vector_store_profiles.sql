-- 独立向量库连接配置：知识库与长期记忆共用；ANN 仅在外置 Milvus/Qdrant，不使用 PostgreSQL pgvector 扩展。
-- 长期记忆条目表 lego_memory_items.embedding 不再写入新向量（保留列兼容历史数据）。

CREATE TABLE lego_vector_store_profiles
(
    id                  varchar(32) PRIMARY KEY,
    name                text        NOT NULL,
    vector_store_kind   varchar(32) NOT NULL,
    vector_store_config jsonb       NOT NULL DEFAULT '{}'::jsonb,
    embedding_model_id  varchar(32) NOT NULL REFERENCES lego_models (id),
    embedding_dims      int         NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_lego_vector_store_profiles_name_lower ON lego_vector_store_profiles (lower(name));

ALTER TABLE lego_kb_collections
    ADD COLUMN vector_store_profile_id varchar(32) REFERENCES lego_vector_store_profiles (id);

CREATE INDEX ix_lego_kb_collections_vector_store_profile_id ON lego_kb_collections (vector_store_profile_id);

COMMENT
ON TABLE lego_vector_store_profiles IS '外置向量库连接与维度模板；知识库集合可引用，记忆按 ownerScope 派生物理 collection 名';
COMMENT
ON COLUMN lego_kb_collections.vector_store_profile_id IS '可选：创建集合时从该配置合并连接参数';
