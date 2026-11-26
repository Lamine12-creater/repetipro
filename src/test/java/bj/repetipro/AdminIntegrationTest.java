package bj.repetipro;

import bj.repetipro.model.Classe;
import bj.repetipro.model.Plan;
import bj.repetipro.model.Repetiteur;
import bj.repetipro.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.DisabledException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Espace SuperAdmin : réservé aux numéros de ADMIN_TELEPHONES, jamais aux répétiteurs. */
@SpringBootTest(properties = {"repetipro.paiement.simulation=false", "repetipro.fedapay.secret-key=",
        "repetipro.admin-telephones=0100000077"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminIntegrationTest {

    @Autowired RepetiteurService repetiteurs;
    @Autowired TarifService tarifs;
    @Autowired ParametreService parametres;
    @Autowired bj.repetipro.repository.RepetiteurRepository repo;
    @Autowired MockMvc mvc;

    private Repetiteur nouveau() {
        String tel = "01" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
        return repetiteurs.inscrire("Test Répétiteur", tel, "secret123");
    }

    private UtilisateurConnecte tuteur() {
        return (UtilisateurConnecte) repetiteurs.loadUserByUsername(nouveau().getTelephone());
    }

    private UtilisateurConnecte superAdmin() {
        Repetiteur r = repo.findByTelephone(bj.repetipro.util.Telephones.normaliser("0100000077"))
                .orElseGet(() -> repetiteurs.inscrire("Super Admin", "0100000077", "secret123"));
        return (UtilisateurConnecte) repetiteurs.loadUserByUsername(r.getTelephone());
    }

    @Test
    void lAnonymeEstRenvoyeVersLaConnexion() throws Exception {
        for (String url : new String[]{"/admin", "/admin/tarifs", "/admin/comptes", "/admin/parametres", "/tarifs"}) {
            mvc.perform(get(url)).andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrlPattern("**/connexion"));
        }
    }

    @Test
    void unRepetiteurNormalAReçoit403PartoutDansAdmin() throws Exception {
        UtilisateurConnecte moi = tuteur();
        assertFalse(moi.isSuperAdmin());
        for (String url : new String[]{"/admin", "/admin/tarifs", "/admin/comptes", "/admin/parametres", "/tarifs"}) {
            mvc.perform(get(url).with(user(moi))).andExpect(status().isForbidden());
        }
        Map<String, String> grille = new HashMap<>();
        for (Classe c : Classe.values()) grille.put(c.name(), "1");
        var post = post("/admin/tarifs").with(user(moi)).with(csrf());
        grille.forEach(post::param);
        mvc.perform(post).andExpect(status().isForbidden());
        mvc.perform(post("/admin/parametres").with(user(moi)).with(csrf()).param("prixSolo", "100")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/comptes/1/plan").with(user(moi)).with(csrf()).param("plan", "SOLO")).andExpect(status().isForbidden());
        mvc.perform(post("/admin/comptes/1/activation").with(user(moi)).with(csrf()).param("actif", "false")).andExpect(status().isForbidden());
        // La grille n'a pas bougé
        assertEquals(25_000, tarifs.montant("5ème"));
    }

    @Test
    void lAdminSansJetonCsrfEstRefuse() throws Exception {
        mvc.perform(post("/admin/parametres").with(user(superAdmin())).param("prixSolo", "100")).andExpect(status().isForbidden());
    }

    @Test
    void leNumeroAdminObtientLeRoleSuperAdmin() {
        UtilisateurConnecte sa = superAdmin();
        assertTrue(sa.isSuperAdmin());
        assertTrue(sa.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN")));
        assertFalse(tuteur().getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_SUPERADMIN")));
    }

    @Test
    void leSuperAdminVoitLesPagesEtLeBoutonDeNavigation() throws Exception {
        UtilisateurConnecte sa = superAdmin();
        mvc.perform(get("/admin").with(user(sa))).andExpect(status().isOk())
                .andExpect(content().string(containsString("répétiteurs inscrits")))
                .andExpect(content().string(containsString("Activité récente")));
        mvc.perform(get("/admin/comptes").with(user(sa))).andExpect(status().isOk());
        mvc.perform(get("/admin/tarifs").with(user(sa))).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"CINQUIEME\"")));
        mvc.perform(get("/admin/parametres").with(user(sa))).andExpect(status().isOk());
        mvc.perform(get("/parametres").with(user(tuteur()))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Passer à Solo · 1 500 FCFA/mois")));
        mvc.perform(get("/tarifs").with(user(sa))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/tarifs"));
        mvc.perform(get("/tableau-de-bord").with(user(sa))).andExpect(content().string(containsString("href=\"/admin\"")));
        // Un répétiteur normal ne voit aucun lien admin dans son menu
        mvc.perform(get("/tableau-de-bord").with(user(tuteur()))).andExpect(content().string(org.hamcrest.Matchers.not(containsString("href=\"/admin\""))));
    }

    @Test
    void laPageAccueilPubliqueMontreLeLienEspaceAdmin() throws Exception {
        mvc.perform(get("/")).andExpect(status().isOk()).andExpect(content().string(containsString("Espace admin")));
    }

    @Test
    void leSuperAdminModifieLaGrilleEtLesReglages() throws Exception {
        UtilisateurConnecte sa = superAdmin();
        Map<String, String> grille = new HashMap<>();
        for (Classe c : Classe.values()) grille.put(c.name(), String.valueOf(c.getTarifDefaut()));
        grille.put("TLE", "65000");
        try {
            var post = post("/admin/tarifs").with(user(sa)).with(csrf());
            grille.forEach(post::param);
            mvc.perform(post).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/tarifs"));
            assertEquals(65_000, tarifs.montant("Tle"));

            // Valeur invalide : refusée, grille inchangée
            var mauvais = post("/admin/tarifs").with(user(sa)).with(csrf());
            grille.put("TLE", "abc");
            grille.forEach(mauvais::param);
            mvc.perform(mauvais).andExpect(status().isOk());
            assertEquals(65_000, tarifs.montant("Tle"));

            mvc.perform(post("/admin/parametres").with(user(sa)).with(csrf())
                            .param("prixSolo", "2000").param("momoNumero", "01 97 11 22 33").param("momoNom", "RépétiPro Test"))
                    .andExpect(status().is3xxRedirection());
            assertEquals(2000, parametres.prixSolo());
            assertEquals("RépétiPro Test", parametres.momoNom());
            mvc.perform(get("/formule").with(user(tuteur()))).andExpect(content().string(containsString("2 000 FCFA")))
                    .andExpect(content().string(containsString("01 97 11 22 33")));
            mvc.perform(post("/admin/parametres").with(user(sa)).with(csrf()).param("prixSolo", "5"))
                    .andExpect(status().isOk());
            assertEquals(2000, parametres.prixSolo());
        } finally {
            grille.put("TLE", "60000");
            tarifs.enregistrer(grille);
            parametres.enregistrerReglages("", "", "");
        }
        assertEquals(1500, parametres.prixSolo());
    }

    @Test
    void prolongerSuspendreEtDesactiverUnCompte() throws Exception {
        UtilisateurConnecte sa = superAdmin();
        Repetiteur r = nouveau();
        mvc.perform(post("/admin/comptes/{id}/prolonger", r.getId()).with(user(sa)).with(csrf()).param("mois", "2"))
                .andExpect(status().is3xxRedirection());
        Repetiteur apres = repetiteurs.trouver(r.getId());
        assertEquals(Plan.SOLO, apres.getPlan());
        assertEquals(java.time.LocalDate.now().plusMonths(2), apres.getSoloJusquAu());

        mvc.perform(post("/admin/comptes/{id}/plan", r.getId()).with(user(sa)).with(csrf()).param("plan", "GRATUIT"))
                .andExpect(status().is3xxRedirection());
        assertEquals(Plan.GRATUIT, repetiteurs.trouver(r.getId()).getPlan());

        mvc.perform(post("/admin/comptes/{id}/activation", r.getId()).with(user(sa)).with(csrf()).param("actif", "false"))
                .andExpect(status().is3xxRedirection());
        assertFalse(repetiteurs.trouver(r.getId()).isCompteActif());
        UtilisateurConnecte bloque = (UtilisateurConnecte) repetiteurs.loadUserByUsername(r.getTelephone());
        assertFalse(bloque.isEnabled(), "un compte désactivé ne peut plus se connecter");
        // Même avec une session déjà ouverte, il est renvoyé vers la connexion
        mvc.perform(get("/eleves").with(user(bloque))).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/connexion?suspendu"));

        mvc.perform(post("/admin/comptes/{id}/activation", r.getId()).with(user(sa)).with(csrf()).param("actif", "true"))
                .andExpect(status().is3xxRedirection());
        assertTrue(repetiteurs.trouver(r.getId()).isCompteActif());
    }

    @Test
    void onNePeutPasDesactiverUnSuperAdmin() throws Exception {
        UtilisateurConnecte sa = superAdmin();
        mvc.perform(post("/admin/comptes/{id}/activation", sa.getId()).with(user(sa)).with(csrf()).param("actif", "false"))
                .andExpect(status().is3xxRedirection());
        assertTrue(repetiteurs.trouver(sa.getId()).isCompteActif());
    }
}
