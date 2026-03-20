export type NavItem = {
    key: string;
    label: string;
    href: string;
};

export const NAV_ITEMS: NavItem[] = [
    {key: "dashboard", label: "仪表盘", href: "/"},
    {key: "models", label: "模型", href: "/models"},
    {key: "tools", label: "工具", href: "/tools"},
    {key: "agents", label: "智能体", href: "/agents"},
    {key: "workflows", label: "工作流", href: "/workflows"},
    {key: "runs", label: "运行查询", href: "/runs"},
    {key: "memory", label: "记忆", href: "/memory"},
    {key: "kb", label: "知识库", href: "/kb"},
    {key: "evaluations", label: "评测", href: "/evaluations"},
    {key: "a2a", label: "A2A", href: "/a2a"},
];

