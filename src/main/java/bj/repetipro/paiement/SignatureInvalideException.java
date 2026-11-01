package bj.repetipro.paiement;

/** Webhook refusé : signature absente, fausse ou trop ancienne. */
public class SignatureInvalideException extends RuntimeException {
    public SignatureInvalideException(String message) { super(message); }
}
