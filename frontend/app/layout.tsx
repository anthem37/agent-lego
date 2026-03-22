import type {Metadata} from "next";
import {Geist, Geist_Mono} from "next/font/google";
import "antd/dist/reset.css";
import "./globals.css";

import {AntdConfigProvider} from "@/components/AntdConfigProvider";

const geistSans = Geist({
    variable: "--font-geist-sans",
    subsets: ["latin"],
});

const geistMono = Geist_Mono({
    variable: "--font-geist-mono",
    subsets: ["latin"],
});

export const metadata: Metadata = {
    title: "Agent Lego",
    description: "智能体乐高平台（前端）",
};

export default function RootLayout({
                                       children,
                                   }: Readonly<{
    children: React.ReactNode;
}>) {
    return (
        <html lang="zh-CN" className={`${geistSans.variable} ${geistMono.variable}`}>
        <body className={geistSans.className}>
        <AntdConfigProvider>{children}</AntdConfigProvider>
        </body>
        </html>
    );
}
