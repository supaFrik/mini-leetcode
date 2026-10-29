/** @type {import('next').NextConfig} */
const nextConfig = {
  // Trình duyệt chỉ gọi localhost:3000/api/... , Next chuyển tiếp sang backend → không dính CORS.
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${process.env.BACKEND_URL ?? "http://localhost:8080"}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
