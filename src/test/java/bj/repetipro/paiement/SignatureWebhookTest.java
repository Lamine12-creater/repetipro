package bj.repetipro.paiement;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** Vérification de X-FEDAPAY-SIGNATURE ("t=...,s=HMAC-SHA256(t.corps)"). */
class SignatureWebhookTest {

    static final String SECRET = "wh_sandbox_test_123";
    static final byte[] CORPS = "{\"name\":\"transaction.approved\",\"entity\":{\"id\":42}}".getBytes(StandardCharsets.UTF_8);
    static final long T = 1_790_000_000L;

    @Test
    void signatureCorrecteAcceptee() {
        String entete = SignatureWebhook.entete(CORPS, SECRET, T);
        assertTrue(entete.matches("t=\\d+,s=[0-9a-f]{64}"));
        assertDoesNotThrow(() -> SignatureWebhook.verifier(CORPS, entete, SECRET, T + 10));
    }

    @Test
    void valeurDeReferenceCalculeeCommeLaBibliothequeOfficielle() {
        // Valeur connue : echo -n "1790000000.{...}" | openssl dgst -sha256 -hmac wh_sandbox_test_123
        assertEquals("bfa020b418baadd98dfc75e42f0d445eadae72a29e4d66849d59dc55ea05af26",
                SignatureWebhook.calculer(T, CORPS, SECRET));
    }

    @Test
    void plusieursSignaturesDontUneBonne() {
        String bonne = SignatureWebhook.calculer(T, CORPS, SECRET);
        assertDoesNotThrow(() -> SignatureWebhook.verifier(CORPS, "t=" + T + ",s=" + "0".repeat(64) + ",s=" + bonne, SECRET, T));
    }

    @Test
    void mauvaisSecretRefuse() {
        String entete = SignatureWebhook.entete(CORPS, "wh_autre", T);
        assertThrows(SignatureInvalideException.class, () -> SignatureWebhook.verifier(CORPS, entete, SECRET, T));
    }

    @Test
    void corpsModifieRefuse() {
        String entete = SignatureWebhook.entete(CORPS, SECRET, T);
        byte[] modifie = "{\"name\":\"transaction.approved\",\"entity\":{\"id\":43}}".getBytes(StandardCharsets.UTF_8);
        assertThrows(SignatureInvalideException.class, () -> SignatureWebhook.verifier(modifie, entete, SECRET, T));
    }

    @Test
    void horodatageTropAncienRefuse() {
        String entete = SignatureWebhook.entete(CORPS, SECRET, T);
        assertThrows(SignatureInvalideException.class,
                () -> SignatureWebhook.verifier(CORPS, entete, SECRET, T + SignatureWebhook.TOLERANCE_SECONDES + 1));
    }

    @Test
    void enteteAbsentOuIllisibleRefuse() {
        assertThrows(SignatureInvalideException.class, () -> SignatureWebhook.verifier(CORPS, null, SECRET, T));
        assertThrows(SignatureInvalideException.class, () -> SignatureWebhook.verifier(CORPS, "n'importe quoi", SECRET, T));
        assertThrows(SignatureInvalideException.class, () -> SignatureWebhook.verifier(CORPS, "t=abc,s=00", SECRET, T));
        assertThrows(SignatureInvalideException.class, () -> SignatureWebhook.verifier(CORPS, "t=" + T, SECRET, T));
    }

    @Test
    void sansSecretConfigureToutEstRefuse() {
        String entete = SignatureWebhook.entete(CORPS, SECRET, T);
        assertThrows(SignatureInvalideException.class, () -> SignatureWebhook.verifier(CORPS, entete, "", T));
    }
}
