"use client";

import {KB_KNOWLEDGE_EDITOR_TOTAL_PX, kbQuillEditorInnerMinPx} from "@/components/kb/knowledge-editor-layout";
import type {CSSProperties} from "react";
import {useEffect, useRef} from "react";

import "./kb-quill-editor.css";

const TOOLBAR_OPTIONS = [
    [{header: [1, 2, false]}],
    ["bold", "italic", "underline", "strike"],
    [{list: "ordered"}, {list: "bullet"}],
    ["blockquote", "code-block"],
    ["link"],
    ["clean"],
] as const;

export type KbRichTextEditorProps = {
    value?: string;
    onChange?: (html: string) => void;
    disabled?: boolean;
};

type QuillType = typeof import("quill").default;

function readHtml(quill: InstanceType<QuillType>): string {
    return quill.root.innerHTML;
}

/**
 * 原生 Quill + 动态 import，兼容 React 19 且避免 SSR 访问 document。
 * （react-quill 依赖已移除的 findDOMNode，在 React 19 下不可用。）
 */
export function KbRichTextEditor({value, onChange, disabled}: KbRichTextEditorProps) {
    const hostRef = useRef<HTMLDivElement>(null);
    const quillRef = useRef<InstanceType<QuillType> | null>(null);
    const syncingRef = useRef(false);
    const onChangeRef = useRef(onChange);
    const valueRef = useRef(value);
    onChangeRef.current = onChange;
    valueRef.current = value;

    useEffect(() => {
        const host = hostRef.current;
        if (!host) {
            return;
        }

        let cancelled = false;

        void (async () => {
            await import("quill/dist/quill.snow.css");
            const {default: Quill} = await import("quill");
            if (cancelled || !hostRef.current) {
                return;
            }

            const mount = document.createElement("div");
            host.appendChild(mount);

            const quill = new Quill(mount, {
                theme: "snow",
                modules: {
                    toolbar: TOOLBAR_OPTIONS,
                },
            });
            quillRef.current = quill;

            const initial = valueRef.current ?? "";
            if (initial) {
                quill.clipboard.dangerouslyPasteHTML(initial);
            }

            const emit = () => {
                if (syncingRef.current) {
                    return;
                }
                onChangeRef.current?.(readHtml(quill));
            };
            quill.on("text-change", emit);
        })();

        return () => {
            cancelled = true;
            quillRef.current = null;
            host.innerHTML = "";
        };
    }, []);

    useEffect(() => {
        const quill = quillRef.current;
        if (!quill) {
            return;
        }
        const html = value ?? "";
        const cur = readHtml(quill);
        if (cur === html) {
            return;
        }
        syncingRef.current = true;
        quill.clipboard.dangerouslyPasteHTML(html || "<p><br></p>");
        syncingRef.current = false;
    }, [value]);

    useEffect(() => {
        quillRef.current?.enable(!disabled);
    }, [disabled]);

    const innerMin = kbQuillEditorInnerMinPx();
    const hostStyle: CSSProperties = {
        minHeight: KB_KNOWLEDGE_EDITOR_TOTAL_PX,
        ["--kb-quill-inner-min" as string]: `${innerMin}px`,
    };
    return <div ref={hostRef} className="kb-rich-text-editor kb-rich-text-editor-host" style={hostStyle}/>;
}
