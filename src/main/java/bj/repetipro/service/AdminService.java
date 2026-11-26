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
                        long factures, long facturesPayees, long paiementsPayes, long revenuSolo) {
        public String revenuSoloFcfa() { return Montants.fcfa(revenuSolo); }
    }

    /** Une ligne du fil d'activité. type : inscription, eleve, facture, paiement. */
    public record Activite(LocalDateTime date, String type, String libelle, String texte) { }

    private final RepetiteurRepository repetiteurs;
    private final EleveRepository eleves;
    private final SeanceRepository seances;
    private final FactureRepository factures;
    private final PaiementRepository paiements;

    public AdminService(RepetiteurRepository repetiteurs, EleveRepository eleves, SeanceRepository seances,
                        FactureRepository factures, PaiementRepository paiements) {
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
                paiements.sommeMontantParStatut(StatutPaiement.PAYE));
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
        l.sort(Comparator.comparing(Activite::date, Comparator.nullsLast(Comparator.reverseOrder())));
        return l.size() > max ? l.subList(0, max) : l;
    }
}
