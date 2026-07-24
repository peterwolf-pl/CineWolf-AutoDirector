package pl.peterwolf.cinewolf.montage.preset;

/**
 * Shared vertical (9:16) composition helpers used by planning and path generation.
 * Values are independent of the game window; they describe the intended export frame.
 */
public final class VerticalComposition {
    public static final double WIDTH_TO_HEIGHT = 9.0 / 16.0;
    /** Default Flashback export resolution for 9:16 (matches Flashback StartExportWindow). */
    public static final int DEFAULT_EXPORT_WIDTH = 1080;
    public static final int DEFAULT_EXPORT_HEIGHT = 1920;
    /** Default Flashback export resolution for 16:9. */
    public static final int LANDSCAPE_EXPORT_WIDTH = 1920;
    public static final int LANDSCAPE_EXPORT_HEIGHT = 1080;

    private VerticalComposition() {
    }

    public static boolean isVertical(OutputAspectRatio aspect) {
        return aspect != null && aspect.vertical();
    }

    /**
     * Pull camera farther for vertical frames so the subject keeps horizontal clearance
     * inside the narrow 9:16 frustum (Minecraft FOV is vertical).
     */
    public static double distanceMultiplier(OutputAspectRatio aspect) {
        return isVertical(aspect) ? 1.45 : 1.0;
    }

    /** Prefer slightly tighter FOV so the subject stays centered in a tall frame. */
    public static double fovForFraming(FramingType framing, OutputAspectRatio aspect) {
        double base = switch (framing == null ? FramingType.MEDIUM : framing) {
            case EXTREME_WIDE -> 78.0;
            case WIDE -> 72.0;
            case MEDIUM -> 65.0;
            case CLOSE -> 55.0;
            case EXTREME_CLOSE -> 45.0;
        };
        if (!isVertical(aspect)) return base;
        return switch (framing == null ? FramingType.MEDIUM : framing) {
            case EXTREME_WIDE -> 70.0;
            case WIDE -> 64.0;
            case MEDIUM -> 58.0;
            case CLOSE -> 50.0;
            case EXTREME_CLOSE -> 42.0;
        };
    }

    /** Map planning framing to a vertical-safe framing (avoid extreme wide). */
    public static FramingType verticalSafeFraming(FramingType framing) {
        if (framing == null) return FramingType.MEDIUM;
        return switch (framing) {
            case EXTREME_WIDE -> FramingType.WIDE;
            case WIDE -> FramingType.MEDIUM;
            default -> framing;
        };
    }

    public static int[] exportResolution(OutputAspectRatio aspect) {
        if (isVertical(aspect)) {
            return new int[]{DEFAULT_EXPORT_WIDTH, DEFAULT_EXPORT_HEIGHT};
        }
        return new int[]{LANDSCAPE_EXPORT_WIDTH, LANDSCAPE_EXPORT_HEIGHT};
    }
}
