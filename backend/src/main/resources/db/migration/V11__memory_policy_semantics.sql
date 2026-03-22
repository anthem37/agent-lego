-- 记忆策略语义字段：目的、作用域解释、检索模式、写入模式（与知识库 RAG 边界见 docs/memory-strategy.md）

ALTER TABLE lego_memory_policies
    ADD COLUMN strategy_kind varchar(32) NOT NULL DEFAULT 'EPISODIC_DIALOGUE',
    ADD COLUMN scope_kind varchar(32) NOT NULL DEFAULT 'CUSTOM_NAMESPACE',
    ADD COLUMN retrieval_mode varchar(32) NOT NULL DEFAULT 'KEYWORD',
    ADD COLUMN write_mode varchar(32) NOT NULL DEFAULT 'OFF';

UPDATE lego_memory_policies
SET write_mode = CASE WHEN write_back THEN 'ASSISTANT_RAW' ELSE 'OFF' END;

ALTER TABLE lego_memory_policies
DROP
COLUMN write_back;

ALTER TABLE lego_memory_policies
    ADD CONSTRAINT chk_lego_memory_policies_strategy_kind
        CHECK (strategy_kind IN ('EPISODIC_DIALOGUE', 'USER_PROFILE', 'TASK_CONTEXT')),
    ADD CONSTRAINT chk_lego_memory_policies_scope_kind
        CHECK (scope_kind IN ('CUSTOM_NAMESPACE', 'TENANT', 'USER', 'AGENT')),
    ADD CONSTRAINT chk_lego_memory_policies_retrieval_mode
        CHECK (retrieval_mode IN ('KEYWORD', 'VECTOR', 'HYBRID')),
    ADD CONSTRAINT chk_lego_memory_policies_write_mode
        CHECK (write_mode IN ('OFF', 'ASSISTANT_RAW', 'ASSISTANT_SUMMARY'));

COMMENT
ON COLUMN lego_memory_policies.strategy_kind IS '记忆目的：情景对话 / 用户画像 / 任务上下文';
COMMENT
ON COLUMN lego_memory_policies.scope_kind IS 'owner_scope 语义：自定义命名空间或预留租户/用户/智能体';
COMMENT
ON COLUMN lego_memory_policies.retrieval_mode IS '检索：KEYWORD 已实现；VECTOR/HYBRID 待接向量库，运行时可降级';
COMMENT
ON COLUMN lego_memory_policies.write_mode IS '写入：OFF | 助手原文 | 摘要（摘要能力待实现）';
