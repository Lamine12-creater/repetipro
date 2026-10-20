package bj.repetipro.util;

import java.text.Normalizer;

/** Petits outils de français. */
public final class Textes {

    private Textes() { }

    /** "de Josué", mais "d'Ayaba" / "d'Élodie" / "d'octobre" (élision devant une voyelle ou un h muet courant). */
    public static String de(String mot) {
        if (mot == null || mot.isBlank()) return "de ";
        // On retire les accents pour tester la première lettre : "É" -> "E"
        char c = Character.toLowerCase(Normalizer.normalize(mot.substring(0, 1), Normalizer.Form.NFD).charAt(0));
        return ("aeiouyh".indexOf(c) >= 0 ? "d'" : "de ") + mot;
    }
}
