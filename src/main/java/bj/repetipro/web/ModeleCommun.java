package bj.repetipro.web;

import bj.repetipro.model.Repetiteur;
import bj.repetipro.service.RepetiteurService;
import bj.repetipro.service.UtilisateurConnecte;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * S'exécute avant chaque contrôleur : met le répétiteur connecté ("moi")
 * à disposition de toutes les pages (barre du haut, prénom, initiales...).
 */
@ControllerAdvice
public class ModeleCommun {

    private final RepetiteurService repetiteurs;

    private final bj.repetipro.service.ParametreService parametres;

    private final bj.repetipro.service.ParentService parents;

    public ModeleCommun(RepetiteurService repetiteurs, bj.repetipro.service.ParametreService parametres,
                        bj.repetipro.service.ParentService parents) {
        this.parents = parents;
        this.repetiteurs = repetiteurs;
        this.parametres = parametres;
    }

    /** Prix de Solo (réglable par le SuperAdmin), pour les pages publiques et connectées. */
    @ModelAttribute("prixSolo")
    public int prixSolo() { return parametres.prixSolo(); }

    @ModelAttribute("moi")
    public Repetiteur moi(@AuthenticationPrincipal UtilisateurConnecte u) {
        return u == null || u.isParent() ? null : repetiteurs.trouver(u.getId());
    }

    /** Le parent connecté (pages /parent), sinon null. */
    @ModelAttribute("moiParent")
    public bj.repetipro.model.CompteParent moiParent(@AuthenticationPrincipal UtilisateurConnecte u) {
        return u != null && u.isParent() ? parents.trouver(u.getId()) : null;
    }

    /** Rubrique courante ("eleves", "seances"...) pour surligner le bon onglet du menu. */
    @ModelAttribute("rubrique")
    public String rubrique(jakarta.servlet.http.HttpServletRequest requete) {
        String uri = requete.getRequestURI();
        if (uri.startsWith("/eleves")) return "eleves";
        if (uri.startsWith("/seances")) return "seances";
        if (uri.startsWith("/factures")) return "factures";
        if (uri.startsWith("/tableau-de-bord")) return "accueil";
        if (uri.startsWith("/admin")) return "admin";
        if (uri.startsWith("/parent")) return "parent";
        return "";
    }

    @ModelAttribute("estAdmin")
    public boolean estAdmin(@AuthenticationPrincipal UtilisateurConnecte u) {
        return u != null && u.isAdmin();
    }
}
