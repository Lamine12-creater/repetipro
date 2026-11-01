package bj.repetipro.paiement;

/** L'API du fournisseur de paiement a répondu une erreur ou n'a pas répondu. */
public class FournisseurException extends RuntimeException {
    public FournisseurException(String message, Throwable cause) { super(message, cause); }
    public FournisseurException(String message) { super(message); }
}
