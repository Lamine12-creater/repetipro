package bj.repetipro.web;

import bj.repetipro.model.Facture;
import bj.repetipro.service.FactureService;
import bj.repetipro.service.PdfFactureService;
import bj.repetipro.service.UtilisateurConnecte;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.YearMonth;
import java.util.List;

/** Factures mensuelles : génération, paiement manuel, PDF, rappel WhatsApp. */
@Controller
@RequestMapping("/factures")
public class FactureController {

    private final FactureService factures;
    private final PdfFactureService pdf;

    public FactureController(FactureService factures, PdfFactureService pdf) {
        this.factures = factures;
        this.pdf = pdf;
    }

    /** ?mois=2026-10 (par défaut : le mois en cours). */
    @GetMapping
    public String liste(@AuthenticationPrincipal UtilisateurConnecte moi,
                        @RequestParam(required = false) YearMonth mois, Model model) {
        YearMonth m = (mois == null) ? YearMonth.now() : mois;
        List<Facture> liste = factures.duMois(moi.getId(), m);
        long total = liste.stream().mapToLong(Facture::getMontant).sum();
        long paye = liste.stream().filter(f -> "paye".equals(f.getClasseBadge())).mapToLong(Facture::getMontant).sum();

        model.addAttribute("mois", m);
        model.addAttribute("libelleMois", FactureService.libelleMois(m));
        model.addAttribute("moisPrecedent", m.minusMonths(1));
        model.addAttribute("moisSuivant", m.plusMonths(1));
        model.addAttribute("factures", liste);
        model.addAttribute("total", total);
        model.addAttribute("paye", paye);
        model.addAttribute("enAttente", total - paye);
        model.addAttribute("impayeesAutres", factures.impayees(moi.getId()).stream()
                .filter(f -> !f.getPeriode().equals(m)).toList());
        // Liens WhatsApp de rappel, préparés ici : id de facture -> lien wa.me (ou absent si pas de numéro)
        java.util.Map<Long, String> liens = new java.util.HashMap<>();
        java.util.stream.Stream.concat(liste.stream(), factures.impayees(moi.getId()).stream())
                .forEach(f -> { String l = factures.lienRappel(f); if (l != null) liens.put(f.getId(), l); });
        model.addAttribute("liensRappel", liens);
        return "factures/liste";
    }

    @PostMapping("/generer")
    public String generer(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam YearMonth mois,
                          RedirectAttributes flash) {
        int n = factures.genererPourLeMois(moi.getId(), mois);
        flash.addFlashAttribute("message", n == 0
                ? "Toutes les factures " + FactureService.deMois(mois) + " existent déjà."
                : n + " facture(s) créée(s) pour " + FactureService.libelleMois(mois) + ".");
        return "redirect:/factures?mois=" + mois;
    }

    @PostMapping("/{id}/payer")
    public String payer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id,
                        @RequestParam(required = false) String referenceMomo, RedirectAttributes flash) {
        Facture f = factures.trouver(id, moi.getId());
        factures.marquerPayee(id, moi.getId(), referenceMomo == null ? null : referenceMomo.substring(0, Math.min(60, referenceMomo.length())));
        flash.addFlashAttribute("message", "Facture de " + f.getEleve().getPrenom() + " marquée payée.");
        return "redirect:/factures?mois=" + f.getPeriode();
    }

    @PostMapping("/{id}/annuler-paiement")
    public String annulerPaiement(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id) {
        Facture f = factures.trouver(id, moi.getId());
        factures.annulerPaiement(id, moi.getId());
        return "redirect:/factures?mois=" + f.getPeriode();
    }

    @PostMapping("/{id}/supprimer")
    public String supprimer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id, RedirectAttributes flash) {
        Facture f = factures.trouver(id, moi.getId());
        YearMonth m = f.getPeriode();
        factures.supprimer(id, moi.getId());
        flash.addFlashAttribute("message", "Facture supprimée.");
        return "redirect:/factures?mois=" + m;
    }

    /** Téléchargement du PDF. */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> telechargerPdf(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable Long id) {
        Facture f = factures.trouver(id, moi.getId());
        byte[] contenu = pdf.generer(f);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getNumero() + ".pdf\"")
                .body(contenu);
    }
}
