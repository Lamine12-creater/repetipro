package bj.repetipro.web;

import bj.repetipro.model.Eleve;
import bj.repetipro.util.Telephones;
import jakarta.validation.constraints.*;

/**
 * Formulaire élève. On n'expose pas directement l'entité Eleve au formulaire :
 * ainsi personne ne peut modifier le champ "repetiteur" en trafiquant la requête.
 */
public class EleveForm {

    @NotBlank(message = "Nom obligatoire")
    @Size(max = 100)
    private String nom;

    @NotBlank(message = "Classe obligatoire")
    @Size(max = 30)
    private String classe;

    @Size(max = 150)
    private String matieres;

    @Size(max = 100)
    private String nomParent;

    /** Facultatif, mais s'il est rempli il doit être un numéro béninois valide (vérifié dans le contrôleur). */
    private String whatsappParent;

    @NotNull(message = "Tarif obligatoire")
    @Min(value = 0, message = "Le tarif ne peut pas être négatif")
    @Max(value = 1_000_000, message = "Tarif trop élevé")
    private Integer tarifMensuel;

    /** Pré-remplit le formulaire avec un élève existant (modification). */
    public static EleveForm depuis(Eleve e) {
        EleveForm f = new EleveForm();
        f.nom = e.getNom();
        f.classe = e.getClasse();
        f.matieres = e.getMatieres();
        f.nomParent = e.getNomParent();
        f.whatsappParent = e.getWhatsappParent() == null ? null : Telephones.afficher(e.getWhatsappParent());
        f.tarifMensuel = e.getTarifMensuel();
        return f;
    }

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
    public Integer getTarifMensuel() { return tarifMensuel; }
    public void setTarifMensuel(Integer tarifMensuel) { this.tarifMensuel = tarifMensuel; }
}
