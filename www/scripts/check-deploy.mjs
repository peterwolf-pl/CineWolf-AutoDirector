import { access } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();

const required = [
  "package.json",
  "server.js",
  "next.config.mjs",
  "tsconfig.json",
  "src/app/page.tsx",
  "src/app/layout.tsx",
  "src/app/globals.css",
  "src/components/ui.tsx",
  "src/components/content-cards.tsx",
  "src/components/json-ld.tsx",
  "src/data/changelog.ts",
  "src/lib/site.ts",
  "public/images/brand/cinewolf-logo.png",
];

const missing = [];

for (const relative of required) {
  try {
    await access(path.join(root, relative));
  } catch {
    missing.push(relative);
  }
}

if (missing.length > 0) {
  console.error("Deploy incomplete. Missing files:");
  for (const file of missing) {
    console.error(`  - ${file}`);
  }
  console.error(`\nCWD: ${root}`);
  console.error("Upload the full project (or cinewolf-www-deploy.zip) into this folder.");
  process.exit(1);
}

console.log(`Deploy file check passed (${required.length} paths).`);
