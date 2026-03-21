"use client";

import {ApiOutlined, DeploymentUnitOutlined, ExperimentOutlined, RobotOutlined, ToolOutlined,} from "@ant-design/icons";
import {Button, Card, Col, Row, Space, Typography} from "antd";
import Link from "next/link";

import {AppLayout} from "@/components/AppLayout";

export default function HomePage() {
    const quickCards = [
        {title: "模型管理", desc: "配置 provider/modelKey 并测试连通性", href: "/models", icon: <ApiOutlined/>},
        {
            title: "工具管理",
            desc: "LOCAL / HTTP / MCP / WORKFLOW 等工具注册与 test-call",
            href: "/tools",
            icon: <ToolOutlined/>
        },
        {title: "智能体管理", desc: "创建 agent，绑定工具与策略并运行", href: "/agents", icon: <RobotOutlined/>},
        {
            title: "工作流管理",
            desc: "编排多智能体步骤并跟踪运行状态",
            href: "/workflows",
            icon: <DeploymentUnitOutlined/>
        },
        {title: "评测管理", desc: "定义评测集并追踪 accuracy/trace", href: "/evaluations", icon: <ExperimentOutlined/>},
    ];

    return (
        <AppLayout>
            <Space orientation="vertical" size={20} style={{width: "100%"}}>
                <div>
                    <Typography.Title level={2} style={{margin: 0}}>
                        智能体平台控制台
                    </Typography.Title>
                    <Typography.Text type="secondary">
                        聚合模型、工具、智能体、工作流、记忆、评测与 A2A 委派能力的统一入口。
                    </Typography.Text>
                </div>

                <Row gutter={[16, 16]} align="stretch">
                    <Col xs={24} md={12} lg={8}>
                        <Card title="联调模式" style={{height: "100%"}}>
                            <Typography.Paragraph style={{marginBottom: 0}}>
                                前端请求统一走同源 <Typography.Text code>/api</Typography.Text>{" "}
                                ，由 Next.js 代理到后端 <Typography.Text code>localhost:8080</Typography.Text>。
                            </Typography.Paragraph>
                        </Card>
                    </Col>
                    <Col xs={24} md={12} lg={8}>
                        <Card title="接口约定" style={{height: "100%"}}>
                            <Typography.Paragraph style={{marginBottom: 0}}>
                                后端统一返回 <Typography.Text code>ApiResponse&lt;T&gt;</Typography.Text>{" "}
                                ：<Typography.Text code>code/message/data/traceId</Typography.Text>。
                            </Typography.Paragraph>
                        </Card>
                    </Col>
                    <Col xs={24} md={12} lg={8}>
                        <Card title="建议流程" style={{height: "100%"}}>
                            <Typography.Paragraph style={{marginBottom: 0}}>
                                先创建模型与工具，再创建智能体，最后接入工作流和评测进行闭环验证。
                            </Typography.Paragraph>
                        </Card>
                    </Col>
                </Row>

                <div>
                    <Typography.Title level={4} style={{marginBottom: 12}}>
                        快捷入口
                    </Typography.Title>
                    <Row gutter={[16, 16]}>
                        {quickCards.map((item) => (
                            <Col key={item.href} xs={24} md={12} lg={8}>
                                <Card
                                    style={{height: "100%"}}
                                    title={
                                        <Space size={8}>
                                            {item.icon}
                                            <span>{item.title}</span>
                                        </Space>
                                    }
                                    extra={
                                        <Link href={item.href}>
                                            <Button type="link" style={{paddingInline: 0}}>
                                                进入
                                            </Button>
                                        </Link>
                                    }
                                >
                                    <Typography.Text type="secondary">{item.desc}</Typography.Text>
                                </Card>
                            </Col>
                        ))}
                    </Row>
                </div>
            </Space>
        </AppLayout>
    );
}
