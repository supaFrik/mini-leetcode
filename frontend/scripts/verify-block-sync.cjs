const fs = require("fs");
const path = require("path");

const registryDir = path.resolve(__dirname, "..", "registry", "problem-detail");
const blocksDir = path.resolve(__dirname, "..", "src", "components", "blocks");

const files = fs.readdirSync(registryDir).filter((f) => f.endsWith(".tsx") || f.endsWith(".ts"));

let drift = 0;
for (const f of files) {
  const a = fs.readFileSync(path.join(registryDir, f), "utf8");
  const b = fs.readFileSync(path.join(blocksDir, f), "utf8");
  if (a.trim() !== b.trim()) {
    console.log("DRIFT:", f);
    drift++;
  } else {
    console.log("match:", f);
  }
}
console.log(drift === 0 ? "\nRegistry and installed block are identical." : `\n${drift} file(s) differ.`);
process.exit(drift === 0 ? 0 : 1);
