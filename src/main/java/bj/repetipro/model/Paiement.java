package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un paiement en ligne de l'abonnement Solo (FedaPay ou simulation locale).
 * "reference" est NOTRE référence, envoyée à FedaPay comme merchant_reference ;
 * "referenceFournisseur" est l'identifiant de la transaction chez FedaPay.
 */
@Entity
@Table(indexes = {
        @Index(name = "idx_paiement_fournisseur_ref", columnList = "fournisseur, referenceFournisseur"),
        @Index(name = "idx_paiement_repetiteur", columnList = "repetiteur_id")
})
public class Paiement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Repetiteur repetiteur;

    /** FEDAPAY ou SIMULATION. */
    @Column(nullable = false, length = 20)
    private String fournisseur;

    @Column(nullable = false, unique = true, length = 40)
    private String reference;

    @Column(length = 60)
    private String referenceFournisseur;

    /** Montant en FCFA (XOF), sans centimes. */
    @Column(nullable = false)
    private long montant;

    @Column(nullable = false, length = 3)
    private String devise = "XOF";

    /** Ce qui est acheté : pour l'instant toujours un mois de Solo. */
    @Column(nullable = false, length = 30)
    private String objet = "SOLO_1_MOIS";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutPaiement statut = StatutPaiement.EN_ATTENTE;

    /** Réseau utilisé quand le fournisseur le donne (mtn_open, moov, sbin...). */
    @Column(length = 40)
    private String moyen;

    /** Remarque technique (erreur API, montant inattendu...). */
    @Column(length = 255)
    private String detail;

    private LocalDateTime dateCreation = LocalDateTime.now();
    private LocalDateTime dateMaj = LocalDateTime.now();
    private LocalDateTime datePaiement;

    /** Fin de la période Solo obtenue grâce à ce paiement. */
    private LocalDate soloJusquAu;

    public Long getId() { return id; }
    public Repetiteur getRepetiteur() { return repetiteur; }
    public void setRepetiteur(Repetiteur repetiteur) { this.repetiteur = repetiteur; }
    public String getFournisseur() { return fournisseur; }
    public void setFournisseur(String fournisseur) { this.fournisseur = fournisseur; }
    public String getReference() { return reference; }
    public void setReference(String reference) { this.reference = reference; }
    public String getReferenceFournisseur() { return referenceFournisseur; }
    public void setReferenceFournisseur(String referenceFournisseur) { this.referenceFournisseur = referenceFournisseur; }
    public long getMontant() { return montant; }
    public void setMontant(long montant) { this.montant = montant; }
    public String getDevise() { return devise; }
    public void setDevise(String devise) { this.devise = devise; }
    public String getObjet() { return objet; }
    public void setObjet(String objet) { this.objet = objet; }
    public StatutPaiement getStatut() { return statut; }
    public void setStatut(StatutPaiement statut) { this.statut = statut; this.dateMaj = LocalDateTime.now(); }
    public String getMoyen() { return moyen; }
    public void setMoyen(String moyen) { this.moyen = moyen; }
    public String getDetail() { return detail; }
    public void setDetail(String detail) {
        this.detail = detail == null ? null : detail.substring(0, Math.min(255, detail.length()));
    }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateMaj() { return dateMaj; }
    public LocalDateTime getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDateTime datePaiement) { this.datePaiement = datePaiement; }
    public LocalDate getSoloJusquAu() { return soloJusquAu; }
    public void setSoloJusquAu(LocalDate soloJusquAu) { this.soloJusquAu = soloJusquAu; }

    /** Nom du réseau lisible (pour l'affichage). */
    public String getMoyenAffiche() {
        if (moyen == null) return null;
        return switch (moyen.toLowerCase()) {
            case "mtn", "mtn_open", "mtn_momo" -> "MTN MoMo";
            case "moov" -> "Moov Money";
            case "sbin", "celtiis" -> "Celtiis Cash";
            case "momo_test" -> "Mobile Money (test)";
            default -> moyen;
        };
    }
}
