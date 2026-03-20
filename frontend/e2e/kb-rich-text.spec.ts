import {expect, test} from "@playwright/test";

function apiEnvelope(data: unknown): string {
    return JSON.stringify({
        code: "OK",
        message: "success",
        data,
        traceId: "e2e-trace",
    });
}

/**
 * Mock 知识库 API，不依赖真实后端，验证：
 * 1) 切换到「富文本」后 Quill 编辑器能挂载（.ql-editor）
 * 2) 提交时 POST body 含 contentFormat: html 与 HTML 正文
 */
test("知识库：富文本模式可挂载编辑器且提交带 contentFormat=html", async ({page}) => {
    await page.route("**/api/kb/bases/base_e2e_1/knowledge**", async (route) => {
        const req = route.request();
        if (req.method() === "GET") {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: apiEnvelope({items: [], total: 0, page: 1, pageSize: 10}),
            });
            return;
        }
        if (req.method() === "POST") {
            await route.fulfill({
                status: 200,
                contentType: "application/json",
                body: apiEnvelope({documentId: "doc_e2e_1", chunkCount: 1}),
            });
            return;
        }
        await route.continue();
    });

    await page.route("**/api/kb/bases", async (route) => {
        if (route.request().method() !== "GET") {
            await route.continue();
            return;
        }
        await route.fulfill({
            status: 200,
            contentType: "application/json",
            body: apiEnvelope([
                {
                    id: "base_e2e_1",
                    kbKey: "e2e_kb",
                    name: "E2E 测试库",
                    description: null,
                    createdAt: "2020-01-01T00:00:00Z",
                    documentCount: 0,
                    lastIngestAt: null,
                },
            ]),
        });
    });

    await page.goto("/kb");

    await expect(page.getByRole("heading", {name: "知识库管理"})).toBeVisible();

    await page.getByRole("button", {name: "添加知识"}).click();

    await expect(page.getByText("写入到知识库：").first()).toBeVisible();

    await page.getByText("富文本 (HTML)", {exact: true}).click();

    const editor = page.locator(".ql-editor");
    await expect(editor).toBeVisible({timeout: 20_000});

    await page.getByPlaceholder("如：退换货政策 2025").fill("E2E 富文本条目");

    await editor.click();
    await page.keyboard.type("E2EHello");

    const postPromise = page.waitForRequest(
        (r) =>
            r.url().includes("/api/kb/bases/base_e2e_1/knowledge") &&
            r.method() === "POST" &&
            !r.url().includes("?"),
    );

    await page.getByRole("button", {name: "保存并分片"}).click();

    const posted = await postPromise;
    const body = JSON.parse(posted.postData() ?? "{}") as {
        name: string;
        content: string;
        contentFormat?: string;
        chunkSize: number;
        overlap: number;
    };

    expect(body.contentFormat).toBe("html");
    expect(body.name).toBe("E2E 富文本条目");
    expect(body.content.toLowerCase()).toContain("e2ehello");
    expect(body.content).toMatch(/<[^>]+>/);
});
