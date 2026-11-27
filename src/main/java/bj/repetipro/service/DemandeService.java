package bj.repetipro.service;

import bj.repetipro.model.*;
import bj.repetipro.repository.*;
import bj.repetipro.util.Telephones;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * Demandes des parents aux répétiteurs : le parent crée la fiche de son enfant, choisit un répétiteur
 * disponible et des créneaux ; le répétiteur accepte ou refuse selon sa disponibilité.
 */
@Service
public class DemandeService {

    /** Résultat d'une création : message d'erreur, ou identifiant de l'objet créé. */
    public record Resultat(String erreur, Long id) { }

    private final DemandeRepository demandes;
    private final CreneauDemandeRepository creneauxDemande;
    private final EnfantParentRepository enfants;
    private final ParentRepository parents;
    private final RepetiteurRepository repetiteurs;
    private final RepetiteurService repetiteurService;
    private final EleveService eleves;
    private final PlanningService planning;
    private final TarifService tarifs;

    public DemandeService(DemandeRepository demandes, CreneauDemandeRepository creneauxDemande, EnfantParentRepository enfants,
                          ParentRepository parents, RepetiteurRepository repetiteurs, RepetiteurService repetiteurService,
                          EleveService eleves, PlanningService planning, TarifService tarifs) {
        this.demandes = demandes;
        this.creneauxDemande = creneauxDemande;
        this.enfants = enfants;
        this.parents = parents;
        this.repetiteurs = repetiteurs;
        this.repetiteurService = repetiteurService;
        this.eleves = eleves;
        this.planning = planning;
        this.tarifs = tarifs;
    }

    // ---------- Enfants du parent ----------

    @Transactional(readOnly = true)
    public List<EnfantParent> enfantsDe(Long parentId) { return enfants.findByParentIdOrderByIdAsc(parentId); }

    @Transactional(readOnly = true)
    public EnfantParent enfant(Long enfantId, Long parentId) {
        return enfants.findByIdAndParentId(enfantId, parentId)
                .orElseThrow(() -> new RessourceIntrouvableException("Enfant introuvable"));
    }

    @Transactional
    public Resultat creerEnfant(Long parentId, String nom, String classeSaisie) {
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty()) return new Resultat("Indiquez le nom de l'enfant.", null);
        if (n.length() > 100) return new Resultat("Nom trop long (100 caractères maximum).", null);
        Classe c = Classe.de(classeSaisie);
        if (c == null) return new Resultat("Choisissez une classe dans la liste.", null);
        EnfantParent e = new EnfantParent();
        e.setParent(parents.findById(parentId).orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable")));
        e.setNom(n);
        e.setClasse(c.getLibelle());
        return new Resultat(null, enfants.save(e).getId());
    }

    /** Nom et classe ne se modifient pas pendant qu'une demande est en cours ou acceptée (voir la fiche suivie ensuite). */
    @Transactional
    public String majEnfant(Long enfantId, Long parentId, String nom, String classeSaisie) {
        EnfantParent e = enfant(enfantId, parentId);
        if (demandeActive(e)) return "Vous ne pouvez plus changer la classe pendant qu'une demande est en cours ou acceptée.";
        String n = nom == null ? "" : nom.trim();
        if (n.isEmpty()) return "Indiquez le nom de l'enfant.";
        if (n.length() > 100) return "Nom trop long (100 caractères maximum).";
        Classe c = Classe.de(classeSaisie);
        if (c == null) return "Choisissez une classe dans la liste.";
        e.setNom(n);
        e.setClasse(c.getLibelle());
        return null;
    }

    // ---------- Répétiteurs disponibles ----------

    /** Un répétiteur peut recevoir des demandes : compte actif, pas SuperAdmin, et place disponible (formule gratuite : 3 élèves). */
    public boolean disponible(Repetiteur r) {
        return r.isCompteActif() && !repetiteurService.estSuperAdmin(r) && eleves.peutAjouter(r.getId());
    }

    @Transactional(readOnly = true)
    public List<Repetiteur> repetiteursDisponibles() {
        List<Repetiteur> l = new ArrayList<>();
        for (Repetiteur r : repetiteurs.findAllByOrderByDateInscriptionDesc()) if (disponible(r)) l.add(r);
        l.sort(Comparator.comparing(r -> r.getNomComplet().toLowerCase(Locale.ROOT)));
        return l;
    }

    // ---------- Demandes ----------

    private boolean demandeActive(EnfantParent e) {
        for (DemandeRepetiteur d : demandes.findByEnfantIdOrderByIdDesc(e.getId())) {
            if (d.getStatut() == StatutDemande.EN_ATTENTE) return true;
            if (d.getStatut() == StatutDemande.ACCEPTEE && e.getEleve() != null && e.getEleve().isActif()) return true;
        }
        return false;
    }

    @Transactional(readOnly = true)
    public boolean aDemandeActive(Long enfantId, Long parentId) { return demandeActive(enfant(enfantId, parentId)); }

    @Transactional(readOnly = true)
    public List<DemandeRepetiteur> demandesDeEnfant(Long enfantId) { return demandes.findByEnfantIdOrderByIdDesc(enfantId); }

    @Transactional(readOnly = true)
    public List<CreneauDemande> creneaux(Long demandeId) { return creneauxDemande.findByDemandeIdOrderByJourAscHeureDebutAsc(demandeId); }

    /** Le parent envoie une demande à un répétiteur avec ses créneaux. */
    @Transactional
    public Resultat demander(Long enfantId, Long parentId, Long repetiteurId, List<String> jours, List<String> heures, String duree) {
        EnfantParent e = enfant(enfantId, parentId);
        Repetiteur r = repetiteurs.findById(repetiteurId).orElseThrow(() -> new RessourceIntrouvableException("Répétiteur introuvable"));
        if (!disponible(r)) return new Resultat("Ce répétiteur n'accepte pas de nouvelle demande pour le moment. Choisissez-en un autre.", null);
        if (demandeActive(e)) return new Resultat("Une demande est déjà en cours ou acceptée pour cet enfant.", null);
        PlanningService.Analyse a = planning.analyser(tarifs.regle(e.getClasse()), jours, heures, duree);
        if (a.erreur() != null) return new Resultat(a.erreur(), null);
        String conflit = planning.conflit(r.getId(), a.plages(), null);
        if (conflit != null) return new Resultat(conflit, null);
        DemandeRepetiteur d = new DemandeRepetiteur();
        d.setEnfant(e);
        d.setRepetiteur(r);
        d = demandes.save(d);
        for (PlanningService.Plage p : a.plages()) creneauxDemande.save(new CreneauDemande(d, p.jour(), p.debut(), p.dureeMinutes()));
        return new Resultat(null, d.getId());
    }

    /** Le parent annule une demande encore en attente. */
    @Transactional
    public String annuler(Long demandeId, Long parentId) {
        DemandeRepetiteur d = demandes.findById(demandeId)
                .filter(x -> x.getEnfant().getParent().getId().equals(parentId))
                .orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable"));
        if (d.getStatut() != StatutDemande.EN_ATTENTE) return "Seule une demande en attente peut être annulée.";
        d.setStatut(StatutDemande.ANNULEE);
        d.setDateDecision(LocalDateTime.now());
        return null;
    }

    // ---------- Côté répétiteur ----------

    @Transactional(readOnly = true)
    public long enAttente(Long repetiteurId) { return demandes.countByRepetiteurIdAndStatut(repetiteurId, StatutDemande.EN_ATTENTE); }

    @Transactional(readOnly = true)
    public List<DemandeRepetiteur> enAttenteDe(Long repetiteurId) {
        return demandes.findByRepetiteurIdAndStatutOrderByDateCreationAsc(repetiteurId, StatutDemande.EN_ATTENTE);
    }

    @Transactional(readOnly = true)
    public List<DemandeRepetiteur> historique(Long repetiteurId) {
        return demandes.findTop20ByRepetiteurIdAndStatutNotOrderByDateDecisionDesc(repetiteurId, StatutDemande.EN_ATTENTE);
    }

    private DemandeRepetiteur duRepetiteur(Long demandeId, Long repetiteurId) {
        return demandes.findByIdAndRepetiteurId(demandeId, repetiteurId)
                .orElseThrow(() -> new RessourceIntrouvableException("Demande introuvable"));
    }

    /** Avertissements pour le répétiteur : chevauchement avec ses élèves ou avec une autre demande en attente. */
    @Transactional(readOnly = true)
    public List<String> avertissements(DemandeRepetiteur d) {
        List<String> l = new ArrayList<>();
        List<PlanningService.Plage> siennes = plages(d.getId());
        for (PlanningService.Plage p : siennes) {
            for (PlanningService.Plage q : planning.prisChez(d.getRepetiteur().getId(), null)) {
                if (p.chevauche(q)) l.add("Chevauche votre emploi du temps : " + q.resume() + " est déjà pris par un de vos élèves.");
            }
        }
        for (DemandeRepetiteur autre : enAttenteDe(d.getRepetiteur().getId())) {
            if (autre.getId().equals(d.getId())) continue;
            for (PlanningService.Plage p : siennes) {
                for (PlanningService.Plage q : plages(autre.getId())) {
                    if (p.chevauche(q)) {
                        l.add("Même créneau que la demande de " + autre.getEnfant().getNom() + " (" + q.resume() + ") : vous ne pourrez en accepter qu'une.");
                    }
                }
            }
        }
        return l.stream().distinct().toList();
    }

    private List<PlanningService.Plage> plages(Long demandeId) {
        List<PlanningService.Plage> l = new ArrayList<>();
        for (CreneauDemande c : creneaux(demandeId)) l.add(new PlanningService.Plage(c.getJour(), c.getHeureDebut(), c.getDureeMinutes()));
        return l;
    }

    /** Accepte : crée l'élève chez le répétiteur avec ces créneaux. @return un message d'erreur ou null */
    @Transactional
    public String accepter(Long demandeId, Long repetiteurId) {
        DemandeRepetiteur d = duRepetiteur(demandeId, repetiteurId);
        if (d.getStatut() != StatutDemande.EN_ATTENTE) return "Cette demande a déjà été traitée.";
        EnfantParent e = d.getEnfant();
        if (e.getEleve() != null && e.getEleve().isActif()) return "Cet enfant est déjà suivi par un répétiteur.";
        List<PlanningService.Plage> plages = plages(d.getId());
        TarifService.Regle regle = tarifs.regle(e.getClasse());
        boolean regleOk = plages.size() == regle.seances() && plages.stream().allMatch(p -> p.dureeMinutes() == regle.dureeMinutes());
        if (!regleOk) return "La règle de la classe a changé depuis la demande : le parent doit refaire sa demande. Refusez-la avec un motif.";
        String conflit = planning.conflit(repetiteurId, plages, null);
        if (conflit != null) return "Impossible d'accepter : " + conflit.replace("chez ce répétiteur", "dans votre emploi du temps");
        if (!eleves.peutAjouter(repetiteurId)) {
            return "Limite de la formule gratuite atteinte (" + EleveService.LIMITE_GRATUITE + " élèves) : passez à Solo pour accepter cette demande.";
        }
        CompteParent parent = e.getParent();
        Eleve eleve = eleves.creerPourParent(repetiteurId, e.getNom(), e.getClasse(), parent.getNomComplet(), parent.getTelephone());
        planning.attribuer(eleve, plages);
        e.setEleve(eleve);
        d.setStatut(StatutDemande.ACCEPTEE);
        d.setDateDecision(LocalDateTime.now());
        return null;
    }

    @Transactional
    public String refuser(Long demandeId, Long repetiteurId, String motif) {
        DemandeRepetiteur d = duRepetiteur(demandeId, repetiteurId);
        if (d.getStatut() != StatutDemande.EN_ATTENTE) return "Cette demande a déjà été traitée.";
        String m = motif == null ? "" : motif.trim();
        if (m.length() > 255) m = m.substring(0, 255);
        d.setStatut(StatutDemande.REFUSEE);
        d.setMotif(m.isEmpty() ? null : m);
        d.setDateDecision(LocalDateTime.now());
        return null;
    }

    /** Lien wa.me pour que le répétiteur contacte le parent (message pré-rempli, rien n'est envoyé automatiquement). */
    public String lienWhatsApp(DemandeRepetiteur d) {
        CompteParent p = d.getEnfant().getParent();
        String msg = "Bonjour " + p.getNomComplet() + ", c'est " + d.getRepetiteur().getNomComplet()
                + ", répétiteur sur RépétiPro. J'ai bien reçu votre demande pour " + d.getEnfant().getNom()
                + " (" + d.getEnfant().getClasse() + "). ";
        return bj.repetipro.util.WhatsApp.lien(p.getTelephone(), msg);
    }

    public String telAffiche(DemandeRepetiteur d) { return Telephones.afficher(d.getEnfant().getParent().getTelephone()); }
}
