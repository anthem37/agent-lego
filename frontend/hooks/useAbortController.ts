"use client";

import React from "react";

/**
 * 在组件卸载时自动 abort，避免路由切换或弹窗关闭后仍 setState / 处理过时响应。
 * 与 {@link @/lib/api/request} 的 `signal` / `timeoutMs` 组合使用。
 */
export function useAbortController(): AbortController {
    const ac = React.useMemo(() => new AbortController(), []);
    React.useEffect(() => {
        return () => {
            ac.abort();
        };
    }, [ac]);
    return ac;
}
