"use client";

import {Spin} from "antd";
import {useRouter, useSearchParams} from "next/navigation";
import React, {Suspense, useEffect} from "react";

import {AppLayout} from "@/components/AppLayout";
import {PageShell} from "@/components/PageShell";

/**
 * 旧路径兼容：统一合并到 /vector-store（主从一页完成配置 + 运维）
 */
function RedirectInner() {
    const router = useRouter();
    const searchParams = useSearchParams();

    useEffect(() => {
        const p = searchParams.get("profile");
        router.replace(p ? `/vector-store?profile=${encodeURIComponent(p)}` : "/vector-store");
    }, [router, searchParams]);

    return (
        <div style={{padding: 48, textAlign: "center"}}>
            <Spin size="large" tip="正在跳转到向量库…"/>
        </div>
    );
}

export default function VectorStoreCollectionsRedirectPage() {
    return (
        <AppLayout>
            <PageShell>
                <Suspense
                    fallback={
                        <div style={{padding: 48, textAlign: "center"}}>
                            <Spin size="large"/>
                        </div>
                    }
                >
                    <RedirectInner/>
                </Suspense>
            </PageShell>
        </AppLayout>
    );
}
