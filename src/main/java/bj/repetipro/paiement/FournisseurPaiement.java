package bj.repetipro.paiement;

import java.util.Optional;

/** Un service qui encaisse le paiement (FedaPay, ou la simulation locale). */
public interface FournisseurPaiement {

    /** Code enregistré dans Paiement.fournisseur. */
    String code();

    /** Crée la transaction chez le fournisseur et renvoie le lien de la page de paiement. */
    Checkout creer(Demande demande);

    /** Statut réel de la transaction chez le fournisseur (vide si non applicable). */
    Optional<EtatDistant> consulter(String referenceFournisseur);

    /** Ce qu'on demande au fournisseur. */
    record Demande(String reference, long montant, String description, String urlRetour, Long repetiteurId) {}

    /** Réponse : identifiant chez le fournisseur + lien vers sa page de paiement. */
    record Checkout(String referenceFournisseur, String urlPaiement) {}

    /** Statut lu chez le fournisseur. */
    record EtatDistant(String statut, long montant, String moyen) {}
}
