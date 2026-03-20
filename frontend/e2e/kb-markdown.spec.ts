import {expect, test} from "@playwright/test";

function apiEnvelope(data: unknown): string {
    return JSON.stringify({
        code: "OK",
        message: "success",
        data,
        traceId: "e2e-trace",
    });
}

test("知识库：Markdown 模式提交 contentFormat=markdown", async ({page}) => {
    await page.route("**/api/kb/bases/base_e2e_md/knowledge**", async (route) => {
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
                body: apiEnvelope({documentId: "doc_md_1", chunkCount: 1}),
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
                    id: "base_e2e_md",
                    kbKey: "e2e_md",
                    name: "E2E MD 库",
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

    await page.getByText("Markdown", {exact: true}).click();

    const mdTextarea = page.locator(".w-md-editor-text").locator("textarea");
    await expect(mdTextarea).toBeVisible({timeout: 20_000});

    await page.getByPlaceholder("如：退换货政策 2025").fill("E2E Markdown 文档");
    await mdTextarea.fill("# 标题\n\n段落 **粗体**");

    const postPromise = page.waitForRequest(
        (r) =>
            r.url().includes("/api/kb/bases/base_e2e_md/knowledge") &&
            r.method() === "POST" &&
            !r.url().includes("?"),
    );

    await page.getByRole("button", {name: "保存并分片"}).click();

    const posted = await postPromise;
    const body = JSON.parse(posted.postData() ?? "{}") as {
        name: string;
        content: string;
        contentFormat?: string;
    };

    expect(body.contentFormat).toBe("markdown");
    expect(body.name).toBe("E2E Markdown 文档");
    expect(body.content).toContain("# 标题");
    expect(body.content).toContain("**粗体**");
});
