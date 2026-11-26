package bj.repetipro.config;

import bj.repetipro.service.RepetiteurService;
import bj.repetipro.service.UtilisateurConnecte;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Déconnecte tout de suite un répétiteur dont le compte vient d'être désactivé par le SuperAdmin. */
@Component
public class SessionActiveInterceptor implements HandlerInterceptor {

    private final RepetiteurService repetiteurs;

    public SessionActiveInterceptor(RepetiteurService repetiteurs) { this.repetiteurs = repetiteurs; }

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) throws Exception {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UtilisateurConnecte u && !u.isSuperAdmin() && !u.isParent()) {
            boolean actif;
            try {
                actif = repetiteurs.trouver(u.getId()).isCompteActif();
            } catch (RuntimeException e) {
                actif = false; // compte supprimé
            }
            if (!actif) {
                if (req.getSession(false) != null) req.getSession(false).invalidate();
                SecurityContextHolder.clearContext();
                res.sendRedirect(req.getContextPath() + "/connexion?suspendu");
                return false;
            }
        }
        return true;
    }
}
