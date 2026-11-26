package bj.repetipro.web;

import bj.repetipro.model.Classe;
import bj.repetipro.service.TarifService;
import bj.repetipro.service.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/** Grille de tarifs mensuels du répétiteur. */
@Controller
public class TarifsController {

    private final TarifService tarifs;

    public TarifsController(TarifService tarifs) { this.tarifs = tarifs; }

    @GetMapping("/tarifs")
    public String page(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        preparer(model, moi.getId());
        return "tarifs";
    }

    @PostMapping("/tarifs")
    public String enregistrer(@AuthenticationPrincipal UtilisateurConnecte moi,
                              @RequestParam Map<String, String> saisie,
                              Model model, RedirectAttributes flash) {
        String erreur = tarifs.enregistrer(moi.getId(), saisie);
        if (erreur != null) {
            model.addAttribute("erreur", erreur);
            preparer(model, moi.getId());
            // On réaffiche les valeurs saisies plutôt que celles déjà en base.
            model.addAttribute("saisie", saisie);
            return "tarifs";
        }
        flash.addFlashAttribute("message", "Tarifs enregistrés. Ils s'appliqueront aux nouveaux élèves et aux changements de classe.");
        return "redirect:/tarifs";
    }

    private void preparer(Model model, Long repetiteurId) {
        model.addAttribute("classes", Classe.values());
        model.addAttribute("grille", tarifs.grille(repetiteurId));
    }
}
