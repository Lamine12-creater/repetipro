package bj.repetipro.paiement;

import bj.repetipro.model.Paiement;
import bj.repetipro.model.Plan;
import bj.repetipro.model.Repetiteur;
import bj.repetipro.model.StatutPaiement;
import bj.repetipro.repository.PaiementRepository;
import bj.repetipro.repository.RepetiteurRepository;
import bj.repetipro.service.RessourceIntrouvableException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

/**
 * Paiement en ligne de l'abonnement Solo :
 *  1. demarrerSolo()   crée un Paiement EN_ATTENTE + la transaction chez le fournisseur ;
 *  2. le répétiteur paie sur la page FedaPay (ou la caisse de simulation) ;
 *  3. traiterWebhook() (signé) ou verifierRetour() (relecture API) passe le paiement à PAYE
 *     et prolonge Solo d'un mois. Chaque paiement ne prolonge Solo qu'UNE fois (idempotent).
 */
@Service
public class PaiementService {

    private static final Logger log = LoggerFactory.getLogger(PaiementService.class);

    public static final long MONTANT_SOLO = 1500;
    public static final String DESCRIPTION_SOLO = "Abonnement RépétiPro Solo - 1 mois";

    private final PaiementRepository paiements;
    private final RepetiteurRepository repetiteurs;
    private final ConfigPaiement config;
    private final FedaPayClient fedapay;
    private final SimulationFournisseur simulation;
    private final ObjectMapper json;
    private final TransactionTemplate tx;
    private final Clock horloge;
    private final EntityManager em;
    private final SecureRandom alea = new SecureRandom();

    public PaiementService(PaiementRepository paiements, RepetiteurRepository repetiteurs, ConfigPaiement config,
                           FedaPayClient fedapay, SimulationFournisseur simulation, ObjectMapper json,
                           PlatformTransactionManager gestionnaire, EntityManager em) {
        this.em = em;
        this.paiements = paiements;
        this.repetiteurs = repetiteurs;
        this.config = config;
        this.fedapay = fedapay;
        this.simulation = simulation;
        this.json = json;
        this.tx = new TransactionTemplate(gestionnaire);
        this.horloge = Clock.systemDefaultZone();
    }

    /** Résultat du démarrage : lien vers la caisse, ou message d'erreur. */
    public record Demarrage(Paiement paiement, String urlPaiement, String erreur) {
        public boolean ok() { return urlPaiement != null; }
    }

    /** Ce qu'a donné un webhook (utile pour les journaux et les tests). */
    public enum Issue { ACTIVE, MIS_A_JOUR, DEJA_TRAITE, IGNORE, MONTANT_INCORRECT }

    public Optional<FournisseurPaiement> fournisseurActif() {
        if (config.simulationActive()) return Optional.of(simulation);
        if (config.fedapayConfigure()) return Optional.of(fedapay);
        return Optional.empty();
    }

    private FournisseurPaiement fournisseur(String code) {
        return SimulationFournisseur.CODE.equals(code) ? simulation : fedapay;
    }

    // ------------------------------------------------------------------ 1. Démarrer

    /**
     * Crée le paiement et la transaction. urlBase = adresse publique du site (pour le retour).
     * L'appel réseau se fait HORS transaction base de données.
     */
    public Demarrage demarrerSolo(Long repetiteurId, String urlBase) {
        FournisseurPaiement f = fournisseurActif().orElse(null);
        if (f == null) return new Demarrage(null, null, "Le paiement en ligne n'est pas encore disponible.");

        Paiement p = tx.execute(s -> {
            Repetiteur r = repetiteurs.findById(repetiteurId)
                    .orElseThrow(() -> new RessourceIntrouvableException("Compte introuvable"));
            Paiement nouveau = new Paiement();
            nouveau.setRepetiteur(r);
            nouveau.setFournisseur(f.code());
            nouveau.setReference(nouvelleReference());
            nouveau.setMontant(MONTANT_SOLO);
            return paiements.save(nouveau);
        });

        String urlRetour = urlBase.replaceAll("/+$", "") + "/paiement/retour/" + p.getReference();
        try {
            FournisseurPaiement.Checkout c = f.creer(new FournisseurPaiement.Demande(
                    p.getReference(), p.getMontant(), DESCRIPTION_SOLO, urlRetour, repetiteurId));
            tx.executeWithoutResult(s -> paiements.findById(p.getId()).ifPresent(x -> x.setReferenceFournisseur(c.referenceFournisseur())));
            log.info("Paiement {} créé chez {} (transaction {})", p.getReference(), f.code(), c.referenceFournisseur());
            return new Demarrage(p, c.urlPaiement(), null);
        } catch (FournisseurException e) {
            log.warn("Paiement {} : création chez {} impossible ({})", p.getReference(), f.code(), e.getMessage());
            tx.executeWithoutResult(s -> paiements.findById(p.getId()).ifPresent(x -> {
                x.setStatut(StatutPaiement.ECHOUE);
                x.setDetail(e.getMessage());
            }));
            return new Demarrage(p, null, "Le service de paiement ne répond pas. Réessayez dans un instant "
                    + "ou utilisez le transfert Mobile Money manuel ci-dessous.");
        }
    }

    private String nouvelleReference() {
        byte[] b = new byte[5];
        alea.nextBytes(b);
        return "RP" + LocalDate.now(horloge).format(DateTimeFormatter.BASIC_ISO_DATE) + "-" + HexFormat.of().formatHex(b).toUpperCase();
    }

    // ------------------------------------------------------------------ 2. Webhook

    /**
     * Traite un webhook FedaPay. Vérifie d'abord la signature (sinon SignatureInvalideException),
     * puis applique le statut de la transaction. Rejouer le même événement ne change rien.
     */
    public Issue traiterWebhook(byte[] corps, String enteteSignature) {
        SignatureWebhook.verifier(corps, enteteSignature, config.secretWebhookEffectif(), horloge.instant().getEpochSecond());
        JsonNode evenement;
        try {
            evenement = json.readTree(new String(corps, StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("Webhook signé mais JSON illisible");
            return Issue.IGNORE;
        }
        String nom = evenement.path("name").asText("");
        JsonNode entite = evenement.path("entity");
        if (!nom.startsWith("transaction.") || !entite.isObject()) return Issue.IGNORE;

        Optional<Paiement> trouve = Optional.empty();
        String refMarchand = FedaPayClient.texte(entite, "merchant_reference");
        if (refMarchand != null) trouve = paiements.findByReference(refMarchand);
        String idTransaction = FedaPayClient.texte(entite, "id");
        if (trouve.isEmpty() && idTransaction != null) {
            trouve = paiements.findByFournisseurAndReferenceFournisseur(FedaPayClient.CODE, idTransaction)
                    .or(() -> paiements.findByFournisseurAndReferenceFournisseur(SimulationFournisseur.CODE, idTransaction));
        }
        if (trouve.isEmpty()) {
            log.info("Webhook {} : transaction {} inconnue, ignorée", nom, idTransaction);
            return Issue.IGNORE;
        }
        String statut = FedaPayClient.texte(entite, "status");
        if (statut == null) statut = nom.substring("transaction.".length());
        Issue issue = appliquer(trouve.get().getReference(), statut, entite.path("amount").asLong(-1), FedaPayClient.texte(entite, "mode"));
        log.info("Webhook {} pour {} : {}", nom, trouve.get().getReference(), issue);
        return issue;
    }

    // ------------------------------------------------------------------ 3. Retour du client

    /**
     * Page de retour : on ne croit PAS le statut écrit dans l'URL ; on relit la transaction
     * chez FedaPay (doc FedaPay, "Utilisation du Lien de Retour").
     */
    public Paiement verifierRetour(String reference, Long repetiteurId) {
        Paiement p = duRepetiteur(reference, repetiteurId);
        boolean aVerifier = p.getStatut() != StatutPaiement.PAYE && p.getReferenceFournisseur() != null;
        if (aVerifier) {
            try {
                fournisseur(p.getFournisseur()).consulter(p.getReferenceFournisseur())
                        .ifPresent(e -> appliquer(reference, e.statut(), e.montant(), e.moyen()));
            } catch (FournisseurException e) {
                log.warn("Paiement {} : relecture du statut impossible ({})", reference, e.getMessage());
            }
        }
        return duRepetiteur(reference, repetiteurId);
    }

    /** Le paiement, seulement s'il appartient au répétiteur (sinon 404). */
    public Paiement duRepetiteur(String reference, Long repetiteurId) {
        return paiements.findByReference(reference)
                .filter(p -> p.getRepetiteur().getId().equals(repetiteurId))
                .orElseThrow(() -> new RessourceIntrouvableException("Paiement introuvable"));
    }

    public List<Paiement> derniers(Long repetiteurId) {
        return paiements.findTop5ByRepetiteurIdOrderByDateCreationDesc(repetiteurId);
    }

    // ------------------------------------------------------------------ Cœur : idempotent

    /**
     * Applique un statut distant au paiement, sous verrou. Le passage à PAYE (et donc la
     * prolongation de Solo) ne peut se produire qu'une seule fois par paiement.
     */
    public Issue appliquer(String reference, String statutDistant, long montantDistant, String moyen) {
        return tx.execute(s -> {
            Paiement p = paiements.findByReference(reference)
                    .orElseThrow(() -> new RessourceIntrouvableException("Paiement introuvable"));
            // Relecture SOUS VERROU (SELECT ... FOR UPDATE) : si le webhook et la page de retour
            // arrivent en même temps, le second attend le premier et voit le statut PAYE.
            em.refresh(p, LockModeType.PESSIMISTIC_WRITE);
            StatutPaiement nouveau = StatutPaiement.depuisFedaPay(statutDistant);
            if (nouveau == null) return Issue.IGNORE;

            if (p.getStatut() == StatutPaiement.PAYE) {
                if (nouveau == StatutPaiement.REMBOURSE) {
                    // Remboursé chez FedaPay : on le note, l'admin décide pour Solo.
                    p.setStatut(StatutPaiement.REMBOURSE);
                    return Issue.MIS_A_JOUR;
                }
                return Issue.DEJA_TRAITE;
            }
            if (p.getStatut() == StatutPaiement.REMBOURSE) return Issue.DEJA_TRAITE;

            if (moyen != null && !moyen.isBlank()) p.setMoyen(moyen.length() > 40 ? moyen.substring(0, 40) : moyen);

            if (nouveau == StatutPaiement.PAYE) {
                if (montantDistant != p.getMontant()) {
                    p.setDetail("Montant reçu " + montantDistant + " au lieu de " + p.getMontant() + " : Solo non activé");
                    log.warn("Paiement {} : montant inattendu {}", reference, montantDistant);
                    return Issue.MONTANT_INCORRECT;
                }
                p.setStatut(StatutPaiement.PAYE);
                p.setDatePaiement(LocalDateTime.now(horloge));
                p.setDetail(null);
                Repetiteur r = p.getRepetiteur();
                em.refresh(r, LockModeType.PESSIMISTIC_WRITE); // deux paiements du même compte : l'un après l'autre
                p.setSoloJusquAu(prolongerSolo(r));
                return Issue.ACTIVE;
            }
            if (nouveau == p.getStatut()) return Issue.DEJA_TRAITE;
            p.setStatut(nouveau);
            return Issue.MIS_A_JOUR;
        });
    }

    /**
     * +1 mois à partir de la fin de la période en cours si elle court encore, sinon à partir
     * d'aujourd'hui. Renvoie la nouvelle date de fin (incluse).
     */
    private LocalDate prolongerSolo(Repetiteur r) {
        LocalDate aujourdhui = LocalDate.now(horloge);
        LocalDate fin = r.getSoloJusquAu();
        LocalDate depart = (r.getPlan() == Plan.SOLO && fin != null && !fin.isBefore(aujourdhui)) ? fin : aujourdhui;
        LocalDate nouvelleFin = depart.plusMonths(1);
        r.setPlan(Plan.SOLO);
        r.setSoloJusquAu(nouvelleFin);
        log.info("Solo prolongé pour le compte {} jusqu'au {}", r.getId(), nouvelleFin);
        return nouvelleFin;
    }

    // ------------------------------------------------------------------ Simulation (dev)

    /**
     * Caisse de simulation : fabrique un événement au format FedaPay, le SIGNE avec le secret
     * du webhook, et le fait passer par traiterWebhook() comme un vrai webhook.
     */
    public Issue simulerResultat(String reference, Long repetiteurId, String statutFedaPay, String moyen) {
        if (!config.simulationActive()) throw new RessourceIntrouvableException("Simulation désactivée");
        Paiement p = duRepetiteur(reference, repetiteurId);
        if (!SimulationFournisseur.CODE.equals(p.getFournisseur())) throw new RessourceIntrouvableException("Paiement non simulé");
        ObjectNode entite = json.createObjectNode()
                .put("id", p.getReferenceFournisseur())
                .put("reference", "trx_" + p.getReferenceFournisseur())
                .put("merchant_reference", p.getReference())
                .put("amount", p.getMontant())
                .put("status", statutFedaPay)
                .put("mode", moyen);
        ObjectNode evenement = json.createObjectNode().put("name", "transaction." + statutFedaPay);
        evenement.set("entity", entite);
        byte[] corps = evenement.toString().getBytes(StandardCharsets.UTF_8);
        String entete = SignatureWebhook.entete(corps, config.secretWebhookEffectif(), horloge.instant().getEpochSecond());
        return traiterWebhook(corps, entete);
    }
}
