"use client";

import {Alert, Typography} from "antd";
import React from "react";

import {ApiError} from "@/lib/api/types";

export function ErrorAlert(props: { error: unknown; title?: string }) {
    const title = props.title ?? "操作失败";

    if (!props.error) {
        return null;
    }

    if (props.error instanceof ApiError) {
        const descParts: string[] = [];
        if (props.error.code) {
            descParts.push(`code=${props.error.code}`);
        }
        if (props.error.httpStatus) {
            descParts.push(`httpStatus=${props.error.httpStatus}`);
        }
        if (props.error.traceId) {
            descParts.push(`traceId=${props.error.traceId}`);
        }

        return (
            <Alert
                type="error"
                showIcon
                title={title}
                description={
                    <div>
                        <div>{props.error.message}</div>
                        {descParts.length > 0 ? (
                            <Typography.Text type="secondary">{descParts.join(" · ")}</Typography.Text>
                        ) : null}
                    </div>
                }
            />
        );
    }

    return <Alert type="error" showIcon title={title} description={String(props.error)}/>;
}

