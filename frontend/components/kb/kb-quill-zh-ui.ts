/**
 * Quill Snow 默认工具栏/气泡为英文（aria-label、CSS content 等），在此统一改为中文展示。
 */

function setBtnZh(btn: HTMLButtonElement, label: string): void {
    btn.title = label;
    btn.setAttribute("aria-label", label);
}

/**
 * 在 Quill 实例挂载后对包裹根节点执行一次（toolbar 与 tooltip 均在根下）。
 */
export function applyKbQuillSnowChineseUi(root: HTMLElement): void {
    const toolbar = root.querySelector(".ql-toolbar");
    if (toolbar) {
        const bold = toolbar.querySelector("button.ql-bold");
        if (bold) {
            setBtnZh(bold as HTMLButtonElement, "粗体");
        }
        const italic = toolbar.querySelector("button.ql-italic");
        if (italic) {
            setBtnZh(italic as HTMLButtonElement, "斜体");
        }
        const underline = toolbar.querySelector("button.ql-underline");
        if (underline) {
            setBtnZh(underline as HTMLButtonElement, "下划线");
        }
        const strike = toolbar.querySelector("button.ql-strike");
        if (strike) {
            setBtnZh(strike as HTMLButtonElement, "删除线");
        }
        const bq = toolbar.querySelector("button.ql-blockquote");
        if (bq) {
            setBtnZh(bq as HTMLButtonElement, "引用");
        }
        const code = toolbar.querySelector("button.ql-code-block");
        if (code) {
            setBtnZh(code as HTMLButtonElement, "代码块");
        }
        const link = toolbar.querySelector("button.ql-link");
        if (link) {
            setBtnZh(link as HTMLButtonElement, "链接");
        }
        const clean = toolbar.querySelector("button.ql-clean");
        if (clean) {
            setBtnZh(clean as HTMLButtonElement, "清除格式");
        }
        const listOrdered = toolbar.querySelector('button.ql-list[value="ordered"]');
        if (listOrdered) {
            setBtnZh(listOrdered as HTMLButtonElement, "有序列表");
        }
        const listBullet = toolbar.querySelector('button.ql-list[value="bullet"]');
        if (listBullet) {
            setBtnZh(listBullet as HTMLButtonElement, "无序列表");
        }
        const indentMinus = toolbar.querySelector('button.ql-indent[value="-1"]');
        if (indentMinus) {
            setBtnZh(indentMinus as HTMLButtonElement, "减少缩进");
        }
        const indentPlus = toolbar.querySelector('button.ql-indent[value="+1"]');
        if (indentPlus) {
            setBtnZh(indentPlus as HTMLButtonElement, "增加缩进");
        }
        const headerPicker = toolbar.querySelector(".ql-picker.ql-header .ql-picker-label");
        if (headerPicker) {
            headerPicker.setAttribute("aria-label", "标题样式");
            (headerPicker as HTMLElement).title = "标题样式";
        }
    }

    const tipInput = root.querySelector('.ql-tooltip input[type="text"]') as HTMLInputElement | null;
    if (tipInput) {
        tipInput.setAttribute("data-link", "请输入链接地址，如 https://example.com");
    }
}
