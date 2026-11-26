package bj.repetipro.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Formulaire d'inscription d'un PARENT (différent de celui du répétiteur : il confirme son mot de passe). */
public class InscriptionParentForm {

    @NotBlank(message = "Indiquez votre nom")
    @Size(max = 100)
    private String nomComplet;

    @NotBlank(message = "Indiquez votre numéro")
    private String telephone;

    @NotBlank(message = "Choisissez un mot de passe")
    @Size(min = 6, max = 72, message = "6 caractères minimum")
    private String motDePasse;

    @NotBlank(message = "Retapez votre mot de passe")
    private String confirmation;

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
    public String getConfirmation() { return confirmation; }
    public void setConfirmation(String confirmation) { this.confirmation = confirmation; }
}
