package bj.repetipro.model;

/** Où en est un paiement en ligne (statuts FedaPay traduits). */
public enum StatutPaiement {
    EN_ATTENTE("En attente"),
    PAYE("Payé"),
    ECHOUE("Échoué"),
    ANNULE("Annulé"),
    EXPIRE("Expiré"),
    REMBOURSE("Remboursé");

    private final String libelle;

    StatutPaiement(String libelle) { this.libelle = libelle; }

    public String getLibelle() { return libelle; }

    /**
     * Traduit un statut FedaPay (pending, approved, declined, canceled, expired, refunded,
     * transferred). Renvoie null pour un statut inconnu.
     * Doc : https://docs.fedapay.com/fr/integration-api/collects-management.md (cycle de vie)
     */
    public static StatutPaiement depuisFedaPay(String statut) {
        if (statut == null) return null;
        return switch (statut.trim().toLowerCase()) {
            case "pending" -> EN_ATTENTE;
            case "approved", "transferred" -> PAYE;
            case "declined" -> ECHOUE;
            case "canceled", "cancelled" -> ANNULE;
            case "expired" -> EXPIRE;
            case "refunded" -> REMBOURSE;
            default -> null;
        };
    }
}
