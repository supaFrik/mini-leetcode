// Minimal static file server used to test local shadcn registry resolution.
const http = require("http");
const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..", "public");
const port = Number(process.argv[2] || 3001);

const types = {
  ".json": "application/json",
  ".js": "text/javascript",
  ".tsx": "text/plain",
  ".html": "text/html",
};

http
  .createServer((req, res) => {
    const url = decodeURIComponent((req.url || "/").split("?")[0]);
    const file = path.join(root, url);
    if (!file.startsWith(root) || !fs.existsSync(file) || fs.statSync(file).isDirectory()) {
      res.writeHead(404, { "content-type": "text/plain" });
      return res.end("not found: " + url);
    }
    res.writeHead(200, { "content-type": types[path.extname(file)] || "application/octet-stream" });
    fs.createReadStream(file).pipe(res);
  })
  .listen(port, () => console.log("registry server on http://localhost:" + port));
