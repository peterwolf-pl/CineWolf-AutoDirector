export const DEFAULT_SITE_URL = "https://cinewolf.peterwolf.pl";

function configuredUrl(value: string | undefined) {
  const trimmed = value?.trim();

  if (!trimmed) {
    return null;
  }

  try {
    const parsed = new URL(trimmed);
    return parsed.protocol === "https:" || parsed.protocol === "http:"
      ? parsed.toString()
      : null;
  } catch {
    return null;
  }
}

function configuredText(value: string | undefined, fallback: string) {
  return value?.trim() || fallback;
}

export const siteConfig = {
  name: "CineWolf AutoDirector",
  shortName: "CineWolf",
  description:
    "Generate professional camera paths, tracking shots, collision-aware movement and editable cinematic montages directly inside the Minecraft Flashback replay editor.",
  url: (configuredUrl(process.env.NEXT_PUBLIC_SITE_URL) ?? DEFAULT_SITE_URL).replace(
    /\/$/,
    "",
  ),
  version: configuredText(process.env.NEXT_PUBLIC_CINEWOLF_VERSION, "1.3.11"),
  minecraftVersion: configuredText(
    process.env.NEXT_PUBLIC_MINECRAFT_VERSION,
    "26.2",
  ),
  flashbackVersion: configuredText(
    process.env.NEXT_PUBLIC_FLASHBACK_VERSION,
    "0.41.1",
  ),
  downloadUrl: configuredUrl(process.env.NEXT_PUBLIC_CINEWOLF_DOWNLOAD_URL),
  modrinthUrl:
    configuredUrl(process.env.NEXT_PUBLIC_MODRINTH_URL) ??
    "https://modrinth.com/mod/cinewolf-autodirector",
  githubUrl: configuredUrl(process.env.NEXT_PUBLIC_GITHUB_URL),
  supportUrl: configuredUrl(process.env.NEXT_PUBLIC_SUPPORT_URL),
  author: "PeterWolf",
};

export const navigation = [
  { href: "/features", label: "Features" },
  { href: "/shots", label: "Shots" },
  { href: "/montage", label: "Generate Montage" },
  { href: "/vehicles", label: "Vehicles" },
  { href: "/flashback", label: "Flashback" },
  { href: "/docs", label: "Documentation" },
  { href: "/roadmap", label: "Roadmap" },
] as const;

export const footerNavigation = [
  { href: "/features", label: "Features" },
  { href: "/docs", label: "Documentation" },
  { href: "/roadmap", label: "Roadmap" },
  { href: "/changelog", label: "Changelog" },
  { href: "/download", label: "Download" },
  { href: "/privacy", label: "Privacy" },
] as const;

export function absoluteUrl(path = "/") {
  return new URL(path, `${siteConfig.url}/`).toString();
}
