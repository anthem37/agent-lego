import {request} from "@/lib/api/request";

export async function delegateA2a(body: Record<string, unknown>): Promise<string> {
    return request<string>("/a2a/delegate", {method: "POST", body});
}
