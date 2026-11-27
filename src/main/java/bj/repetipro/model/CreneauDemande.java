package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalTime;

/** Un créneau hebdomadaire proposé dans une demande. */
@Entity
@Table(name = "creneau_demande")
public class CreneauDemande {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private DemandeRepetiteur demande;

    @Column(nullable = false)
    private int jour;

    @Column(nullable = false)
    private LocalTime heureDebut;

    @Column(nullable = false)
    private int dureeMinutes;

    protected CreneauDemande() { }

    public CreneauDemande(DemandeRepetiteur demande, int jour, LocalTime heureDebut, int dureeMinutes) {
        this.demande = demande;
        this.jour = jour;
        this.heureDebut = heureDebut;
        this.dureeMinutes = dureeMinutes;
    }

    public Long getId() { return id; }
    public DemandeRepetiteur getDemande() { return demande; }
    public int getJour() { return jour; }
    public LocalTime getHeureDebut() { return heureDebut; }
    public int getDureeMinutes() { return dureeMinutes; }
    public String getResume() {
        return Jours.libelle(jour) + " · " + Jours.heure(heureDebut) + " – " + Jours.heure(heureDebut.plusMinutes(dureeMinutes));
    }
}
