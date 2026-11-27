package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Fiche d'un enfant créée par son parent (avant d'avoir un répétiteur). Devient un {@link Eleve} à l'acceptation. */
@Entity
@Table(name = "enfant_parent")
public class EnfantParent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private CompteParent parent;

    @Column(nullable = false, length = 100)
    private String nom;

    /** Libellé officiel de la classe (grille). */
    @Column(nullable = false, length = 30)
    private String classe;

    private LocalDateTime dateCreation = LocalDateTime.now();

    /** Renseigné quand un répétiteur a accepté : l'élève suivi chez ce répétiteur. */
    @ManyToOne(fetch = FetchType.LAZY)
    private Eleve eleve;

    public Long getId() { return id; }
    public CompteParent getParent() { return parent; }
    public void setParent(CompteParent parent) { this.parent = parent; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }
    public LocalDateTime getDateCreation() { return dateCreation; }
    public Eleve getEleve() { return eleve; }
    public void setEleve(Eleve eleve) { this.eleve = eleve; }
}
