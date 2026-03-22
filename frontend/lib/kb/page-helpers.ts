/**
 * 知识库控制台页用纯函数（从 page.tsx 抽出，便于单测与复用）。
 */

import type {KbChunkStrategyMetaDto} from "@/lib/kb/types";

export function collectionNamePatternForKind(kind: string | undefined): RegExp {
    const k = (kind ?? "").toUpperCase();
    if (k === "QDRANT") {
        return /^[a-zA-Z0-9_-]+$/;
    }
    return /^[a-zA-Z0-9_]+$/;
}

export function maskVectorStoreConfig(raw?: Record<string, unknown>): Record<string, unknown> {
    if (!raw) {
        return {};
    }
    const out: Record<string, unknown> = {...raw};
    if (typeof out.token === "string" && out.token) {
        out.token = "***";
    }
    if (typeof out.password === "string" && out.password) {
        out.password = "***";
    }
    if (typeof out.apiKey === "string" && out.apiKey) {
        out.apiKey = "***";
    }
    return out;
}

export function formatKbDateTime(iso?: string): string {
    if (!iso) {
        return "—";
    }
    const d = new Date(iso);
    if (Number.isNaN(d.getTime())) {
        return iso;
    }
    return d.toLocaleString("zh-CN", {
        year: "numeric",
        month: "2-digit",
        day: "2-digit",
        hour: "2-digit",
        minute: "2-digit",
    });
}

export function chunkStrategyLabel(meta: KbChunkStrategyMetaDto[], code?: string): string {
    if (!code) {
        return "—";
    }
    return meta.find((m) => m.value === code)?.label ?? code;
}
