package bj.repetipro.web;

import bj.repetipro.model.*;
import bj.repetipro.repository.FactureRepository;
import bj.repetipro.repository.SeanceRepository;
import bj.repetipro.service.*;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Espace parent (/parent/**, réservé à ROLE_PARENT). Un parent ne voit que ses enfants :
 * les élèves dont le numéro du parent est le sien (voir ParentService.enfant).
 */
@Controller
@RequestMapping("/parent")
public class ParentController {

    private final ParentService parents;
    private final PlanningService planning;
    private final TarifService tarifs;
    private final SeanceRepository seances;
    private final FactureRepository factures;
    private final DemandeService demandes;

    public ParentController(ParentService parents, PlanningService planning, TarifService tarifs,
                            SeanceRepository seances, FactureRepository factures, DemandeService demandes) {
        this.demandes = demandes;
        this.parents = parents;
        this.planning = planning;
        this.tarifs = tarifs;
        this.seances = seances;
        this.factures = factures;
    }

    /** Ligne « enfant » de l'accueil : la fiche créée par le parent et l'état de sa dernière demande. */
    public record EnfantVue(EnfantParent enfant, TarifService.Regle regle, int tarif,
                            DemandeRepetiteur derniere, boolean active, Eleve eleve) { }

    private EnfantVue vue(EnfantParent e) {
        List<DemandeRepetiteur> l = demandes.demandesDeEnfant(e.getId());
        DemandeRepetiteur derniere = l.isEmpty() ? null : l.get(0);
        Eleve suivi = e.getEleve() != null && e.getEleve().isActif() ? e.getEleve() : null;
        boolean active = suivi != null || (derniere != null && derniere.getStatut() == StatutDemande.EN_ATTENTE);
        return new EnfantVue(e, tarifs.regle(e.getClasse()), tarifs.montant(e.getClasse()), derniere, active, suivi);
    }

    @GetMapping
    public String accueil(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        List<EnfantParent> mes = demandes.enfantsDe(moi.getId());
        Set<Long> dejaAffiches = new HashSet<>();
        for (EnfantParent e : mes) if (e.getEleve() != null) dejaAffiches.add(e.getEleve().getId());
        List<Eleve> enfants = parents.enfants(moi.getUsername()).stream().filter(e -> !dejaAffiches.contains(e.getId())).toList();
        Map<Long, TarifService.Regle> regles = new HashMap<>();
        Map<Long, Boolean> complets = new HashMap<>();
        Map<Long, List<CreneauEleve>> creneaux = new HashMap<>();
        for (Eleve e : enfants) {
            regles.put(e.getId(), planning.regle(e));
            complets.put(e.getId(), planning.creneauxComplets(e));
            creneaux.put(e.getId(), planning.creneaux(e.getId()));
        }
        model.addAttribute("mesEnfants", mes.stream().map(this::vue).toList());
        model.addAttribute("enfants", enfants);
        model.addAttribute("regles", regles);
        model.addAttribute("complets", complets);
        model.addAttribute("creneauxParEleve", creneaux);
        return "parent/accueil";
    }

    // ---------- Fiche enfant créée par le parent ----------

    @GetMapping("/enfants/nouveau")
    public String nouvelEnfant(Model model) {
        remplirClasses(model);
        return "parent/enfant-nouveau";
    }

    @PostMapping("/enfants")
    public String creerEnfant(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam String nom, @RequestParam String classe,
                              Model model, RedirectAttributes flash, HttpServletResponse reponse) {
        DemandeService.Resultat r = demandes.creerEnfant(moi.getId(), nom, classe);
        if (r.erreur() != null) {
            reponse.setStatus(HttpStatus.BAD_REQUEST.value());
            remplirClasses(model);
            model.addAttribute("erreur", r.erreur());
            model.addAttribute("saisieNom", nom);
            model.addAttribute("saisieClasse", classe);
            return "parent/enfant-nouveau";
        }
        flash.addFlashAttribute("message", "Fiche créée. Choisissez maintenant un répétiteur.");
        return "redirect:/parent/enfants/" + r.id() + "/repetiteurs";
    }

    @GetMapping("/enfants/{id}")
    public String ficheEnfant(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, Model model) {
        remplirFiche(moi, id, model);
        return "parent/enfant-fiche";
    }

    @PostMapping("/enfants/{id}/infos")
    public String majEnfant(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                            @RequestParam String nom, @RequestParam String classe,
                            Model model, RedirectAttributes flash, HttpServletResponse reponse) {
        String erreur = demandes.majEnfant(id, moi.getId(), nom, classe);
        if (erreur != null) {
            reponse.setStatus(HttpStatus.BAD_REQUEST.value());
            remplirFiche(moi, id, model);
            model.addAttribute("erreur", erreur);
            return "parent/enfant-fiche";
        }
        flash.addFlashAttribute("message", "Informations enregistrées.");
        return "redirect:/parent/enfants/" + id;
    }

    /** Liste des répétiteurs disponibles. */
    @GetMapping("/enfants/{id}/repetiteurs")
    public String repetiteurs(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, Model model) {
        EnfantParent e = demandes.enfant(id, moi.getId());
        model.addAttribute("enfant", e);
        model.addAttribute("regle", tarifs.regle(e.getClasse()));
        model.addAttribute("tarif", tarifs.montant(e.getClasse()));
        model.addAttribute("active", demandes.aDemandeActive(id, moi.getId()));
        model.addAttribute("repetiteurs", demandes.repetiteursDisponibles());
        return "parent/repetiteurs";
    }

    /** Choix des créneaux auprès d'un répétiteur donné. */
    @GetMapping("/enfants/{id}/demande")
    public String formulaireDemande(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                                    @RequestParam Long repetiteur, Model model) {
        remplirDemande(moi, id, repetiteur, model);
        return "parent/demande";
    }

    @PostMapping("/enfants/{id}/demande")
    public String envoyerDemande(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                                 @RequestParam Long repetiteur,
                                 @RequestParam(name = "jour", required = false) List<String> jours,
                                 @RequestParam(name = "heure", required = false) List<String> heures,
                                 @RequestParam(name = "dureeMinutes", required = false) String duree,
                                 Model model, RedirectAttributes flash, HttpServletResponse reponse) {
        DemandeService.Resultat r = demandes.demander(id, moi.getId(), repetiteur, jours, heures, duree);
        if (r.erreur() != null) {
            try {
                remplirDemande(moi, id, repetiteur, model);
            } catch (bj.repetipro.service.RessourceIntrouvableException ex) {
                // Répétiteur devenu indisponible : retour à la liste avec le message
                flash.addFlashAttribute("erreur", r.erreur());
                return "redirect:/parent/enfants/" + id + "/repetiteurs";
            }
            reponse.setStatus(HttpStatus.BAD_REQUEST.value());
            model.addAttribute("erreur", r.erreur());
            model.addAttribute("saisieJours", jours);
            model.addAttribute("saisieHeures", heures);
            return "parent/demande";
        }
        flash.addFlashAttribute("message", "Demande envoyée. Le répétiteur la verra dans ses demandes et vous répondra.");
        return "redirect:/parent/enfants/" + id;
    }

    @PostMapping("/demandes/{id}/annuler")
    public String annulerDemande(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, RedirectAttributes flash) {
        String erreur = demandes.annuler(id, moi.getId());
        if (erreur != null) flash.addFlashAttribute("erreur", erreur);
        else flash.addFlashAttribute("message", "Demande annulée. Vous pouvez choisir un autre répétiteur.");
        return "redirect:/parent";
    }

    private void remplirClasses(Model model) {
        model.addAttribute("classes", Classe.values());
        model.addAttribute("grille", tarifs.grille());
        model.addAttribute("regles", tarifs.regles());
        // Textes prêts à afficher pour l'aperçu du tarif (data-* de la liste des classes)
        Map<String, String> tarifTxt = new HashMap<>(), regleTxt = new HashMap<>();
        for (Classe c : Classe.values()) {
            tarifTxt.put(c.getLibelle(), bj.repetipro.util.Montants.fcfa(tarifs.montant(c.getLibelle())));
            regleTxt.put(c.getLibelle(), tarifs.regle(c.getLibelle()).texte());
        }
        model.addAttribute("tarifTxt", tarifTxt);
        model.addAttribute("regleTxt", regleTxt);
    }

    private void remplirFiche(UtilisateurConnecte moi, Long id, Model model) {
        EnfantParent e = demandes.enfant(id, moi.getId());
        EnfantVue v = vue(e);
        model.addAttribute("enfant", e);
        model.addAttribute("vue", v);
        List<DemandeRepetiteur> l = demandes.demandesDeEnfant(id);
        model.addAttribute("demandes", l);
        Map<Long, List<CreneauDemande>> cren = new HashMap<>();
        for (DemandeRepetiteur d : l) cren.put(d.getId(), demandes.creneaux(d.getId()));
        model.addAttribute("creneauxDemande", cren);
        remplirClasses(model);
    }

    private void remplirDemande(UtilisateurConnecte moi, Long id, Long repetiteurId, Model model) {
        EnfantParent e = demandes.enfant(id, moi.getId());
        Repetiteur r = demandes.repetiteursDisponibles().stream().filter(x -> x.getId().equals(repetiteurId)).findFirst()
                .orElseThrow(() -> new bj.repetipro.service.RessourceIntrouvableException("Répétiteur indisponible"));
        TarifService.Regle regle = tarifs.regle(e.getClasse());
        model.addAttribute("enfant", e);
        model.addAttribute("repetiteur", r);
        model.addAttribute("regle", regle);
        model.addAttribute("tarif", tarifs.montant(e.getClasse()));
        model.addAttribute("jours", Jours.NOMS);
        model.addAttribute("heures", planning.heuresProposees(regle.dureeMinutes()));
        model.addAttribute("lignes", java.util.stream.IntStream.range(0, regle.seances()).boxed().toList());
        model.addAttribute("pris", planning.prisChez(r.getId(), null));
        model.addAttribute("active", demandes.aDemandeActive(id, moi.getId()));
    }

    @GetMapping("/eleves/{id}")
    public String enfant(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, Model model) {
        remplir(moi, id, model, null);
        return "parent/enfant";
    }

    /** Le parent choisit (ou modifie) les créneaux hebdomadaires de son enfant. */
    @PostMapping("/eleves/{id}/creneaux")
    public String creneaux(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                           @RequestParam(name = "jour", required = false) List<String> jours,
                           @RequestParam(name = "heure", required = false) List<String> heures,
                           @RequestParam(name = "dureeMinutes", required = false) String duree,
                           Model model, RedirectAttributes flash, HttpServletResponse reponse) {
        parents.enfant(id, moi.getUsername()); // 404 si ce n'est pas son enfant
        String erreur = planning.enregistrer(id, jours, heures, duree);
        if (erreur != null) {
            reponse.setStatus(HttpStatus.BAD_REQUEST.value());
            remplir(moi, id, model, erreur);
            model.addAttribute("saisieJours", jours);
            model.addAttribute("saisieHeures", heures);
            return "parent/enfant";
        }
        flash.addFlashAttribute("message", "Créneaux enregistrés. Le répétiteur les voit dans son planning.");
        return "redirect:/parent/eleves/" + id;
    }

    /** Le parent corrige le nom ou la classe de son enfant (le tarif suit la grille). */
    @PostMapping("/eleves/{id}/infos")
    public String infos(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                        @RequestParam String nom, @RequestParam String classe,
                        Model model, RedirectAttributes flash, HttpServletResponse reponse) {
        String erreur = parents.majEnfant(id, moi.getUsername(), nom, classe);
        if (erreur != null) {
            reponse.setStatus(HttpStatus.BAD_REQUEST.value());
            remplir(moi, id, model, erreur);
            return "parent/enfant";
        }
        flash.addFlashAttribute("message", "Informations enregistrées.");
        return "redirect:/parent/eleves/" + id;
    }

    private void remplir(UtilisateurConnecte moi, Long id, Model model, String erreur) {
        Eleve e = parents.enfant(id, moi.getUsername());
        planning.genererPourEleve(e);
        TarifService.Regle regle = planning.regle(e);
        List<bj.repetipro.model.CreneauEleve> actuels = planning.creneaux(e.getId());
        LocalDate aujourdhui = LocalDate.now();
        model.addAttribute("enfant", e);
        model.addAttribute("regle", regle);
        model.addAttribute("creneaux", actuels);
        model.addAttribute("complet", planning.creneauxComplets(e));
        model.addAttribute("jours", Jours.NOMS);
        model.addAttribute("heures", planning.heuresProposees(regle.dureeMinutes()));
        model.addAttribute("lignes", java.util.stream.IntStream.range(0, regle.seances()).boxed().toList());
        model.addAttribute("classes", Classe.values());
        model.addAttribute("classeActuelle", Optional.ofNullable(Classe.de(e.getClasse())).map(Classe::getLibelle).orElse(e.getClasse()));
        model.addAttribute("prochaines", seances.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), aujourdhui.atStartOfDay())
                .stream().limit(8).toList());
        model.addAttribute("pointages", seances.findTop10ByEleveIdOrderByDebutDesc(e.getId()).stream()
                .filter(s -> s.getPresence() != bj.repetipro.model.Presence.NON_POINTEE).toList());
        model.addAttribute("factures", factures.findTop12ByEleveIdOrderByIdDesc(e.getId()));
        model.addAttribute("repetiteurNom", e.getRepetiteur().getNomComplet());
        if (erreur != null) model.addAttribute("erreur", erreur);
        model.addAttribute("maintenant", LocalDateTime.now());
    }
}
