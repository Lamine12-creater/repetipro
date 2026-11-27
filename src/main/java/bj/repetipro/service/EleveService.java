package bj.repetipro.service;

import bj.repetipro.model.Classe;
import bj.repetipro.model.Eleve;
import bj.repetipro.model.Repetiteur;
import bj.repetipro.repository.EleveRepository;
import bj.repetipro.util.Telephones;
import bj.repetipro.web.EleveForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Règles métier des élèves (dont la limite de la formule gratuite). */
@Service
public class EleveService {

    /** Nombre maximum d'élèves actifs en formule gratuite. */
    public static final int LIMITE_GRATUITE = 3;

    private final EleveRepository repo;
    private final RepetiteurService repetiteurs;
    private final TarifService tarifs;
    private final PlanningService planning;

    public EleveService(EleveRepository repo, RepetiteurService repetiteurs, TarifService tarifs, PlanningService planning) {
        this.planning = planning;
        this.repo = repo;
        this.repetiteurs = repetiteurs;
        this.tarifs = tarifs;
    }

    public List<Eleve> actifs(Long repetiteurId) {
        return repo.findByRepetiteurIdAndActifTrueOrderByNomAsc(repetiteurId);
    }

    public List<Eleve> archives(Long repetiteurId) {
        return repo.findByRepetiteurIdAndActifFalseOrderByNomAsc(repetiteurId);
    }

    /** Renvoie l'élève SEULEMENT s'il appartient au répétiteur, sinon 404. */
    public Eleve trouver(Long id, Long repetiteurId) {
        return repo.findByIdAndRepetiteurId(id, repetiteurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Élève introuvable"));
    }

    /** Vrai si le répétiteur peut encore ajouter un élève actif. */
    public boolean peutAjouter(Long repetiteurId) {
        Repetiteur r = repetiteurs.trouver(repetiteurId);
        return r.isSoloActif() || repo.countByRepetiteurIdAndActifTrue(repetiteurId) < LIMITE_GRATUITE;
    }

    /** Crée l'élève quand un répétiteur accepte la demande d'un parent (tarif de la grille, limite gratuite respectée). */
    @Transactional
    public Eleve creerPourParent(Long repetiteurId, String nom, String classe, String nomParent, String telephoneParent) {
        if (!peutAjouter(repetiteurId)) throw new LimiteGratuiteException(LIMITE_GRATUITE);
        Eleve e = new Eleve();
        e.setRepetiteur(repetiteurs.trouver(repetiteurId));
        e.setNom(nom);
        e.setClasse(classe);
        e.setNomParent(nomParent);
        e.setWhatsappParent(telephoneParent);
        e.setTarifMensuel(tarifs.montant(classe));
        return repo.save(e);
    }

    @Transactional
    public Eleve creer(Long repetiteurId, EleveForm f) {
        if (!peutAjouter(repetiteurId)) throw new LimiteGratuiteException(LIMITE_GRATUITE);
        Eleve e = new Eleve();
        e.setRepetiteur(repetiteurs.trouver(repetiteurId));
        copier(f, e);
        return repo.save(e);
    }

    @Transactional
    public void modifier(Long id, Long repetiteurId, EleveForm f) {
        copier(f, trouver(id, repetiteurId)); // l'entité est "suivie" : sauvegarde automatique
    }

    /** Archive (au lieu de supprimer) pour garder l'historique des séances et factures. */
    @Transactional
    public void archiver(Long id, Long repetiteurId) {
        trouver(id, repetiteurId).setActif(false);
    }

    @Transactional
    public void reactiver(Long id, Long repetiteurId) {
        if (!peutAjouter(repetiteurId)) throw new LimiteGratuiteException(LIMITE_GRATUITE);
        trouver(id, repetiteurId).setActif(true);
    }

    private void copier(EleveForm f, Eleve e) {
        String ancienne = e.getClasse();
        String saisie = f.getClasse() == null ? "" : f.getClasse().trim();
        Classe officielle = Classe.de(saisie);
        String nouvelle = officielle != null ? officielle.getLibelle() : saisie;
        e.setNom(f.getNom().trim());
        e.setClasse(nouvelle);
        e.setMatieres(f.getMatieres());
        e.setNomParent(f.getNomParent());
        e.setWhatsappParent(Telephones.normaliser(f.getWhatsappParent()));
        // Le montant saisi par le client est ignoré : on prend la grille, sauf
        // pour un élève déjà enregistré dont la classe n'a pas changé (ancien tarif conservé).
        boolean nouveau = e.getId() == null;
        boolean classeChangee = ancienne != null && !Classe.meme(ancienne, nouvelle);
        if (nouveau || classeChangee) {
            e.setTarifMensuel(tarifs.montant(nouvelle));
            if (classeChangee) planning.reconcilier(e);
        }
    }
}
