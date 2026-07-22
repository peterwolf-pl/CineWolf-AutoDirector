package pl.peterwolf.cinewolf.debug;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Local-only redaction for exported diagnostics. Never uploads data. */
public final class DebugRedactionPolicy {
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "playername", "player_name", "username", "account", "ip", "path", "absolutepath", "home"
    );

    private final boolean redactPlayerNames;
    private final boolean redactAbsolutePaths;

    public DebugRedactionPolicy(boolean redactPlayerNames, boolean redactAbsolutePaths) {
        this.redactPlayerNames = redactPlayerNames;
        this.redactAbsolutePaths = redactAbsolutePaths;
    }

    public static DebugRedactionPolicy defaults() {
        return new DebugRedactionPolicy(true, true);
    }

    public String redactText(String value) {
        if (value == null || value.isBlank()) return value;
        String result = value;
        if (redactAbsolutePaths) {
            result = result.replaceAll("(?i)(/Users/[^\\s\"']+|/home/[^\\s\"']+|[A-Z]:\\\\[^\\s\"']+)", "[redacted-path]");
        }
        if (redactPlayerNames && result.length() > 2 && result.matches(".*\\b[A-Za-z0-9_]{3,16}\\b.*")) {
            // Conservative: leave structured ids, redact free-form display labels only when marked.
            if (result.toLowerCase(Locale.ROOT).contains("player") || result.toLowerCase(Locale.ROOT).contains("name=")) {
                result = result.replaceAll("(?i)(player|name)=([^,\\s]+)", "$1=[redacted]");
            }
        }
        return result;
    }

    public boolean shouldRedactKey(String key) {
        if (key == null) return false;
        String normalized = key.toLowerCase(Locale.ROOT);
        if (SENSITIVE_KEYS.contains(normalized)) return true;
        return redactPlayerNames && normalized.contains("player") && normalized.contains("name");
    }

    public String maybeRedact(String key, String value) {
        if (shouldRedactKey(key)) return "[redacted]";
        return redactText(Objects.requireNonNullElse(value, ""));
    }
}
