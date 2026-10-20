package bj.repetipro.web;

import bj.repetipro.model.Repetiteur;
import bj.repetipro.service.RepetiteurService;
import bj.repetipro.service.UtilisateurConnecte;
import bj.repetipro.util.Telephones;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Paramètres du répétiteur : nom et numéro Mobile Money affiché sur les factures. */
@Controller
public class ParametresController {

    private final RepetiteurService repetiteurs;

    public ParametresController(RepetiteurService repetiteurs) { this.repetiteurs = repetiteurs; }

    @GetMapping("/parametres")
    public String parametres(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        Repetiteur r = repetiteurs.trouver(moi.getId());
        model.addAttribute("numeroMomo", Telephones.afficher(r.getNumeroMomo()));
        return "parametres";
    }

    @PostMapping("/parametres")
    public String enregistrer(@AuthenticationPrincipal UtilisateurConnecte moi,
                              @RequestParam String nomComplet,
                              @RequestParam(required = false) String numeroMomo,
                              @RequestParam(required = false) String nomMomo,
                              Model model, RedirectAttributes flash) {
        boolean momoInvalide = numeroMomo != null && !numeroMomo.isBlank() && !Telephones.estValide(numeroMomo);
        if (nomComplet == null || nomComplet.isBlank() || nomComplet.length() > 100 || momoInvalide) {
            model.addAttribute("erreur", momoInvalide ? "Numéro Mobile Money invalide. Exemple : 01 97 00 00 00" : "Nom obligatoire.");
            model.addAttribute("numeroMomo", numeroMomo);
            return "parametres";
        }
        repetiteurs.majParametres(moi.getId(), nomComplet, numeroMomo,
                nomMomo == null ? null : nomMomo.substring(0, Math.min(100, nomMomo.length())));
        flash.addFlashAttribute("message", "Paramètres enregistrés.");
        return "redirect:/parametres";
    }
}
