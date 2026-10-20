package bj.repetipro.util;

/**
 * Outils pour les numéros béninois.
 * Depuis fin 2024, les numéros mobiles du Bénin ont 10 chiffres et commencent par 01
 * (ex. 01 97 00 00 00). Format international : +229 01 97 00 00 00.
 */
public final class Telephones {

    private Telephones() { }

    /**
     * Met un numéro au format "+229XXXXXXXXXX".
     * Accepte : "01 97 00 00 00", "+229 0197000000", "00229...", "229...".
     * Un ancien numéro à 8 chiffres reçoit le préfixe "01".
     * @return le numéro normalisé, ou null s'il est invalide.
     */
    public static String normaliser(String saisie) {
        if (saisie == null) return null;
        String chiffres = saisie.replaceAll("[^0-9]", ""); // on garde seulement les chiffres
        if (chiffres.startsWith("00229")) chiffres = chiffres.substring(5);
        else if (chiffres.startsWith("229") && chiffres.length() > 10) chiffres = chiffres.substring(3);
        if (chiffres.length() == 8) chiffres = "01" + chiffres;   // ancien format
        if (chiffres.length() != 10 || !chiffres.startsWith("01")) return null;
        return "+229" + chiffres;
    }

    /** Vrai si le numéro peut être normalisé. */
    public static boolean estValide(String saisie) {
        return normaliser(saisie) != null;
    }

    /** Pour l'affichage : "+2290197000000" -> "01 97 00 00 00". */
    public static String afficher(String normalise) {
        if (normalise == null || !normalise.startsWith("+229") || normalise.length() != 14) return normalise;
        String n = normalise.substring(4);
        return n.substring(0, 2) + " " + n.substring(2, 4) + " " + n.substring(4, 6) + " "
                + n.substring(6, 8) + " " + n.substring(8, 10);
    }
}
