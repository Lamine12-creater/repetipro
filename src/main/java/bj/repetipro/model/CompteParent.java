package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** Compte d'un parent : il voit les élèves dont le répétiteur a saisi son numéro. */
@Entity
@Table(name = "compte_parent")
public class CompteParent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nomComplet;

    /** Téléphone normalisé +229XXXXXXXXXX (identifiant de connexion, unique). */
    @Column(nullable = false, unique = true, length = 20)
    private String telephone;

    @Column(nullable = false, length = 100)
    private String motDePasseHash;

    @Column(nullable = false)
    private LocalDateTime dateInscription = LocalDateTime.now();

    public Long getId() { return id; }
    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getMotDePasseHash() { return motDePasseHash; }
    public void setMotDePasseHash(String h) { this.motDePasseHash = h; }
    public LocalDateTime getDateInscription() { return dateInscription; }

    public String getInitiales() {
        StringBuilder sb = new StringBuilder();
        for (String mot : nomComplet.trim().split("\\s+")) {
            sb.append(Character.toUpperCase(mot.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.toString();
    }
}
