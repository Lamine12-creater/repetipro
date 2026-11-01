package bj.repetipro.paiement;

import bj.repetipro.model.*;
import bj.repetipro.repository.PaiementRepository;
import bj.repetipro.repository.RepetiteurRepository;
import bj.repetipro.service.EleveService;
import bj.repetipro.service.RepetiteurService;
import bj.repetipro.service.UtilisateurConnecte;
import bj.repetipro.web.EleveForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Parcours complet du paiement Solo en mode simulation + webhook signé. */
@SpringBootTest(properties = {
        "repetipro.paiement.simulation=true",
        "repetipro.fedapay.webhook-secret=wh_test_integration",
        "repetipro.fedapay.secret-key="
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaiementIntegrationTest {

    static final String SECRET = "wh_test_integration";

    @Autowired PaiementService service;
    @Autowired PaiementRepository paiementRepo;
    @Autowired RepetiteurRepository repetiteurRepo;
    @Autowired RepetiteurService repetiteurs;
    @Autowired EleveService eleves;
    @Autowired MockMvc mvc;

    private Repetiteur nouveauRepetiteur() {
        String tel = "01" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
        return repetiteurs.inscrire("Test Paiement", tel, "secret123");
    }

    private Repetiteur relire(Repetiteur r) { return repetiteurRepo.findById(r.getId()).orElseThrow(); }

    private Paiement demarrer(Repetiteur r) {
        PaiementService.Demarrage d = service.demarrerSolo(r.getId(), "http://localhost");
        assertTrue(d.ok());
        return paiementRepo.findByReference(d.paiement().getReference()).orElseThrow();
    }

    private byte[] evenement(String nom, Paiement p, String statut, long montant) {
        return ("{\"name\":\"" + nom + "\",\"entity\":{\"id\":\"" + p.getReferenceFournisseur() + "\",\"merchant_reference\":\""
                + p.getReference() + "\",\"amount\":" + montant + ",\"status\":\"" + statut + "\",\"mode\":\"moov\"}}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private String signer(byte[] corps) {
        return SignatureWebhook.entete(corps, SECRET, Instant.now().getEpochSecond());
    }

    @Test
    void webhookSigneActiveSoloUnMoisEtResteIdempotent() {
        Repetiteur r = nouveauRepetiteur();
        Paiement p = demarrer(r);
        assertEquals(StatutPaiement.EN_ATTENTE, p.getStatut());
        assertEquals(1500, p.getMontant());
        assertEquals(SimulationFournisseur.CODE, p.getFournisseur());
        assertNotNull(p.getReferenceFournisseur());

        byte[] corps = evenement("transaction.approved", p, "approved", 1500);
        assertEquals(PaiementService.Issue.ACTIVE, service.traiterWebhook(corps, signer(corps)));

        Repetiteur apres = relire(r);
        LocalDate fin = LocalDate.now().plusMonths(1);
        assertEquals(Plan.SOLO, apres.getPlan());
        assertEquals(fin, apres.getSoloJusquAu());
        assertTrue(apres.isSoloActif());
        Paiement paye = paiementRepo.findByReference(p.getReference()).orElseThrow();
        assertEquals(StatutPaiement.PAYE, paye.getStatut());
        assertEquals("moov", paye.getMoyen());
        assertNotNull(paye.getDatePaiement());

        // Rejeu du même événement (FedaPay peut renvoyer jusqu'à 10 fois) : rien ne bouge
        assertEquals(PaiementService.Issue.DEJA_TRAITE, service.traiterWebhook(corps, signer(corps)));
        byte[] transfere = evenement("transaction.transferred", p, "transferred", 1500);
        assertEquals(PaiementService.Issue.DEJA_TRAITE, service.traiterWebhook(transfere, signer(transfere)));
        assertEquals(fin, relire(r).getSoloJusquAu());
    }

    @Test
    void unDeuxiemePaiementProlongeDUnMoisDePlus() {
        Repetiteur r = nouveauRepetiteur();
        for (int i = 0; i < 2; i++) {
            Paiement p = demarrer(r);
            byte[] corps = evenement("transaction.approved", p, "approved", 1500);
            service.traiterWebhook(corps, signer(corps));
        }
        assertEquals(LocalDate.now().plusMonths(2), relire(r).getSoloJusquAu());
    }

    @Test
    void mauvaiseSignatureNeChangeRien() {
        Repetiteur r = nouveauRepetiteur();
        Paiement p = demarrer(r);
        byte[] corps = evenement("transaction.approved", p, "approved", 1500);
        String fausse = SignatureWebhook.entete(corps, "wh_pirate", Instant.now().getEpochSecond());
        assertThrows(SignatureInvalideException.class, () -> service.traiterWebhook(corps, fausse));
        assertThrows(SignatureInvalideException.class, () -> service.traiterWebhook(corps, null));
        assertEquals(StatutPaiement.EN_ATTENTE, paiementRepo.findByReference(p.getReference()).orElseThrow().getStatut());
        assertEquals(Plan.GRATUIT, relire(r).getPlan());
    }

    @Test
    void montantIncorrectNActivePasSolo() {
        Repetiteur r = nouveauRepetiteur();
        Paiement p = demarrer(r);
        byte[] corps = evenement("transaction.approved", p, "approved", 100);
        assertEquals(PaiementService.Issue.MONTANT_INCORRECT, service.traiterWebhook(corps, signer(corps)));
        assertEquals(Plan.GRATUIT, relire(r).getPlan());
        assertEquals(StatutPaiement.EN_ATTENTE, paiementRepo.findByReference(p.getReference()).orElseThrow().getStatut());
    }

    @Test
    void paiementRefusePuisReussi() {
        Repetiteur r = nouveauRepetiteur();
        Paiement p = demarrer(r);
        byte[] refus = evenement("transaction.declined", p, "declined", 1500);
        assertEquals(PaiementService.Issue.MIS_A_JOUR, service.traiterWebhook(refus, signer(refus)));
        assertEquals(StatutPaiement.ECHOUE, paiementRepo.findByReference(p.getReference()).orElseThrow().getStatut());
        assertEquals(Plan.GRATUIT, relire(r).getPlan());
        // "declined" n'est pas final chez FedaPay : une nouvelle tentative peut réussir
        byte[] ok = evenement("transaction.approved", p, "approved", 1500);
        assertEquals(PaiementService.Issue.ACTIVE, service.traiterWebhook(ok, signer(ok)));
        assertEquals(Plan.SOLO, relire(r).getPlan());
    }

    @Test
    void evenementInconnuIgnore() {
        byte[] corps = "{\"name\":\"transaction.approved\",\"entity\":{\"id\":\"999999\",\"merchant_reference\":\"INCONNUE\",\"amount\":1500,\"status\":\"approved\"}}"
                .getBytes(StandardCharsets.UTF_8);
        assertEquals(PaiementService.Issue.IGNORE, service.traiterWebhook(corps, signer(corps)));
        byte[] client = "{\"name\":\"customer.created\",\"entity\":{\"id\":1}}".getBytes(StandardCharsets.UTF_8);
        assertEquals(PaiementService.Issue.IGNORE, service.traiterWebhook(client, signer(client)));
    }

    @Test
    void soloPayeExpireRetombeSurLaLimiteGratuite() {
        Repetiteur r = nouveauRepetiteur();
        Repetiteur entite = relire(r);
        entite.setPlan(Plan.SOLO);
        entite.setSoloJusquAu(LocalDate.now().minusDays(1));
        repetiteurRepo.save(entite);
        for (int i = 1; i <= 3; i++) {
            EleveForm f = new EleveForm();
            f.setNom("Élève " + i); f.setClasse("3e"); f.setTarifMensuel(10000);
            eleves.creer(r.getId(), f);
        }
        assertFalse(eleves.peutAjouter(r.getId()), "Solo expiré = limite gratuite");
        // Un paiement repart d'aujourd'hui (pas de la date passée)
        Paiement p = demarrer(r);
        byte[] corps = evenement("transaction.approved", p, "approved", 1500);
        service.traiterWebhook(corps, signer(corps));
        assertEquals(LocalDate.now().plusMonths(1), relire(r).getSoloJusquAu());
        assertTrue(eleves.peutAjouter(r.getId()));
    }

    // ------------------------------------------------------------ HTTP

    @Test
    void webhookHttpSansCsrfEtAvecSignature() throws Exception {
        Repetiteur r = nouveauRepetiteur();
        Paiement p = demarrer(r);
        byte[] corps = evenement("transaction.approved", p, "approved", 1500);

        mvc.perform(post("/paiement/webhook/fedapay").contentType(MediaType.APPLICATION_JSON).content(corps)
                        .header("X-FEDAPAY-SIGNATURE", "t=1,s=00"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/paiement/webhook/fedapay").contentType(MediaType.APPLICATION_JSON).content(corps))
                .andExpect(status().isBadRequest());
        assertEquals(Plan.GRATUIT, relire(r).getPlan());

        mvc.perform(post("/paiement/webhook/fedapay").contentType(MediaType.APPLICATION_JSON).content(corps)
                        .header("X-FEDAPAY-SIGNATURE", signer(corps)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ACTIVE")));
        mvc.perform(post("/paiement/webhook/fedapay").contentType(MediaType.APPLICATION_JSON).content(corps)
                        .header("X-FEDAPAY-SIGNATURE", signer(corps)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("DEJA_TRAITE")));
        assertEquals(LocalDate.now().plusMonths(1), relire(r).getSoloJusquAu());
    }

    @Test
    void parcoursCompletAvecLaCaisseDeSimulation() throws Exception {
        Repetiteur r = nouveauRepetiteur();
        UtilisateurConnecte moi = (UtilisateurConnecte) repetiteurs.loadUserByUsername(r.getTelephone());

        mvc.perform(get("/formule").with(user(moi)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Payer 1 500 FCFA par Mobile Money")))
                .andExpect(content().string(containsString("Ou par transfert manuel")));

        MvcResult res = mvc.perform(post("/paiement/solo").with(user(moi)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andReturn();
        String caisse = res.getResponse().getRedirectedUrl();
        assertNotNull(caisse);
        assertTrue(caisse.startsWith("/paiement/simulation/RP"), caisse);
        String ref = caisse.substring("/paiement/simulation/".length());

        mvc.perform(get(caisse).with(user(moi))).andExpect(status().isOk())
                .andExpect(content().string(containsString("aucun argent réel")));

        // Un autre compte ne voit ni la caisse ni le retour
        Repetiteur autre = nouveauRepetiteur();
        UtilisateurConnecte intrus = (UtilisateurConnecte) repetiteurs.loadUserByUsername(autre.getTelephone());
        mvc.perform(get(caisse).with(user(intrus))).andExpect(status().isNotFound());
        mvc.perform(get("/paiement/retour/" + ref).with(user(intrus))).andExpect(status().isNotFound());

        res = mvc.perform(post(caisse).with(user(moi)).with(csrf()).param("action", "payer").param("reseau", "sbin"))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertTrue(res.getResponse().getRedirectedUrl().startsWith("/paiement/retour/" + ref + "?id="));

        mvc.perform(get("/paiement/retour/" + ref).with(user(moi)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Paiement confirmé")))
                .andExpect(content().string(containsString("Celtiis Cash")));
        assertEquals(Plan.SOLO, relire(r).getPlan());

        // Re-soumettre la caisse ne prolonge pas une deuxième fois
        mvc.perform(post(caisse).with(user(moi)).with(csrf()).param("action", "payer"));
        assertEquals(LocalDate.now().plusMonths(1), relire(r).getSoloJusquAu());
    }

    @Test
    void simulationEchecAfficheLaPageDEchec() throws Exception {
        Repetiteur r = nouveauRepetiteur();
        UtilisateurConnecte moi = (UtilisateurConnecte) repetiteurs.loadUserByUsername(r.getTelephone());
        String caisse = mvc.perform(post("/paiement/solo").with(user(moi)).with(csrf())).andReturn().getResponse().getRedirectedUrl();
        String retour = mvc.perform(post(caisse).with(user(moi)).with(csrf()).param("action", "echec"))
                .andReturn().getResponse().getRedirectedUrl();
        mvc.perform(get(retour).with(user(moi)))
                .andExpect(content().string(containsString("Paiement non abouti")));
        assertEquals(Plan.GRATUIT, relire(r).getPlan());
    }

    @Test
    void payerExigeUneConnexion() throws Exception {
        mvc.perform(post("/paiement/solo").with(csrf())).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/connexion"));
    }
}
