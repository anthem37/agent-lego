"use client";

import {
    ApartmentOutlined,
    ApiOutlined,
    DeploymentUnitOutlined,
    ExperimentOutlined,
    FileSearchOutlined,
    HomeOutlined,
    RobotOutlined,
    ShareAltOutlined,
    ToolOutlined,
} from "@ant-design/icons";
import {Badge, Layout, Menu, Tag, Typography} from "antd";
import Link from "next/link";
import {usePathname} from "next/navigation";
import React from "react";

import {NAV_ITEMS} from "@/lib/nav";

const {Header, Sider, Content} = Layout;

function toSelectedKey(pathname: string): string {
    if (pathname === "/") {
        return "dashboard";
    }
    const first = pathname.split("/").filter(Boolean)[0] ?? "dashboard";
    if (first === "models") return "models";
    if (first === "tools") return "tools";
    if (first === "agents") return "agents";
    if (first === "workflows") return "workflows";
    if (first === "runs") return "runs";
    if (first === "memory") return "memory";
    if (first === "kb") return "kb";
    if (first === "evaluations") return "evaluations";
    if (first === "a2a") return "a2a";
    return "dashboard";
}

export function AppLayout(props: { children: React.ReactNode }) {
    const pathname = usePathname();
    const selectedKey = toSelectedKey(pathname);
    const menuIconMap: Record<string, React.ReactNode> = {
        dashboard: <HomeOutlined/>,
        models: <ApiOutlined/>,
        tools: <ToolOutlined/>,
        agents: <RobotOutlined/>,
        workflows: <DeploymentUnitOutlined/>,
        runs: <FileSearchOutlined/>,
        memory: <ApartmentOutlined/>,
        kb: <ShareAltOutlined/>,
        evaluations: <ExperimentOutlined/>,
        a2a: <ShareAltOutlined/>,
    };

    return (
        <Layout style={{minHeight: "100vh", background: "#f5f7fb"}}>
            <Sider
                breakpoint="lg"
                collapsedWidth="0"
                theme="dark"
                style={{
                    boxShadow: "2px 0 8px rgba(15, 23, 42, 0.12)",
                }}
            >
                <div
                    style={{
                        padding: 16,
                        borderBottom: "1px solid rgba(255,255,255,0.08)",
                        background: "linear-gradient(120deg, rgba(22,119,255,0.22), rgba(114,46,209,0.26))",
                    }}
                >
                    <Typography.Title level={4} style={{color: "white", margin: 0, letterSpacing: 0.4}}>
                        Agent Lego
                    </Typography.Title>
                    <Typography.Text style={{color: "rgba(255,255,255,0.78)"}}>
                        管理控制台
                    </Typography.Text>
                </div>
                <Menu
                    theme="dark"
                    mode="inline"
                    selectedKeys={[selectedKey]}
                    style={{borderInlineEnd: "none", paddingTop: 8}}
                    items={NAV_ITEMS.map((it) => ({
                        key: it.key,
                        icon: menuIconMap[it.key],
                        label: <Link href={it.href}>{it.label}</Link>,
                    }))}
                />
            </Sider>
            <Layout>
                <Header
                    style={{
                        background: "rgba(255,255,255,0.9)",
                        borderBottom: "1px solid #eef2f7",
                        backdropFilter: "blur(8px)",
                        paddingInline: 20,
                        display: "flex",
                        alignItems: "center",
                        justifyContent: "space-between",
                    }}
                >
                    <Badge status="processing" text="控制台在线"/>
                    <div style={{display: "flex", alignItems: "center", gap: 8}}>
                        <Tag color="blue" style={{marginInlineEnd: 0}}>
                            /api 代理
                        </Tag>
                        <Typography.Text style={{color: "rgba(0,0,0,0.65)"}}>localhost:8080</Typography.Text>
                    </div>
                </Header>
                <Content style={{padding: 24}}>
                    <div style={{maxWidth: 1280, marginInline: "auto"}}>{props.children}</div>
                </Content>
            </Layout>
        </Layout>
    );
}

