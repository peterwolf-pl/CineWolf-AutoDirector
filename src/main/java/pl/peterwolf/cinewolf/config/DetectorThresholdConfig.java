package pl.peterwolf.cinewolf.config;

import pl.peterwolf.cinewolf.montage.event.DetectorThresholds;

/** Gson-friendly detector profile; normalization is delegated to the immutable analysis model. */
public final class DetectorThresholdConfig {
    public double positionChangeDistance = 1.5;
    public double playerHighSpeed = 5.5;
    public double vehicleHighSpeed = 8.0;
    public double flightHighSpeed = 7.0;
    public double acceleration = 3.0;
    public double turnDegrees = 45.0;
    public double turnMinimumSpeed = 1.0;
    public double altitudeVerticalSpeed = 1.0;
    public double altitudeMinimumChange = 2.0;
    public double pauseMaximumSpeed = 0.15;
    public long pauseMinimumDurationTicks = 40;
    public double combatProximity = 8.0;
    public long combatMergeGapTicks = 20;
    public double damageHealthDrop = 0.01;
    public long damageMergeGapTicks = 10;
    public long flightMinimumDurationTicks = 20;
    public double flightGroundClearance = 1.5;
    public double landingGroundProximity = 0.6;
    public long blockGroupGapTicks = 40;
    public double blockGroupRadius = 8.0;
    public long eventMergeGapTicks = 12;
    public double eventMergeRadius = 16.0;
    public long sceneGapTicks = 100;
    public double teleportDistance = 32.0;

    public DetectorThresholds toModel() {
        return new DetectorThresholds(positionChangeDistance, playerHighSpeed, vehicleHighSpeed, flightHighSpeed,
                acceleration, turnDegrees, turnMinimumSpeed, altitudeVerticalSpeed, altitudeMinimumChange,
                pauseMaximumSpeed, pauseMinimumDurationTicks, combatProximity, combatMergeGapTicks,
                damageHealthDrop, damageMergeGapTicks, flightMinimumDurationTicks, flightGroundClearance,
                landingGroundProximity, blockGroupGapTicks, blockGroupRadius, eventMergeGapTicks, eventMergeRadius,
                sceneGapTicks, teleportDistance);
    }

    public void normalize() {
        DetectorThresholds value = toModel();
        positionChangeDistance = value.positionChangeDistance();
        playerHighSpeed = value.playerHighSpeed();
        vehicleHighSpeed = value.vehicleHighSpeed();
        flightHighSpeed = value.flightHighSpeed();
        acceleration = value.acceleration();
        turnDegrees = value.turnDegrees();
        turnMinimumSpeed = value.turnMinimumSpeed();
        altitudeVerticalSpeed = value.altitudeVerticalSpeed();
        altitudeMinimumChange = value.altitudeMinimumChange();
        pauseMaximumSpeed = value.pauseMaximumSpeed();
        pauseMinimumDurationTicks = value.pauseMinimumDurationTicks();
        combatProximity = value.combatProximity();
        combatMergeGapTicks = value.combatMergeGapTicks();
        damageHealthDrop = value.damageHealthDrop();
        damageMergeGapTicks = value.damageMergeGapTicks();
        flightMinimumDurationTicks = value.flightMinimumDurationTicks();
        flightGroundClearance = value.flightGroundClearance();
        landingGroundProximity = value.landingGroundProximity();
        blockGroupGapTicks = value.blockGroupGapTicks();
        blockGroupRadius = value.blockGroupRadius();
        eventMergeGapTicks = value.eventMergeGapTicks();
        eventMergeRadius = value.eventMergeRadius();
        sceneGapTicks = value.sceneGapTicks();
        teleportDistance = value.teleportDistance();
    }
}
