package pl.peterwolf.cinewolf.api.v2;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CineWolfApiVersionTest {
    @Test
    void compatibilityAndParse() {
        assertTrue(CineWolfApiVersion.CURRENT.isCompatibleWith(new CineWolfApiVersion(2, 0, 0)));
        assertFalse(CineWolfApiVersion.CURRENT.isCompatibleWith(new CineWolfApiVersion(3, 0, 0)));
        assertEquals("2.0.0", CineWolfApiVersion.parse("2.0.0").toString());
    }
}
