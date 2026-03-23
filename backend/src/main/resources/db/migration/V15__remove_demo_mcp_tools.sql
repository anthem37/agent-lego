-- 移除历史基线中的演示 MCP 工具（若曾插入）；新库已无此行，本脚本幂等。
DELETE
FROM lego_agent_tools
WHERE tool_id IN ('1800000000001000001', '1800000000001000002', '1800000000001000003');
DELETE
FROM lego_tools
WHERE id IN ('1800000000001000001', '1800000000001000002', '1800000000001000003');

DELETE
FROM lego_local_builtin_tool_exposure
WHERE tool_name IN ('echo', 'now', 'format_line');
