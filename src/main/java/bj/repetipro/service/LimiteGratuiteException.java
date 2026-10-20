package bj.repetipro.service;

/** Levée quand un compte gratuit veut dépasser la limite d'élèves actifs. */
public class LimiteGratuiteException extends RuntimeException {
    public LimiteGratuiteException(int limite) {
        super("La formule gratuite est limitée à " + limite + " élèves actifs.");
    }
}
