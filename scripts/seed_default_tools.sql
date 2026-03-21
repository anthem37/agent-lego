-- ---------------------------------------------------------------------------
-- 可选种子：各类型演示工具（LOCAL / HTTP / MCP / WORKFLOW）
-- ---------------------------------------------------------------------------
-- 用法：在 PostgreSQL 中针对 agent-lego 库执行，例如：
--   psql "$DATABASE_URL" -f scripts/seed_default_tools.sql
--
-- 说明：
-- - 以「全平台 name 唯一」为准：仅当不存在同名（忽略大小写）工具时才插入。
-- - LOCAL 的 name 必须与后端内置一致（echo / now / format_line），definition 可为 {}。
-- - HTTP 使用 jsonplaceholder.typicode.com 公网演示接口（请确保运行环境可出网且未被 SSRF 策略拦截）。
-- - MCP 默认 endpoint 指向本机 8080，与 V1 迁移里示例一致；请按实际 MCP 地址修改或删除重复项。
-- - WORKFLOW 工具依赖 lego_workflows 中已存在的工作流 id；本脚本会先插入两条占位工作流。
-- ---------------------------------------------------------------------------

BEGIN;

-- 占位工作流（供 WORKFLOW 类型工具绑定；definition 可在管理端再编辑）
INSERT INTO lego_workflows (id, name, definition)
SELECT '2900000000005000001',
       'seed_wf_demo_alpha',
       '{"kind":"seed","note":"占位演示，请替换为真实编排"}'::jsonb WHERE NOT EXISTS (SELECT 1 FROM lego_workflows w WHERE w.id = '2900000000005000001')
  AND NOT EXISTS (SELECT 1 FROM lego_workflows w WHERE lower(trim(w.name)) = lower(trim('seed_wf_demo_alpha')));

INSERT INTO lego_workflows (id, name, definition)
SELECT '2900000000005000002',
       'seed_wf_demo_beta',
       '{"kind":"seed","note":"占位演示，请替换为真实编排"}'::jsonb WHERE NOT EXISTS (SELECT 1 FROM lego_workflows w WHERE w.id = '2900000000005000002')
  AND NOT EXISTS (SELECT 1 FROM lego_workflows w WHERE lower(trim(w.name)) = lower(trim('seed_wf_demo_beta')));

-- LOCAL（3）：内置名 + 空 definition
INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000002000001',
       'LOCAL',
       'ACTION',
       'echo',
       '内置回显',
       '种子数据：LOCAL echo，入参 content。',
       '{}'::jsonb, now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('echo')));

INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000002000002',
       'LOCAL',
       'QUERY',
       'now',
       '当前时间',
       '种子数据：LOCAL now。',
       '{}'::jsonb, now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('now')));

INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000002000003',
       'LOCAL',
       'ACTION',
       'format_line',
       '模板格式化',
       '种子数据：LOCAL format_line（template/who/what）。',
       '{}'::jsonb, now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('format_line')));

-- HTTP（3）：jsonplaceholder 只读/写入演示
INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000003000001',
       'HTTP',
       'QUERY',
       'seed_http_json_user',
       '演示 GET 用户',
       'GET https://jsonplaceholder.typicode.com/users/1',
       jsonb_build_object(
               'url', 'https://jsonplaceholder.typicode.com/users/1',
               'method', 'GET',
               'description', '拉取 JSONPlaceholder 用户 1（演示只读 HTTP 工具）'
       ),
       now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('seed_http_json_user')));

INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000003000002',
       'HTTP',
       'QUERY',
       'seed_http_json_todo',
       '演示 GET 待办',
       'GET https://jsonplaceholder.typicode.com/todos/1',
       jsonb_build_object(
               'url', 'https://jsonplaceholder.typicode.com/todos/1',
               'method', 'GET',
               'description', '拉取待办 1'
       ),
       now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('seed_http_json_todo')));

INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000003000003',
       'HTTP',
       'ACTION',
       'seed_http_json_post',
       '演示 POST 帖子',
       'POST https://jsonplaceholder.typicode.com/posts',
       jsonb_build_object(
               'url', 'https://jsonplaceholder.typicode.com/posts',
               'method', 'POST',
               'sendJsonBody', true,
               'description', '创建帖子；入参可为 title、body、userId 等字段（JSON body）',
               'parameters', jsonb_build_object(
                       'type', 'object',
                       'properties', jsonb_build_object(
                               'title', jsonb_build_object('type', 'string'),
                               'body', jsonb_build_object('type', 'string'),
                               'userId', jsonb_build_object('type', 'integer')
                                     ),
                       'required', jsonb_build_array('title', 'body', 'userId')
                             )
       ),
       now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('seed_http_json_post')));

-- MCP（3）：与 V1 示例相同 endpoint，平台 name 不同，避免与 mcp_local_* 冲突
INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000004000001',
       'MCP',
       'ACTION',
       'seed_mcp_echo',
       'MCP 回显（种子）',
       '远端工具 echo；请按环境修改 definition.endpoint。',
       jsonb_build_object(
               'endpoint', 'http://127.0.0.1:8080/mcp',
               'mcpToolName', 'echo',
               'description', '经 MCP 调用本服务暴露的 echo',
               'parameters', jsonb_build_object(
                       'type', 'object',
                       'properties', jsonb_build_object(
                               'content', jsonb_build_object('type', 'string', 'description', '要回显的文本')
                                     ),
                       'required', jsonb_build_array('content')
                             )
       ),
       now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('seed_mcp_echo')));

INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000004000002',
       'MCP',
       'QUERY',
       'seed_mcp_now',
       'MCP 时间（种子）',
       '远端工具 now。',
       jsonb_build_object(
               'endpoint', 'http://127.0.0.1:8080/mcp',
               'mcpToolName', 'now',
               'description', '经 MCP 调用 now',
               'parameters', jsonb_build_object(
                       'type', 'object',
                       'properties', jsonb_build_object(
                               'content', jsonb_build_object('type', 'string', 'description', '可选')
                                     ),
                       'required', '[]'::jsonb
                             )
       ),
       now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('seed_mcp_now')));

INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000004000003',
       'MCP',
       'ACTION',
       'seed_mcp_format_line',
       'MCP 格式化（种子）',
       '远端工具 format_line。',
       jsonb_build_object(
               'endpoint', 'http://127.0.0.1:8080/mcp',
               'mcpToolName', 'format_line',
               'description', '经 MCP 调用 format_line',
               'parameters', jsonb_build_object(
                       'type', 'object',
                       'properties', jsonb_build_object(
                               'template', jsonb_build_object('type', 'string', 'description', '含 {who}、{what}'),
                               'who', jsonb_build_object('type', 'string'),
                               'what', jsonb_build_object('type', 'string')
                                     ),
                       'required', jsonb_build_array('template', 'who')
                             )
       ),
       now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('seed_mcp_format_line')));

-- WORKFLOW（2）：绑定上方占位工作流
INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000006000001',
       'WORKFLOW',
       'ACTION',
       'seed_wf_tool_alpha',
       '工作流工具 α',
       '同步执行工作流 seed_wf_demo_alpha（占位）。',
       jsonb_build_object('workflowId', '2900000000005000001'),
       now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('seed_wf_tool_alpha')));

INSERT INTO lego_tools (id, tool_type, tool_category, name, display_label, description, definition, created_at)
SELECT '2900000000006000002',
       'WORKFLOW',
       'ACTION',
       'seed_wf_tool_beta',
       '工作流工具 β',
       '同步执行工作流 seed_wf_demo_beta（占位）。',
       jsonb_build_object('workflowId', '2900000000005000002'),
       now() WHERE NOT EXISTS (SELECT 1 FROM lego_tools t WHERE lower(trim(t.name)) = lower(trim('seed_wf_tool_beta')));

COMMIT;
