package bj.repetipro.service;

import bj.repetipro.model.Plan;
import bj.repetipro.model.Repetiteur;
import bj.repetipro.repository.RepetiteurRepository;
import bj.repetipro.util.Telephones;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Comptes des répétiteurs : inscription, connexion, paramètres, formule.
 * Implémente UserDetailsService : Spring Security l'appelle à chaque connexion.
 */
@Service
public class RepetiteurService implements UserDetailsService {

    private final RepetiteurRepository repo;
    private final PasswordEncoder encodeur;
    /** Téléphones administrateurs, lus dans la config (repetipro.admin-telephones). */
    private final Set<String> telephonesAdmin;

    public RepetiteurService(RepetiteurRepository repo, PasswordEncoder encodeur,
                             @Value("${repetipro.admin-telephones:}") String admins) {
        this.repo = repo;
        this.encodeur = encodeur;
        this.telephonesAdmin = Arrays.stream(admins.split(","))
                .map(Telephones::normaliser)
                .filter(t -> t != null)
                .collect(Collectors.toSet());
    }

    /** Appelé par Spring Security au moment de la connexion. */
    @Override
    public UserDetails loadUserByUsername(String saisie) throws UsernameNotFoundException {
        String tel = Telephones.normaliser(saisie);
        Repetiteur r = (tel == null ? null : repo.findByTelephone(tel).orElse(null));
        if (r == null) throw new UsernameNotFoundException("Compte introuvable");
        boolean superAdmin = telephonesAdmin.contains(r.getTelephone());
        // Un SuperAdmin ne peut jamais être bloqué (sinon plus personne ne pourrait réparer).
        return new UtilisateurConnecte(r.getId(), r.getTelephone(), r.getMotDePasseHash(),
                superAdmin, superAdmin || r.isCompteActif());
    }

    /** Crée un compte. Lève IllegalArgumentException avec un message lisible en cas de problème. */
    @Transactional
    public Repetiteur inscrire(String nomComplet, String telephoneSaisi, String motDePasse) {
        String tel = Telephones.normaliser(telephoneSaisi);
        if (tel == null) throw new IllegalArgumentException("Numéro invalide. Exemple : 01 97 00 00 00");
        if (repo.existsByTelephone(tel)) throw new IllegalArgumentException("Un compte existe déjà avec ce numéro.");
        Repetiteur r = new Repetiteur();
        r.setNomComplet(nomComplet.trim());
        r.setTelephone(tel);
        r.setMotDePasseHash(encodeur.encode(motDePasse)); // BCrypt
        r.setNumeroMomo(tel); // par défaut, le numéro MoMo = numéro du compte (modifiable)
        r.setPlan(Plan.GRATUIT);
        return repo.save(r);
    }

    public Repetiteur trouver(Long id) {
        return repo.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable"));
    }

    @Transactional
    public void majParametres(Long id, String nomComplet, String numeroMomo, String nomMomo) {
        Repetiteur r = trouver(id);
        r.setNomComplet(nomComplet.trim());
        r.setNumeroMomo(Telephones.normaliser(numeroMomo));
        r.setNomMomo(nomMomo == null ? null : nomMomo.trim());
    }

    // --- Administration (activation manuelle de la formule Solo) ---

    public List<Repetiteur> tous() {
        return repo.findAllByOrderByDateInscriptionDesc();
    }

    @Transactional
    public void changerPlan(Long id, Plan plan) {
        Repetiteur r = trouver(id);
        r.setPlan(plan);
        // Activation/retrait manuel par l'admin : pas de date de fin (géré à la main).
        r.setSoloJusquAu(null);
    }

    /** Vrai si ce numéro (saisi ou normalisé) est celui d'un SuperAdmin. */
    public boolean estSuperAdmin(String telephone) {
        String tel = Telephones.normaliser(telephone);
        return tel != null && telephonesAdmin.contains(tel);
    }

    public boolean estSuperAdmin(Repetiteur r) { return r != null && telephonesAdmin.contains(r.getTelephone()); }

    /** Prolonge Solo de n mois à partir d'aujourd'hui (ou de la fin actuelle si elle est plus tard). */
    @Transactional
    public void prolongerSolo(Long id, int mois) {
        Repetiteur r = trouver(id);
        java.time.LocalDate base = r.getSoloJusquAu() != null && r.getSoloJusquAu().isAfter(java.time.LocalDate.now())
                ? r.getSoloJusquAu() : java.time.LocalDate.now();
        r.setPlan(Plan.SOLO);
        r.setSoloJusquAu(base.plusMonths(mois));
    }

    /** Suspend Solo : retour à la formule gratuite. */
    @Transactional
    public void suspendreSolo(Long id) {
        Repetiteur r = trouver(id);
        r.setPlan(Plan.GRATUIT);
        r.setSoloJusquAu(null);
    }

    /** Active ou désactive un compte (jamais celui d'un SuperAdmin). @return false si refusé */
    @Transactional
    public boolean changerActivation(Long id, boolean actif) {
        Repetiteur r = trouver(id);
        if (!actif && estSuperAdmin(r)) return false;
        r.setCompteActif(actif);
        return true;
    }
}
