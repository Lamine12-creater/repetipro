package bj.repetipro.service;

import bj.repetipro.model.Classe;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Grille de tarifs mensuels (FCFA) UNIQUE pour toute l'application.
 * Seul le SuperAdmin la modifie ; les répétiteurs la lisent.
 */
@Service
public class TarifService {

    public static final int MONTANT_MIN = 0;
    public static final int MONTANT_MAX = 1_000_000;
    private static final String PREFIXE = "tarif.";

    private final ParametreService parametres;

    public TarifService(ParametreService parametres) {
        this.parametres = parametres;
    }

    /** Grille complète : tarif enregistré s'il existe, sinon le tarif par défaut. */
    public Map<String, Integer> grille() {
        Map<String, Integer> g = new LinkedHashMap<>();
        for (Classe c : Classe.values()) {
            g.put(c.getLibelle(), montantDe(c));
        }
        return g;
    }

    private int montantDe(Classe c) {
        String v = parametres.valeur(PREFIXE + c.name());
        if (v != null) {
            try { return Integer.parseInt(v.trim()); } catch (NumberFormatException ignore) { /* défaut */ }
        }
        return c.getTarifDefaut();
    }

    /** Montant à appliquer pour une classe. Lève si la classe n'est pas reconnue. */
    public int montant(String classeSaisie) {
        Classe c = Classe.de(classeSaisie);
        if (c == null) throw new IllegalArgumentException("Classe inconnue.");
        return montantDe(c);
    }

    /**
     * Enregistre la grille (une valeur par classe officielle, champs nommés comme l'enum).
     * @return un message d'erreur lisible, ou null si tout va bien
     */
    @Transactional
    public String enregistrer(Map<String, String> saisie) {
        Map<Classe, Integer> nouvelle = new LinkedHashMap<>();
        for (Classe c : Classe.values()) {
            String brut = saisie.get(c.name());
            if (brut == null || brut.isBlank()) return "Indiquez un montant pour chaque classe.";
            int montant;
            try {
                montant = Integer.parseInt(brut.trim().replace(" ", "").replace("\u00a0", ""));
            } catch (NumberFormatException ex) {
                return "Montant invalide pour " + c.getLibelle() + ".";
            }
            if (montant < MONTANT_MIN || montant > MONTANT_MAX) {
                return "Le tarif de " + c.getLibelle() + " doit être compris entre 0 et 1 000 000 FCFA.";
            }
            nouvelle.put(c, montant);
        }
        nouvelle.forEach((c, m) -> parametres.enregistrer(PREFIXE + c.name(), String.valueOf(m)));
        return null;
    }
}
