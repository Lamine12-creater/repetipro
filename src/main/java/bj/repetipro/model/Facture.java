package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;

/**
 * Facture mensuelle d'un élève. Règle choisie (la plus simple) :
 * montant = tarif mensuel de l'élève au moment de la génération.
 * Le nombre de séances du mois est noté à titre d'information.
 */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"eleve_id", "annee", "mois"}))
public class Facture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Repetiteur repetiteur;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Eleve eleve;

    @Column(nullable = false)
    private int annee;

    /** 1 = janvier ... 12 = décembre. */
    @Column(nullable = false)
    private int mois;

    @Column(nullable = false)
    private int montant;

    /** Nombre de séances réalisées (présent ou en retard) dans le mois. */
    private int nombreSeances;

    /** Date limite de paiement (le 10 du mois suivant, par défaut). */
    @Column(nullable = false)
    private LocalDate dateEcheance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutFacture statut = StatutFacture.EN_ATTENTE;

    /** Référence de la transaction Mobile Money, saisie à la main par le répétiteur. */
    @Column(length = 60)
    private String referenceMomo;

    private LocalDateTime datePaiement;

    /** Numéro de facture lisible, ex. "F-2026-10-0007". */
    public String getNumero() {
        return String.format("F-%d-%02d-%04d", annee, mois, id == null ? 0 : id);
    }

    public YearMonth getPeriode() {
        return YearMonth.of(annee, mois);
    }

    /** Vrai si la facture n'est pas payée et que l'échéance est passée. */
    public boolean isEnRetard() {
        return statut == StatutFacture.EN_ATTENTE && LocalDate.now().isAfter(dateEcheance);
    }

    /** Statut affiché : "Payé", "En retard" ou "En attente". */
    public String getStatutAffiche() {
        if (statut == StatutFacture.PAYE) return "Payé";
        return isEnRetard() ? "En retard" : "En attente";
    }

    /** Classe CSS du badge (voir style.css : .badge.paye / .retard / .attente). */
    public String getClasseBadge() {
        if (statut == StatutFacture.PAYE) return "paye";
        return isEnRetard() ? "retard" : "attente";
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Repetiteur getRepetiteur() { return repetiteur; }
    public void setRepetiteur(Repetiteur repetiteur) { this.repetiteur = repetiteur; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public int getAnnee() { return annee; }
    public void setAnnee(int annee) { this.annee = annee; }
    public int getMois() { return mois; }
    public void setMois(int mois) { this.mois = mois; }
    public int getMontant() { return montant; }
    public void setMontant(int montant) { this.montant = montant; }
    public int getNombreSeances() { return nombreSeances; }
    public void setNombreSeances(int nombreSeances) { this.nombreSeances = nombreSeances; }
    public LocalDate getDateEcheance() { return dateEcheance; }
    public void setDateEcheance(LocalDate dateEcheance) { this.dateEcheance = dateEcheance; }
    public StatutFacture getStatut() { return statut; }
    public void setStatut(StatutFacture statut) { this.statut = statut; }
    public String getReferenceMomo() { return referenceMomo; }
    public void setReferenceMomo(String referenceMomo) { this.referenceMomo = referenceMomo; }
    public LocalDateTime getDatePaiement() { return datePaiement; }
    public void setDatePaiement(LocalDateTime datePaiement) { this.datePaiement = datePaiement; }
}
