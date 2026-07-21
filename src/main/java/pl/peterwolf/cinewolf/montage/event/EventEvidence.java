package pl.peterwolf.cinewolf.montage.event;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EventEvidence(
        Set<DetectionSource> sources,
        List<Measurement> measurements,
        List<Attribute> attributes,
        Set<ReplayEventType> relatedTypes
) {
    public EventEvidence {
        sources = sources == null || sources.isEmpty()
                ? Set.of(DetectionSource.DERIVED_MOVEMENT) : Set.copyOf(sources);
        List<Measurement> ordered = new ArrayList<>(Objects.requireNonNullElse(measurements, List.of()));
        ordered.sort(Comparator.comparing(Measurement::name).thenComparingDouble(Measurement::value));
        measurements = List.copyOf(ordered);
        List<Attribute> orderedAttributes = new ArrayList<>(Objects.requireNonNullElse(attributes, List.of()));
        orderedAttributes.sort(Comparator.comparing(Attribute::name).thenComparing(Attribute::value));
        attributes = List.copyOf(orderedAttributes);
        relatedTypes = relatedTypes == null || relatedTypes.isEmpty()
                ? Set.of() : Collections.unmodifiableSet(EnumSet.copyOf(relatedTypes));
    }

    public static EventEvidence of(DetectionSource source, Measurement... measurements) {
        return new EventEvidence(Set.of(source), List.of(measurements), List.of(), Set.of());
    }

    public EventEvidence merge(EventEvidence other) {
        EnumSet<DetectionSource> mergedSources = EnumSet.copyOf(sources);
        mergedSources.addAll(other.sources);
        List<Measurement> mergedMeasurements = new ArrayList<>(measurements);
        mergedMeasurements.addAll(other.measurements);
        List<Attribute> mergedAttributes = new ArrayList<>(attributes);
        mergedAttributes.addAll(other.attributes);
        EnumSet<ReplayEventType> mergedRelated = relatedTypes.isEmpty()
                ? EnumSet.noneOf(ReplayEventType.class) : EnumSet.copyOf(relatedTypes);
        mergedRelated.addAll(other.relatedTypes);
        return new EventEvidence(mergedSources, mergedMeasurements, mergedAttributes, mergedRelated);
    }

    public EventEvidence withRelatedType(ReplayEventType type) {
        EnumSet<ReplayEventType> related = relatedTypes.isEmpty()
                ? EnumSet.noneOf(ReplayEventType.class) : EnumSet.copyOf(relatedTypes);
        related.add(type);
        return new EventEvidence(sources, measurements, attributes, related);
    }

    public EventEvidence withAttribute(String name, String value) {
        List<Attribute> updated = new ArrayList<>(attributes);
        updated.add(new Attribute(name, value));
        return new EventEvidence(sources, measurements, updated, relatedTypes);
    }

    public enum DetectionSource {
        DIRECT_ACTION,
        ENTITY_STATE,
        DERIVED_MOVEMENT,
        REPLAY_MARKER,
        AGGREGATED_ACTIONS
    }

    public enum Comparison { GREATER_THAN_OR_EQUAL, LESS_THAN_OR_EQUAL, CHANGE, PRESENT, NONE }

    public record Measurement(String name, double value, String unit, Comparison comparison, double threshold) {
        public Measurement {
            name = Objects.requireNonNullElse(name, "measurement");
            unit = Objects.requireNonNullElse(unit, "");
            comparison = Objects.requireNonNullElse(comparison, Comparison.NONE);
            if (!Double.isFinite(value)) value = 0.0;
            if (!Double.isFinite(threshold)) threshold = 0.0;
        }

        public static Measurement observed(String name, double value, String unit) {
            return new Measurement(name, value, unit, Comparison.NONE, 0.0);
        }

        public static Measurement atLeast(String name, double value, String unit, double threshold) {
            return new Measurement(name, value, unit, Comparison.GREATER_THAN_OR_EQUAL, threshold);
        }

        public static Measurement atMost(String name, double value, String unit, double threshold) {
            return new Measurement(name, value, unit, Comparison.LESS_THAN_OR_EQUAL, threshold);
        }
    }

    public record Attribute(String name, String value) {
        public Attribute {
            name = Objects.requireNonNullElse(name, "attribute");
            value = Objects.requireNonNullElse(value, "");
        }
    }
}
