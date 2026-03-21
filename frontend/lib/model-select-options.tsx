import {Space, Tag, Typography} from "antd";
import React from "react";

import {providerDisplayName} from "@/lib/model-config-labels";

export type ModelOptionRow = {
    id: string;
    name: string;
    provider: string;
    modelKey: string;
    configSummary?: string;
    /** 与 GET /models 的 chatProvider 对齐：false 为嵌入类模型 */
    chatProvider?: boolean;
};

/**
 * 将模型列表转为 Ant Design Select 的 options（主文案中文，便于区分多套同 modelKey 配置）。
 */
export type ModelSelectOption = {
    value: string;
    searchText: string;
    label: React.ReactNode;
};

export function toModelSelectOptions(rows: ModelOptionRow[]): ModelSelectOption[] {
    return rows.map((m) => ({
        value: m.id,
        searchText: `${m.name} ${m.provider} ${m.modelKey} ${m.configSummary ?? ""} ${m.id}`.toLowerCase(),
        label: (
            <Space orientation="vertical" size={0} style={{width: "100%"}}>
                <div>
                    <Typography.Text strong>{m.name}</Typography.Text>
                    {m.chatProvider === false ? (
                        <Tag color="blue" style={{marginLeft: 8}}>
                            嵌入
                        </Tag>
                    ) : m.chatProvider === true ? (
                        <Tag color="green" style={{marginLeft: 8}}>
                            聊天
                        </Tag>
                    ) : null}
                    <Typography.Text type="secondary" style={{marginLeft: 8}}>
                        {providerDisplayName(m.provider)} · {m.modelKey}
                    </Typography.Text>
                </div>
                {m.configSummary ? (
                    <Typography.Text type="secondary" style={{fontSize: 12}} ellipsis>
                        {m.configSummary}
                    </Typography.Text>
                ) : (
                    <Typography.Text type="secondary" style={{fontSize: 12}}>
                        无参数摘要
                    </Typography.Text>
                )}
                <Typography.Text type="secondary" copyable={{text: m.id}} style={{fontSize: 11}}>
                    编号 {m.id}
                </Typography.Text>
            </Space>
        ),
    }));
}
