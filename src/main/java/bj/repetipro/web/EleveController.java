package bj.repetipro.web;

import bj.repetipro.model.Classe;
import bj.repetipro.model.Eleve;
import bj.repetipro.service.EleveService;
import bj.repetipro.service.TarifService;
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
    private final TarifService tarifs;
    private final bj.repetipro.service.PlanningService planning;
    private final bj.repetipro.service.ParentService parentsService;
    private final bj.repetipro.repository.SeanceRepository seancesRepo;

    public EleveController(EleveService eleves, bj.repetipro.service.RepetiteurService repetiteurs, TarifService tarifs,
                           bj.repetipro.service.PlanningService planning, bj.repetipro.service.ParentService parentsService,
                           bj.repetipro.repository.SeanceRepository seancesRepo) {
        this.planning = planning;
        this.parentsService = parentsService;
        this.seancesRepo = seancesRepo;
        this.eleves = eleves;
        this.repetiteurs = repetiteurs;
        this.tarifs = tarifs;
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
        java.util.Map<Long, Boolean> creneauxOk = new java.util.HashMap<>();
        for (bj.repetipro.model.Eleve e : liste) creneauxOk.put(e.getId(), planning.creneauxComplets(e));
        model.addAttribute("creneauxOk", creneauxOk);
        model.addAttribute("archives", eleves.archives(moi.getId()));
        model.addAttribute("peutAjouter", eleves.peutAjouter(moi.getId()));
        model.addAttribute("limite", EleveService.LIMITE_GRATUITE);
        return "eleves/liste";
    }

    /** Fiche d'un élève : règle de séances, créneaux choisis par le parent, séances à pointer, invitation du parent. */
    @GetMapping("/{id}")
    public String fiche(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, Model model,
                        jakarta.servlet.http.HttpServletRequest requete) {
        Eleve e = eleves.trouver(id, moi.getId());
        planning.genererPourEleve(e);
        model.addAttribute("eleve", e);
        model.addAttribute("regle", planning.regle(e));
        model.addAttribute("creneaux", planning.creneaux(e.getId()));
        model.addAttribute("complet", planning.creneauxComplets(e));
        model.addAttribute("compteParent", parentsService.parTelephone(e.getWhatsappParent()).orElse(null));
        model.addAttribute("prochaines", seancesRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(
                e.getId(), java.time.LocalDate.now().minusDays(7).atStartOfDay()).stream().limit(12).toList());
        String lien = lienInvitation(e, repetiteurs.trouver(moi.getId()).getPrenom(), requete);
        model.addAttribute("lienInvitation", lien);
        model.addAttribute("urlInscriptionParent", urlInscription(requete));
        return "eleves/fiche";
    }

    private static String urlInscription(jakarta.servlet.http.HttpServletRequest requete) {
        return org.springframework.web.servlet.support.ServletUriComponentsBuilder.fromContextPath(requete)
                .path("/inscription/parent").build().toUriString();
    }

    /** Lien wa.me : invitation du parent à créer son compte (avec le lien d'inscription pré-rempli dans le message). */
    static String lienInvitation(Eleve e, String prenomRepetiteur, jakarta.servlet.http.HttpServletRequest requete) {
        String tel = e.getWhatsappParent() == null ? "" : Telephones.afficher(e.getWhatsappParent());
        String msg = "Bonjour" + (e.getNomParent() == null || e.getNomParent().isBlank() ? "" : " " + e.getNomParent())
                + ", c'est " + prenomRepetiteur + ", le répétiteur de " + e.getPrenom() + ".\n"
                + "Créez votre compte parent sur RépétiPro avec ce numéro (" + tel + ") : vous choisirez vous-même les jours "
                + "et heures des séances de la semaine, et vous suivrez les présences et les factures.\n"
                + urlInscription(requete);
        if (e.getWhatsappParent() == null || e.getWhatsappParent().isBlank()) {
            return "https://wa.me/?text=" + java.net.URLEncoder.encode(msg, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        }
        return bj.repetipro.util.WhatsApp.lien(e.getWhatsappParent(), msg);
    }

    @GetMapping("/nouveau")
    public String nouveau(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        if (!eleves.peutAjouter(moi.getId())) return "redirect:/formule?limite";
        model.addAttribute("form", new EleveForm());
        model.addAttribute("eleveId", null);
        preparerFormulaire(model, moi.getId(), null);
        return "eleves/formulaire";
    }

    @PostMapping
    public String creer(@AuthenticationPrincipal UtilisateurConnecte moi,
                        @Valid @ModelAttribute("form") EleveForm form, BindingResult erreurs,
                        Model model, RedirectAttributes flash) {
        verifierWhatsApp(form, erreurs);
        verifierClasse(form, erreurs, null);
        if (erreurs.hasErrors()) {
            model.addAttribute("eleveId", null);
            preparerFormulaire(model, moi.getId(), null);
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
        Eleve e = eleves.trouver(id, moi.getId());
        model.addAttribute("form", EleveForm.depuis(e));
        model.addAttribute("eleveId", id);
        preparerFormulaire(model, moi.getId(), e);
        return "eleves/formulaire";
    }

    @PostMapping("/{id}")
    public String enregistrer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                              @Valid @ModelAttribute("form") EleveForm form, BindingResult erreurs,
                              Model model, RedirectAttributes flash) {
        Eleve existant = eleves.trouver(id, moi.getId());
        verifierWhatsApp(form, erreurs);
        verifierClasse(form, erreurs, existant);
        if (erreurs.hasErrors()) {
            model.addAttribute("eleveId", id);
            preparerFormulaire(model, moi.getId(), existant);
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

    /** Nouvelle fiche : uniquement une classe officielle. Modification : on accepte aussi l'ancienne saisie libre. */
    private void verifierClasse(EleveForm form, BindingResult erreurs, Eleve existant) {
        String saisie = form.getClasse();
        if (saisie == null || saisie.isBlank()) return; // @NotBlank s'en charge
        if (Classe.de(saisie) != null) return;
        if (existant != null && existant.getClasse() != null && existant.getClasse().equals(saisie.trim())) return;
        erreurs.rejectValue("classe", "inconnue", "Choisissez une classe dans la liste.");
    }

    private void preparerFormulaire(Model model, Long repetiteurId, Eleve existant) {
        java.util.Map<String, Integer> grille = tarifs.grille();
        model.addAttribute("classes", Classe.values());
        model.addAttribute("grille", grille);
        if (existant != null) {
            Classe c = Classe.de(existant.getClasse());
            model.addAttribute("classeActuelle", c != null ? c.getLibelle() : existant.getClasse());
            model.addAttribute("tarifActuel", existant.getTarifMensuel());
            model.addAttribute("classeLibre", c == null);
            Integer duGrille = c == null ? null : grille.get(c.getLibelle());
            model.addAttribute("tarifAncien", duGrille != null && existant.getTarifMensuel() != duGrille);
        } else {
            model.addAttribute("classeLibre", false);
        }
    }
}
