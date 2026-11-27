package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Demande d'un parent à un répétiteur : « suivre mon enfant sur ces créneaux ». */
@Entity
@Table(name = "demande_repetiteur")
public class DemandeRepetiteur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private EnfantParent enfant;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Repetiteur repetiteur;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private StatutDemande statut = StatutDemande.EN_ATTENTE;

    @Column(nullable = false)
    private LocalDateTime dateCreation = LocalDateTime.now();

    private LocalDateTime dateDecision;

    /** Motif donné par le répétiteur en cas de refus (facultatif). */
    @Column(length = 255)
    private String motif;

    public Long getId() { return id; }
    public EnfantParent getEnfant() { return enfant; }
    public void setEnfant(EnfantParent enfant) { this.enfant = enfant; }
    public Repetiteur getRepetiteur() { return repetiteur; }
    public void setRepetiteur(Repetiteur repetiteur) { this.repetiteur = repetiteur; }
    public StatutDemande getStatut() { return statut; }
    public void setStatut(StatutDemande statut) { this.statut = statut; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public LocalDateTime getDateDecision() { return dateDecision; }
    public void setDateDecision(LocalDateTime d) { this.dateDecision = d; }
    public String getMotif() { return motif; }
    public void setMotif(String motif) { this.motif = motif; }
}
