package bj.repetipro.model;

/** État d'une demande d'un parent à un répétiteur. */
public enum StatutDemande {
    EN_ATTENTE("En attente"),
    ACCEPTEE("Acceptée"),
    REFUSEE("Refusée"),
    ANNULEE("Annulée");

    private final String libelle;
    StatutDemande(String libelle) { this.libelle = libelle; }
    public String getLibelle() { return libelle; }

    /** Classe CSS du badge d'affichage. */
    public String getClasseBadge() {
        return switch (this) {
            case ACCEPTEE -> "paye";
            case REFUSEE -> "retard";
            default -> "attente";
        };
    }
}
