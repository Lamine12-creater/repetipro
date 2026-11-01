package bj.repetipro.paiement;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Vérification de l'en-tête X-FEDAPAY-SIGNATURE, comme les bibliothèques officielles
 * (fedapay-php, lib/WebhookSignature.php) :
 *   en-tête  = "t=<horodatage unix>,s=<signature hex>"
 *   signature = HMAC-SHA256( "<horodatage>.<corps brut>", secret du webhook )
 * L'horodatage doit être récent (5 minutes, valeur par défaut de la bibliothèque) pour
 * bloquer le rejeu d'un ancien message.
 * Doc : https://docs.fedapay.com/fr/integration-api/webhooks.md
 */
public final class SignatureWebhook {

    public static final long TOLERANCE_SECONDES = 300;

    private SignatureWebhook() {}

    /** Lève SignatureInvalideException si l'en-tête ne correspond pas au corps. */
    public static void verifier(byte[] corps, String entete, String secret, long maintenantSecondes) {
        if (secret == null || secret.isBlank()) throw new SignatureInvalideException("secret du webhook non configuré");
        if (entete == null || entete.isBlank()) throw new SignatureInvalideException("en-tête de signature absent");
        long horodatage = -1;
        java.util.List<String> signatures = new java.util.ArrayList<>();
        for (String morceau : entete.split(",")) {
            String[] cv = morceau.trim().split("=", 2);
            if (cv.length != 2) continue;
            if (cv[0].equals("t")) {
                try { horodatage = Long.parseLong(cv[1].trim()); } catch (NumberFormatException e) { horodatage = -1; }
            } else if (cv[0].equals("s")) {
                signatures.add(cv[1].trim());
            }
        }
        if (horodatage < 0) throw new SignatureInvalideException("horodatage illisible");
        if (signatures.isEmpty()) throw new SignatureInvalideException("aucune signature");
        byte[] attendue = calculer(horodatage, corps, secret).getBytes(StandardCharsets.US_ASCII);
        boolean trouvee = false;
        for (String s : signatures) {
            // Comparaison à temps constant (pas d'indice sur les caractères justes).
            if (MessageDigest.isEqual(attendue, s.toLowerCase().getBytes(StandardCharsets.US_ASCII))) trouvee = true;
        }
        if (!trouvee) throw new SignatureInvalideException("signature incorrecte");
        if (Math.abs(maintenantSecondes - horodatage) > TOLERANCE_SECONDES) {
            throw new SignatureInvalideException("horodatage trop ancien");
        }
    }

    /** Construit un en-tête valide (utilisé par la simulation et les tests). */
    public static String entete(byte[] corps, String secret, long horodatage) {
        return "t=" + horodatage + ",s=" + calculer(horodatage, corps, secret);
    }

    static String calculer(long horodatage, byte[] corps, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update((horodatage + ".").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(mac.doFinal(corps));
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA256 indisponible", e);
        }
    }
}
