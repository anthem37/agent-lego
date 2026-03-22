-- AgentScope：平台侧区分 ReActAgent（工具+多步推理）与轻量对话（无工具、maxIters=1）
ALTER TABLE lego_agents
    ADD COLUMN IF NOT EXISTS runtime_kind varchar (32) NOT NULL DEFAULT 'REACT';
ALTER TABLE lego_agents
    ADD COLUMN IF NOT EXISTS max_react_iters int NOT NULL DEFAULT 10;

COMMENT
ON COLUMN lego_agents.runtime_kind IS 'REACT=ReActAgent；CHAT=对话（无工具，固定 maxIters=1）';
COMMENT
ON COLUMN lego_agents.max_react_iters IS 'ReActAgent.maxIters，仅 runtime_kind=REACT 时生效';
