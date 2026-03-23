/** 判断是否为 fetch / AbortSignal 触发的中止，便于在 catch 中忽略过时响应。 */
export function isAbortError(e: unknown): boolean {
    if (e instanceof DOMException && e.name === "AbortError") {
        return true;
    }
    if (typeof e === "object" && e !== null && "name" in e) {
        return (e as { name: string }).name === "AbortError";
    }
    return false;
}
