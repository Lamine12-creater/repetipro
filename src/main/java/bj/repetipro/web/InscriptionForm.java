package bj.repetipro.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Données du formulaire d'inscription (validées par @Valid dans le contrôleur). */
public class InscriptionForm {

    @NotBlank(message = "Indiquez votre nom")
    @Size(max = 100)
    private String nomComplet;

    @NotBlank(message = "Indiquez votre numéro")
    private String telephone;

    @NotBlank(message = "Choisissez un mot de passe")
    @Size(min = 6, max = 72, message = "6 caractères minimum")
    private String motDePasse;

    public String getNomComplet() { return nomComplet; }
    public void setNomComplet(String nomComplet) { this.nomComplet = nomComplet; }
    public String getTelephone() { return telephone; }
    public void setTelephone(String telephone) { this.telephone = telephone; }
    public String getMotDePasse() { return motDePasse; }
    public void setMotDePasse(String motDePasse) { this.motDePasse = motDePasse; }
}
