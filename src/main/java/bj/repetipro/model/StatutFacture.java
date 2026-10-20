package bj.repetipro.model;

/**
 * Statut STOCKÉ d'une facture. "En retard" n'est pas stocké :
 * il est calculé (facture en attente dont l'échéance est dépassée), voir Facture#getStatutAffiche().
 */
public enum StatutFacture {
    EN_ATTENTE,
    PAYE
}
