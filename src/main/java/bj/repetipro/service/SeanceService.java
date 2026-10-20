package bj.repetipro.service;

import bj.repetipro.model.Eleve;
import bj.repetipro.model.Presence;
import bj.repetipro.model.Seance;
import bj.repetipro.repository.SeanceRepository;
import bj.repetipro.util.WhatsApp;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Planification et pointage des séances. */
@Service
public class SeanceService {

    private final SeanceRepository repo;
    private final EleveService eleves;
    private final RepetiteurService repetiteurs;

    public SeanceService(SeanceRepository repo, EleveService eleves, RepetiteurService repetiteurs) {
        this.repo = repo;
        this.eleves = eleves;
        this.repetiteurs = repetiteurs;
    }

    /** Séances d'une journée donnée. */
    public List<Seance> duJour(Long repetiteurId, LocalDate jour) {
        return repo.findByRepetiteurIdAndDebutBetweenOrderByDebutAsc(
                repetiteurId, jour.atStartOfDay(), jour.plusDays(1).atStartOfDay().minusNanos(1));
    }

    /** Séances des 7 prochains jours (à partir d'aujourd'hui). */
    public List<Seance> aVenir(Long repetiteurId, LocalDate depuis) {
        return repo.findByRepetiteurIdAndDebutBetweenOrderByDebutAsc(
                repetiteurId, depuis.atStartOfDay(), depuis.plusDays(8).atStartOfDay());
    }

    /** Séances des 14 derniers jours (pour pointer en retard). */
    public List<Seance> recentes(Long repetiteurId, LocalDate jusqua) {
        return repo.findByRepetiteurIdAndDebutBetweenOrderByDebutAsc(
                repetiteurId, jusqua.minusDays(14).atStartOfDay(), jusqua.atStartOfDay());
    }

    public Seance trouver(Long id, Long repetiteurId) {
        return repo.findByIdAndRepetiteurId(id, repetiteurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Séance introuvable"));
    }

    @Transactional
    public Seance planifier(Long repetiteurId, Long eleveId, LocalDateTime debut, int dureeMinutes) {
        Eleve e = eleves.trouver(eleveId, repetiteurId); // vérifie que l'élève est bien à lui
        Seance s = new Seance();
        s.setRepetiteur(repetiteurs.trouver(repetiteurId));
        s.setEleve(e);
        s.setDebut(debut);
        s.setDureeMinutes(dureeMinutes);
        return repo.save(s);
    }

    @Transactional
    public void pointer(Long id, Long repetiteurId, Presence presence, String notions, String compteRendu) {
        Seance s = trouver(id, repetiteurId);
        s.setPresence(presence);
        s.setNotions(notions);
        s.setCompteRendu(compteRendu);
    }

    @Transactional
    public void supprimer(Long id, Long repetiteurId) {
        repo.delete(trouver(id, repetiteurId));
    }

    /** Message WhatsApp du compte rendu, prêt à envoyer au parent. */
    public String messageCompteRendu(Seance s) {
        Eleve e = s.getEleve();
        DateTimeFormatter f = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH);
        StringBuilder m = new StringBuilder();
        m.append("Bonjour").append(e.getNomParent() == null || e.getNomParent().isBlank() ? "" : " " + e.getNomParent())
         .append(",\nCompte rendu de la séance ").append(bj.repetipro.util.Textes.de(e.getPrenom()))
         .append(" du ").append(s.getDebut().format(f)).append(" :\n");
        m.append("• Présence : ").append(s.getPresence().getLibelle()).append("\n");
        if (s.getNotions() != null && !s.getNotions().isBlank()) m.append("• Notions : ").append(s.getNotions()).append("\n");
        if (s.getCompteRendu() != null && !s.getCompteRendu().isBlank()) m.append("\n").append(s.getCompteRendu()).append("\n");
        m.append("\n").append(s.getRepetiteur().getNomComplet()).append(" (répétiteur)");
        return m.toString();
    }

    /** Lien wa.me vers le parent avec le compte rendu pré-rempli (null si pas de numéro). */
    public String lienWhatsApp(Seance s) {
        return WhatsApp.lien(s.getEleve().getWhatsappParent(), messageCompteRendu(s));
    }
}
