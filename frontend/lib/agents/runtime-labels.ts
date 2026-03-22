import {AGENT_RUNTIME_OPTIONS} from "@/lib/agents/runtime-kinds";

export function runtimeKindLabel(kind?: string) {
    if (!kind) {
        return "—";
    }
    const opt = AGENT_RUNTIME_OPTIONS.find((o) => o.value === kind);
    return opt ? `${opt.title}（${opt.badge}）` : kind;
}
