"use client";

import {
    ApiOutlined,
    ArrowRightOutlined,
    DeploymentUnitOutlined,
    ExperimentOutlined,
    RobotOutlined,
    ToolOutlined,
} from "@ant-design/icons";
import {Button, Card, Col, Row, Space, Steps, Typography} from "antd";
import Link from "next/link";

import {AppLayout} from "@/components/AppLayout";
import {PageShell} from "@/components/PageShell";
import {NAV_GROUPS} from "@/lib/nav";

export default function HomePage() {
    const flowGroups = NAV_GROUPS.filter((g) => g.key !== "overview");

    return (
        <AppLayout>
            <PageShell gap={28}>
                <div>
                    <Typography.Title level={2} style={{margin: "0 0 8px", fontWeight: 700}}>
                        智能体平台控制台
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 0, fontSize: 15, maxWidth: 720}}>
                        从<strong>模型与工具</strong>出发，配置<strong>智能体与工作流</strong>，通过<strong>运行查询</strong>验证；
                        将语料写入<strong>知识库</strong>，再用<strong>评测</strong>与
                        <strong> A2A</strong> 扩展协作与质量闭环。
                    </Typography.Paragraph>
                </div>

                <Card
                    title="推荐路径（一次走完主流程）"
                    style={{
                        borderRadius: "var(--app-radius-lg)",
                        borderColor: "var(--app-border)",
                        boxShadow: "var(--app-shadow-sm)",
                    }}
                >
                    <Steps
                        orientation="horizontal"
                        responsive
                        size="small"
                        items={[
                            {title: "模型", content: "连通性"},
                            {title: "工具", content: "注册"},
                            {title: "智能体", content: "绑定"},
                            {title: "工作流", content: "编排"},
                            {title: "运行", content: "验证"},
                            {title: "知识库", content: "可选"},
                        ]}
                    />
                    <Typography.Paragraph type="secondary" style={{marginTop: 16, marginBottom: 0, fontSize: 13}}>
                        左侧导航按相同阶段分组：先搭「基础能力」，再在「编排与执行」里联调，最后在「数据与知识」沉淀语料。
                    </Typography.Paragraph>
                </Card>

                <Row gutter={[16, 16]} align="stretch">
                    <Col xs={24} md={8}>
                        <Card
                            title="联调模式"
                            style={{
                                height: "100%",
                                borderRadius: "var(--app-radius-lg)",
                                borderColor: "var(--app-border)"
                            }}
                        >
                            <Typography.Paragraph style={{marginBottom: 0, lineHeight: 1.65}}>
                                前端请求走同源 <Typography.Text code>/api</Typography.Text>，由 Next 代理至{" "}
                                <Typography.Text code>localhost:8080</Typography.Text>，与后端一致调试。
                            </Typography.Paragraph>
                        </Card>
                    </Col>
                    <Col xs={24} md={8}>
                        <Card
                            title="接口约定"
                            style={{
                                height: "100%",
                                borderRadius: "var(--app-radius-lg)",
                                borderColor: "var(--app-border)"
                            }}
                        >
                            <Typography.Paragraph style={{marginBottom: 0, lineHeight: 1.65}}>
                                统一 <Typography.Text code>ApiResponse&lt;T&gt;</Typography.Text>：
                                <Typography.Text code>code / message / data / traceId</Typography.Text>。
                            </Typography.Paragraph>
                        </Card>
                    </Col>
                    <Col xs={24} md={8}>
                        <Card
                            title="模块关系"
                            style={{
                                height: "100%",
                                borderRadius: "var(--app-radius-lg)",
                                borderColor: "var(--app-border)"
                            }}
                        >
                            <Typography.Paragraph style={{marginBottom: 0, lineHeight: 1.65}}>
                                智能体引用模型与工具；工作流编排智能体；知识库依赖嵌入模型并可嵌工具标签；评测与 A2A
                                建立在上述能力之上。
                            </Typography.Paragraph>
                        </Card>
                    </Col>
                </Row>

                <div>
                    <Typography.Title level={4} style={{marginBottom: 4}}>
                        按分组进入功能
                    </Typography.Title>
                    <Typography.Paragraph type="secondary" style={{marginBottom: 16, fontSize: 13}}>
                        与侧栏一致：先看分组说明，再进具体页面。
                    </Typography.Paragraph>
                    <Row gutter={[16, 16]}>
                        {flowGroups.map((g) => (
                            <Col key={g.key} xs={24} lg={12} xl={8}>
                                <Card
                                    size="small"
                                    title={
                                        <Space size={8}>
                                            <span style={{fontWeight: 600}}>{g.title}</span>
                                        </Space>
                                    }
                                    style={{
                                        height: "100%",
                                        borderRadius: "var(--app-radius-md)",
                                        borderColor: "var(--app-border)",
                                    }}
                                >
                                    {g.description ? (
                                        <Typography.Paragraph
                                            type="secondary"
                                            style={{fontSize: 12, marginBottom: 12, lineHeight: 1.55}}
                                        >
                                            {g.description}
                                        </Typography.Paragraph>
                                    ) : null}
                                    <Space orientation="vertical" size={8} style={{width: "100%"}}>
                                        {g.items.map((it) => (
                                            <Link key={it.key} href={it.href} style={{display: "block"}}>
                                                <div
                                                    style={{
                                                        display: "flex",
                                                        alignItems: "center",
                                                        justifyContent: "space-between",
                                                        gap: 8,
                                                        padding: "10px 12px",
                                                        borderRadius: "var(--app-radius-sm)",
                                                        border: "1px solid var(--app-border)",
                                                        background: "var(--app-primary-soft)",
                                                        transition: "border-color 0.15s ease, box-shadow 0.15s ease",
                                                    }}
                                                >
                                                    <div style={{minWidth: 0}}>
                                                        <Typography.Text strong>{it.label}</Typography.Text>
                                                        {it.relation ? (
                                                            <Typography.Paragraph
                                                                type="secondary"
                                                                style={{
                                                                    fontSize: 12,
                                                                    margin: "4px 0 0",
                                                                    marginBottom: 0
                                                                }}
                                                            >
                                                                {it.relation}
                                                            </Typography.Paragraph>
                                                        ) : null}
                                                    </div>
                                                    <ArrowRightOutlined
                                                        style={{color: "var(--app-primary)", flexShrink: 0}}/>
                                                </div>
                                            </Link>
                                        ))}
                                    </Space>
                                </Card>
                            </Col>
                        ))}
                    </Row>
                </div>

                <Row gutter={[16, 16]}>
                    <Col xs={24} md={12} lg={8}>
                        <Card
                            title={
                                <Space size={8}>
                                    <ApiOutlined/>
                                    模型管理
                                </Space>
                            }
                            extra={
                                <Link href="/models">
                                    <Button type="link" size="small" icon={<ArrowRightOutlined/>}>
                                        进入
                                    </Button>
                                </Link>
                            }
                            style={{borderRadius: "var(--app-radius-lg)", borderColor: "var(--app-border)"}}
                        >
                            <Typography.Text type="secondary">配置 provider / modelKey，测试连通性。</Typography.Text>
                        </Card>
                    </Col>
                    <Col xs={24} md={12} lg={8}>
                        <Card
                            title={
                                <Space size={8}>
                                    <ToolOutlined/>
                                    工具管理
                                </Space>
                            }
                            extra={
                                <Link href="/tools">
                                    <Button type="link" size="small" icon={<ArrowRightOutlined/>}>
                                        进入
                                    </Button>
                                </Link>
                            }
                            style={{borderRadius: "var(--app-radius-lg)", borderColor: "var(--app-border)"}}
                        >
                            <Typography.Text type="secondary">LOCAL / HTTP / MCP / WORKFLOW
                                注册与调试。</Typography.Text>
                        </Card>
                    </Col>
                    <Col xs={24} md={12} lg={8}>
                        <Card
                            title={
                                <Space size={8}>
                                    <RobotOutlined/>
                                    智能体
                                </Space>
                            }
                            extra={
                                <Link href="/agents">
                                    <Button type="link" size="small" icon={<ArrowRightOutlined/>}>
                                        进入
                                    </Button>
                                </Link>
                            }
                            style={{borderRadius: "var(--app-radius-lg)", borderColor: "var(--app-border)"}}
                        >
                            <Typography.Text type="secondary">绑定模型、工具与策略并运行。</Typography.Text>
                        </Card>
                    </Col>
                    <Col xs={24} md={12} lg={8}>
                        <Card
                            title={
                                <Space size={8}>
                                    <DeploymentUnitOutlined/>
                                    工作流
                                </Space>
                            }
                            extra={
                                <Link href="/workflows">
                                    <Button type="link" size="small" icon={<ArrowRightOutlined/>}>
                                        进入
                                    </Button>
                                </Link>
                            }
                            style={{borderRadius: "var(--app-radius-lg)", borderColor: "var(--app-border)"}}
                        >
                            <Typography.Text type="secondary">多步骤编排与状态跟踪。</Typography.Text>
                        </Card>
                    </Col>
                    <Col xs={24} md={12} lg={8}>
                        <Card
                            title={
                                <Space size={8}>
                                    <ExperimentOutlined/>
                                    评测
                                </Space>
                            }
                            extra={
                                <Link href="/evaluations">
                                    <Button type="link" size="small" icon={<ArrowRightOutlined/>}>
                                        进入
                                    </Button>
                                </Link>
                            }
                            style={{borderRadius: "var(--app-radius-lg)", borderColor: "var(--app-border)"}}
                        >
                            <Typography.Text type="secondary">评测集、实验与指标。</Typography.Text>
                        </Card>
                    </Col>
                </Row>
            </PageShell>
        </AppLayout>
    );
}
