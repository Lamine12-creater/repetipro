package bj.repetipro.web;

import bj.repetipro.model.Classe;
import bj.repetipro.model.Plan;
import bj.repetipro.service.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Map;

/**
 * Espace SuperAdmin (/admin/**, réservé à ROLE_SUPERADMIN = numéros de ADMIN_TELEPHONES) :
 * tableau de bord, comptes, grille de tarifs unique, réglages de l'application.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final RepetiteurService repetiteurs;
    private final AdminService admin;
    private final TarifService tarifs;
    private final ParametreService parametres;

    public AdminController(RepetiteurService repetiteurs, AdminService admin, TarifService tarifs, ParametreService parametres) {
        this.repetiteurs = repetiteurs;
        this.admin = admin;
        this.tarifs = tarifs;
        this.parametres = parametres;
    }

    // ---------- Tableau de bord ----------

    @GetMapping
    public String tableau(Model model) {
        model.addAttribute("stats", admin.stats());
        model.addAttribute("activites", admin.activiteRecente(20));
        model.addAttribute("maintenant", java.time.LocalDateTime.now());
        return "admin/tableau";
    }

    // ---------- Comptes ----------

    @GetMapping("/comptes")
    public String comptes(Model model) {
        model.addAttribute("comptes", repetiteurs.tous());
        model.addAttribute("repetiteursService", repetiteurs);
        return "admin/comptes";
    }

    @PostMapping("/comptes/{id}/plan")
    public String changerPlan(@PathVariable Long id, @RequestParam Plan plan, RedirectAttributes flash) {
        if (plan == Plan.SOLO) repetiteurs.changerPlan(id, plan);
        else repetiteurs.suspendreSolo(id);
        flash.addFlashAttribute("message", plan == Plan.SOLO ? "Solo activé (sans date de fin)." : "Solo suspendu.");
        return "redirect:/admin/comptes";
    }

    @PostMapping("/comptes/{id}/prolonger")
    public String prolonger(@PathVariable Long id, @RequestParam(defaultValue = "1") int mois, RedirectAttributes flash) {
        int m = Math.max(1, Math.min(mois, 12));
        repetiteurs.prolongerSolo(id, m);
        flash.addFlashAttribute("message", "Solo prolongé de " + m + " mois.");
        return "redirect:/admin/comptes";
    }

    @PostMapping("/comptes/{id}/activation")
    public String activation(@PathVariable Long id, @RequestParam boolean actif, RedirectAttributes flash) {
        if (repetiteurs.changerActivation(id, actif)) {
            flash.addFlashAttribute("message", actif ? "Compte réactivé." : "Compte désactivé : connexion impossible.");
        } else {
            flash.addFlashAttribute("erreur", "Un compte SuperAdmin ne peut pas être désactivé.");
        }
        return "redirect:/admin/comptes";
    }

    // ---------- Tarifs (grille unique) ----------

    @GetMapping("/tarifs")
    public String tarifs(Model model) {
        model.addAttribute("classes", Classe.values());
        model.addAttribute("grille", tarifs.grille());
        return "admin/tarifs";
    }

    @PostMapping("/tarifs")
    public String enregistrerTarifs(@RequestParam Map<String, String> saisie, Model model, RedirectAttributes flash) {
        String erreur = tarifs.enregistrer(saisie);
        if (erreur != null) {
            model.addAttribute("erreur", erreur);
            model.addAttribute("classes", Classe.values());
            model.addAttribute("grille", tarifs.grille());
            model.addAttribute("saisie", saisie);
            return "admin/tarifs";
        }
        flash.addFlashAttribute("message", "Tarifs enregistrés pour tous les répétiteurs. Ils s'appliquent aux nouveaux élèves et aux changements de classe.");
        return "redirect:/admin/tarifs";
    }

    // ---------- Réglages ----------

    @GetMapping("/parametres")
    public String reglages(Model model) {
        remplir(model);
        return "admin/parametres";
    }

    @PostMapping("/parametres")
    public String enregistrerReglages(@RequestParam(defaultValue = "") String prixSolo,
                                      @RequestParam(defaultValue = "") String momoNumero,
                                      @RequestParam(defaultValue = "") String momoNom,
                                      Model model, RedirectAttributes flash) {
        String erreur = parametres.enregistrerReglages(prixSolo, momoNumero, momoNom);
        if (erreur != null) {
            remplir(model);
            model.addAttribute("erreur", erreur);
            model.addAttribute("saisiePrix", prixSolo);
            model.addAttribute("saisieNumero", momoNumero);
            model.addAttribute("saisieNom", momoNom);
            return "admin/parametres";
        }
        flash.addFlashAttribute("message", "Réglages enregistrés.");
        return "redirect:/admin/parametres";
    }

    private void remplir(Model model) {
        model.addAttribute("prixActuel", parametres.prixSolo());
        model.addAttribute("numeroActuel", parametres.momoNumero());
        model.addAttribute("nomActuel", parametres.momoNom());
        model.addAttribute("saisiePrix", parametres.prixSoloEnregistre());
        model.addAttribute("saisieNumero", parametres.momoNumeroEnregistre());
        model.addAttribute("saisieNom", parametres.momoNomEnregistre());
    }
}
