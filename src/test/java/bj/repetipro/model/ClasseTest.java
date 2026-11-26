package bj.repetipro.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClasseTest {

    @Test
    void reconnaitLesSaisiesCourantes() {
        assertEquals(Classe.TROISIEME, Classe.de("3e"));
        assertEquals(Classe.TROISIEME, Classe.de("3ème"));
        assertEquals(Classe.SIXIEME, Classe.de("6e"));
        assertEquals(Classe.CINQUIEME, Classe.de("5ème"));
        assertEquals(Classe.SECONDE, Classe.de("2nde"));
        assertEquals(Classe.PREMIERE, Classe.de("1ere"));
        assertEquals(Classe.TLE, Classe.de("Tle D"));
        assertEquals(Classe.TLE, Classe.de("Terminale C"));
        assertEquals(Classe.CM2, Classe.de("CM2"));
        assertEquals(Classe.CE1, Classe.de("ce 1"));
        assertNull(Classe.de("Licence 1"));
        assertNull(Classe.de(""));
        assertTrue(Classe.meme("3e", "3ème"));
        assertFalse(Classe.meme("3e", "4ème"));
    }

    @Test
    void tarifsParDefaut() {
        assertEquals(20_000, Classe.CI.getTarifDefaut());
        assertEquals(20_000, Classe.CM2.getTarifDefaut());
        assertEquals(25_000, Classe.SIXIEME.getTarifDefaut());
        assertEquals(25_000, Classe.CINQUIEME.getTarifDefaut());
        assertEquals(30_000, Classe.QUATRIEME.getTarifDefaut());
        assertEquals(30_000, Classe.TROISIEME.getTarifDefaut());
        assertEquals(35_000, Classe.SECONDE.getTarifDefaut());
        assertEquals(40_000, Classe.PREMIERE.getTarifDefaut());
        assertEquals(60_000, Classe.TLE.getTarifDefaut());
    }
}
