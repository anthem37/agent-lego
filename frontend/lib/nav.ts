export type NavItem = {
    key: string;
    label: string;
    href: string;
    /** 与其它模块关系的一句话（顶栏提示） */
    relation?: string;
};

export type NavGroup = {
    key: string;
    /** 分组标题（侧栏分组名） */
    title: string;
    /** 分组说明：帮助理解模块间依赖 */
    description?: string;
    items: NavItem[];
};

/**
 * 侧栏分组顺序体现「从基础能力 → 编排执行 → 数据沉淀 → 质量协作」的直觉路径。
 * 模型/工具为底座；智能体/工作流/运行为主线；知识库为数据面；评测与 A2A 为扩展。
 */
export const NAV_GROUPS: NavGroup[] = [
    {
        key: "overview",
        title: "概览",
        description: "入口与联调",
        items: [{key: "dashboard", label: "仪表盘", href: "/", relation: "平台总览与快捷入口"}],
    },
    {
        key: "foundation",
        title: "基础能力",
        description: "模型与工具可被智能体、工作流、知识库引用",
        items: [
            {key: "models", label: "模型", href: "/models", relation: "Chat / Embedding 等模型配置"},
            {key: "tools", label: "工具", href: "/tools", relation: "注册后供智能体绑定；知识库可嵌工具标签"},
            {
                key: "vector-store",
                label: "向量库",
                href: "/vector-store",
                relation:
                    "同一页：保存连接与嵌入模型后，点表格选中即可展开远程 collection 运维；?profile= 可深链。每个物理 collection 至多绑定一个知识库集合",
            },
        ],
    },
    {
        key: "runtime",
        title: "编排与执行",
        description: "智能体串联模型与工具，工作流编排步骤，运行查询看轨迹",
        items: [
            {key: "agents", label: "智能体", href: "/agents", relation: "绑定模型、工具与策略"},
            {key: "workflows", label: "工作流", href: "/workflows", relation: "多步骤与分支"},
            {key: "runs", label: "运行查询", href: "/runs", relation: "执行记录与调试"},
        ],
    },
    {
        key: "data",
        title: "数据与知识",
        description: "可检索语料；嵌入模型与知识库策略联动",
        items: [
            {
                key: "memory-policies",
                label: "记忆策略",
                href: "/memory-policies",
                relation: "策略 CRUD 与条目；智能体绑定 memoryPolicyId 后按策略检索/写回",
            },
            {key: "kb", label: "知识库", href: "/kb", relation: "RAG 语料与独立向量库；与「模型」嵌入配置一致"},
        ],
    },
    {
        key: "quality",
        title: "质量与协作",
        description: "评测闭环与多智能体协作",
        items: [
            {key: "evaluations", label: "评测", href: "/evaluations", relation: "数据集与实验"},
            {key: "a2a", label: "A2A", href: "/a2a", relation: "Agent-to-Agent 委派"},
        ],
    },
];

/** 扁平列表（兼容旧代码、面包屑查找等） */
export const NAV_ITEMS: NavItem[] = NAV_GROUPS.flatMap((g) => g.items);

export function navKeyFromPath(pathname: string): string {
    if (pathname === "/") {
        return "dashboard";
    }
    const segments = pathname.split("/").filter(Boolean);
    const first = segments[0] ?? "dashboard";
    if (first === "models") {
        return "models";
    }
    if (first === "tools") {
        return "tools";
    }
    if (first === "vector-store") {
        return "vector-store";
    }
    if (first === "agents") {
        return "agents";
    }
    if (first === "workflows") {
        return "workflows";
    }
    if (first === "runs") {
        return "runs";
    }
    if (first === "memory") {
        return "memory-policies";
    }
    if (first === "memory-policies") {
        return "memory-policies";
    }
    if (first === "kb") {
        return "kb";
    }
    if (first === "evaluations") {
        return "evaluations";
    }
    if (first === "a2a") {
        return "a2a";
    }
    return "dashboard";
}

export type NavContext = {
    /** 当前分组名 */
    groupTitle?: string;
    /** 分组说明（模块间关系） */
    groupDescription?: string;
    /** 当前页名 */
    pageTitle: string;
    /** 当前页与其它模块关系 */
    relation?: string;
};

export function getNavContext(pathname: string): NavContext {
    const segments = pathname.split("/").filter(Boolean);
    const first = segments[0] ?? "";

    /** 详情页：面包屑与顶栏说明更贴近中文业务语境 */
    if (first === "agents" && segments[1]) {
        return {
            groupTitle: "编排与执行",
            groupDescription: NAV_GROUPS.find((g) => g.key === "runtime")?.description,
            pageTitle: "智能体详情",
            relation: "与「模型」「工具」「知识库」绑定。返回列表可管理全部智能体。",
        };
    }
    if (first === "models" && segments[1]) {
        return {
            groupTitle: "基础能力",
            groupDescription: NAV_GROUPS.find((g) => g.key === "foundation")?.description,
            pageTitle: "模型详情",
            relation: "对话与嵌入模型可被智能体、工作流、知识库引用。",
        };
    }
    if (first === "tools" && segments[1]) {
        return {
            groupTitle: "基础能力",
            groupDescription: NAV_GROUPS.find((g) => g.key === "foundation")?.description,
            pageTitle: "工具详情",
            relation: "注册后绑定到智能体即可调用；知识库文档可嵌工具占位符。",
        };
    }
    if (first === "workflows" && segments[1]) {
        return {
            groupTitle: "编排与执行",
            groupDescription: NAV_GROUPS.find((g) => g.key === "runtime")?.description,
            pageTitle: "工作流详情",
            relation: "按步骤编排多个智能体；运行结果可在「运行查询」追踪。",
        };
    }
    if (first === "runs" && segments[1]) {
        return {
            groupTitle: "编排与执行",
            groupDescription: NAV_GROUPS.find((g) => g.key === "runtime")?.description,
            pageTitle: "运行详情",
            relation: "智能体或工作流单次执行的输入输出与状态。",
        };
    }
    if (first === "vector-store" && segments.length === 1) {
        const rel = NAV_GROUPS.flatMap((g) => g.items).find((i) => i.key === "vector-store")?.relation;
        return {
            groupTitle: "基础能力",
            groupDescription: NAV_GROUPS.find((g) => g.key === "foundation")?.description,
            pageTitle: "向量库",
            relation: rel,
        };
    }
    if (first === "vector-store" && segments[1] === "collections") {
        return {
            groupTitle: "基础能力",
            groupDescription: NAV_GROUPS.find((g) => g.key === "foundation")?.description,
            pageTitle: "向量库（跳转中）",
            relation: "旧链接已合并到「向量库」主从页；正在跳转。",
        };
    }

    if (first === "memory-policies" && segments[1]) {
        return {
            groupTitle: "数据与知识",
            groupDescription: NAV_GROUPS.find((g) => g.key === "data")?.description,
            pageTitle: "记忆策略详情",
            relation: "查看策略参数、条目与手动写入；返回列表管理全部策略。",
        };
    }

    if (first === "evaluations" && segments[1] === "runs" && segments[2]) {
        return {
            groupTitle: "质量与协作",
            groupDescription: NAV_GROUPS.find((g) => g.key === "quality")?.description,
            pageTitle: "评测运行详情",
            relation: "单次评测任务的结果与明细。",
        };
    }

    const key = navKeyFromPath(pathname);
    for (const g of NAV_GROUPS) {
        const item = g.items.find((i) => i.key === key);
        if (item) {
            return {
                groupTitle: g.title,
                groupDescription: g.description,
                pageTitle: item.label,
                relation: item.relation,
            };
        }
    }
    return {pageTitle: "控制台"};
}
