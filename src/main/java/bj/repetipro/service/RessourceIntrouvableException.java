package bj.repetipro.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levée quand un objet n'existe pas OU appartient à un autre répétiteur.
 * On répond "404 introuvable" dans les deux cas : on ne révèle pas que l'objet existe.
 */
@ResponseStatus(HttpStatus.NOT_FOUND)
public class RessourceIntrouvableException extends RuntimeException {
    public RessourceIntrouvableException(String message) { super(message); }
}
