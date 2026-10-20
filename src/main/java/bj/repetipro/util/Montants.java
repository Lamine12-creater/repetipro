package bj.repetipro.util;

/** Formatage des montants en FCFA : 25000 -> "25 000 FCFA". */
public final class Montants {

    private Montants() { }

    public static String fcfa(long montant) {
        // %,d ajoute des séparateurs de milliers ; on remplace la virgule par une espace.
        return String.format("%,d", montant).replace(',', ' ') + " FCFA";
    }
}
