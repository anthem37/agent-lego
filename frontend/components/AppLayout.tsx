"use client";

import {
    BookOutlined,
    BranchesOutlined,
    CloudServerOutlined,
    ClusterOutlined,
    DatabaseOutlined,
    ExperimentOutlined,
    HomeOutlined,
    PlayCircleOutlined,
    RobotOutlined,
    TeamOutlined,
    ToolOutlined,
} from "@ant-design/icons";
import {Badge, Breadcrumb, Layout, Menu, Tag, Typography} from "antd";
import Link from "next/link";
import {usePathname} from "next/navigation";
import React from "react";

import {getNavContext, NAV_GROUPS, navKeyFromPath} from "@/lib/nav";

const {Header, Sider, Content} = Layout;

const menuIconMap: Record<string, React.ReactNode> = {
    dashboard: <HomeOutlined/>,
    models: <CloudServerOutlined/>,
    tools: <ToolOutlined/>,
    "vector-store": <ClusterOutlined/>,
    agents: <RobotOutlined/>,
    workflows: <BranchesOutlined/>,
    runs: <PlayCircleOutlined/>,
    "memory-policies": <DatabaseOutlined/>,
    kb: <BookOutlined/>,
    evaluations: <ExperimentOutlined/>,
    a2a: <TeamOutlined/>,
};

export function AppLayout(props: { children: React.ReactNode }) {
    const pathname = usePathname();
    const selectedKey = navKeyFromPath(pathname);
    const navCtx = getNavContext(pathname);

    const menuItems = React.useMemo(
        () =>
            NAV_GROUPS.map((g) => ({
                type: "group" as const,
                key: `grp-${g.key}`,
                label: g.title,
                children: g.items.map((it) => ({
                    key: it.key,
                    icon: menuIconMap[it.key],
                    label: <Link href={it.href}>{it.label}</Link>,
                    title: it.relation,
                })),
            })),
        [],
    );

    const breadcrumbItems = React.useMemo(() => {
        const items: { title: React.ReactNode }[] = [
            {
                title: (
                    <Link href="/" style={{color: "var(--foreground-muted)", fontSize: 13}}>
                        首页
                    </Link>
                ),
            },
        ];
        if (selectedKey === "dashboard") {
            items.push({
                title: <span style={{fontWeight: 600, fontSize: 14}}>仪表盘</span>,
            });
        } else {
            if (navCtx.groupTitle) {
                items.push({
                    title: (
                        <span style={{color: "var(--foreground-muted)", fontSize: 13}}>{navCtx.groupTitle}</span>
                    ),
                });
            }
            items.push({
                title: <span style={{fontWeight: 600, fontSize: 14}}>{navCtx.pageTitle}</span>,
            });
        }
        return items;
    }, [navCtx.groupTitle, navCtx.pageTitle, selectedKey]);

    return (
        <Layout style={{minHeight: "100vh", background: "transparent"}}>
            <Sider
                className="app-sider"
                breakpoint="lg"
                collapsedWidth="0"
                width={232}
                theme="dark"
                style={{
                    position: "sticky",
                    top: 0,
                    height: "100vh",
                    overflow: "auto",
                    background: "var(--app-sider-surface, #001529)",
                    boxShadow: "2px 0 8px rgba(0, 0, 0, 0.08)",
                }}
            >
                <div
                    style={{
                        padding: "20px 18px 18px",
                        borderBottom: "1px solid rgba(255, 255, 255, 0.08)",
                    }}
                >
                    <Typography.Title
                        level={4}
                        style={{
                            color: "rgba(255, 255, 255, 0.95)",
                            margin: 0,
                            letterSpacing: 0,
                            fontWeight: 600,
                            fontSize: 18,
                        }}
                    >
                        Agent Lego
                    </Typography.Title>
                    <Typography.Text
                        style={{
                            color: "rgba(255, 255, 255, 0.65)",
                            fontSize: 13,
                            display: "block",
                            marginTop: 6,
                            lineHeight: 1.5,
                        }}
                    >
                        智能体编排控制台
                    </Typography.Text>
                </div>
                <Menu
                    className="app-sider-menu"
                    theme="dark"
                    mode="inline"
                    selectedKeys={[selectedKey]}
                    style={{borderInlineEnd: "none", padding: "8px 0 20px"}}
                    items={menuItems}
                />
            </Sider>
            <Layout style={{background: "transparent", minHeight: "100vh", display: "flex", flexDirection: "column"}}>
                <Header
                    style={{
                        position: "sticky",
                        top: 0,
                        zIndex: 10,
                        height: "auto",
                        lineHeight: 1.5,
                        paddingBlock: 14,
                        background: "#ffffff",
                        borderBottom: "1px solid #f0f0f0",
                        backdropFilter: "blur(8px)",
                        WebkitBackdropFilter: "blur(8px)",
                        paddingInline: 24,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                        gap: 16,
                    }}
                >
                    <div style={{minWidth: 0, flex: 1}}>
                        <Breadcrumb items={breadcrumbItems} style={{marginBottom: 0}}/>
                        {navCtx.relation ? (
                            <Typography.Text
                                type="secondary"
                                style={{fontSize: 12, display: "block", marginTop: 4, lineHeight: 1.45}}
                            >
                                {navCtx.relation}
                            </Typography.Text>
                        ) : null}
                    </div>
                    <div style={{display: "flex", alignItems: "center", gap: 12, flexShrink: 0}}>
                        <Badge
                            status="success"
                            text={<span style={{fontSize: 13, color: "var(--foreground-muted)"}}>服务正常</span>}
                        />
                        <Tag color="blue" variant="filled" style={{marginInlineEnd: 0}}>
                            后端 API
                        </Tag>
                        <Typography.Text type="secondary" style={{fontSize: 13}}>
                            localhost:8080
                        </Typography.Text>
                    </div>
                </Header>
                <Content
                    style={{
                        padding: "var(--app-content-pad)",
                        flex: 1,
                    }}
                >
                    <div className="app-main-inner">{props.children}</div>
                </Content>
            </Layout>
        </Layout>
    );
}
