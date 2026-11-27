package bj.repetipro.service;

import bj.repetipro.model.*;
import bj.repetipro.repository.CreneauRepository;
import bj.repetipro.repository.EleveRepository;
import bj.repetipro.repository.SeanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/**
 * Créneaux hebdomadaires choisis par le parent, et séances générées à partir de ces créneaux.
 * Règle (nombre de séances, durée) = grille de la classe, fixée par le SuperAdmin.
 */
@Service
public class PlanningService {

    /** Les séances doivent tenir entre ces deux heures. */
    public static final LocalTime PREMIERE_HEURE = LocalTime.of(7, 0);
    public static final LocalTime DERNIERE_HEURE = LocalTime.of(22, 0);
    public static final int PAS_MINUTES = 30;

    private final CreneauRepository creneaux;
    private final SeanceRepository seances;
    private final EleveRepository eleves;
    private final TarifService tarifs;

    public PlanningService(CreneauRepository creneaux, SeanceRepository seances, EleveRepository eleves, TarifService tarifs) {
        this.creneaux = creneaux;
        this.seances = seances;
        this.eleves = eleves;
        this.tarifs = tarifs;
    }

    public TarifService.Regle regle(Eleve e) { return tarifs.regle(e.getClasse()); }

    @Transactional(readOnly = true)
    public List<CreneauEleve> creneaux(Long eleveId) {
        return creneaux.findByEleveIdOrderByJourAscHeureDebutAsc(eleveId);
    }

    /** Heures de début proposées au parent, par pas de 30 min, pour une durée donnée. */
    public List<String> heuresProposees(int dureeMinutes) {
        List<String> l = new ArrayList<>();
        for (LocalTime h = PREMIERE_HEURE; !h.plusMinutes(dureeMinutes).isAfter(DERNIERE_HEURE) && !h.plusMinutes(dureeMinutes).isBefore(h);
             h = h.plusMinutes(PAS_MINUTES)) {
            l.add(h.toString());
        }
        return l;
    }

    /** Vrai si les créneaux enregistrés respectent la règle actuelle de la classe. */
    @Transactional(readOnly = true)
    public boolean creneauxComplets(Eleve e) {
        TarifService.Regle r = regle(e);
        List<CreneauEleve> l = creneaux(e.getId());
        return l.size() == r.seances() && l.stream().allMatch(c -> c.getDureeMinutes() == r.dureeMinutes());
    }

    /** Un créneau hebdomadaire analysé (jour 1 = lundi … 7 = dimanche). */
    public record Plage(int jour, LocalTime debut, int dureeMinutes) {
        public LocalTime fin() { return debut.plusMinutes(dureeMinutes); }
        public boolean chevauche(Plage o) { return jour == o.jour && debut.isBefore(o.fin()) && o.debut.isBefore(fin()); }
        public String resume() { return Jours.libelle(jour) + " · " + Jours.heure(debut) + " – " + Jours.heure(fin()); }
    }

    /** Résultat de l'analyse d'une saisie : un message d'erreur, ou les créneaux valides. */
    public record Analyse(String erreur, List<Plage> plages) { }

    /**
     * Contrôle une saisie de créneaux d'après la règle de la classe : durée non modifiable, exactement N créneaux,
     * jours distincts, pas de 30 min, séance entre 7h00 et 22h00.
     */
    public Analyse analyser(TarifService.Regle r, List<String> jours, List<String> heures, String dureeSoumise) {
        int duree = r.dureeMinutes();
        if (dureeSoumise != null && !dureeSoumise.isBlank()) {
            String d = dureeSoumise.trim();
            if (!d.equals(String.valueOf(duree)) && !d.equals(String.valueOf(r.heures()))) {
                return new Analyse("La durée des séances est fixée à " + r.heures() + " h pour cette classe : elle ne peut pas être modifiée.", null);
            }
        }
        List<String> js = jours == null ? List.of() : jours;
        List<String> hs = heures == null ? List.of() : heures;
        if (js.size() != r.seances() || hs.size() != r.seances()) {
            return new Analyse("Choisissez exactement " + r.seances() + (r.seances() > 1 ? " créneaux" : " créneau") + " par semaine.", null);
        }
        Set<Integer> dejaVus = new HashSet<>();
        List<Plage> plages = new ArrayList<>();
        for (int i = 0; i < r.seances(); i++) {
            int jour;
            LocalTime debut;
            try {
                jour = Integer.parseInt(js.get(i).trim());
                debut = LocalTime.parse(hs.get(i).trim());
            } catch (RuntimeException ex) {
                return new Analyse("Jour ou heure invalide pour le créneau " + (i + 1) + ".", null);
            }
            if (jour < 1 || jour > 7) return new Analyse("Jour invalide pour le créneau " + (i + 1) + ".", null);
            if (debut.getMinute() % PAS_MINUTES != 0 || debut.getSecond() != 0) {
                return new Analyse("L'heure de début doit être à l'heure pile ou à la demi-heure (créneau " + (i + 1) + ").", null);
            }
            LocalTime fin = debut.plusMinutes(duree);
            if (debut.isBefore(PREMIERE_HEURE) || fin.isBefore(debut) || fin.isAfter(DERNIERE_HEURE)) {
                return new Analyse("Le créneau " + (i + 1) + " doit commencer après 7h00 et finir avant 22h00.", null);
            }
            if (!dejaVus.add(jour)) return new Analyse("Choisissez des jours différents : une seule séance par jour.", null);
            plages.add(new Plage(jour, debut, duree));
        }
        plages.sort(Comparator.comparingInt(Plage::jour).thenComparing(Plage::debut));
        return new Analyse(null, plages);
    }

    /** Créneaux déjà occupés chez ce répétiteur (élèves actifs), sans l'élève exclu. Aucune identité n'est révélée. */
    @Transactional(readOnly = true)
    public List<Plage> prisChez(Long repetiteurId, Long exclureEleveId) {
        List<Plage> l = new ArrayList<>();
        for (CreneauEleve c : creneaux.findByEleveRepetiteurIdAndEleveActifTrue(repetiteurId)) {
            if (exclureEleveId != null && c.getEleve().getId().equals(exclureEleveId)) continue;
            l.add(new Plage(c.getJour(), c.getHeureDebut(), c.getDureeMinutes()));
        }
        l.sort(Comparator.comparingInt(Plage::jour).thenComparing(Plage::debut));
        return l;
    }

    /** Message si l'un des créneaux chevauche un créneau déjà pris chez ce répétiteur, sinon null. */
    public String conflit(Long repetiteurId, List<Plage> plages, Long exclureEleveId) {
        List<Plage> pris = prisChez(repetiteurId, exclureEleveId);
        for (Plage p : plages) {
            for (Plage q : pris) {
                if (p.chevauche(q)) {
                    return "Ce créneau n'est pas disponible chez ce répétiteur (" + q.resume() + " est déjà pris). Choisissez une autre heure ou un autre jour.";
                }
            }
        }
        return null;
    }

    /** Remplace les créneaux d'un élève par ces plages et génère ses séances. */
    @Transactional
    public void attribuer(Eleve e, List<Plage> plages) {
        creneaux.deleteByEleveId(e.getId());
        creneaux.flush();
        for (Plage p : plages) creneaux.save(new CreneauEleve(e, p.jour(), p.debut(), p.dureeMinutes()));
        e.setCreneauxMajLe(LocalDateTime.now());
        supprimerSeancesAutoAVenir(e);
        genererPourEleve(e);
    }

    /**
     * Enregistre les créneaux choisis par le parent pour un élève suivi. Refuse les créneaux déjà pris chez le répétiteur.
     * @return un message d'erreur lisible, ou null si tout est enregistré
     */
    @Transactional
    public String enregistrer(Long eleveId, List<String> jours, List<String> heures, String dureeSoumise) {
        Eleve e = eleves.findById(eleveId).orElseThrow(() -> new RessourceIntrouvableException("Élève introuvable"));
        Analyse a = analyser(regle(e), jours, heures, dureeSoumise);
        if (a.erreur() != null) return a.erreur();
        String conflit = conflit(e.getRepetiteur().getId(), a.plages(), eleveId);
        if (conflit != null) return conflit;
        attribuer(e, a.plages());
        return null;
    }

    /** Si la règle de la classe a changé (nouvelle classe, nouvelle grille), les créneaux sont à rechoisir. */
    @Transactional
    public void reconcilier(Eleve e) {
        List<CreneauEleve> l = creneaux(e.getId());
        if (l.isEmpty()) return;
        if (!creneauxComplets(e)) {
            creneaux.deleteByEleveId(e.getId());
            supprimerSeancesAutoAVenir(e);
        }
    }

    private void supprimerSeancesAutoAVenir(Eleve e) {
        seances.deleteAll(seances.findByEleveIdAndDebutAfterAndAutomatiqueTrueAndPresence(
                e.getId(), LocalDateTime.now(), Presence.NON_POINTEE));
    }

    /** Fenêtre de génération : d'aujourd'hui à la fin de la semaine prochaine. */
    private LocalDate finFenetre(LocalDate depuis) {
        return depuis.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY)).plusWeeks(1);
    }

    /** Crée les séances manquantes de cet élève d'après ses créneaux. Sans effet si déjà créées. */
    @Transactional
    public int genererPourEleve(Eleve e) {
        if (!e.isActif()) return 0;
        List<CreneauEleve> l = creneaux(e.getId());
        if (l.isEmpty()) return 0;
        LocalDate aujourdhui = LocalDate.now();
        LocalDate fin = finFenetre(aujourdhui);
        int crees = 0;
        for (LocalDate d = aujourdhui; !d.isAfter(fin); d = d.plusDays(1)) {
            for (CreneauEleve c : l) {
                if (c.getJour() != d.getDayOfWeek().getValue()) continue;
                LocalDateTime debut = d.atTime(c.getHeureDebut());
                if (seances.existsByEleveIdAndDebut(e.getId(), debut)) continue;
                Seance s = new Seance();
                s.setRepetiteur(e.getRepetiteur());
                s.setEleve(e);
                s.setDebut(debut);
                s.setDureeMinutes(c.getDureeMinutes());
                s.setAutomatique(true);
                seances.save(s);
                crees++;
            }
        }
        return crees;
    }

    /** Complète les séances de tous les élèves actifs d'un répétiteur (appelé à l'ouverture du planning). */
    @Transactional
    public void genererPourRepetiteur(Long repetiteurId) {
        for (Eleve e : eleves.findByRepetiteurIdAndActifTrueOrderByNomAsc(repetiteurId)) genererPourEleve(e);
    }

    /** Élèves actifs du répétiteur dont le parent n'a pas (ou plus) choisi les créneaux. */
    @Transactional(readOnly = true)
    public List<Eleve> sansCreneaux(Long repetiteurId) {
        List<Eleve> l = new ArrayList<>();
        for (Eleve e : eleves.findByRepetiteurIdAndActifTrueOrderByNomAsc(repetiteurId)) {
            if (!creneauxComplets(e)) l.add(e);
        }
        return l;
    }
}
