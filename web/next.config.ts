import type { NextConfig } from "next";

// The browser talks to this Next.js server, which proxies /api to the Spring Boot API.
// Same-origin requests keep the setup simple and avoid CORS in development.
const apiUrl = process.env.BROKERS_API_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiUrl}/api/:path*` }];
  },
};

export default nextConfig;
