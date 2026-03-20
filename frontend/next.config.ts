import type {NextConfig} from "next";

const nextConfig: NextConfig = {
    async rewrites() {
        const backendOrigin = process.env.BACKEND_ORIGIN ?? "http://localhost:8080";
        return [
            {
                source: "/api/:path*",
                destination: `${backendOrigin}/:path*`,
            },
        ];
    },
};

export default nextConfig;
