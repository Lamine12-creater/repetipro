package bj.repetipro.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Tests unitaires simples (sans Spring) des utilitaires téléphone / WhatsApp / montants. */
class TelephonesTest {

    @Test
    void normaliseLesFormatsCourants() {
        assertEquals("+2290197000000", Telephones.normaliser("01 97 00 00 00"));
        assertEquals("+2290197000000", Telephones.normaliser("+229 01 97 00 00 00"));
        assertEquals("+2290197000000", Telephones.normaliser("00229-0197000000"));
        assertEquals("+2290197000000", Telephones.normaliser("2290197000000"));
        assertEquals("+2290197000000", Telephones.normaliser("97 00 00 00")); // ancien format : 01 ajouté
    }

    @Test
    void refuseLesNumerosInvalides() {
        assertNull(Telephones.normaliser("02 97 00 00 00")); // doit commencer par 01
        assertNull(Telephones.normaliser("abc"));
        assertNull(Telephones.normaliser(null));
        assertFalse(Telephones.estValide("0197"));
    }

    @Test
    void afficheAvecDesEspaces() {
        assertEquals("01 97 00 00 00", Telephones.afficher("+2290197000000"));
    }

    @Test
    void lienWhatsAppSansPlusEtTexteEncode() {
        String lien = WhatsApp.lien("+2290197000000", "Bonjour Fifamè, 25 000 FCFA & merci !");
        assertTrue(lien.startsWith("https://wa.me/2290197000000?text="), lien);
        assertTrue(lien.contains("Bonjour%20Fifam%C3%A8%2C%2025%20000%20FCFA%20%26%20merci%20%21"), lien);
        assertFalse(lien.contains("+"), "les espaces doivent être %20, pas +");
        assertNull(WhatsApp.lien(null, "x"));
    }

    @Test
    void elisionEnFrancais() {
        assertEquals("d'Ayaba", Textes.de("Ayaba"));
        assertEquals("d'Élodie", Textes.de("Élodie"));
        assertEquals("de Josué", Textes.de("Josué"));
    }

    @Test
    void montantEnFcfa() {
        assertEquals("25 000 FCFA", Montants.fcfa(25000));
        assertEquals("1 500 000 FCFA", Montants.fcfa(1500000));
    }
}
