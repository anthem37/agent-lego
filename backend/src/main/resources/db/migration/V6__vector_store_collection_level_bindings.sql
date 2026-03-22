-- 独占粒度从「每个 profile」改为「每个物理 collection」：同一 profile 下不同物理集合可分别绑定知识库或长期记忆；
-- 同一 (profile_id, physical_collection_name) 至多一种写入方（KB 或 MEMORY）。

CREATE TABLE lego_vector_store_collection_bindings
(
    profile_id               varchar(32)  NOT NULL REFERENCES lego_vector_store_profiles (id) ON DELETE CASCADE,
    physical_collection_name varchar(512) NOT NULL,
    binding_kind             varchar(16)  NOT NULL CHECK (binding_kind IN ('KB', 'MEMORY')),
    kb_collection_id         varchar(32) NULL REFERENCES lego_kb_collections (id) ON DELETE CASCADE,
    memory_owner_scope       varchar(512) NULL,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (profile_id, physical_collection_name),
    CONSTRAINT chk_vscb_kind_kb
        CHECK ((binding_kind = 'KB' AND kb_collection_id IS NOT NULL AND memory_owner_scope IS NULL)
            OR (binding_kind = 'MEMORY' AND kb_collection_id IS NULL AND memory_owner_scope IS NOT NULL))
);

COMMENT
ON TABLE lego_vector_store_collection_bindings IS '物理集合级独占：每个 profile×物理 collection 至多绑定一个知识库集合或一条长期记忆 ownerScope';

CREATE UNIQUE INDEX ux_lego_vscb_kb_collection ON lego_vector_store_collection_bindings (kb_collection_id) WHERE kb_collection_id IS NOT NULL;

CREATE UNIQUE INDEX ux_lego_vscb_memory_scope ON lego_vector_store_collection_bindings (profile_id, memory_owner_scope) WHERE binding_kind = 'MEMORY' AND memory_owner_scope IS NOT NULL;

-- 从知识库回填（物理名 = vector_store_config.collectionName）
INSERT INTO lego_vector_store_collection_bindings (profile_id, physical_collection_name, binding_kind, kb_collection_id,
                                                   memory_owner_scope)
SELECT c.vector_store_profile_id,
       trim(c.vector_store_config ->> 'collectionName'),
       'KB',
       c.id,
       NULL
FROM lego_kb_collections c
WHERE c.vector_store_profile_id IS NOT NULL
  AND c.vector_store_config ->> 'collectionName' IS NOT NULL
  AND trim (c.vector_store_config ->> 'collectionName') <> '';

-- 从旧记忆绑定回填（物理名与 Java MemoryPhysicalCollectionNames.forOwner 一致）
INSERT INTO lego_vector_store_collection_bindings (profile_id, physical_collection_name, binding_kind, kb_collection_id,
                                                   memory_owner_scope)
SELECT m.profile_id,
       'mem_' || regexp_replace(m.profile_id, '[^a-zA-Z0-9_]', '_', 'g') || '_' ||
       substr(md5(m.profile_id || '::' || m.memory_owner_scope), 1, 16),
       'MEMORY',
       NULL,
       m.memory_owner_scope
FROM lego_vector_store_memory_bindings m;

DROP INDEX IF EXISTS ux_lego_kb_collections_vector_store_profile_id;

DROP TABLE lego_vector_store_memory_bindings;
