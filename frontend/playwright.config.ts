import {defineConfig} from "@playwright/test";

/** 与日常 `pnpm dev` 同端口，避免同一仓库再起一个 Next 实例报 “Another next dev server is already running” */
const devOrigin = process.env.PLAYWRIGHT_BASE_URL ?? "http://localhost:3000";

export default defineConfig({
    testDir: "./e2e",
    timeout: 60_000,
    use: {
        baseURL: devOrigin,
        channel: "chrome",
        trace: "retain-on-failure",
    },
    webServer: process.env.PLAYWRIGHT_SKIP_WEBSERVER
        ? undefined
        : {
            command: "pnpm run dev",
            url: devOrigin,
            reuseExistingServer: true,
            timeout: 120_000,
        },
});

