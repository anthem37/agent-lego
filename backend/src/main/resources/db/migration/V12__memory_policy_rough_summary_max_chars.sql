-- ASSISTANT_SUMMARY 本地粗略摘要：可配置最大字符数（UTF-16 码元）；NULL 表示运行时默认 480。

ALTER TABLE lego_memory_policies
    ADD COLUMN rough_summary_max_chars int NULL;

ALTER TABLE lego_memory_policies
    ADD CONSTRAINT chk_lego_memory_policies_rough_summary_max_chars
        CHECK (rough_summary_max_chars IS NULL OR (rough_summary_max_chars >= 16 AND rough_summary_max_chars <= 8192));

COMMENT
ON COLUMN lego_memory_policies.rough_summary_max_chars IS 'ASSISTANT_SUMMARY 粗略摘要最大字符数；空则使用平台默认 480';
