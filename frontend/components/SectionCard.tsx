"use client";

import {Card} from "antd";
import React from "react";

export function SectionCard(props: {
    title: React.ReactNode;
    extra?: React.ReactNode;
    children: React.ReactNode;
}) {
    return (
        <Card
            title={props.title}
            extra={props.extra}
            style={{
                borderRadius: 12,
                borderColor: "#e9eef5",
            }}
            styles={{
                header: {minHeight: 52},
            }}
        >
            {props.children}
        </Card>
    );
}

