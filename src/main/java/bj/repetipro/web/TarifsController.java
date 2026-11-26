package bj.repetipro.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Ancienne adresse /tarifs : la grille est maintenant unique et gérée par le SuperAdmin.
 * Cette route est réservée à ROLE_SUPERADMIN (403 pour un répétiteur) et renvoie vers /admin/tarifs.
 */
@Controller
public class TarifsController {

    @GetMapping("/tarifs")
    public String ancienneAdresse() { return "redirect:/admin/tarifs"; }
}
