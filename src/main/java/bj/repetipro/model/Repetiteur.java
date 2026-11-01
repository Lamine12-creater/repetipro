package bj.repetipro.model;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Un répétiteur = un compte utilisateur. Toutes les autres données
 * (élèves, séances, factures) lui appartiennent.
 */
@Entity
public class Repetiteur {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Numéro de téléphone normalisé (+229XXXXXXXXXX) : sert d'identifiant de connexion. */
    @Column(nullable = false, unique = true, length = 20)
    private String telephone;

    @Column(nullable = false, length = 100)
    private String nomComplet;

    /** Jamais le mot de passe en clair : seulement son empreinte BCrypt. */
    @Column(nullable = false)
    private String motDePasseHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Plan plan = Plan.GRATUIT;

    /** Numéro Mobile Money affiché sur les factures (les parents paient dessus). */
    @Column(length = 20)
    private String numeroMomo;

    /** Nom qui s'affiche chez l'opérateur quand on envoie l'argent. */
    @Column(length = 100)
    private String nomMomo;

    private LocalDateTime dateInscription = LocalDateTime.now();

    /**
     * Fin de la période Solo payée en ligne (incluse). Vide (null) = Solo activé à la main
     * par l'admin, sans date de fin.
     */
    private LocalDate soloJusquAu;

    // --- Getters / setters (générés classiquement) ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }
    public String getMotDePasseHash() { return motDePasseHash; }
    public void setMotDePasseHash(String motDePasseHash) { this.motDePasseHash = motDePasseHash; }
    public Plan getPlan() { return plan; }
    public void setPlan(Plan plan) { this.plan = plan; }
    public String getNumeroMomo() { return numeroMomo; }
    public void setNumeroMomo(String numeroMomo) { this.numeroMomo = numeroMomo; }
    public String getNomMomo() { return nomMomo; }
    public void setNomMomo(String nomMomo) { this.nomMomo = nomMomo; }
    public LocalDateTime getDateInscription() { return dateInscription; }
    public void setDateInscription(LocalDateTime dateInscription) { this.dateInscription = dateInscription; }
    public LocalDate getSoloJusquAu() { return soloJusquAu; }
    public void setSoloJusquAu(LocalDate soloJusquAu) { this.soloJusquAu = soloJusquAu; }

    /**
     * Vrai si le compte profite de Solo aujourd'hui : formule SOLO et, si elle a été payée
     * en ligne, date de fin pas encore dépassée. Dans les gabarits : ${moi.soloActif}
     */
    public boolean isSoloActif() {
        return plan == Plan.SOLO && (soloJusquAu == null || !soloJusquAu.isBefore(LocalDate.now()));
    }

    /** Vrai si la formule Solo payée en ligne est arrivée à échéance. */
    public boolean isSoloExpire() {
        return plan == Plan.SOLO && soloJusquAu != null && soloJusquAu.isBefore(LocalDate.now());
    }

    /** Prénom (premier mot du nom) pour dire "Bonjour Rodrigue". */
    public String getPrenom() {
        return nomComplet == null ? "" : nomComplet.trim().split("\\s+")[0];
    }

    /** Initiales pour l'avatar rond (ex. "RD"). */
    public String getInitiales() {
        if (nomComplet == null || nomComplet.isBlank()) return "?";
        StringBuilder sb = new StringBuilder();
        for (String mot : nomComplet.trim().split("\\s+")) {
            sb.append(Character.toUpperCase(mot.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.toString();
    }
}
