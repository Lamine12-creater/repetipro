package bj.repetipro.web;

import bj.repetipro.model.Paiement;
import bj.repetipro.model.StatutPaiement;
import bj.repetipro.paiement.ConfigPaiement;
import bj.repetipro.paiement.PaiementService;
import bj.repetipro.paiement.SignatureInvalideException;
import bj.repetipro.paiement.SimulationFournisseur;
import bj.repetipro.service.RessourceIntrouvableException;
import bj.repetipro.service.UtilisateurConnecte;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.Set;

/**
 * Paiement en ligne de Solo :
 *   POST /paiement/solo              crée la transaction puis redirige vers la caisse
 *   GET  /paiement/retour/{ref}      page de retour (statut relu chez FedaPay)
 *   POST /paiement/webhook/fedapay   notification FedaPay signée (publique, sans CSRF)
 *   GET/POST /paiement/simulation/{ref}  fausse caisse (profil dev + PAIEMENT_SIMULATION=true)
 */
@Controller
@RequestMapping("/paiement")
public class PaiementController {

    private static final Logger log = LoggerFactory.getLogger(PaiementController.class);
    private static final Set<String> RESEAUX = Set.of("mtn_open", "moov", "sbin");

    private final PaiementService paiements;
    private final ConfigPaiement config;

    public PaiementController(PaiementService paiements, ConfigPaiement config) {
        this.paiements = paiements;
        this.config = config;
    }

    @PostMapping("/solo")
    public String payerSolo(@AuthenticationPrincipal UtilisateurConnecte moi, RedirectAttributes flash) {
        String base = config.urlPublique().isEmpty()
                ? ServletUriComponentsBuilder.fromCurrentContextPath().build().toUriString()
                : config.urlPublique();
        PaiementService.Demarrage d = paiements.demarrerSolo(moi.getId(), base);
        if (!d.ok()) {
            flash.addFlashAttribute("erreur", d.erreur());
            return "redirect:/formule";
        }
        return "redirect:" + d.urlPaiement();
    }

    /** FedaPay ajoute ?id=...&status=... : on les ignore volontairement et on relit le vrai statut. */
    @GetMapping("/retour/{reference}")
    public String retour(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String reference, Model model) {
        Paiement p = paiements.verifierRetour(reference, moi.getId());
        model.addAttribute("paiement", p);
        model.addAttribute("simulation", SimulationFournisseur.CODE.equals(p.getFournisseur()));
        return "paiement/retour";
    }

    @PostMapping(value = "/webhook/fedapay", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public ResponseEntity<String> webhook(@RequestBody(required = false) byte[] corps,
                                          @RequestHeader(value = "X-FEDAPAY-SIGNATURE", required = false) String signature) {
        try {
            PaiementService.Issue issue = paiements.traiterWebhook(corps == null ? new byte[0] : corps, signature);
            return ResponseEntity.ok("{\"received\":true,\"result\":\"" + issue + "\"}");
        } catch (SignatureInvalideException e) {
            log.warn("Webhook FedaPay refusé : {}", e.getMessage());
            return ResponseEntity.badRequest().body("{\"received\":false,\"error\":\"signature\"}");
        }
    }

    // ---------------------------------------------------------------- Simulation (dev)

    @GetMapping("/simulation/{reference}")
    public String caisseSimulee(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String reference, Model model) {
        Paiement p = paiementSimule(reference, moi);
        model.addAttribute("paiement", p);
        return "paiement/simulation";
    }

    @PostMapping("/simulation/{reference}")
    public String resultatSimule(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String reference,
                                 @RequestParam String action, @RequestParam(required = false) String reseau) {
        Paiement p = paiementSimule(reference, moi);
        String moyen = reseau != null && RESEAUX.contains(reseau) ? reseau : "mtn_open";
        String statut = switch (action) {
            case "payer" -> "approved";
            case "echec" -> "declined";
            default -> "canceled";
        };
        if (p.getStatut() != StatutPaiement.PAYE) paiements.simulerResultat(reference, moi.getId(), statut, moyen);
        // Même forme d'URL que FedaPay : ?id=<transaction>&status=<statut>
        return "redirect:/paiement/retour/" + reference + "?id=" + p.getReferenceFournisseur() + "&status=" + statut;
    }

    private Paiement paiementSimule(String reference, UtilisateurConnecte moi) {
        if (!config.simulationActive()) throw new RessourceIntrouvableException("Simulation désactivée");
        Paiement p = paiements.duRepetiteur(reference, moi.getId());
        if (!SimulationFournisseur.CODE.equals(p.getFournisseur())) throw new RessourceIntrouvableException("Paiement non simulé");
        return p;
    }
}
