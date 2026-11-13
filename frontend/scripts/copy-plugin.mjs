// Chép bản build của plugin IntelliJ (ide-plugin/build/distributions/*.zip) vào public/downloads/
// để trang /ide phục vụ tải về. Build trước: cd ide-plugin && ./gradlew buildPlugin
import { copyFileSync, mkdirSync, readdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const dist = join(root, "..", "ide-plugin", "build", "distributions");
let zips;
try {
  zips = readdirSync(dist).filter((f) => f.endsWith(".zip")).sort();
} catch {
  zips = [];
}
if (zips.length === 0) {
  console.error(`No plugin ZIP in ${dist}. Run: cd ide-plugin && ./gradlew buildPlugin`);
  process.exit(1);
}
const target = join(root, "public", "downloads", "mini-leetcode-intellij.zip");
mkdirSync(dirname(target), { recursive: true });
copyFileSync(join(dist, zips[zips.length - 1]), target);
console.log(`Copied ${zips[zips.length - 1]} -> public/downloads/mini-leetcode-intellij.zip`);
