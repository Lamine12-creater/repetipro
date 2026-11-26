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

    /**
     * Enregistre les créneaux choisis par le parent. Contrôles côté serveur : durée non modifiable,
     * exactement N créneaux, jours distincts, pas de 30 min, dans la plage horaire.
     * @return un message d'erreur lisible, ou null si tout est enregistré
     */
    @Transactional
    public String enregistrer(Long eleveId, List<String> jours, List<String> heures, String dureeSoumise) {
        Eleve e = eleves.findById(eleveId).orElseThrow(() -> new RessourceIntrouvableException("Élève introuvable"));
        TarifService.Regle r = regle(e);
        int duree = r.dureeMinutes();

        if (dureeSoumise != null && !dureeSoumise.isBlank()) {
            String d = dureeSoumise.trim();
            if (!d.equals(String.valueOf(duree)) && !d.equals(String.valueOf(r.heures()))) {
                return "La durée des séances est fixée à " + r.heures() + " h pour cette classe : elle ne peut pas être modifiée.";
            }
        }
        List<String> js = jours == null ? List.of() : jours;
        List<String> hs = heures == null ? List.of() : heures;
        if (js.size() != r.seances() || hs.size() != r.seances()) {
            return "Choisissez exactement " + r.seances() + (r.seances() > 1 ? " créneaux" : " créneau") + " par semaine.";
        }
        Set<Integer> dejaVus = new HashSet<>();
        List<CreneauEleve> nouveaux = new ArrayList<>();
        for (int i = 0; i < r.seances(); i++) {
            int jour;
            LocalTime debut;
            try {
                jour = Integer.parseInt(js.get(i).trim());
                debut = LocalTime.parse(hs.get(i).trim());
            } catch (RuntimeException ex) {
                return "Jour ou heure invalide pour le créneau " + (i + 1) + ".";
            }
            if (jour < 1 || jour > 7) return "Jour invalide pour le créneau " + (i + 1) + ".";
            if (debut.getMinute() % PAS_MINUTES != 0 || debut.getSecond() != 0) {
                return "L'heure de début doit être à l'heure pile ou à la demi-heure (créneau " + (i + 1) + ").";
            }
            LocalTime fin = debut.plusMinutes(duree);
            if (debut.isBefore(PREMIERE_HEURE) || fin.isBefore(debut) || fin.isAfter(DERNIERE_HEURE)) {
                return "Le créneau " + (i + 1) + " doit commencer après 7h00 et finir avant 22h00.";
            }
            if (!dejaVus.add(jour)) return "Choisissez des jours différents : une seule séance par jour.";
            nouveaux.add(new CreneauEleve(e, jour, debut, duree));
        }
        creneaux.deleteByEleveId(eleveId);
        creneaux.flush();
        nouveaux.sort(Comparator.comparingInt(CreneauEleve::getJour).thenComparing(CreneauEleve::getHeureDebut));
        creneaux.saveAll(nouveaux);
        e.setCreneauxMajLe(LocalDateTime.now());
        supprimerSeancesAutoAVenir(e);
        genererPourEleve(e);
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
