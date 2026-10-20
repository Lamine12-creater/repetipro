package bj.repetipro.web;

import bj.repetipro.model.Plan;
import bj.repetipro.service.RepetiteurService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Espace admin (réservé aux numéros listés dans repetipro.admin-telephones) :
 * après réception d'un paiement MoMo, on passe le compte en formule Solo à la main.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private final RepetiteurService repetiteurs;

    public AdminController(RepetiteurService repetiteurs) { this.repetiteurs = repetiteurs; }

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("comptes", repetiteurs.tous());
        return "admin/liste";
    }

    @PostMapping("/{id}/plan")
    public String changerPlan(@PathVariable Long id, @RequestParam Plan plan, RedirectAttributes flash) {
        repetiteurs.changerPlan(id, plan);
        flash.addFlashAttribute("message", "Formule mise à jour : " + plan);
        return "redirect:/admin";
    }
}
