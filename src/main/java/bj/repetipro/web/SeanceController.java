package bj.repetipro.web;

import bj.repetipro.model.Presence;
import bj.repetipro.model.Seance;
import bj.repetipro.service.EleveService;
import bj.repetipro.service.SeanceService;
import bj.repetipro.service.UtilisateurConnecte;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalTime;

/** Planification des séances, pointage et compte rendu. */
@Controller
@RequestMapping("/seances")
public class SeanceController {

    private final SeanceService seances;
    private final EleveService eleves;

    public SeanceController(SeanceService seances, EleveService eleves) {
        this.seances = seances;
        this.eleves = eleves;
    }

    /** Liste : à venir + récentes, et formulaire de planification. */
    @GetMapping
    public String liste(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        SeanceForm form = new SeanceForm();
        form.setDate(LocalDate.now());
        form.setHeure(LocalTime.of(17, 0));
        preparerListe(moi, model, form);
        return "seances/liste";
    }

    @PostMapping
    public String planifier(@AuthenticationPrincipal UtilisateurConnecte moi,
                            @Valid @ModelAttribute("form") SeanceForm form, BindingResult erreurs,
                            Model model, RedirectAttributes flash) {
        if (erreurs.hasErrors()) {
            preparerListe(moi, model, form);
            return "seances/liste";
        }
        seances.planifier(moi.getId(), form.getEleveId(), form.getDate().atTime(form.getHeure()), form.getDureeMinutes());
        flash.addFlashAttribute("message", "Séance planifiée.");
        return "redirect:/seances";
    }

    /** Page de pointage d'une séance. */
    @GetMapping("/{id}")
    public String detail(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, Model model) {
        Seance s = seances.trouver(id, moi.getId());
        model.addAttribute("seance", s);
        model.addAttribute("presences", new Presence[]{Presence.PRESENT, Presence.EN_RETARD, Presence.ABSENT});
        model.addAttribute("lienWhatsApp", seances.lienWhatsApp(s));
        return "seances/detail";
    }

    /**
     * Enregistre présence + compte rendu.
     * Si on a cliqué "Enregistrer et envoyer sur WhatsApp" (action=whatsapp),
     * on redirige directement vers wa.me : WhatsApp s'ouvre avec le message prêt.
     */
    @PostMapping("/{id}")
    public String pointer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                          @RequestParam Presence presence,
                          @RequestParam(required = false) String notions,
                          @RequestParam(required = false) String compteRendu,
                          @RequestParam(defaultValue = "enregistrer") String action,
                          RedirectAttributes flash) {
        seances.pointer(id, moi.getId(), presence, limiter(notions, 255), limiter(compteRendu, 2000));
        if ("whatsapp".equals(action)) {
            String lien = seances.lienWhatsApp(seances.trouver(id, moi.getId()));
            if (lien != null) return "redirect:" + lien;
            flash.addFlashAttribute("erreur", "Pas de numéro WhatsApp pour ce parent : ajoutez-le dans la fiche élève.");
        } else {
            flash.addFlashAttribute("message", "Séance enregistrée.");
        }
        return "redirect:/seances/" + id;
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, RedirectAttributes flash) {
        seances.supprimer(id, moi.getId());
        flash.addFlashAttribute("message", "Séance supprimée.");
        return "redirect:/seances";
    }

    private void preparerListe(UtilisateurConnecte moi, Model model, SeanceForm form) {
        LocalDate aujourdhui = LocalDate.now();
        model.addAttribute("form", form);
        model.addAttribute("eleves", eleves.actifs(moi.getId()));
        model.addAttribute("aVenir", seances.aVenir(moi.getId(), aujourdhui));
        model.addAttribute("recentes", seances.recentes(moi.getId(), aujourdhui));
    }

    private static String limiter(String texte, int max) {
        if (texte == null) return null;
        return texte.length() > max ? texte.substring(0, max) : texte;
    }
}
