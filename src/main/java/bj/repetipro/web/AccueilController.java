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
import org.springframework.web.bind.annotation.RequestParam;

/** Pages publiques : accueil, connexion, inscription, formule Solo. */
@Controller
public class AccueilController {

    private final RepetiteurService repetiteurs;
    private final bj.repetipro.paiement.ConfigPaiement configPaiement;
    private final bj.repetipro.paiement.PaiementService paiements;
    private final bj.repetipro.service.ParametreService parametresApp;
    private final bj.repetipro.service.ParentService parentsService;

    public AccueilController(RepetiteurService repetiteurs, bj.repetipro.paiement.ConfigPaiement configPaiement,
                             bj.repetipro.paiement.PaiementService paiements,
                             bj.repetipro.service.ParametreService parametresApp,
                             bj.repetipro.service.ParentService parentsService) {
        this.parentsService = parentsService;
        this.parametresApp = parametresApp;
        this.repetiteurs = repetiteurs;
        this.configPaiement = configPaiement;
        this.paiements = paiements;
    }

    @GetMapping("/")
    public String accueil() { return "index"; }

    /** Le formulaire est traité par Spring Security (voir SecurityConfig). */
    @GetMapping("/connexion")
    public String connexion() { return "connexion"; }

    /** Première étape : choisir son type de compte. */
    @GetMapping("/inscription")
    public String choixInscription(@RequestParam(required = false) String parent) {
        if (parent != null) return "redirect:/inscription/parent";   // anciens liens d'invitation
        return "inscription-choix";
    }

    // ----- Compte RÉPÉTITEUR -----

    @GetMapping("/inscription/repetiteur")
    public String inscriptionRepetiteur(Model model) {
        model.addAttribute("form", new InscriptionForm());
        return "inscription";
    }

    /** POST /inscription est gardé comme ancienne adresse du formulaire répétiteur. */
    @PostMapping({"/inscription", "/inscription/repetiteur"})
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
        return repetiteurs.estSuperAdmin(form.getTelephone()) ? "redirect:/admin" : "redirect:/tableau-de-bord?bienvenue";
    }

    // ----- Compte PARENT -----

    @GetMapping("/inscription/parent")
    public String inscriptionParent(Model model) {
        model.addAttribute("form", new InscriptionParentForm());
        return "inscription-parent";
    }

    @PostMapping("/inscription/parent")
    public String inscrireParent(@Valid @ModelAttribute("form") InscriptionParentForm form, BindingResult erreurs,
                                 HttpServletRequest requete) throws ServletException {
        if (form.getMotDePasse() != null && form.getConfirmation() != null
                && !form.getMotDePasse().equals(form.getConfirmation())) {
            erreurs.rejectValue("confirmation", "different", "Les deux mots de passe ne sont pas identiques.");
        }
        if (erreurs.hasErrors()) return "inscription-parent";
        try {
            parentsService.inscrire(form.getNomComplet(), form.getTelephone(), form.getMotDePasse());
        } catch (IllegalArgumentException ex) {
            erreurs.rejectValue("telephone", "invalide", ex.getMessage());
            return "inscription-parent";
        }
        requete.login(form.getTelephone(), form.getMotDePasse());
        return "redirect:/parent?bienvenue";
    }

    /** Formule Solo : paiement en ligne (FedaPay / simulation) et, toujours, le transfert manuel. */
    @GetMapping("/formule")
    public String formule(Model model,
                          @org.springframework.security.core.annotation.AuthenticationPrincipal bj.repetipro.service.UtilisateurConnecte moi) {
        model.addAttribute("momoNumero", parametresApp.momoNumero());
        model.addAttribute("momoNom", parametresApp.momoNom());
        model.addAttribute("limite", bj.repetipro.service.EleveService.LIMITE_GRATUITE);
        model.addAttribute("paiementEnLigne", configPaiement.paiementEnLigneDisponible());
        model.addAttribute("modeSimulation", configPaiement.simulationActive());
        model.addAttribute("modeSandbox", !configPaiement.simulationActive() && configPaiement.fedapayConfigure() && !configPaiement.estLive());
        model.addAttribute("mesPaiements", moi == null ? java.util.List.of() : paiements.derniers(moi.getId()));
        return "formule";
    }
}
