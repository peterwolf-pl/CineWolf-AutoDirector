package pl.peterwolf.cinewolf.api.v2;

import java.util.Objects;

/** Semantic version of the public CineWolf integration API. */
public record CineWolfApiVersion(int major, int minor, int patch) implements Comparable<CineWolfApiVersion> {
    public static final CineWolfApiVersion CURRENT = new CineWolfApiVersion(2, 0, 0);

    public CineWolfApiVersion {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("API version components must be non-negative");
        }
    }

    public boolean isCompatibleWith(CineWolfApiVersion required) {
        Objects.requireNonNull(required, "required");
        if (major != required.major) return false;
        if (minor < required.minor) return false;
        return minor != required.minor || patch >= required.patch;
    }

    @Override
    public int compareTo(CineWolfApiVersion other) {
        int majorCmp = Integer.compare(major, other.major);
        if (majorCmp != 0) return majorCmp;
        int minorCmp = Integer.compare(minor, other.minor);
        if (minorCmp != 0) return minorCmp;
        return Integer.compare(patch, other.patch);
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }

    public static CineWolfApiVersion parse(String text) {
        Objects.requireNonNull(text, "text");
        String[] parts = text.trim().split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("Invalid API version: " + text);
        }
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = parts.length == 3 ? Integer.parseInt(parts[2]) : 0;
        return new CineWolfApiVersion(major, minor, patch);
    }
}
