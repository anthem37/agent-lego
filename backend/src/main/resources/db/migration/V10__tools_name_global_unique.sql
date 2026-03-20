-- AgentScope Toolkit 以工具名为键；若不同类型下存在同名工具，注册到同一 Toolkit 时会冲突或覆盖。
-- 与业务层「全平台 name 唯一（大小写不敏感）」校验一致。
create unique index if not exists ux_platform_tools_name_lower on platform_tools (lower (name));
