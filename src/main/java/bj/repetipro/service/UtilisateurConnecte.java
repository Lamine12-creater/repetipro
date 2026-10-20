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

    public UtilisateurConnecte(Long id, String telephone, String motDePasseHash, boolean admin) {
        this.id = id;
        this.telephone = telephone;
        this.motDePasseHash = motDePasseHash;
        this.admin = admin;
    }

    public Long getId() { return id; }
    public boolean isAdmin() { return admin; }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        List<GrantedAuthority> roles = new ArrayList<>();
        roles.add(new SimpleGrantedAuthority("ROLE_REPETITEUR"));
        if (admin) roles.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        return roles;
    }

    @Override public String getPassword() { return motDePasseHash; }
    @Override public String getUsername() { return telephone; }
}
