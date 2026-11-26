package bj.repetipro.service;

import bj.repetipro.model.*;
import bj.repetipro.repository.*;
import bj.repetipro.util.Montants;
import bj.repetipro.util.Telephones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Chiffres et activité récente pour le tableau de bord SuperAdmin. */
@Service
public class AdminService {

    /** Chiffres clés. */
    public record Stats(long repetiteurs, long repetiteursSolo, long eleves, long seances,
                        long factures, long facturesPayees, long paiementsPayes, long revenuSolo,
                        long parents, long seancesPointees) {
        public String revenuSoloFcfa() { return Montants.fcfa(revenuSolo); }
    }

    /** Une ligne du fil d'activité. type : inscription, eleve, facture, paiement. */
    public record Activite(LocalDateTime date, String type, String libelle, String texte) { }

    private final RepetiteurRepository repetiteurs;
    private final EleveRepository eleves;
    private final SeanceRepository seances;
    private final FactureRepository factures;
    private final PaiementRepository paiements;
    private final ParentRepository parents;

    public AdminService(RepetiteurRepository repetiteurs, EleveRepository eleves, SeanceRepository seances,
                        FactureRepository factures, PaiementRepository paiements, ParentRepository parents) {
        this.parents = parents;
        this.repetiteurs = repetiteurs;
        this.eleves = eleves;
        this.seances = seances;
        this.factures = factures;
        this.paiements = paiements;
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        return new Stats(
                repetiteurs.count(),
                repetiteurs.countByPlan(Plan.SOLO),
                eleves.countByActifTrue(),
                seances.count(),
                factures.count(),
                factures.countByStatut(StatutFacture.PAYE),
                paiements.countByStatut(StatutPaiement.PAYE),
                paiements.sommeMontantParStatut(StatutPaiement.PAYE),
                parents.count(),
                seances.countByPointeLeNotNull());
    }

    /** Dernières inscriptions, élèves, factures et paiements, du plus récent au plus ancien. */
    @Transactional(readOnly = true)
    public List<Activite> activiteRecente(int max) {
        List<Activite> l = new ArrayList<>();
        for (Repetiteur r : repetiteurs.findTop8ByOrderByDateInscriptionDesc()) {
            l.add(new Activite(r.getDateInscription(), "inscription", "Inscription",
                    r.getNomComplet() + " (" + Telephones.afficher(r.getTelephone()) + ")"));
        }
        for (Eleve e : eleves.findTop8ByOrderByIdDesc()) {
            if (e.getDateCreation() == null) continue;
            l.add(new Activite(e.getDateCreation(), "eleve", "Élève ajouté",
                    e.getNom() + " · " + e.getClasse() + " · chez " + e.getRepetiteur().getNomComplet()));
        }
        for (Facture f : factures.findTop8ByOrderByIdDesc()) {
            if (f.getDateCreation() == null) continue;
            l.add(new Activite(f.getDateCreation(), "facture", "Facture",
                    f.getEleve().getPrenom() + " · " + Montants.fcfa(f.getMontant()) + " · "
                            + FactureService.libelleMois(f.getPeriode()) + " (" + f.getStatutAffiche() + ")"));
        }
        for (Paiement p : paiements.findTop8ByOrderByDateCreationDesc()) {
            l.add(new Activite(p.getDateCreation(), "paiement", "Paiement Solo",
                    p.getRepetiteur().getNomComplet() + " · " + Montants.fcfa(p.getMontant()) + " · " + p.getStatut().getLibelle()));
        }
        for (CompteParent p : parents.findTop8ByOrderByDateInscriptionDesc()) {
            l.add(new Activite(p.getDateInscription(), "inscription", "Parent inscrit",
                    p.getNomComplet() + " (" + Telephones.afficher(p.getTelephone()) + ")"));
        }
        for (Eleve e : eleves.findTop8ByCreneauxMajLeNotNullOrderByCreneauxMajLeDesc()) {
            l.add(new Activite(e.getCreneauxMajLe(), "creneaux", "Créneaux choisis",
                    e.getNom() + " · " + e.getClasse() + " · chez " + e.getRepetiteur().getNomComplet()));
        }
        for (Seance s : seances.findTop8ByPointeLeNotNullOrderByPointeLeDesc()) {
            l.add(new Activite(s.getPointeLe(), "pointage", "Pointage",
                    s.getEleve().getNom() + " · " + s.getPresence().getLibelle() + " · "
                            + s.getDebut().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm"))
                            + " · " + s.getRepetiteur().getNomComplet()));
        }
        l.sort(Comparator.comparing(Activite::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return l.size() > max ? l.subList(0, max) : l;
    }
}
