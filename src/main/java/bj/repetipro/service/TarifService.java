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

    /** Règle hebdomadaire d'une classe : nombre de séances par semaine et durée (en heures) de chacune. */
    public record Regle(int seances, int heures) {
        public int dureeMinutes() { return heures * 60; }
        public String texte() {
            return seances + (seances > 1 ? " séances" : " séance") + " de " + heures + " h par semaine";
        }
        public String texteCourt() { return seances + " × " + heures + " h"; }
    }

    public static final int SEANCES_MAX = 7;
    public static final int HEURES_MAX = 6;

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

    /** Règles de séances de toutes les classes, par libellé de classe. */
    public Map<String, Regle> regles() {
        Map<String, Regle> g = new LinkedHashMap<>();
        for (Classe c : Classe.values()) g.put(c.getLibelle(), regleDe(c));
        return g;
    }

    private Regle regleDe(Classe c) {
        return new Regle(entier("seances." + c.name(), c.getSeancesParSemaineDefaut(), 1, SEANCES_MAX),
                entier("heures." + c.name(), c.getDureeHeuresDefaut(), 1, HEURES_MAX));
    }

    private int entier(String cle, int defaut, int min, int max) {
        String v = parametres.valeur(cle);
        if (v != null) {
            try {
                int n = Integer.parseInt(v.trim());
                if (n >= min && n <= max) return n;
            } catch (NumberFormatException ignore) { /* défaut */ }
        }
        return defaut;
    }

    /** Règle de séances pour une classe saisie (classe inconnue : 2 séances de 2 h). */
    public Regle regle(String classeSaisie) {
        Classe c = Classe.de(classeSaisie);
        return c == null ? new Regle(2, 2) : regleDe(c);
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
        // Séances par semaine et durée : facultatives dans la saisie (sinon on garde la valeur actuelle)
        Map<String, Integer> regles = new LinkedHashMap<>();
        for (Classe c : Classe.values()) {
            Regle actuelle = regleDe(c);
            Integer s = lireEntier(saisie.get("seances_" + c.name()), actuelle.seances());
            Integer h = lireEntier(saisie.get("heures_" + c.name()), actuelle.heures());
            if (s == null || s < 1 || s > SEANCES_MAX) return "Séances par semaine pour " + c.getLibelle() + " : entre 1 et " + SEANCES_MAX + ".";
            if (h == null || h < 1 || h > HEURES_MAX) return "Durée d'une séance pour " + c.getLibelle() + " : entre 1 et " + HEURES_MAX + " heures.";
            regles.put("seances." + c.name(), s);
            regles.put("heures." + c.name(), h);
        }
        nouvelle.forEach((c, m) -> parametres.enregistrer(PREFIXE + c.name(), String.valueOf(m)));
        regles.forEach((k, v) -> parametres.enregistrer(k, String.valueOf(v)));
        return null;
    }

    private static Integer lireEntier(String brut, int defaut) {
        if (brut == null || brut.isBlank()) return defaut;
        try { return Integer.parseInt(brut.trim()); } catch (NumberFormatException e) { return null; }
    }
}
