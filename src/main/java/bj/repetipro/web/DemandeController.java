package bj.repetipro.web;

import bj.repetipro.model.CreneauDemande;
import bj.repetipro.model.DemandeRepetiteur;
import bj.repetipro.service.DemandeService;
import bj.repetipro.service.EleveService;
import bj.repetipro.service.TarifService;
import bj.repetipro.service.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Demandes reçues des parents : le répétiteur accepte ou refuse selon sa disponibilité. */
@Controller
@RequestMapping("/demandes")
public class DemandeController {

    private final DemandeService demandes;
    private final TarifService tarifs;

    public DemandeController(DemandeService demandes, TarifService tarifs) {
        this.demandes = demandes;
        this.tarifs = tarifs;
    }

    @GetMapping
    public String liste(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        List<DemandeRepetiteur> attente = demandes.enAttenteDe(moi.getId());
        Map<Long, List<CreneauDemande>> creneaux = new HashMap<>();
        Map<Long, List<String>> avertissements = new HashMap<>();
        Map<Long, String> liens = new HashMap<>();
        Map<Long, Integer> tarif = new HashMap<>();
        Map<Long, TarifService.Regle> regles = new HashMap<>();
        for (DemandeRepetiteur d : attente) {
            creneaux.put(d.getId(), demandes.creneaux(d.getId()));
            avertissements.put(d.getId(), demandes.avertissements(d));
            liens.put(d.getId(), demandes.lienWhatsApp(d));
            tarif.put(d.getId(), tarifs.montant(d.getEnfant().getClasse()));
            regles.put(d.getId(), tarifs.regle(d.getEnfant().getClasse()));
        }
        List<DemandeRepetiteur> historique = demandes.historique(moi.getId());
        for (DemandeRepetiteur d : historique) {
            creneaux.put(d.getId(), demandes.creneaux(d.getId()));
            liens.put(d.getId(), demandes.lienWhatsApp(d));
        }
        model.addAttribute("attente", attente);
        model.addAttribute("historique", historique);
        model.addAttribute("creneaux", creneaux);
        model.addAttribute("avertissements", avertissements);
        model.addAttribute("liens", liens);
        model.addAttribute("tarifs", tarif);
        model.addAttribute("regles", regles);
        model.addAttribute("limite", EleveService.LIMITE_GRATUITE);
        return "demandes/liste";
    }

    @PostMapping("/{id}/accepter")
    public String accepter(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, RedirectAttributes flash) {
        String erreur = demandes.accepter(id, moi.getId());
        if (erreur != null) flash.addFlashAttribute("erreur", erreur);
        else flash.addFlashAttribute("message", "Demande acceptée : l'élève est ajouté à votre liste avec ses créneaux. Vous pointez ses séances comme d'habitude.");
        return "redirect:/demandes";
    }

    @PostMapping("/{id}/refuser")
    public String refuser(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                          @RequestParam(required = false) String motif, RedirectAttributes flash) {
        String erreur = demandes.refuser(id, moi.getId(), motif);
        if (erreur != null) flash.addFlashAttribute("erreur", erreur);
        else flash.addFlashAttribute("message", "Demande refusée. Le parent en est informé dans son espace.");
        return "redirect:/demandes";
    }
}
