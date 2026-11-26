package bj.repetipro.web;

import bj.repetipro.model.Classe;
import bj.repetipro.model.Eleve;
import bj.repetipro.model.Jours;
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

    public ParentController(ParentService parents, PlanningService planning, TarifService tarifs,
                            SeanceRepository seances, FactureRepository factures) {
        this.parents = parents;
        this.planning = planning;
        this.tarifs = tarifs;
        this.seances = seances;
        this.factures = factures;
    }

    @GetMapping
    public String accueil(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        List<Eleve> enfants = parents.enfants(moi.getUsername());
        Map<Long, TarifService.Regle> regles = new HashMap<>();
        Map<Long, Boolean> complets = new HashMap<>();
        Map<Long, List<bj.repetipro.model.CreneauEleve>> creneaux = new HashMap<>();
        for (Eleve e : enfants) {
            regles.put(e.getId(), planning.regle(e));
            complets.put(e.getId(), planning.creneauxComplets(e));
            creneaux.put(e.getId(), planning.creneaux(e.getId()));
        }
        model.addAttribute("enfants", enfants);
        model.addAttribute("regles", regles);
        model.addAttribute("complets", complets);
        model.addAttribute("creneauxParEleve", creneaux);
        return "parent/accueil";
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
