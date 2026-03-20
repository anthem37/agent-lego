-- 默认 MCP 工具：连接「本服务」对外暴露的 MCP Server（见 agentlego.mcp.server.sse-path，默认路径 /mcp）。
-- endpoint 使用 127.0.0.1:8080；若端口或部署不同，请在工具管理中修改 definition.endpoint。

insert into platform_tools (id, tool_type, name, definition, created_at)
values (
           '1800000000001000001',
           'MCP',
           'mcp_local_echo',
           jsonb_build_object(
                   'endpoint', 'http://127.0.0.1:8080/mcp',
                   'mcpToolName', 'echo',
                   'description', '默认示例：经 MCP 调用本机内置 echo（回显文本）。请按实际服务地址修改 endpoint。',
                   'parameters', jsonb_build_object(
                           'type', 'object',
                           'properties', jsonb_build_object(
                                   'content', jsonb_build_object(
                                       'type', 'string',
                                       'description', '要回显的文本'
                                             )
                                         ),
                           'required', jsonb_build_array('content')
                                 )
           ),
           now()
       )
on conflict (tool_type, name) do nothing;

insert into platform_tools (id, tool_type, name, definition, created_at)
values (
           '1800000000001000002',
           'MCP',
           'mcp_local_now',
           jsonb_build_object(
                   'endpoint', 'http://127.0.0.1:8080/mcp',
                   'mcpToolName', 'now',
                   'description', '默认示例：经 MCP 调用本机内置 now（当前时间 ISO-8601）。请按实际服务地址修改 endpoint。',
                   'parameters', jsonb_build_object(
                           'type', 'object',
                           'properties', jsonb_build_object(
                                   'content', jsonb_build_object(
                                       'type', 'string',
                                       'description', '可选，可为空'
                                             )
                                         ),
                           'required', '[]'::jsonb
                                 )
           ),
           now()
       )
on conflict (tool_type, name) do nothing;

insert into platform_tools (id, tool_type, name, definition, created_at)
values (
           '1800000000001000003',
           'MCP',
           'mcp_local_format_line',
           jsonb_build_object(
                   'endpoint', 'http://127.0.0.1:8080/mcp',
                   'mcpToolName', 'format_line',
                   'description', '默认示例：经 MCP 调用本机内置 format_line（模板 {who}/{what}）。请按实际服务地址修改 endpoint。',
                   'parameters', jsonb_build_object(
                           'type', 'object',
                           'properties', jsonb_build_object(
                                   'template', jsonb_build_object('type', 'string', 'description', '含 {who}、{what} 的模板'),
                                   'who', jsonb_build_object('type', 'string', 'description', '替换 {who}'),
                                   'what', jsonb_build_object('type', 'string', 'description', '替换 {what}，可选')
                                         ),
                           'required', jsonb_build_array('template', 'who')
                                 )
           ),
           now()
       )
on conflict (tool_type, name) do nothing;
