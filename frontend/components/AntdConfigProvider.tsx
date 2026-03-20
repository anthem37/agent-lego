"use client";

import {ConfigProvider} from "antd";
import zhCN from "antd/locale/zh_CN";
import React from "react";

/**
 * 全局 Ant Design 配置：中文语言包（分页「条/页」、日期等组件文案）。
 */
export function AntdConfigProvider(props: {children: React.ReactNode}) {
    return (
        <ConfigProvider locale={zhCN}>
            {props.children}
        </ConfigProvider>
    );
}
