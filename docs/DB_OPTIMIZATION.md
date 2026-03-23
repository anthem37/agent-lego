# 数据库结构说明（基线 + 设计要点）

## Flyway 基线

- **空库**仅执行 **`backend/src/main/resources/db/migration/V1__baseline.sql`**（当前唯一迁移）：`pg_trgm`、全表最终形态、KB
  元数据表、MCP 种子；**无** pgvector、**无** `lego_kb_chunks`。
- **已有 `flyway_schema_history` 的旧库**：不要直接换成单文件基线；应保留原迁移链，或**新建库并迁移数据**后再用基线。

## 设计要点

- **智能体工具**：`lego_agent_tools(agent_id, tool_id)`，外键到 `lego_tools`；删除工具若仍被引用则 `RESTRICT`。
- **查询**：模型/智能体/工作流运行/评测运行/KB 文档列表等见各迁移 SQL；**KB 向量检索不在 PostgreSQL**
  （Milvus）。
- **约束**：工作流/评测运行 `status`、KB 文档 `status` 使用 `CHECK`。
- **KB 向量**：仅存外置 **Milvus**；PG 中 `lego_kb_collections.vector_store_config` 描述连接与物理 collection。
- **模型删除**：`lego_agents.model_id` → `ON DELETE SET NULL`。

## 应用层约定

- 新建智能体：写 `lego_agents` + `lego_agent_tools`（同一事务）。
- 读智能体：`LEFT JOIN lego_agent_tools` + `string_agg(tool_id ORDER BY tool_id)` 还原 `toolIds`。

## 回滚

不建议对生产库做 Flyway 回滚；若必须回退，需自定义脚本并 `flyway repair`。
