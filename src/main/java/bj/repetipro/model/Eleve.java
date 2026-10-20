package bj.repetipro.model;

import jakarta.persistence.*;

/** Un élève suivi par un répétiteur. */
@Entity
public class Eleve {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Le propriétaire : sert à n'afficher que SES élèves au répétiteur connecté. */
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    private Repetiteur repetiteur;

    @Column(nullable = false, length = 100)
    private String nom;

    /** Classe ou niveau : "3e", "Tle D", "CM2"... */
    @Column(nullable = false, length = 30)
    private String classe;

    /** Matières, en texte libre : "Maths, PCT". */
    @Column(length = 150)
    private String matieres;

    @Column(length = 100)
    private String nomParent;

    /** WhatsApp du parent, normalisé au format +229XXXXXXXXXX. */
    @Column(length = 20)
    private String whatsappParent;

    /** Tarif mensuel en FCFA : c'est le montant de la facture du mois. */
    @Column(nullable = false)
    private int tarifMensuel;

    /** Un élève archivé ne compte plus dans la limite gratuite et n'est plus facturé. */
    @Column(nullable = false)
    private boolean actif = true;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Repetiteur getRepetiteur() { return repetiteur; }
    public void setRepetiteur(Repetiteur repetiteur) { this.repetiteur = repetiteur; }
    public String getNom() { return nom; }
    public void setNom(String nom) { this.nom = nom; }
    public String getClasse() { return classe; }
    public void setClasse(String classe) { this.classe = classe; }
    public String getMatieres() { return matieres; }
    public void setMatieres(String matieres) { this.matieres = matieres; }
    public String getNomParent() { return nomParent; }
    public void setNomParent(String nomParent) { this.nomParent = nomParent; }
    public String getWhatsappParent() { return whatsappParent; }
    public void setWhatsappParent(String whatsappParent) { this.whatsappParent = whatsappParent; }
    public int getTarifMensuel() { return tarifMensuel; }
    public void setTarifMensuel(int tarifMensuel) { this.tarifMensuel = tarifMensuel; }
    public boolean isActif() { return actif; }
    public void setActif(boolean actif) { this.actif = actif; }

    /** Initiales pour la pastille ronde de la liste. */
    public String getInitiales() {
        if (nom == null || nom.isBlank()) return "?";
        StringBuilder sb = new StringBuilder();
        for (String mot : nom.trim().split("\\s+")) {
            sb.append(Character.toUpperCase(mot.charAt(0)));
            if (sb.length() == 2) break;
        }
        return sb.toString();
    }

    /** Prénom de l'élève, utilisé dans les messages WhatsApp. */
    public String getPrenom() {
        return nom == null ? "" : nom.trim().split("\\s+")[0];
    }
}
