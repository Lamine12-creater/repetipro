package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalTime;

/** Un créneau hebdomadaire choisi par le parent pour un élève (ex. mardi 17h00, 2 h). */
@Entity
@Table(name = "creneau_eleve")
public class CreneauEleve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Eleve eleve;

    /** Jour de la semaine, 1 = lundi … 7 = dimanche (norme ISO). */
    @Column(nullable = false)
    private int jour;

    @Column(nullable = false)
    private LocalTime heureDebut;

    /** Durée en minutes, fixée par la classe (le parent ne la choisit pas). */
    @Column(nullable = false)
    private int dureeMinutes;

    protected CreneauEleve() { }

    public CreneauEleve(Eleve eleve, int jour, LocalTime heureDebut, int dureeMinutes) {
        this.eleve = eleve;
        this.jour = jour;
        this.heureDebut = heureDebut;
        this.dureeMinutes = dureeMinutes;
    }

    public Long getId() { return id; }
    public Eleve getEleve() { return eleve; }
    public int getJour() { return jour; }
    public LocalTime getHeureDebut() { return heureDebut; }
    public int getDureeMinutes() { return dureeMinutes; }
    public LocalTime getHeureFin() { return heureDebut.plusMinutes(dureeMinutes); }

    public String getJourLibelle() { return Jours.libelle(jour); }

    /** "Mardi · 17h00 – 19h00" */
    public String getResume() {
        return Jours.libelle(jour) + " · " + Jours.heure(heureDebut) + " – " + Jours.heure(getHeureFin());
    }
}
