"use client";

import React from "react";

import styles from "./detail-section.module.css";

/**
 * 详情区统一块：标题 + 可选说明 + 右侧附加（如 Spin），与 SectionCard / 知识库 shell 观感一致。
 */
export function DetailSection(props: {
    title: React.ReactNode;
    /** 标题下方浅灰说明（12px） */
    hint?: React.ReactNode;
    /** 标题行右侧，如加载指示 */
    extra?: React.ReactNode;
    children: React.ReactNode;
    className?: string;
}) {
    return (
        <section className={`${styles.root} ${props.className ?? ""}`}>
            <div className={styles.header}>
                <div style={{minWidth: 0, flex: 1}}>
                    <div className={styles.title}>{props.title}</div>
                    {props.hint ? <p className={styles.hint}>{props.hint}</p> : null}
                </div>
                {props.extra ? <div style={{flexShrink: 0}}>{props.extra}</div> : null}
            </div>
            <div className={styles.body}>{props.children}</div>
        </section>
    );
}
