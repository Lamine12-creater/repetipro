package bj.repetipro.service;

import bj.repetipro.model.Classe;
import bj.repetipro.model.CompteParent;
import bj.repetipro.model.Eleve;
import bj.repetipro.repository.EleveRepository;
import bj.repetipro.repository.ParentRepository;
import bj.repetipro.repository.RepetiteurRepository;
import bj.repetipro.util.Telephones;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Comptes parents. Un parent voit les élèves actifs dont le répétiteur a saisi SON numéro
 * (le lien se fait par le numéro de téléphone : pas besoin d'invitation à valider).
 */
@Service
public class ParentService {

    private final ParentRepository repo;
    private final RepetiteurRepository repetiteurs;
    private final EleveRepository eleves;
    private final PasswordEncoder encodeur;
    private final TarifService tarifs;
    private final PlanningService planning;

    public ParentService(ParentRepository repo, RepetiteurRepository repetiteurs, EleveRepository eleves,
                         PasswordEncoder encodeur, TarifService tarifs, PlanningService planning) {
        this.repo = repo;
        this.repetiteurs = repetiteurs;
        this.eleves = eleves;
        this.encodeur = encodeur;
        this.tarifs = tarifs;
        this.planning = planning;
    }

    @Transactional
    public CompteParent inscrire(String nomComplet, String telephoneSaisi, String motDePasse) {
        String tel = Telephones.normaliser(telephoneSaisi);
        if (tel == null) throw new IllegalArgumentException("Numéro invalide. Exemple : 01 97 00 00 00");
        if (repetiteurs.existsByTelephone(tel)) {
            throw new IllegalArgumentException("Ce numéro est déjà utilisé par un compte répétiteur. Un numéro ne peut avoir qu'un seul type de compte : "
                    + "connectez-vous avec ce compte, ou utilisez un autre numéro pour votre compte parent.");
        }
        if (repo.existsByTelephone(tel)) {
            throw new IllegalArgumentException("Un compte parent existe déjà avec ce numéro. Connectez-vous.");
        }
        CompteParent p = new CompteParent();
        p.setNomComplet(nomComplet.trim());
        p.setTelephone(tel);
        p.setMotDePasseHash(encodeur.encode(motDePasse));
        return repo.save(p);
    }

    public Optional<CompteParent> parTelephone(String telephoneNormalise) {
        return telephoneNormalise == null ? Optional.empty() : repo.findByTelephone(telephoneNormalise);
    }

    public CompteParent trouver(Long id) {
        return repo.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable"));
    }

    /** Enfants de ce parent (élèves actifs dont le numéro du parent est le sien). */
    @Transactional(readOnly = true)
    public List<Eleve> enfants(String telephoneParent) {
        return eleves.findByWhatsappParentAndActifTrueOrderByNomAsc(telephoneParent);
    }

    /** Un enfant de ce parent, sinon 404 (jamais l'élève d'un autre parent). */
    @Transactional(readOnly = true)
    public Eleve enfant(Long eleveId, String telephoneParent) {
        return eleves.findById(eleveId)
                .filter(e -> e.isActif() && telephoneParent != null && telephoneParent.equals(e.getWhatsappParent()))
                .orElseThrow(() -> new RessourceIntrouvableException("Enfant introuvable"));
    }

    /**
     * Le parent corrige le nom ou la classe de son enfant. Le tarif suit la grille ; si la règle de séances
     * change, les créneaux sont à rechoisir.
     * @return message d'erreur ou null
     */
    @Transactional
    public String majEnfant(Long eleveId, String telephoneParent, String nom, String classeSaisie) {
        Eleve e = enfant(eleveId, telephoneParent);
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty()) return "Indiquez le nom de l'enfant.";
        if (n.length() > 100) return "Nom trop long (100 caractères maximum).";
        Classe c = Classe.de(classeSaisie);
        if (c == null) return "Choisissez une classe dans la liste.";
        boolean changee = !Classe.meme(e.getClasse(), c.getLibelle());
        e.setNom(n);
        if (changee) {
            e.setClasse(c.getLibelle());
            e.setTarifMensuel(tarifs.montant(c.getLibelle()));
            planning.reconcilier(e);
        }
        return null;
    }
}
