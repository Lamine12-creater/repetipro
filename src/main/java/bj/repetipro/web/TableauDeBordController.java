package bj.repetipro.web;

import bj.repetipro.model.Facture;
import bj.repetipro.model.Seance;
import bj.repetipro.service.EleveService;
import bj.repetipro.service.FactureService;
import bj.repetipro.service.SeanceService;
import bj.repetipro.service.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.util.List;

/** Page d'accueil du répétiteur connecté. */
@Controller
public class TableauDeBordController {

    private final SeanceService seances;
    private final FactureService factures;
    private final EleveService eleves;

    public TableauDeBordController(SeanceService seances, FactureService factures, EleveService eleves) {
        this.seances = seances;
        this.factures = factures;
        this.eleves = eleves;
    }

    @GetMapping("/tableau-de-bord")
    public String tableauDeBord(@AuthenticationPrincipal UtilisateurConnecte moi, Model model) {
        LocalDate aujourdhui = LocalDate.now();
        List<Seance> duJour = seances.duJour(moi.getId(), aujourdhui);
        List<Facture> impayees = factures.impayees(moi.getId());
        long aEncaisser = impayees.stream().mapToLong(Facture::getMontant).sum();

        model.addAttribute("aujourdhui", aujourdhui);
        model.addAttribute("seancesDuJour", duJour);
        model.addAttribute("impayees", impayees);
        model.addAttribute("aEncaisser", aEncaisser);
        model.addAttribute("nbEleves", eleves.actifs(moi.getId()).size());
        return "tableau-de-bord";
    }
}
