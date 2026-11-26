package bj.repetipro.model;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Niveaux scolaires béninois, avec le tarif mensuel par défaut (FCFA).
 * Chaque répétiteur peut modifier ces montants dans sa grille de tarifs.
 */
public enum Classe {

    CI("CI", 20_000, "c.i.", "cours initiation"),
    CP("CP", 20_000, "c.p.", "cours preparatoire"),
    CE1("CE1", 20_000, "c.e.1", "ce 1"),
    CE2("CE2", 20_000, "c.e.2", "ce 2"),
    CM1("CM1", 20_000, "c.m.1", "cm 1"),
    CM2("CM2", 20_000, "c.m.2", "cm 2"),
    SIXIEME("6ème", 25_000, "6e", "6eme", "6è", "6°", "sixieme"),
    CINQUIEME("5ème", 25_000, "5e", "5eme", "5è", "5°", "cinquieme"),
    QUATRIEME("4ème", 30_000, "4e", "4eme", "4è", "4°", "quatrieme"),
    TROISIEME("3ème", 30_000, "3e", "3eme", "3è", "3°", "troisieme"),
    SECONDE("2nde", 35_000, "2de", "seconde"),
    PREMIERE("1ère", 40_000, "1ere", "1re", "1è", "premiere"),
    TLE("Tle", 60_000, "tle a", "tle b", "tle c", "tle d", "terminale", "term");

    private final String libelle;
    private final int tarifDefaut;
    private final String[] alias;

    Classe(String libelle, int tarifDefaut, String... alias) {
        this.libelle = libelle;
        this.tarifDefaut = tarifDefaut;
        this.alias = alias;
    }

    public String getLibelle() { return libelle; }
    public int getTarifDefaut() { return tarifDefaut; }

    /** Primaire, collège ou lycée — pour grouper la page Tarifs. */
    public String getGroupe() {
        return ordinal() <= CM2.ordinal() ? "Primaire"
                : ordinal() <= TROISIEME.ordinal() ? "Collège"
                : "Lycée";
    }

    /**
     * Reconnaît une saisie libre (« 3e », « Tle D », « 6ème »…).
     * @return la classe officielle, ou null si on ne la connaît pas
     */
    public static Classe de(String saisie) {
        if (saisie == null || saisie.isBlank()) return null;
        String n = normaliser(saisie);
        for (Classe c : values()) {
            if (normaliser(c.libelle).equals(n)) return c;
            for (String a : c.alias) {
                if (normaliser(a).equals(n)) return c;
            }
        }
        if (n.startsWith("tle") || n.startsWith("terminale") || n.equals("term")) return TLE;
        return null;
    }

    /** Vrai si deux libellés désignent la même classe officielle (ou le même texte). */
    public static boolean meme(String a, String b) {
        if (a == null || b == null) return false;
        Classe ca = de(a), cb = de(b);
        if (ca != null && cb != null) return ca == cb;
        return a.trim().equalsIgnoreCase(b.trim());
    }

    static String normaliser(String s) {
        String x = Normalizer.normalize(s.trim().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "")
                .replace('°', ' ')
                .replace('.', ' ')
                .replace(',', ' ')
                .replace('\'', ' ')
                .replace('-', ' ');
        return x.replaceAll("\\s+", "");
    }
}
