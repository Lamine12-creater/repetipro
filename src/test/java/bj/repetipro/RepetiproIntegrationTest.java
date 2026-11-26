package bj.repetipro;

import bj.repetipro.model.*;
import bj.repetipro.repository.FactureRepository;
import bj.repetipro.repository.SeanceRepository;
import bj.repetipro.service.*;
import bj.repetipro.web.EleveForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tests d'intégration : l'application complète démarre avec une base H2 en mémoire. */
// Paiement en ligne forcé à "non configuré", quelles que soient les variables de la machine.
@SpringBootTest(properties = {"repetipro.paiement.simulation=false", "repetipro.fedapay.secret-key="})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RepetiproIntegrationTest {

    @Autowired RepetiteurService repetiteurs;
    @Autowired EleveService eleves;
    @Autowired SeanceService seances;
    @Autowired FactureService factures;
    @Autowired TarifService tarifs;
    @Autowired SeanceRepository seanceRepo;
    @Autowired FactureRepository factureRepo;
    @Autowired MockMvc mvc;

    /** Crée un répétiteur avec un numéro unique pour que les tests ne se gênent pas. */
    private Repetiteur nouveauRepetiteur() {
        String tel = "01" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
        return repetiteurs.inscrire("Test Répétiteur", tel, "secret123");
    }

    private EleveForm eleve(String nom, int tarif) {
        EleveForm f = new EleveForm();
        f.setNom(nom);
        f.setClasse("5ème");
        f.setMatieres("Maths");
        f.setNomParent("Parent Test");
        f.setWhatsappParent("01 00 00 00 09");
        f.setTarifMensuel(tarif);
        return f;
    }

    @Test
    void laFactureMensuelleVautLeTarifEtCompteLesSeancesFaites() {
        Repetiteur r = nouveauRepetiteur();
        Eleve e = eleves.creer(r.getId(), eleve("Élève Un", 99)); // 99 doit être ignoré
        YearMonth mois = YearMonth.of(2026, 9);
        Seance s1 = seances.planifier(r.getId(), e.getId(), mois.atDay(3).atTime(16, 0), 120);
        Seance s2 = seances.planifier(r.getId(), e.getId(), mois.atDay(5).atTime(16, 0), 120);
        seances.planifier(r.getId(), e.getId(), mois.atDay(7).atTime(16, 0), 120); // non pointée
        seances.pointer(s1.getId(), r.getId(), Presence.PRESENT, "Thalès", "Bien");
        seances.pointer(s2.getId(), r.getId(), Presence.EN_RETARD, null, null);

        assertEquals(1, factures.genererPourLeMois(r.getId(), mois));
        assertEquals(0, factures.genererPourLeMois(r.getId(), mois), "pas de doublon");

        Facture f = factures.duMois(r.getId(), mois).get(0);
        assertEquals(25000, f.getMontant());
        assertEquals(2, f.getNombreSeances());
        assertEquals(mois.plusMonths(1).atDay(10), f.getDateEcheance());

        factures.marquerPayee(f.getId(), r.getId(), "MP123");
        Facture payee = factures.trouver(f.getId(), r.getId());
        assertEquals(StatutFacture.PAYE, payee.getStatut());
        assertEquals("Payé", payee.getStatutAffiche());
    }

    @Test
    void laFormuleGratuiteEstLimiteeATroisEleves() {
        Repetiteur r = nouveauRepetiteur();
        for (int i = 1; i <= 3; i++) eleves.creer(r.getId(), eleve("Élève " + i, 10000));
        assertFalse(eleves.peutAjouter(r.getId()));
        assertThrows(LimiteGratuiteException.class, () -> eleves.creer(r.getId(), eleve("Élève 4", 10000)));

        repetiteurs.changerPlan(r.getId(), Plan.SOLO);   // l'admin active Solo
        assertDoesNotThrow(() -> eleves.creer(r.getId(), eleve("Élève 4", 10000)));
    }

    @Test
    void unRepetiteurNeVoitPasLesDonneesDUnAutre() throws Exception {
        Repetiteur proprietaire = nouveauRepetiteur();
        Repetiteur intrus = nouveauRepetiteur();
        Eleve e = eleves.creer(proprietaire.getId(), eleve("Élève Privé", 15000));
        Seance s = seances.planifier(proprietaire.getId(), e.getId(), YearMonth.now().atDay(1).atTime(9, 0), 60);
        factures.genererPourLeMois(proprietaire.getId(), YearMonth.now());
        Long factureId = factures.duMois(proprietaire.getId(), YearMonth.now()).get(0).getId();

        UtilisateurConnecte moiIntrus = (UtilisateurConnecte) repetiteurs.loadUserByUsername(intrus.getTelephone());

        mvc.perform(get("/seances/" + s.getId()).with(user(moiIntrus))).andExpect(status().isNotFound());
        mvc.perform(get("/eleves/" + e.getId() + "/modifier").with(user(moiIntrus))).andExpect(status().isNotFound());
        mvc.perform(get("/factures/" + factureId + "/pdf").with(user(moiIntrus))).andExpect(status().isNotFound());
        mvc.perform(post("/factures/" + factureId + "/payer").with(user(moiIntrus)).with(csrf()))
                .andExpect(status().isNotFound());
        assertEquals(StatutFacture.EN_ATTENTE, factureRepo.findById(factureId).orElseThrow().getStatut());

        // Le propriétaire, lui, obtient bien le PDF
        UtilisateurConnecte moi = (UtilisateurConnecte) repetiteurs.loadUserByUsername(proprietaire.getTelephone());
        mvc.perform(get("/factures/" + factureId + "/pdf").with(user(moi)))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void lesPagesPriveesExigentUneConnexion() throws Exception {
        mvc.perform(get("/tableau-de-bord")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/connexion"));
        mvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void sansCleFedaPayLaPageFormuleGardeLePaiementManuelSeulement() throws Exception {
        mvc.perform(get("/formule")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Comment payer")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Payer 1 500 FCFA par Mobile Money"))));
        Repetiteur r = nouveauRepetiteur();
        UtilisateurConnecte moi = (UtilisateurConnecte) repetiteurs.loadUserByUsername(r.getTelephone());
        mvc.perform(post("/paiement/solo").with(user(moi)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/formule"));
    }

    @Test
    void leTarifVientDeLaGrilleEtIgnoreLeMontantClient() {
        Repetiteur r = nouveauRepetiteur();
        Eleve e = eleves.creer(r.getId(), eleve("Kossi", 1));
        assertEquals("5ème", e.getClasse());
        assertEquals(25_000, e.getTarifMensuel());

        java.util.Map<String, String> grille = new java.util.HashMap<>();
        for (Classe c : Classe.values()) grille.put(c.name(), String.valueOf(c.getTarifDefaut()));
        grille.put("CINQUIEME", "27000");
        assertNull(tarifs.enregistrer(r.getId(), grille));

        EleveForm f = eleve("Kossi", 1);
        f.setClasse("5ème");
        eleves.modifier(e.getId(), r.getId(), f);
        assertEquals(25_000, eleves.trouver(e.getId(), r.getId()).getTarifMensuel(),
                "même classe : l'ancien tarif est conservé");

        f.setClasse("3ème");
        eleves.modifier(e.getId(), r.getId(), f);
        Eleve apres = eleves.trouver(e.getId(), r.getId());
        assertEquals("3ème", apres.getClasse());
        assertEquals(30_000, apres.getTarifMensuel());

        f.setClasse("5ème");
        eleves.modifier(e.getId(), r.getId(), f);
        assertEquals(27_000, eleves.trouver(e.getId(), r.getId()).getTarifMensuel(),
                "changement de classe : nouveau tarif de la grille");
    }

    @Test
    void leFormulaireEleveNAplusDeChampTarifLibreEtLaPageTarifsEstProtegee() throws Exception {
        Repetiteur r = nouveauRepetiteur();
        UtilisateurConnecte moi = (UtilisateurConnecte) repetiteurs.loadUserByUsername(r.getTelephone());
        mvc.perform(get("/tarifs")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/tarifs").with(user(moi))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Tarifs mensuels")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("25")));
        mvc.perform(get("/eleves/nouveau").with(user(moi))).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"classe\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-montant")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"tarifMensuel\""))));
        mvc.perform(post("/eleves").with(user(moi)).with(csrf())
                        .param("nom", "Fifamè")
                        .param("classe", "Tle")
                        .param("tarifMensuel", "1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/eleves"));
        Eleve e = eleves.actifs(r.getId()).get(0);
        assertEquals("Tle", e.getClasse());
        assertEquals(60_000, e.getTarifMensuel());
    }
}
