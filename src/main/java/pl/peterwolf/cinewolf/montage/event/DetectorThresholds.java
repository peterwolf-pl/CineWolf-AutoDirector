package pl.peterwolf.cinewolf.montage.event;

public record DetectorThresholds(
        double positionChangeDistance,
        double playerHighSpeed,
        double vehicleHighSpeed,
        double flightHighSpeed,
        double acceleration,
        double turnDegrees,
        double turnMinimumSpeed,
        double altitudeVerticalSpeed,
        double altitudeMinimumChange,
        double pauseMaximumSpeed,
        long pauseMinimumDurationTicks,
        double combatProximity,
        long combatMergeGapTicks,
        double damageHealthDrop,
        long damageMergeGapTicks,
        long flightMinimumDurationTicks,
        double flightGroundClearance,
        double landingGroundProximity,
        long blockGroupGapTicks,
        double blockGroupRadius,
        long eventMergeGapTicks,
        double eventMergeRadius,
        long sceneGapTicks,
        double teleportDistance
) {
    public DetectorThresholds {
        positionChangeDistance = positive(positionChangeDistance, 1.5);
        playerHighSpeed = positive(playerHighSpeed, 5.5);
        vehicleHighSpeed = positive(vehicleHighSpeed, 8.0);
        flightHighSpeed = positive(flightHighSpeed, 7.0);
        acceleration = positive(acceleration, 3.0);
        turnDegrees = positive(turnDegrees, 45.0);
        turnMinimumSpeed = positive(turnMinimumSpeed, 1.0);
        altitudeVerticalSpeed = positive(altitudeVerticalSpeed, 1.0);
        altitudeMinimumChange = positive(altitudeMinimumChange, 2.0);
        pauseMaximumSpeed = positive(pauseMaximumSpeed, 0.15);
        pauseMinimumDurationTicks = positive(pauseMinimumDurationTicks, 40);
        combatProximity = positive(combatProximity, 8.0);
        combatMergeGapTicks = positive(combatMergeGapTicks, 20);
        damageHealthDrop = positive(damageHealthDrop, 0.01);
        damageMergeGapTicks = positive(damageMergeGapTicks, 10);
        flightMinimumDurationTicks = positive(flightMinimumDurationTicks, 20);
        flightGroundClearance = positive(flightGroundClearance, 1.5);
        landingGroundProximity = positive(landingGroundProximity, 0.6);
        blockGroupGapTicks = positive(blockGroupGapTicks, 40);
        blockGroupRadius = positive(blockGroupRadius, 8.0);
        eventMergeGapTicks = positive(eventMergeGapTicks, 12);
        eventMergeRadius = positive(eventMergeRadius, 16.0);
        sceneGapTicks = positive(sceneGapTicks, 100);
        teleportDistance = positive(teleportDistance, 32.0);
    }

    public static DetectorThresholds defaults() {
        return new DetectorThresholds(1.5, 5.5, 8.0, 7.0, 3.0, 45.0, 1.0,
                1.0, 2.0, 0.15, 40, 8.0, 20, 0.01, 10, 20,
                1.5, 0.6, 40, 8.0, 12, 16.0, 100, 32.0);
    }

    public DetectorThresholds withHighSpeedThresholds(double player, double vehicle, double flight) {
        return new DetectorThresholds(positionChangeDistance, player, vehicle, flight, acceleration, turnDegrees,
                turnMinimumSpeed, altitudeVerticalSpeed, altitudeMinimumChange, pauseMaximumSpeed,
                pauseMinimumDurationTicks, combatProximity, combatMergeGapTicks, damageHealthDrop,
                damageMergeGapTicks, flightMinimumDurationTicks, flightGroundClearance, landingGroundProximity,
                blockGroupGapTicks, blockGroupRadius, eventMergeGapTicks, eventMergeRadius, sceneGapTicks,
                teleportDistance);
    }

    public DetectorThresholds withPause(double maximumSpeed, long minimumDurationTicks) {
        return new DetectorThresholds(positionChangeDistance, playerHighSpeed, vehicleHighSpeed, flightHighSpeed,
                acceleration, turnDegrees, turnMinimumSpeed, altitudeVerticalSpeed, altitudeMinimumChange,
                maximumSpeed, minimumDurationTicks, combatProximity, combatMergeGapTicks, damageHealthDrop,
                damageMergeGapTicks, flightMinimumDurationTicks, flightGroundClearance, landingGroundProximity,
                blockGroupGapTicks, blockGroupRadius, eventMergeGapTicks, eventMergeRadius, sceneGapTicks,
                teleportDistance);
    }

    public double sensitivityAdjusted(double threshold, double sensitivity) {
        double normalized = Double.isFinite(sensitivity) ? Math.max(0.0, Math.min(1.0, sensitivity)) : 0.5;
        return threshold * (1.25 - normalized * 0.5);
    }

    private static double positive(double value, double fallback) {
        return Double.isFinite(value) && value > 0.0 ? value : fallback;
    }

    private static long positive(long value, long fallback) {
        return value > 0 ? value : fallback;
    }
}
