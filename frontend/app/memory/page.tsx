import {redirect} from "next/navigation";

/** @deprecated 使用 /memory-policies */
export default function LegacyMemoryRedirectPage() {
    redirect("/memory-policies");
}
