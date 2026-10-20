package bj.repetipro.util;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Construit les liens "wa.me" : ils ouvrent WhatsApp avec un message pré-rempli.
 * Gratuit : pas besoin de l'API WhatsApp Business.
 */
public final class WhatsApp {

    private WhatsApp() { }

    /**
     * @param telephoneNormalise numéro au format +229XXXXXXXXXX
     * @param message texte à pré-remplir (accents et retours à la ligne acceptés)
     * @return l'URL https://wa.me/229...?text=...  ou null si pas de numéro
     */
    public static String lien(String telephoneNormalise, String message) {
        if (telephoneNormalise == null || telephoneNormalise.isBlank()) return null;
        String chiffres = telephoneNormalise.replaceAll("[^0-9]", ""); // wa.me veut les chiffres sans "+"
        // URLEncoder encode les espaces en "+" : WhatsApp préfère "%20", on remplace donc.
        String texte = URLEncoder.encode(message == null ? "" : message, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "https://wa.me/" + chiffres + "?text=" + texte;
    }
}
