package pl.peterwolf.cinewolf.debug;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CineWolfDebugExportTest {
    @Test
    void redactionPolicyRemovesAbsolutePaths() {
        DebugRedactionPolicy policy = DebugRedactionPolicy.defaults();
        String redacted = policy.redactText("Saved under /Users/piotrek/Documents/secret/project.json");
        assertTrue(redacted.contains("[redacted-path]"));
        assertFalse(redacted.contains("/Users/piotrek"));
    }

    @Test
    void eventStrengthBuckets() {
        assertEquals("STRONG", EventDiagnostic.strengthFor(0.9));
        assertEquals("PROBABLE", EventDiagnostic.strengthFor(0.5));
        assertEquals("WEAK", EventDiagnostic.strengthFor(0.2));
    }

    @Test
    void falsePositiveHintsDistinguishWeakAndProbable() {
        FalsePositiveHint weak = FalsePositiveHint.weak("x", "weak", 0.2);
        FalsePositiveHint probable = FalsePositiveHint.probable("y", "probable", 0.6);
        assertFalse(weak.probable());
        assertTrue(probable.probable());
    }
}
