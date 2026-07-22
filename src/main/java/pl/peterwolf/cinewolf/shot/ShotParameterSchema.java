package pl.peterwolf.cinewolf.shot;

import java.util.List;
import java.util.Objects;

public record ShotParameterSchema(List<Parameter> parameters) {
    public ShotParameterSchema {
        parameters = List.copyOf(Objects.requireNonNullElse(parameters, List.of()));
    }

    public record Parameter(String id, String kind, double minimum, double maximum, double defaultValue) {
        public Parameter {
            id = Objects.requireNonNullElse(id, "");
            kind = Objects.requireNonNullElse(kind, "number");
        }
    }

    public static ShotParameterSchema of(Parameter... values) {
        return new ShotParameterSchema(List.of(values));
    }
}
