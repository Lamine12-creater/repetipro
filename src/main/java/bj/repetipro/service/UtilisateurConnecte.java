package bj.repetipro.service;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * L'utilisateur connecté tel que Spring Security le garde en session.
 * On y range l'id du répétiteur pour filtrer toutes les requêtes.
 * Dans un contrôleur : @AuthenticationPrincipal UtilisateurConnecte moi
 */
public class UtilisateurConnecte implements UserDetails {

    private final Long id;
    private final String telephone;
    private final String motDePasseHash;
    private final boolean admin;
    private final boolean actif;

    public UtilisateurConnecte(Long id, String telephone, String motDePasseHash, boolean admin) {
        this(id, telephone, motDePasseHash, admin, true);
    }

    public UtilisateurConnecte(Long id, String telephone, String motDePasseHash, boolean admin, boolean actif) {
        this.actif = actif;
        this.id = id;
        this.telephone = telephone;
        this.motDePasseHash = motDePasseHash;
        this.admin = admin;
    }

    public Long getId() { return id; }
    /** SuperAdmin : numéro listé dans ADMIN_TELEPHONES. */
    public boolean isAdmin() { return admin; }
    public boolean isSuperAdmin() { return admin; }
    @Override public boolean isEnabled() { return actif; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_REPETITEUR"));
        if (admin) {
            roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
            roles.add(new SimpleGrantedAuthority("ROLE_SUPERADMIN"));
        }
        return roles;
    }

    @Override public String getPassword() { return motDePasseHash; }
    @Override public String getUsername() { return telephone; }
}
