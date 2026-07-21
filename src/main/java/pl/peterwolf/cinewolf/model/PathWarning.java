package pl.peterwolf.cinewolf.model;

public record PathWarning(Severity severity, String code, String message, double cinematicTimeSeconds) {
    public enum Severity { INFO, WARNING, ERROR }
}
