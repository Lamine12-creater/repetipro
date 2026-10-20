package bj.repetipro.web;

import bj.repetipro.service.EleveService;
import bj.repetipro.service.LimiteGratuiteException;
import bj.repetipro.service.UtilisateurConnecte;
import bj.repetipro.util.Telephones;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Liste, ajout, modification et archivage des élèves. */
@Controller
@RequestMapping("/eleves")
public class EleveController {

    private final EleveService eleves;

    private final bj.repetipro.service.RepetiteurService repetiteurs;

    public EleveController(EleveService eleves, bj.repetipro.service.RepetiteurService repetiteurs) {
        this.eleves = eleves;
        this.repetiteurs = repetiteurs;
    }

    @GetMapping
    public String liste(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        java.util.List<bj.repetipro.model.Eleve> liste = eleves.actifs(moi.getId());
        model.addAttribute("eleves", liste);
        // Lien WhatsApp "bonjour" vers chaque parent (id élève -> lien wa.me), préparé ici
        // car Thymeleaf interdit les appels de méthodes complexes dans th:href.
        String prenomProf = repetiteurs.trouver(moi.getId()).getPrenom();
        java.util.Map<Long, String> liens = new java.util.HashMap<>();
        for (bj.repetipro.model.Eleve e : liste) {
            String l = bj.repetipro.util.WhatsApp.lien(e.getWhatsappParent(),
                    "Bonjour, c'est " + prenomProf + ", le répétiteur de " + e.getPrenom() + ".");
            if (l != null) liens.put(e.getId(), l);
        }
        model.addAttribute("liensWhatsApp", liens);
        model.addAttribute("archives", eleves.archives(moi.getId()));
        model.addAttribute("peutAjouter", eleves.peutAjouter(moi.getId()));
        model.addAttribute("limite", EleveService.LIMITE_GRATUITE);
        return "eleves/liste";
    }

    @GetMapping("/nouveau")
    public String nouveau(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        if (!eleves.peutAjouter(moi.getId())) return "redirect:/formule?limite";
        model.addAttribute("form", new EleveForm());
        model.addAttribute("eleveId", null);
        return "eleves/formulaire";
    }

    @PostMapping
    public String creer(@AuthenticationPrincipal UtilisateurConnecte moi,
                        @Valid @ModelAttribute("form") EleveForm form, BindingResult erreurs,
                        Model model, RedirectAttributes flash) {
        verifierWhatsApp(form, erreurs);
        if (erreurs.hasErrors()) {
            model.addAttribute("eleveId", null);
            return "eleves/formulaire";
        }
        try {
            eleves.creer(moi.getId(), form);
        } catch (LimiteGratuiteException ex) {
            return "redirect:/formule?limite";
        }
        flash.addFlashAttribute("message", "Élève ajouté : " + form.getNom());
        return "redirect:/eleves";
    }

    @GetMapping("/{id}/modifier")
    public String modifier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, Model model) {
        model.addAttribute("form", EleveForm.depuis(eleves.trouver(id, moi.getId())));
        model.addAttribute("eleveId", id);
        return "eleves/formulaire";
    }

    @PostMapping("/{id}")
    public String enregistrer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                              @Valid @ModelAttribute("form") EleveForm form, BindingResult erreurs,
                              Model model, RedirectAttributes flash) {
        verifierWhatsApp(form, erreurs);
        if (erreurs.hasErrors()) {
            model.addAttribute("eleveId", id);
            return "eleves/formulaire";
        }
        eleves.modifier(id, moi.getId(), form);
        flash.addFlashAttribute("message", "Modifications enregistrées.");
        return "redirect:/eleves";
    }

    @PostMapping("/{id}/archiver")
    public String archiver(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, RedirectAttributes flash) {
        eleves.archiver(id, moi.getId());
        flash.addFlashAttribute("message", "Élève archivé (historique conservé).");
        return "redirect:/eleves";
    }

    @PostMapping("/{id}/reactiver")
    public String reactiver(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, RedirectAttributes flash) {
        try {
            eleves.reactiver(id, moi.getId());
        } catch (LimiteGratuiteException ex) {
            return "redirect:/formule?limite";
        }
        flash.addFlashAttribute("message", "Élève réactivé.");
        return "redirect:/eleves";
    }

    /** Le numéro WhatsApp est facultatif ; s'il est saisi, il doit être valide. */
    private void verifierWhatsApp(EleveForm form, BindingResult erreurs) {
        String n = form.getWhatsappParent();
        if (n != null && !n.isBlank() && !Telephones.estValide(n)) {
            erreurs.rejectValue("whatsappParent", "invalide", "Numéro invalide. Exemple : 01 97 00 00 00");
        }
    }
}
