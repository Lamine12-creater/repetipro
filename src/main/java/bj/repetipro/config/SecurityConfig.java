package bj.repetipro.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Sécurité : quelles pages sont publiques, comment se connecter.
 * La protection CSRF est active : Thymeleaf ajoute le jeton caché dans chaque formulaire th:action.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filtres(HttpSecurity http) throws Exception {
        http
            // Pas de jeton CSRF pour le webhook (FedaPay ne peut pas en avoir) : la signature HMAC le remplace.
            .csrf(csrf -> csrf.ignoringRequestMatchers("/paiement/webhook/**"))
            .authorizeHttpRequests(regles -> regles
                // Pages accessibles sans compte
                .requestMatchers("/", "/connexion", "/inscription", "/inscription/**", "/formule",
                                 "/css/**", "/js/**", "/favicon.svg", "/favicon.ico", "/error").permitAll()
                // Webhook FedaPay : appelé par les serveurs FedaPay (sans session) ; protégé par
                // la signature X-FEDAPAY-SIGNATURE vérifiée dans PaiementService.
                .requestMatchers("/paiement/webhook/**").permitAll()
                // Espace SuperAdmin (numéros de ADMIN_TELEPHONES) : tableau de bord, comptes, tarifs, réglages
                .requestMatchers("/admin", "/admin/**", "/tarifs", "/tarifs/**").hasRole("SUPERADMIN")
                // Espace parent : réservé aux comptes parents
                .requestMatchers("/parent", "/parent/**").hasRole("PARENT")
                // Tout le reste (espace répétiteur) : il faut être connecté en répétiteur ; un parent n'y entre pas
                .anyRequest().hasRole("REPETITEUR"))
            .formLogin(form -> form
                .loginPage("/connexion")              // notre page HTML
                .loginProcessingUrl("/connexion")     // le formulaire envoie en POST ici
                .usernameParameter("telephone")
                .passwordParameter("motDePasse")
                // Le SuperAdmin arrive sur son tableau de bord /admin, les répétiteurs sur le leur.
                .successHandler((req, res, auth) -> res.sendRedirect(req.getContextPath()
                        + (auth.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_SUPERADMIN"))
                           ? "/admin"
                           : auth.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ROLE_PARENT"))
                             ? "/parent" : "/tableau-de-bord")))
                .failureUrl("/connexion?erreur")
                .permitAll())
            .logout(logout -> logout
                .logoutUrl("/deconnexion")
                .logoutSuccessUrl("/connexion?deconnecte"))
            // "Se souvenir de moi" pendant 30 jours : pratique sur téléphone
            .rememberMe(rm -> rm.rememberMeParameter("souvenir").tokenValiditySeconds(30 * 24 * 3600)
                // Clé secrète lue dans l'environnement ; à défaut, clé aléatoire
                // (les connexions "souvenir" sont alors perdues à chaque redémarrage).
                .key(System.getenv().getOrDefault("REMEMBER_ME_KEY", java.util.UUID.randomUUID().toString())));
        return http.build();
    }

    /** BCrypt : algorithme de hachage lent, donc résistant aux attaques par force brute. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
