/**
 * 与后端 AgentApplicationService / AgentScope 对齐的运行时形态。
 * HTTP 同步路径统一通过 ReActAgent 构建；CHAT 为不挂载工具且 maxIters=1 的轻量形态。
 */
export const AGENT_RUNTIME = {
    REACT: "REACT",
    CHAT: "CHAT",
} as const;

export type AgentRuntimeKind = (typeof AGENT_RUNTIME)[keyof typeof AGENT_RUNTIME];

export const AGENT_RUNTIME_OPTIONS: {
    value: AgentRuntimeKind;
    title: string;
    badge: string;
    summary: string;
    bullets: string[];
}[] = [
    {
        value: "REACT",
        title: "ReAct 推理智能体",
        badge: "ReActAgent",
        summary: "工具调用 + 多步推理（reasoning loop），适合需要查数、调 API、多步任务的场景。",
        bullets: [
            "对应 AgentScope：io.agentscope.core.ReActAgent（toolkit + 会话内 InMemoryMemory + 可选 Knowledge RAG）",
            "可配置 ReAct 最大迭代步数（maxIters），与官方 Builder 一致",
            "可选绑定工具、平台记忆检索、知识库向量召回",
        ],
    },
    {
        value: "CHAT",
        title: "对话智能体",
        badge: "轻量",
        summary: "不挂载工具、单步推理，适合纯问答、品牌话术等。",
        bullets: [
            "运行时仍由 ReActAgent 执行，但不注册工具，且 maxIters 固定为 1",
            "可与记忆、知识库策略组合（与 ReAct 相同的策略模型）",
            "若需人机协同、Studio 等能力，请使用对应会话/网关能力（非本页同步试运行）",
        ],
    },
];
