package bj.repetipro.service;

import bj.repetipro.model.Classe;
import bj.repetipro.model.Repetiteur;
import bj.repetipro.repository.RepetiteurRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/** Grille de tarifs mensuels (FCFA) propre à chaque répétiteur. */
@Service
public class TarifService {

    public static final int MONTANT_MIN = 0;
    public static final int MONTANT_MAX = 1_000_000;

    private final RepetiteurRepository repo;

    public TarifService(RepetiteurRepository repo) {
        this.repo = repo;
    }

    /** Grille complète : tarif personnalisé s'il existe, sinon le tarif par défaut. */
    @Transactional(readOnly = true)
    public Map<String, Integer> grille(Long repetiteurId) {
        return grilleDe(trouver(repetiteurId));
    }

    public Map<String, Integer> grilleDe(Repetiteur r) {
        Map<String, Integer> perso = r.getTarifs();
        Map<String, Integer> g = new LinkedHashMap<>();
        for (Classe c : Classe.values()) {
            Integer v = perso == null ? null : perso.get(c.getLibelle());
            g.put(c.getLibelle(), v != null ? v : c.getTarifDefaut());
        }
        return g;
    }

    /** Montant à appliquer pour une classe. Lève si la classe n'est pas reconnue. */
    @Transactional(readOnly = true)
    public int montant(Long repetiteurId, String classeSaisie) {
        Classe c = Classe.de(classeSaisie);
        if (c == null) throw new IllegalArgumentException("Classe inconnue.");
        return grille(repetiteurId).get(c.getLibelle());
    }

    /**
     * Enregistre la grille (une valeur par classe officielle).
     * @return un message d'erreur lisible, ou null si tout va bien
     */
    @Transactional
    public String enregistrer(Long repetiteurId, Map<String, String> saisie) {
        Map<String, Integer> nouvelle = new LinkedHashMap<>();
        for (Classe c : Classe.values()) {
            String brut = saisie.get(c.name());
            if (brut == null || brut.isBlank()) {
                return "Indiquez un montant pour chaque classe.";
            }
            int montant;
            try {
                montant = Integer.parseInt(brut.trim().replace(" ", "").replace("\u00a0", ""));
            } catch (NumberFormatException ex) {
                return "Montant invalide pour " + c.getLibelle() + ".";
            }
            if (montant < MONTANT_MIN || montant > MONTANT_MAX) {
                return "Le tarif de " + c.getLibelle() + " doit être compris entre 0 et 1 000 000 FCFA.";
            }
            nouvelle.put(c.getLibelle(), montant);
        }
        Repetiteur r = trouver(repetiteurId);
        r.getTarifs().clear();
        r.getTarifs().putAll(nouvelle);
        return null;
    }

    private Repetiteur trouver(Long id) {
        return repo.findById(id).orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable"));
    }
}
