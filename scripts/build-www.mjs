import { cp, rm, mkdir } from "node:fs/promises";
import { existsSync } from "node:fs";

const OUT_DIR = "www";

const ITEMS = [
  "index.html",
  "privacy.html",
  "terms.html",
  "delete-account.html",
  "css",
  "js",
  "manifest.json",
  "icon.svg",
  "icon-192.png",
  "icon-512.png",
  "icon-maskable-512.png"
];

async function main() {
  await rm(OUT_DIR, { recursive: true, force: true });
  await mkdir(OUT_DIR, { recursive: true });
  for (const item of ITEMS) {
    if (!existsSync(item)) continue;
    await cp(item, `${OUT_DIR}/${item}`, { recursive: true });
  }
}

main();
