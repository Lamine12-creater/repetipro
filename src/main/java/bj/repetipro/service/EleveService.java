package bj.repetipro.service;

import bj.repetipro.model.Eleve;
import bj.repetipro.model.Plan;
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

    public EleveService(EleveRepository repo, RepetiteurService repetiteurs) {
        this.repo = repo;
        this.repetiteurs = repetiteurs;
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
        return r.getPlan() == Plan.SOLO || repo.countByRepetiteurIdAndActifTrue(repetiteurId) < LIMITE_GRATUITE;
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
        e.setNom(f.getNom().trim());
        e.setClasse(f.getClasse().trim());
        e.setMatieres(f.getMatieres());
        e.setNomParent(f.getNomParent());
        e.setWhatsappParent(Telephones.normaliser(f.getWhatsappParent()));
        e.setTarifMensuel(f.getTarifMensuel());
    }
}
