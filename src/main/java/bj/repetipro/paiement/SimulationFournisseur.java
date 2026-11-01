package bj.repetipro.paiement;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Fausse caisse pour le développement (PAIEMENT_SIMULATION=true, profil dev) :
 * renvoie vers /paiement/simulation/{ref} au lieu de FedaPay. La confirmation passe ensuite
 * par le VRAI traitement de webhook (événement signé), pour tester tout le parcours.
 */
@Component
public class SimulationFournisseur implements FournisseurPaiement {

    public static final String CODE = "SIMULATION";

    @Override public String code() { return CODE; }

    @Override
    public Checkout creer(Demande d) {
        String id = "SIM" + ThreadLocalRandom.current().nextLong(100_000_000L, 999_999_999L);
        return new Checkout(id, "/paiement/simulation/" + d.reference());
    }

    /** Pas de serveur distant : le statut local fait foi. */
    @Override
    public Optional<EtatDistant> consulter(String referenceFournisseur) { return Optional.empty(); }
}
