const { createProxyMiddleware } = require("http-proxy-middleware");

// CRA loads this only for the development server. Keep local API requests on
// the frontend's origin so browsers can send the refresh cookie without CORS.
module.exports = function setupProxy(app) {
  app.use("/api/v1", createProxyMiddleware({
    target: "http://127.0.0.1:8080",
    changeOrigin: true,
    onProxyRes(proxyResponse) {
      const cookies = proxyResponse.headers["set-cookie"];
      if (!cookies) return;

      // The local backend sends SameSite=None without Secure over HTTP, which
      // browsers reject. The development proxy is same-origin, so Lax works.
      proxyResponse.headers["set-cookie"] = cookies.map((cookie) => cookie
        .replace(/;\s*Secure\b/gi, "")
        .replace(/;\s*SameSite=None\b/gi, "; SameSite=Lax"));
    },
  }));
};
