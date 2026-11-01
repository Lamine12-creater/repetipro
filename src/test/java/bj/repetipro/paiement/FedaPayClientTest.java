package bj.repetipro.paiement;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** Appels REST FedaPay simulés (aucun accès réseau). */
class FedaPayClientTest {

    MockRestServiceServer serveur;
    FedaPayClient client;

    @BeforeEach
    void preparer() {
        ConfigPaiement config = new ConfigPaiement("sk_sandbox_FAUSSE", "", "sandbox", "", false, new MockEnvironment());
        RestClient.Builder builder = RestClient.builder();
        serveur = MockRestServiceServer.bindTo(builder).build();
        client = new FedaPayClient(builder, config, new ObjectMapper());
    }

    @Test
    void creeLaTransactionPuisLeLienDePaiement() {
        serveur.expect(requestTo("https://sandbox-api.fedapay.com/v1/transactions"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_sandbox_FAUSSE"))
                .andExpect(jsonPath("$.amount").value(1500))
                .andExpect(jsonPath("$.currency.iso").value("XOF"))
                .andExpect(jsonPath("$.merchant_reference").value("RP20261101-AAAA"))
                .andExpect(jsonPath("$.callback_url").value("https://exemple.bj/paiement/retour/RP20261101-AAAA"))
                .andRespond(withStatus(HttpStatus.CREATED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"v1/transaction\":{\"id\":1234,\"reference\":\"trx_x\",\"status\":\"pending\",\"amount\":1500}}"));
        serveur.expect(requestTo("https://sandbox-api.fedapay.com/v1/transactions/1234/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk_sandbox_FAUSSE"))
                .andRespond(withSuccess("{\"token\":\"tok\",\"url\":\"https://sandbox-process.fedapay.com/tok\"}", MediaType.APPLICATION_JSON));

        FournisseurPaiement.Checkout c = client.creer(new FournisseurPaiement.Demande(
                "RP20261101-AAAA", 1500, "Abonnement", "https://exemple.bj/paiement/retour/RP20261101-AAAA", 7L));

        assertEquals("1234", c.referenceFournisseur());
        assertEquals("https://sandbox-process.fedapay.com/tok", c.urlPaiement());
        serveur.verify();
    }

    @Test
    void relitLeStatutReel() {
        serveur.expect(requestTo("https://sandbox-api.fedapay.com/v1/transactions/1234"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"v1/transaction\":{\"id\":1234,\"status\":\"approved\",\"amount\":1500,\"mode\":\"mtn_open\"}}",
                        MediaType.APPLICATION_JSON));
        FournisseurPaiement.EtatDistant e = client.consulter("1234").orElseThrow();
        assertEquals("approved", e.statut());
        assertEquals(1500, e.montant());
        assertEquals("mtn_open", e.moyen());
    }

    @Test
    void erreurApiDevientFournisseurException() {
        serveur.expect(requestTo("https://sandbox-api.fedapay.com/v1/transactions"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("{\"message\":\"Invalid API key\"}"));
        FournisseurException e = assertThrows(FournisseurException.class, () -> client.creer(
                new FournisseurPaiement.Demande("RP-X", 1500, "A", "https://exemple.bj/r", 1L)));
        assertFalse(e.getMessage().contains("sk_sandbox_FAUSSE"), "la clé ne doit jamais apparaître");
    }

    @Test
    void urlLiveQuandFedapayEnvVautLive() {
        ConfigPaiement live = new ConfigPaiement("sk_live_X", "", "live", "", false, new MockEnvironment());
        assertEquals("https://api.fedapay.com/v1", live.urlApiFedaPay());
        assertEquals("https://sandbox-api.fedapay.com/v1",
                new ConfigPaiement("sk", "", "", "", false, new MockEnvironment()).urlApiFedaPay());
    }

    @Test
    void simulationInterditeEnProduction() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        assertFalse(new ConfigPaiement("", "", "sandbox", "", true, prod).simulationActive());
        MockEnvironment dev = new MockEnvironment();
        dev.setActiveProfiles("dev");
        assertTrue(new ConfigPaiement("", "", "sandbox", "", true, dev).simulationActive());
        assertFalse(new ConfigPaiement("", "", "sandbox", "", false, dev).simulationActive());
        // Sans clé ni simulation : pas de paiement en ligne (le transfert manuel reste affiché)
        assertFalse(new ConfigPaiement("", "", "sandbox", "", false, dev).paiementEnLigneDisponible());
    }
}
