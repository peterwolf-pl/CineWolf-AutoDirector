import { access, readFile } from "node:fs/promises";
import path from "node:path";

const root = process.cwd();
const requiredRoutes = [
  "page.tsx",
  "features/page.tsx",
  "shots/page.tsx",
  "montage/page.tsx",
  "vehicles/page.tsx",
  "flashback/page.tsx",
  "download/page.tsx",
  "docs/page.tsx",
  "roadmap/page.tsx",
  "changelog/page.tsx",
  "faq/page.tsx",
  "privacy/page.tsx",
  "not-found.tsx",
  "error.tsx",
  "sitemap.ts",
  "robots.ts",
];

const checks = [];

async function text(relativePath) {
  return readFile(path.join(root, relativePath), "utf8");
}

async function exists(relativePath) {
  try {
    await access(path.join(root, relativePath));
    return true;
  } catch {
    return false;
  }
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message);
  }
  checks.push(message);
}

for (const route of requiredRoutes) {
  assert(await exists(path.join("src", "app", route)), `Route present: /${route.replace("/page.tsx", "").replace("page.tsx", "")}`);
}

const [layout, home, download, faqPage, changelogPage, globals, siteConfig, robots, sitemap] = await Promise.all([
  text("src/app/layout.tsx"),
  text("src/app/page.tsx"),
  text("src/components/download-card.tsx"),
  text("src/app/faq/page.tsx"),
  text("src/app/changelog/page.tsx"),
  text("src/app/globals.css"),
  text("src/lib/site.ts"),
  text("src/app/robots.ts"),
  text("src/app/sitemap.ts"),
]);

assert(layout.includes('href="#main-content"'), "Accessibility: skip link is present");
assert(layout.includes("<main"), "Accessibility: semantic main landmark is present");
assert(globals.includes("prefers-reduced-motion"), "Accessibility: reduced-motion rule is present");
assert(globals.includes(":focus-visible"), "Accessibility: visible focus styles are present");
assert(home.includes("SoftwareApplicationJsonLd"), "Structured data: SoftwareApplication hook is present");
assert(faqPage.includes('"FAQPage"'), "Structured data: FAQPage hook is present");
assert(changelogPage.includes('"Article"'), "Structured data: Article hook is present");
assert(robots.includes("sitemap"), "SEO: robots references sitemap");
assert(sitemap.includes("MetadataRoute.Sitemap"), "SEO: sitemap route is present");
assert(download.includes("siteConfig.downloadUrl"), "Download: direct URL state is conditional");
assert(download.includes("modrinthUrl"), "Download: Modrinth link is wired");
assert(download.includes("Download on Modrinth") || download.includes("Get on Modrinth"), "Download: Modrinth CTA label is present");
assert(!download.includes('href="#"'), "Download: no fake hash URL is used");
assert(!siteConfig.includes('downloadUrl: "#"'), "Configuration: no fake download URL is configured");
assert(siteConfig.includes("modrinth.com/mod/cinewolf-autodirector"), "Configuration: default Modrinth URL is set");

const sourceFiles = await Promise.all(
  [
    "src/app/page.tsx",
    "src/app/features/page.tsx",
    "src/app/shots/page.tsx",
    "src/app/montage/page.tsx",
    "src/app/vehicles/page.tsx",
    "src/app/flashback/page.tsx",
    "src/app/download/page.tsx",
    "src/app/docs/page.tsx",
    "src/app/roadmap/page.tsx",
    "src/app/changelog/page.tsx",
    "src/app/faq/page.tsx",
    "src/app/privacy/page.tsx",
  ].map(text),
);

assert(!sourceFiles.some((file) => /href\s*=\s*["']#["']/.test(file)), "Navigation: no empty internal CTA href is present");
assert(sourceFiles.every((file) => file.includes("pageMetadata")), "SEO: every primary page defines route metadata");

console.log(`Site verification passed (${checks.length} checks).`);
