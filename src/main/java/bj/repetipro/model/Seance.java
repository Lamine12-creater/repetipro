package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Une séance de cours planifiée avec un élève. */
@Entity
public class Seance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Repetiteur repetiteur;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Eleve eleve;

    /** Date et heure de début. */
    @Column(nullable = false)
    private LocalDateTime debut;

    /** Durée en minutes (ex. 120 pour 2 h). */
    @Column(nullable = false)
    private int dureeMinutes = 120;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Presence presence = Presence.NON_POINTEE;

    @Column(length = 255)
    private String notions;

    /** Compte rendu pour le parent (texte long). */
    @Column(length = 2000)
    private String compteRendu;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Repetiteur getRepetiteur() { return repetiteur; }
    public void setRepetiteur(Repetiteur repetiteur) { this.repetiteur = repetiteur; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
    public LocalDateTime getDebut() { return debut; }
    public void setDebut(LocalDateTime debut) { this.debut = debut; }
    public int getDureeMinutes() { return dureeMinutes; }
    public void setDureeMinutes(int dureeMinutes) { this.dureeMinutes = dureeMinutes; }
    public Presence getPresence() { return presence; }
    public void setPresence(Presence presence) { this.presence = presence; }
    public String getNotions() { return notions; }
    public void setNotions(String notions) { this.notions = notions; }
    public String getCompteRendu() { return compteRendu; }
    public void setCompteRendu(String compteRendu) { this.compteRendu = compteRendu; }

    /** Durée lisible : "2 h", "1 h 30". */
    public String getDureeLisible() {
        int h = dureeMinutes / 60, m = dureeMinutes % 60;
        if (h == 0) return m + " min";
        return m == 0 ? h + " h" : h + " h " + String.format("%02d", m);
    }
}
