package pl.peterwolf.cinewolf.compatibility;

import java.util.Objects;

/** Inclusive semantic version range using dotted version strings. */
public record VersionRange(String minimumInclusive, String maximumInclusive) {
    public VersionRange {
        minimumInclusive = Objects.requireNonNull(minimumInclusive, "minimumInclusive").trim();
        maximumInclusive = Objects.requireNonNull(maximumInclusive, "maximumInclusive").trim();
        if (minimumInclusive.isEmpty() || maximumInclusive.isEmpty()) {
            throw new IllegalArgumentException("Version range bounds must not be blank");
        }
    }

    public static VersionRange exact(String version) {
        return new VersionRange(version, version);
    }

    public boolean contains(String version) {
        if (version == null || version.isBlank()) return false;
        return compare(version, minimumInclusive) >= 0 && compare(version, maximumInclusive) <= 0;
    }

    public String display() {
        if (minimumInclusive.equals(maximumInclusive)) return minimumInclusive;
        return minimumInclusive + " .. " + maximumInclusive;
    }

    /** Compares dotted numeric versions; non-numeric suffixes are compared lexicographically. */
    public static int compare(String left, String right) {
        String[] a = left.split("[.+\\-]");
        String[] b = right.split("[.+\\-]");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            String av = i < a.length ? a[i] : "0";
            String bv = i < b.length ? b[i] : "0";
            boolean aNum = av.chars().allMatch(Character::isDigit);
            boolean bNum = bv.chars().allMatch(Character::isDigit);
            if (aNum && bNum) {
                int cmp = Integer.compare(Integer.parseInt(av), Integer.parseInt(bv));
                if (cmp != 0) return cmp;
            } else {
                int cmp = av.compareToIgnoreCase(bv);
                if (cmp != 0) return cmp;
            }
        }
        return 0;
    }
}
