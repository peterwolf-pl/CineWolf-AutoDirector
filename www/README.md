# CineWolf AutoDirector website

Production website for [CineWolf AutoDirector](https://cinewolf.peterwolf.pl), the local cinematic camera and montage tool for Minecraft Fabric replays in Flashback.

## Stack

- Next.js 16 / React 19 / TypeScript
- Tailwind CSS 4 with a small project-specific visual system in `src/app/globals.css`
- Static App Router routes, local image assets, semantic HTML, and no runtime data service

## Requirements

- Node.js `>=20.9.0`
- npm

## Run locally

```bash
cp .env.example .env.local
npm install
npm run dev
```

Open `http://localhost:3000`.

## Configuration

All public deployment values are optional except the canonical site URL, which defaults safely to `https://cinewolf.peterwolf.pl`.

| Variable | Purpose |
| --- | --- |
| `NEXT_PUBLIC_SITE_URL` | Canonical public URL used by metadata, sitemap, robots, and structured data. |
| `NEXT_PUBLIC_CINEWOLF_VERSION` | Current release display value. |
| `NEXT_PUBLIC_MINECRAFT_VERSION` | Tested Minecraft version display value. |
| `NEXT_PUBLIC_FLASHBACK_VERSION` | Tested Flashback version display value. |
| `NEXT_PUBLIC_CINEWOLF_DOWNLOAD_URL` | Real release JAR URL. If empty, the download action stays disabled and says `Download not configured`. |
| `NEXT_PUBLIC_MODRINTH_URL` | Optional footer link; hidden if empty. |
| `NEXT_PUBLIC_GITHUB_URL` | Optional footer link; hidden if empty. |
| `NEXT_PUBLIC_SUPPORT_URL` | Optional footer link; hidden if empty. |

Do not set a placeholder `#` download URL. Use an actual HTTPS release URL only when it is ready.

## Content updates

The route components stay small; production content is structured under `src/data/`.

| File | Update here |
| --- | --- |
| `src/data/compatibility.ts` | Tested version matrix, keyframe support, warnings, and limitations. |
| `src/data/shots.ts` | Shot catalogue, filters, parameters, target types, and montage notes. |
| `src/data/features.ts` | Detailed feature cards and examples. |
| `src/data/vehicles.ts` | Vehicle categories, anchors, and profile caveats. |
| `src/data/integrations.ts` | Optional PeterWolf ecosystem outlook. |
| `src/data/roadmap.ts` | Version status, technical focus, and limitations. |
| `src/data/changelog.ts` | Release notes grouped by category. |
| `src/data/faq.ts` | FAQ content and schema source. |
| `src/data/docs.ts` | Documentation navigator and searchable articles. |

Brand assets are stored in `public/images/brand/`. Replace labelled screenshot slots only with approved real editor captures. A 9:16 CineWolf wallpaper was not supplied to this project, so it is intentionally not invented or represented as a product screenshot.

## Validate

```bash
npm run lint
npm run typecheck
npm run verify
npm run build
```

`npm run verify` checks the required route inventory, metadata hooks, structured-data hooks, accessibility foundations, sitemap/robots hooks, disabled download fallback, and empty-link guard. The production build statically prerenders all public pages.

## Deploy on shared hosting with Node.js

Preferred setup: run Next.js with Node (`next start`). Do **not** use the static `out/` export for this host type.

### What to upload

Upload the app directory (e.g. `www/`) **without** local junk:

```
package.json
package-lock.json
next.config.mjs
tsconfig.json
postcss.config.mjs
public/
src/
.env
```

Do **not** upload `node_modules/`, `.next/`, `out/`, or `.env.local` from your laptop unless you intentionally mirror prod env that way.

For shared hosting that cannot run `npm run build` reliably, upload the prebuilt package instead (`.next/` already included) and only install production deps + start.

### cPanel / “Setup Node.js App” style host

1. Create a Node.js application in the panel.
2. Set **Node.js version** to `20.x` or newer (`>=20.9.0`).
3. Set **Application root** to the folder where you uploaded the files above.
4. Set **Application startup file** to `server.js` (this repo’s shared-hosting entrypoint).
5. Put production variables in the panel env editor (or `.env` in the app root):

```env
NEXT_PUBLIC_SITE_URL=https://cinewolf.peterwolf.pl
NEXT_PUBLIC_CINEWOLF_VERSION=1.3.11
NEXT_PUBLIC_MINECRAFT_VERSION=26.2
NEXT_PUBLIC_FLASHBACK_VERSION=0.41.1
NEXT_PUBLIC_CINEWOLF_DOWNLOAD_URL=
NEXT_PUBLIC_MODRINTH_URL=
NEXT_PUBLIC_GITHUB_URL=
NEXT_PUBLIC_SUPPORT_URL=
PORT=3000
```

`PORT` is often injected by the panel — leave it if the host already sets it. `npm start` binds `0.0.0.0` and uses `$PORT` when present.

6. In the app directory on the server:

```bash
npm ci
npm run build
```

`npm run build` uses Webpack (`next build --webpack`) because CloudLinux/shared-hosting `node_modules` is often a symlink outside the app root, which breaks Turbopack.

If the host cannot compile the app (missing `src/`, broken panel env wrappers), prefer a **prebuilt** deploy: run `npm run build` on your machine, then upload `.next/`, `public/`, `package.json`, `package-lock.json`, `server.js`, and `next.config.mjs`. On the server only run `npm ci --omit=dev` and start `server.js` — do **not** run `npm run build` / the panel **build** script there.

7. Restart / start the Node app from the panel.
8. Point the domain (or subdomain) at that Node app. Many panels proxy the domain automatically after you attach it in “Setup Node.js App”.

9. Verify:

- `https://cinewolf.peterwolf.pl/`
- `/features`, `/download`, etc. (refresh must keep working)
- `/robots.txt`
- `/sitemap.xml`

### Notes

- Build must run **on the server** (or with the same env values) so `NEXT_PUBLIC_*` values are baked correctly.
- If the panel has separate “Run JS script” / “NPM Install” buttons, use them for `npm ci` + `npm run build`, then start the app.
- Static `out/` export is optional only for pure file hosts without Node; this project targets Node for shared hosting.

## SEO and accessibility

- Unique route metadata, canonical URLs, Open Graph/Twitter card data, `robots.txt`, and `sitemap.xml`
- SoftwareApplication, BreadcrumbList, FAQPage, and changelog Article JSON-LD; no fabricated ratings or reviews
- Skip link, semantic landmarks, keyboard-operable menu/filter/accordion controls, visible focus styles, responsive tables/code blocks, and reduced-motion support
- No autoplay video, external font request, fake screenshot, fake download counter, or hidden empty CTA
