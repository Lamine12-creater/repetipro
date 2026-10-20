package bj.repetipro.web;

import bj.repetipro.service.RepetiteurService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

/** Pages publiques : accueil, connexion, inscription, formule Solo. */
@Controller
public class AccueilController {

    private final RepetiteurService repetiteurs;

    public AccueilController(RepetiteurService repetiteurs) { this.repetiteurs = repetiteurs; }

    @GetMapping("/")
    public String accueil() { return "index"; }

    /** Le formulaire est traité par Spring Security (voir SecurityConfig). */
    @GetMapping("/connexion")
    public String connexion() { return "connexion"; }

    @GetMapping("/inscription")
    public String inscription(Model model) {
        model.addAttribute("form", new InscriptionForm());
        return "inscription";
    }

    @PostMapping("/inscription")
    public String inscrire(@Valid @ModelAttribute("form") InscriptionForm form, BindingResult erreurs,
                           HttpServletRequest requete) throws ServletException {
        if (erreurs.hasErrors()) return "inscription";
        try {
            repetiteurs.inscrire(form.getNomComplet(), form.getTelephone(), form.getMotDePasse());
        } catch (IllegalArgumentException ex) {
            erreurs.rejectValue("telephone", "invalide", ex.getMessage());
            return "inscription";
        }
        // Connexion automatique juste après l'inscription.
        requete.login(form.getTelephone(), form.getMotDePasse());
        return "redirect:/tableau-de-bord?bienvenue";
    }

    /** Explique comment passer à la formule Solo (paiement MoMo manuel). */
    @GetMapping("/formule")
    public String formule(Model model,
                          @org.springframework.beans.factory.annotation.Value("${repetipro.paiement.momo-numero}") String numero,
                          @org.springframework.beans.factory.annotation.Value("${repetipro.paiement.momo-nom}") String nom) {
        model.addAttribute("momoNumero", numero);
        model.addAttribute("momoNom", nom);
        model.addAttribute("limite", bj.repetipro.service.EleveService.LIMITE_GRATUITE);
        return "formule";
    }
}
