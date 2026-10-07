package pl.peterwolf.cinewolf.integration.flashback;

import pl.peterwolf.cinewolf.config.CineWolfConfig;

public final class TimelapseExportController {
    private static volatile CineWolfConfig config;

    private TimelapseExportController() {
    }

    public static void bindConfig(CineWolfConfig cineWolfConfig) {
        config = cineWolfConfig;
    }

    public static double samplingFps(double outputFps) {
        CineWolfConfig current = config;
        if (current == null || current.timelapse == null) {
            return outputFps;
        }
        return current.timelapse.samplingFps(outputFps);
    }
}
