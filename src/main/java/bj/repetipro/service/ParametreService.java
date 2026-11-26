package bj.repetipro.service;

import bj.repetipro.model.ParametreApp;
import bj.repetipro.repository.ParametreRepository;
import bj.repetipro.util.Telephones;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réglages modifiables à chaud par le SuperAdmin. Chaque réglage a une valeur par défaut
 * (variables d'environnement) utilisée tant que rien n'est enregistré en base.
 */
@Service
public class ParametreService {

    public static final String PRIX_SOLO = "solo.prix";
    public static final String MOMO_NUMERO = "momo.numero";
    public static final String MOMO_NOM = "momo.nom";

    public static final int PRIX_SOLO_DEFAUT = 1500;
    public static final int PRIX_SOLO_MIN = 100;
    public static final int PRIX_SOLO_MAX = 100_000;

    private final ParametreRepository repo;
    private final String momoNumeroDefaut;
    private final String momoNomDefaut;

    public ParametreService(ParametreRepository repo,
                            @Value("${repetipro.paiement.momo-numero:}") String momoNumeroDefaut,
                            @Value("${repetipro.paiement.momo-nom:}") String momoNomDefaut) {
        this.repo = repo;
        this.momoNumeroDefaut = momoNumeroDefaut;
        this.momoNomDefaut = momoNomDefaut;
    }

    @Transactional(readOnly = true)
    public String valeur(String cle) {
        return repo.findById(cle).map(ParametreApp::getValeur).orElse(null);
    }

    @Transactional
    public void enregistrer(String cle, String valeur) {
        if (valeur == null || valeur.isBlank()) {
            repo.findById(cle).ifPresent(repo::delete); // retour au défaut
            return;
        }
        ParametreApp p = repo.findById(cle).orElse(null);
        if (p == null) repo.save(new ParametreApp(cle, valeur.trim()));
        else p.setValeur(valeur.trim());
    }

    /** Prix de l'abonnement Solo pour un mois, en FCFA. */
    public int prixSolo() {
        String v = valeur(PRIX_SOLO);
        if (v != null) {
            try {
                int n = Integer.parseInt(v.trim());
                if (n >= PRIX_SOLO_MIN && n <= PRIX_SOLO_MAX) return n;
            } catch (NumberFormatException ignore) { /* retour au défaut */ }
        }
        return PRIX_SOLO_DEFAUT;
    }

    /** Numéro Mobile Money RépétiPro, tel qu'on l'affiche aux répétiteurs. */
    public String momoNumero() {
        String v = valeur(MOMO_NUMERO);
        String brut = v != null ? v : momoNumeroDefaut;
        String normalise = Telephones.normaliser(brut);
        return normalise != null ? Telephones.afficher(normalise) : brut;
    }

    public String momoNom() {
        String v = valeur(MOMO_NOM);
        return v != null ? v : momoNomDefaut;
    }

    /** Valeur brute enregistrée (pour pré-remplir le formulaire) ; vide = valeur par défaut. */
    public String momoNumeroEnregistre() { String v = valeur(MOMO_NUMERO); return v == null ? "" : v; }
    public String momoNomEnregistre() { String v = valeur(MOMO_NOM); return v == null ? "" : v; }
    public String prixSoloEnregistre() { String v = valeur(PRIX_SOLO); return v == null ? "" : v; }

    /**
     * Enregistre les réglages d'application.
     * @return un message d'erreur lisible, ou null si tout va bien
     */
    @Transactional
    public String enregistrerReglages(String prix, String numero, String nom) {
        String p = prix == null ? "" : prix.trim().replace(" ", "").replace("\u00a0", "");
        if (!p.isEmpty()) {
            int n;
            try { n = Integer.parseInt(p); } catch (NumberFormatException e) { return "Prix Solo invalide."; }
            if (n < PRIX_SOLO_MIN || n > PRIX_SOLO_MAX) {
                return "Le prix Solo doit être compris entre " + PRIX_SOLO_MIN + " et " + PRIX_SOLO_MAX + " FCFA.";
            }
        }
        String num = numero == null ? "" : numero.trim();
        if (!num.isEmpty() && !Telephones.estValide(num)) return "Numéro Mobile Money invalide. Exemple : 01 97 00 00 00";
        String nm = nom == null ? "" : nom.trim();
        if (nm.length() > 100) return "Nom Mobile Money trop long (100 caractères maximum).";
        enregistrer(PRIX_SOLO, p);
        enregistrer(MOMO_NUMERO, num.isEmpty() ? "" : Telephones.afficher(Telephones.normaliser(num)));
        enregistrer(MOMO_NOM, nm);
        return null;
    }
}
