package bj.repetipro.service;

import bj.repetipro.model.*;
import bj.repetipro.repository.FactureRepository;
import bj.repetipro.repository.SeanceRepository;
import bj.repetipro.util.Montants;
import bj.repetipro.util.Telephones;
import bj.repetipro.util.WhatsApp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * Factures mensuelles.
 * Règle de calcul : montant = tarif mensuel de l'élève (simple et prévisible pour les parents).
 * Le nombre de séances faites dans le mois est enregistré pour information.
 */
@Service
public class FactureService {

    /** Jour du mois suivant avant lequel il faut payer. */
    public static final int JOUR_ECHEANCE = 10;

    private final FactureRepository repo;
    private final SeanceRepository seances;
    private final EleveService eleves;

    public FactureService(FactureRepository repo, SeanceRepository seances, EleveService eleves) {
        this.repo = repo;
        this.seances = seances;
        this.eleves = eleves;
    }

    public List<Facture> duMois(Long repetiteurId, YearMonth mois) {
        return repo.findByRepetiteurIdAndAnneeAndMoisOrderByIdAsc(repetiteurId, mois.getYear(), mois.getMonthValue());
    }

    /** Toutes les factures non payées (en attente ou en retard). */
    public List<Facture> impayees(Long repetiteurId) {
        return repo.findByRepetiteurIdAndStatutOrderByDateEcheanceAsc(repetiteurId, StatutFacture.EN_ATTENTE);
    }

    public Facture trouver(Long id, Long repetiteurId) {
        return repo.findByIdAndRepetiteurId(id, repetiteurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Facture introuvable"));
    }

    /**
     * Crée la facture du mois pour chaque élève actif qui n'en a pas encore.
     * @return le nombre de factures créées
     */
    @Transactional
    public int genererPourLeMois(Long repetiteurId, YearMonth mois) {
        int crees = 0;
        LocalDateTime debut = mois.atDay(1).atStartOfDay();
        LocalDateTime fin = mois.plusMonths(1).atDay(1).atStartOfDay();
        for (Eleve e : eleves.actifs(repetiteurId)) {
            if (repo.existsByEleveIdAndAnneeAndMois(e.getId(), mois.getYear(), mois.getMonthValue())) continue;
            Facture f = new Facture();
            f.setRepetiteur(e.getRepetiteur());
            f.setEleve(e);
            f.setAnnee(mois.getYear());
            f.setMois(mois.getMonthValue());
            f.setMontant(e.getTarifMensuel());
            f.setNombreSeances((int) seances.countByEleveIdAndDebutBetweenAndPresenceIn(
                    e.getId(), debut, fin, EnumSet.of(Presence.PRESENT, Presence.EN_RETARD)));
            f.setDateEcheance(mois.plusMonths(1).atDay(JOUR_ECHEANCE));
            repo.save(f);
            crees++;
        }
        return crees;
    }

    /** Marque une facture comme payée, avec la référence MoMo (facultative). */
    @Transactional
    public void marquerPayee(Long id, Long repetiteurId, String referenceMomo) {
        Facture f = trouver(id, repetiteurId);
        f.setStatut(StatutFacture.PAYE);
        f.setReferenceMomo(referenceMomo == null ? null : referenceMomo.trim());
        f.setDatePaiement(LocalDateTime.now());
    }

    /** Annule un paiement saisi par erreur. */
    @Transactional
    public void annulerPaiement(Long id, Long repetiteurId) {
        Facture f = trouver(id, repetiteurId);
        f.setStatut(StatutFacture.EN_ATTENTE);
        f.setReferenceMomo(null);
        f.setDatePaiement(null);
    }

    @Transactional
    public void supprimer(Long id, Long repetiteurId) {
        repo.delete(trouver(id, repetiteurId));
    }

    /** "octobre 2026" */
    public static String libelleMois(YearMonth m) {
        return m.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH));
    }

    /** "d'octobre 2026" ou "de septembre 2026" (élision devant une voyelle). */
    public static String deMois(YearMonth m) {
        return bj.repetipro.util.Textes.de(libelleMois(m));
    }

    /** Message de rappel de paiement envoyé au parent sur WhatsApp. */
    public String messageRappel(Facture f) {
        Repetiteur r = f.getRepetiteur();
        Eleve e = f.getEleve();
        StringBuilder m = new StringBuilder();
        m.append("Bonjour").append(e.getNomParent() == null || e.getNomParent().isBlank() ? "" : " " + e.getNomParent())
         .append(",\nVoici la facture ").append(deMois(f.getPeriode())).append(" pour ").append(e.getPrenom())
         .append(" : ").append(Montants.fcfa(f.getMontant()));
        if (f.getNombreSeances() > 0) m.append(" (").append(f.getNombreSeances()).append(" séance(s))");
        m.append(".\n");
        if (r.getNumeroMomo() != null) {
            m.append("Paiement Mobile Money au ").append(Telephones.afficher(r.getNumeroMomo()));
            if (r.getNomMomo() != null && !r.getNomMomo().isBlank()) m.append(" (").append(r.getNomMomo()).append(")");
            m.append(", motif « ").append(e.getPrenom()).append(" ").append(libelleMois(f.getPeriode())).append(" ».\n");
        }
        m.append("À régler avant le ").append(f.getDateEcheance().format(DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)))
         .append(". Merci !\n").append(r.getNomComplet());
        return m.toString();
    }

    public String lienRappel(Facture f) {
        return WhatsApp.lien(f.getEleve().getWhatsappParent(), messageRappel(f));
    }
}
