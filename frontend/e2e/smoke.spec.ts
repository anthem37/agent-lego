import {expect, test} from "@playwright/test";

test("dashboard renders", async ({page}) => {
    await page.goto("/");
    await expect(page.getByRole("heading", {name: "仪表盘"})).toBeVisible();
    await expect(page.getByText("后端默认：localhost:8080")).toBeVisible();
});

