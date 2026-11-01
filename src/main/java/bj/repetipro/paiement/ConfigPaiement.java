package bj.repetipro.paiement;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * Réglages du paiement en ligne, lus UNIQUEMENT dans les variables d'environnement
 * (aucun secret dans le code ni dans git) :
 *   FEDAPAY_SECRET_KEY      clé API secrète (sk_sandbox_... ou sk_live_...)
 *   FEDAPAY_WEBHOOK_SECRET  secret du webhook (wh_sandbox_... ou wh_live_...)
 *   FEDAPAY_ENV             sandbox (par défaut) ou live
 *   APP_BASE_URL            adresse publique du site, ex. https://repetipro.onrender.com
 *   PAIEMENT_SIMULATION     true = fausse caisse locale (profil dev uniquement)
 */
@Component
public class ConfigPaiement {

    private static final Logger log = LoggerFactory.getLogger(ConfigPaiement.class);

    private final String cleSecrete;
    private final String secretWebhook;
    private final String environnement;
    private final String urlPublique;
    private final boolean simulationDemandee;
    private final Environment spring;
    /** Secret tiré au hasard au démarrage si la simulation tourne sans FEDAPAY_WEBHOOK_SECRET. */
    private final String secretSimulation;

    public ConfigPaiement(@Value("${repetipro.fedapay.secret-key:}") String cleSecrete,
                          @Value("${repetipro.fedapay.webhook-secret:}") String secretWebhook,
                          @Value("${repetipro.fedapay.env:sandbox}") String environnement,
                          @Value("${repetipro.app-base-url:}") String urlPublique,
                          @Value("${repetipro.paiement.simulation:false}") boolean simulationDemandee,
                          Environment spring) {
        this.cleSecrete = cleSecrete == null ? "" : cleSecrete.trim();
        this.secretWebhook = secretWebhook == null ? "" : secretWebhook.trim();
        this.environnement = environnement == null || environnement.isBlank() ? "sandbox" : environnement.trim().toLowerCase();
        this.urlPublique = urlPublique == null ? "" : urlPublique.trim().replaceAll("/+$", "");
        this.simulationDemandee = simulationDemandee;
        this.spring = spring;
        byte[] alea = new byte[24];
        new SecureRandom().nextBytes(alea);
        this.secretSimulation = "wh_simulation_" + HexFormat.of().formatHex(alea);
    }

    @PostConstruct
    void annoncer() {
        if (simulationDemandee && !simulationActive()) {
            log.warn("PAIEMENT_SIMULATION=true ignoré : la simulation n'est permise qu'en profil dev.");
        }
        if (simulationActive()) {
            log.info("Paiement en ligne : MODE SIMULATION (aucun argent réel).");
        } else if (fedapayConfigure()) {
            log.info("Paiement en ligne : FedaPay ({}).", estLive() ? "LIVE" : "sandbox");
            if (secretWebhook.isEmpty()) {
                log.warn("FEDAPAY_WEBHOOK_SECRET absent : les webhooks seront refusés ; "
                        + "seule la vérification au retour du client activera Solo.");
            }
        } else {
            log.info("Paiement en ligne désactivé (pas de FEDAPAY_SECRET_KEY) : paiement manuel seulement.");
        }
    }

    /** Simulation : demandée ET profil dev (ou test pour les tests automatiques), jamais en prod. */
    public boolean simulationActive() {
        return simulationDemandee
                && spring.acceptsProfiles(Profiles.of("dev | test"))
                && !spring.acceptsProfiles(Profiles.of("prod"));
    }

    public boolean fedapayConfigure() { return !cleSecrete.isEmpty(); }

    /** Le bouton "Payer par Mobile Money" est-il proposé ? */
    public boolean paiementEnLigneDisponible() { return simulationActive() || fedapayConfigure(); }

    public boolean estLive() { return "live".equals(environnement); }

    /** URL de l'API FedaPay selon l'environnement (doc : https://docs.fedapay.com/fr/api-reference/introduction). */
    public String urlApiFedaPay() {
        return estLive() ? "https://api.fedapay.com/v1" : "https://sandbox-api.fedapay.com/v1";
    }

    String cleSecrete() { return cleSecrete; }

    /** Secret utilisé pour vérifier X-FEDAPAY-SIGNATURE ; vide = webhooks refusés. */
    public String secretWebhookEffectif() {
        if (!secretWebhook.isEmpty()) return secretWebhook;
        return simulationActive() ? secretSimulation : "";
    }

    /** Adresse publique configurée (APP_BASE_URL), ou vide pour la déduire de la requête. */
    public String urlPublique() { return urlPublique; }
}
