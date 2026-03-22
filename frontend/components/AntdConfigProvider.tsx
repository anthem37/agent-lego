"use client";

import {ConfigProvider, theme} from "antd";
import zhCN from "antd/locale/zh_CN";
import React from "react";

/**
 * 全局 Ant Design：大众熟悉的企业后台风格 — 默认品牌蓝、标准圆角与语义色。
 */
export function AntdConfigProvider(props: { children: React.ReactNode }) {
    return (
        <ConfigProvider
            locale={zhCN}
            theme={{
                algorithm: theme.defaultAlgorithm,
                token: {
                    colorPrimary: "#1677ff",
                    colorLink: "#1677ff",
                    colorSuccess: "#52c41a",
                    colorWarning: "#faad14",
                    colorError: "#ff4d4f",
                    colorInfo: "#1677ff",
                    borderRadius: 8,
                    fontFamily:
                        'var(--font-geist-sans), -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "PingFang SC", "Microsoft YaHei", sans-serif',
                    fontFamilyCode:
                        'var(--font-geist-mono), ui-monospace, "SF Mono", Menlo, Monaco, Consolas, monospace',
                    fontSize: 14,
                    lineHeight: 1.5715,
                    colorBgLayout: "transparent",
                    colorText: "rgba(0, 0, 0, 0.88)",
                    colorTextSecondary: "rgba(0, 0, 0, 0.65)",
                    colorTextTertiary: "rgba(0, 0, 0, 0.45)",
                    colorBorder: "#d9d9d9",
                    colorBorderSecondary: "#f0f0f0",
                    boxShadow: "0 1px 2px rgba(0, 0, 0, 0.03)",
                    boxShadowSecondary: "0 4px 12px rgba(0, 0, 0, 0.08)",
                    controlHeight: 32,
                    motionDurationMid: "0.2s",
                },
                components: {
                    Layout: {
                        headerBg: "#ffffff",
                        bodyBg: "transparent",
                        footerBg: "transparent",
                    },
                    Card: {
                        borderRadiusLG: 12,
                        paddingLG: 16,
                    },
                    Menu: {
                        itemBorderRadius: 8,
                        groupTitleFontSize: 12,
                        darkItemBg: "transparent",
                        darkItemSelectedBg: "rgba(22, 119, 255, 0.2)",
                        darkItemHoverBg: "rgba(255, 255, 255, 0.08)",
                    },
                    Table: {
                        headerBorderRadius: 8,
                        borderRadius: 8,
                    },
                    Drawer: {
                        paddingLG: 24,
                    },
                    Tabs: {
                        borderRadius: 8,
                    },
                    Collapse: {
                        borderRadiusLG: 10,
                    },
                    Button: {
                        borderRadius: 8,
                        controlHeight: 32,
                    },
                    Input: {
                        borderRadius: 8,
                        paddingBlock: 6,
                    },
                    Select: {
                        borderRadius: 8,
                    },
                    Modal: {
                        borderRadiusLG: 12,
                    },
                    Tag: {
                        borderRadiusSM: 6,
                    },
                },
            }}
        >
            {props.children}
        </ConfigProvider>
    );
}
