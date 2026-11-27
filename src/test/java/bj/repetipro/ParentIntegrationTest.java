package bj.repetipro;

import bj.repetipro.model.*;
import bj.repetipro.repository.SeanceRepository;
import bj.repetipro.service.*;
import bj.repetipro.util.Telephones;
import bj.repetipro.web.EleveForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Comptes parents, créneaux hebdomadaires choisis par le parent, pointage par le répétiteur. */
@SpringBootTest(properties = {"repetipro.paiement.simulation=false", "repetipro.fedapay.secret-key=",
        "repetipro.admin-telephones=0100000078"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ParentIntegrationTest {

    @Autowired RepetiteurService repetiteurs;
    @Autowired ParentService parents;
    @Autowired EleveService eleves;
    @Autowired PlanningService planning;
    @Autowired TarifService tarifs;
    @Autowired SeanceRepository seanceRepo;
    @Autowired MockMvc mvc;

    private static String telAleatoire() {
        return "01" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
    }

    private Repetiteur tuteur() { return repetiteurs.inscrire("Tuteur Test", telAleatoire(), "secret123"); }

    private UtilisateurConnecte principal(String tel) { return (UtilisateurConnecte) repetiteurs.loadUserByUsername(tel); }

    private Eleve eleve(Repetiteur r, String nom, String classe, String telParent) {
        EleveForm f = new EleveForm();
        f.setNom(nom); f.setClasse(classe); f.setNomParent("Parent " + nom); f.setWhatsappParent(telParent);
        return eleves.creer(r.getId(), f);
    }

    private MockHttpServletRequestBuilder creneaux(UtilisateurConnecte p, Long eleveId, String[] jours, String[] heures, String... extra) {
        var b = post("/parent/eleves/{id}/creneaux", eleveId).with(user(p)).with(csrf());
        for (String j : jours) b.param("jour", j);
        for (String h : heures) b.param("heure", h);
        for (int i = 0; i + 1 < extra.length; i += 2) b.param(extra[i], extra[i + 1]);
        return b;
    }

    @Test
    void lesReglesParDefautSuiventLesRegles() {
        for (String c : new String[]{"6ème", "5ème", "4ème", "3ème", "CM2", "CI"}) {
            assertEquals(new TarifService.Regle(2, 2), tarifs.regle(c), c);
        }
        for (String c : new String[]{"2nde", "1ère", "Tle"}) {
            assertEquals(new TarifService.Regle(2, 3), tarifs.regle(c), c);
        }
    }

    @Test
    void lInscriptionCommenceParLeChoixDuTypeDeCompte() throws Exception {
        mvc.perform(get("/inscription")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Je suis répétiteur")))
                .andExpect(content().string(containsString("Je suis parent")))
                .andExpect(content().string(containsString("/inscription/repetiteur")))
                .andExpect(content().string(containsString("/inscription/parent")));
        mvc.perform(get("/inscription/repetiteur")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Compte répétiteur")))
                .andExpect(content().string(not(containsString("name=\"confirmation\""))));
        mvc.perform(get("/inscription/parent")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Compte parent")))
                .andExpect(content().string(containsString("name=\"confirmation\"")));
        mvc.perform(get("/")).andExpect(content().string(containsString("Espace répétiteur")))
                .andExpect(content().string(containsString("Espace parent")));
    }

    @Test
    void inscriptionParentEtUnNumeroUnSeulTypeDeCompte() throws Exception {
        String tel = telAleatoire();
        mvc.perform(post("/inscription/parent").with(csrf())
                        .param("nomComplet", "Mme Parent").param("telephone", tel)
                        .param("motDePasse", "secret123").param("confirmation", "autre"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("pas identiques")));
        mvc.perform(post("/inscription/parent").with(csrf())
                        .param("nomComplet", "Mme Parent").param("telephone", tel)
                        .param("motDePasse", "secret123").param("confirmation", "secret123"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/parent?bienvenue"));
        UtilisateurConnecte p = principal(tel);
        assertTrue(p.isParent());
        assertTrue(p.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_PARENT")));
        assertFalse(p.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_REPETITEUR")));

        // Le numéro d'un parent ne peut pas servir à un compte répétiteur, et inversement
        mvc.perform(post("/inscription/repetiteur").with(csrf())
                        .param("nomComplet", "Tuteur").param("telephone", tel).param("motDePasse", "secret123"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("compte parent")));
        Repetiteur r = tuteur();
        mvc.perform(post("/inscription/parent").with(csrf())
                        .param("nomComplet", "Mme X").param("telephone", r.getTelephone())
                        .param("motDePasse", "secret123").param("confirmation", "secret123"))
                .andExpect(status().isOk()).andExpect(content().string(containsString("compte répétiteur")));
    }

    @Test
    void chaqueEspaceEstFermeAL_autre() throws Exception {
        UtilisateurConnecte parent = principal(parents.inscrire("Parent", telAleatoire(), "secret123").getTelephone());
        UtilisateurConnecte tuteur = principal(tuteur().getTelephone());
        for (String url : new String[]{"/tableau-de-bord", "/eleves", "/seances", "/factures", "/parametres", "/admin"}) {
            mvc.perform(get(url).with(user(parent))).andExpect(status().isForbidden());
        }
        mvc.perform(get("/parent").with(user(tuteur))).andExpect(status().isForbidden());
        mvc.perform(get("/parent")).andExpect(status().is3xxRedirection());
        mvc.perform(get("/parent").with(user(parent))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Mes enfants")))
                .andExpect(content().string(containsString("Parent</span>")));
        mvc.perform(get("/tableau-de-bord").with(user(tuteur))).andExpect(content().string(containsString("Répétiteur</span>")));
    }

    @Test
    void unParentNeVoitQueSesEnfants() throws Exception {
        Repetiteur r = tuteur();
        String telA = telAleatoire(), telB = telAleatoire();
        Eleve enfantA = eleve(r, "Awa Alpha", "5ème", telA);
        Eleve enfantB = eleve(r, "Bako Bravo", "Tle", telB);
        UtilisateurConnecte parentA = principal(parents.inscrire("Parent A", telA, "secret123").getTelephone());
        UtilisateurConnecte parentB = principal(parents.inscrire("Parent B", telB, "secret123").getTelephone());

        mvc.perform(get("/parent").with(user(parentA))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Awa Alpha")))
                .andExpect(content().string(not(containsString("Bako Bravo"))));
        mvc.perform(get("/parent/eleves/{id}", enfantA.getId()).with(user(parentA))).andExpect(status().isOk())
                .andExpect(content().string(containsString("25 000 FCFA")))
                .andExpect(content().string(containsString("2 séances de 2 h par semaine")));
        mvc.perform(get("/parent/eleves/{id}", enfantB.getId()).with(user(parentA))).andExpect(status().isNotFound());
        mvc.perform(creneaux(parentA, enfantB.getId(), new String[]{"1", "3"}, new String[]{"17:00", "17:00"})).andExpect(status().isNotFound());
        mvc.perform(post("/parent/eleves/{id}/infos", enfantB.getId()).with(user(parentA)).with(csrf())
                .param("nom", "Pirate").param("classe", "CI")).andExpect(status().isNotFound());
        assertEquals("Bako Bravo", eleves.trouver(enfantB.getId(), r.getId()).getNom());
        assertTrue(planning.creneaux(enfantB.getId()).isEmpty());

        // Un parent dont le numéro n'est lié à aucun élève ne voit rien
        UtilisateurConnecte inconnu = principal(parents.inscrire("Parent C", telAleatoire(), "secret123").getTelephone());
        mvc.perform(get("/parent").with(user(inconnu))).andExpect(content().string(containsString("pas encore ajouté d'enfant")));
        // Le parent B (3 h) voit sa propre règle
        mvc.perform(get("/parent/eleves/{id}", enfantB.getId()).with(user(parentB))).andExpect(status().isOk())
                .andExpect(content().string(containsString("2 séances de 3 h par semaine")))
                .andExpect(content().string(containsString("60 000 FCFA")));
    }

    @Test
    void leParentChoisitSesCreneauxAvecValidationCoteServeur() throws Exception {
        Repetiteur r = tuteur();
        String telP = telAleatoire();
        Eleve e = eleve(r, "Chloé Test", "4ème", telP);
        UtilisateurConnecte parent = principal(parents.inscrire("Mme Test", telP, "secret123").getTelephone());

        // mauvais nombre de créneaux
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2"}, new String[]{"17:00"}))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("exactement 2")));
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4", "6"}, new String[]{"17:00", "17:00", "17:00"}))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("exactement 2")));
        // même jour deux fois
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "2"}, new String[]{"09:00", "15:00"}))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("jours différents")));
        // durée modifiée : refusée
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"17:00", "17:00"}, "dureeMinutes", "60"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("ne peut pas être modifiée")));
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"17:00", "17:00"}, "dureeMinutes", "240"))
                .andExpect(status().isBadRequest());
        // hors plage : trop tôt, fin après 22h, pas de 30 min
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"06:30", "17:00"})).andExpect(status().isBadRequest());
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"20:30", "17:00"})).andExpect(status().isBadRequest());
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"17:10", "17:00"})).andExpect(status().isBadRequest());
        mvc.perform(creneaux(parent, e.getId(), new String[]{"8", "4"}, new String[]{"17:00", "17:00"})).andExpect(status().isBadRequest());
        mvc.perform(creneaux(parent, e.getId(), new String[]{"x", "4"}, new String[]{"17:00", "abc"})).andExpect(status().isBadRequest());
        assertTrue(planning.creneaux(e.getId()).isEmpty(), "rien n'est enregistré après un refus");

        // valide
        mvc.perform(creneaux(parent, e.getId(), new String[]{"4", "2"}, new String[]{"16:30", "18:00"}, "dureeMinutes", "120"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/parent/eleves/" + e.getId()));
        List<CreneauEleve> l = planning.creneaux(e.getId());
        assertEquals(2, l.size());
        assertEquals(2, l.get(0).getJour());
        assertEquals("Mardi · 18h00 – 20h00", l.get(0).getResume());
        assertEquals("Jeudi · 16h30 – 18h30", l.get(1).getResume());
        assertTrue(planning.creneauxComplets(e));
        // les séances de la semaine sont générées, durée fixe 2 h
        var seances = seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().atStartOfDay());
        assertTrue(seances.size() >= 2, "séances générées");
        assertTrue(seances.stream().allMatch(s -> s.getDureeMinutes() == 120 && Boolean.TRUE.equals(s.getAutomatique())));
        planning.genererPourEleve(eleves.trouver(e.getId(), r.getId()));
        assertEquals(seances.size(), seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().atStartOfDay()).size(),
                "la génération ne crée pas de doublons");

        // modification ultérieure par le même compte
        mvc.perform(creneaux(parent, e.getId(), new String[]{"1", "5"}, new String[]{"08:00", "19:00"})).andExpect(status().is3xxRedirection());
        assertEquals("Lundi · 08h00 – 10h00", planning.creneaux(e.getId()).get(0).getResume());
        var apres = seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), java.time.LocalDateTime.now());
        assertTrue(apres.stream().allMatch(s -> s.getDebut().getDayOfWeek().getValue() == 1 || s.getDebut().getDayOfWeek().getValue() == 5),
                "les anciennes séances à venir non pointées sont remplacées");
    }

    @Test
    void secondeEtTerminaleDurentTroisHeures() throws Exception {
        Repetiteur r = tuteur();
        String telP = telAleatoire();
        Eleve e = eleve(r, "Dossou Seconde", "2nde", telP);
        UtilisateurConnecte parent = principal(parents.inscrire("Parent S", telP, "secret123").getTelephone());
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"20:00", "17:00"})).andExpect(status().isBadRequest()); // 20h + 3h > 22h
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"19:00", "17:00"}, "dureeMinutes", "120")).andExpect(status().isBadRequest());
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"19:00", "14:00"})).andExpect(status().is3xxRedirection());
        assertEquals("Mardi · 19h00 – 22h00", planning.creneaux(e.getId()).get(0).getResume());
        assertTrue(seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().atStartOfDay())
                .stream().allMatch(s -> s.getDureeMinutes() == 180));
    }

    @Test
    void leRepetiteurPointeMaisNeChangeNiCreneauxNiDuree() throws Exception {
        Repetiteur r = tuteur();
        Repetiteur autre = tuteur();
        String telP = telAleatoire();
        Eleve e = eleve(r, "Edem Pointage", "5ème", telP);
        UtilisateurConnecte moi = principal(r.getTelephone());
        UtilisateurConnecte intrus = principal(autre.getTelephone());
        UtilisateurConnecte parent = principal(parents.inscrire("Parent E", telP, "secret123").getTelephone());

        // Le parent n'a rien choisi : le répétiteur voit l'avertissement
        mvc.perform(get("/eleves/{id}", e.getId()).with(user(moi))).andExpect(status().isOk())
                .andExpect(content().string(containsString("n'a pas encore choisi")))
                .andExpect(content().string(containsString("Compte parent créé")));
        // Parent pas encore inscrit : bouton d'invitation WhatsApp avec le lien d'inscription parent
        Eleve sansCompte = eleve(r, "Hervé Invité", "6ème", telAleatoire());
        mvc.perform(get("/eleves/{id}", sansCompte.getId()).with(user(moi))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Inviter le parent sur WhatsApp")))
                .andExpect(content().string(containsString("https://wa.me/229")))
                .andExpect(content().string(containsString("inscription%2Fparent")));
        mvc.perform(get("/eleves/{id}", e.getId()).with(user(intrus))).andExpect(status().isNotFound());
        mvc.perform(get("/tableau-de-bord").with(user(moi))).andExpect(content().string(containsString("n'ont pas encore leurs créneaux")));

        // Le parent choisit un créneau aujourd'hui et un autre jour
        int aujourdhui = LocalDate.now().getDayOfWeek().getValue();
        int autreJour = aujourdhui % 7 + 1;
        mvc.perform(creneaux(parent, e.getId(), new String[]{String.valueOf(aujourdhui), String.valueOf(autreJour)}, new String[]{"09:00", "09:00"}))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/eleves/{id}", e.getId()).with(user(moi))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("n'a pas encore choisi"))))
                .andExpect(content().string(containsString("Présent")))
                .andExpect(content().string(containsString("Absent")));
        // Aucun formulaire de modification des créneaux côté répétiteur
        mvc.perform(post("/parent/eleves/{id}/creneaux", e.getId()).with(user(moi)).with(csrf())
                .param("jour", "1").param("jour", "3").param("heure", "10:00").param("heure", "10:00")).andExpect(status().isForbidden());

        Seance duJour = seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().atStartOfDay())
                .stream().filter(s -> s.getDebut().toLocalDate().equals(LocalDate.now())).findFirst().orElseThrow();
        Seance demain = seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().plusDays(1).atStartOfDay())
                .stream().findFirst().orElseThrow();

        // Un autre répétiteur ne peut pas pointer
        mvc.perform(post("/seances/{id}/pointage", duJour.getId()).with(user(intrus)).with(csrf()).param("presence", "PRESENT"))
                .andExpect(status().isNotFound());
        // Pointage « Présent »
        mvc.perform(post("/seances/{id}/pointage", duJour.getId()).with(user(moi)).with(csrf())
                        .param("presence", "PRESENT").param("retour", "/tableau-de-bord"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/tableau-de-bord"));
        Seance pointee = seanceRepo.findById(duJour.getId()).orElseThrow();
        assertEquals(Presence.PRESENT, pointee.getPresence());
        assertNotNull(pointee.getPointeLe());
        assertEquals(120, pointee.getDureeMinutes());
        // Séance d'un autre jour : pas encore pointable
        mvc.perform(post("/seances/{id}/pointage", demain.getId()).with(user(moi)).with(csrf()).param("presence", "ABSENT"))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("erreur"));
        assertEquals(Presence.NON_POINTEE, seanceRepo.findById(demain.getId()).orElseThrow().getPresence());
        // Redirection extérieure refusée
        mvc.perform(post("/seances/{id}/pointage", duJour.getId()).with(user(moi)).with(csrf())
                        .param("presence", "ABSENT").param("retour", "//evil.example"))
                .andExpect(redirectedUrl("/seances"));

        // Le répétiteur ne peut pas changer la durée : celle du formulaire est ignorée
        mvc.perform(post("/seances").with(user(moi)).with(csrf()).param("eleveId", String.valueOf(e.getId()))
                        .param("date", LocalDate.now().plusDays(20).toString()).param("heure", "10:00").param("dureeMinutes", "30"))
                .andExpect(status().is3xxRedirection());
        Seance manuelle = seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().plusDays(19).atStartOfDay()).get(0);
        assertEquals(120, manuelle.getDureeMinutes(), "durée imposée par la classe");
        mvc.perform(get("/seances").with(user(moi))).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("name=\"dureeMinutes\""))));
        // Le parent voit le pointage
        mvc.perform(get("/parent/eleves/{id}", e.getId()).with(user(parent))).andExpect(content().string(containsString("Présences notées")))
                .andExpect(content().string(containsString("Absent")));
    }

    @Test
    void leParentModifieLeNomEtLaClasseEtLaGrilleSuit() throws Exception {
        Repetiteur r = tuteur();
        String telP = telAleatoire();
        Eleve e = eleve(r, "Fifi Classe", "5ème", telP);
        UtilisateurConnecte parent = principal(parents.inscrire("Parent F", telP, "secret123").getTelephone());
        mvc.perform(creneaux(parent, e.getId(), new String[]{"2", "4"}, new String[]{"17:00", "17:00"})).andExpect(status().is3xxRedirection());
        mvc.perform(post("/parent/eleves/{id}/infos", e.getId()).with(user(parent)).with(csrf()).param("nom", "Fifi Renommée").param("classe", "1ère"))
                .andExpect(status().is3xxRedirection());
        Eleve apres = eleves.trouver(e.getId(), r.getId());
        assertEquals("Fifi Renommée", apres.getNom());
        assertEquals("1ère", apres.getClasse());
        assertEquals(40_000, apres.getTarifMensuel());
        assertTrue(planning.creneaux(e.getId()).isEmpty(), "la règle passe de 2 h à 3 h : créneaux à rechoisir");
        mvc.perform(post("/parent/eleves/{id}/infos", e.getId()).with(user(parent)).with(csrf()).param("nom", "X").param("classe", "Classe inventée"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void leSuperAdminReglesSeancesEtDureeEtVoitCreneauxEtPointages() throws Exception {
        Repetiteur sa = repetiteurs.inscrire("Super", "0100000078", "secret123");
        UtilisateurConnecte admin = principal("0100000078");
        assertTrue(admin.isSuperAdmin());
        Map<String, String> g = new HashMap<>();
        for (Classe c : Classe.values()) {
            g.put(c.name(), String.valueOf(c.getTarifDefaut()));
            g.put("seances_" + c.name(), String.valueOf(c.getSeancesParSemaineDefaut()));
            g.put("heures_" + c.name(), String.valueOf(c.getDureeHeuresDefaut()));
        }
        mvc.perform(get("/admin/tarifs").with(user(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"seances_TLE\"")))
                .andExpect(content().string(containsString("name=\"heures_TLE\"")));
        try {
            g.put("seances_TLE", "3");
            g.put("heures_TLE", "4");
            var post = post("/admin/tarifs").with(user(admin)).with(csrf());
            g.forEach(post::param);
            mvc.perform(post).andExpect(status().is3xxRedirection());
            assertEquals(new TarifService.Regle(3, 4), tarifs.regle("Tle"));
            // valeur invalide refusée
            g.put("heures_TLE", "12");
            var mauvais = post("/admin/tarifs").with(user(admin)).with(csrf());
            g.forEach(mauvais::param);
            mvc.perform(mauvais).andExpect(status().isOk());
            assertEquals(4, tarifs.regle("Tle").heures());
            // un répétiteur ne peut pas changer les règles
            UtilisateurConnecte simple = principal(tuteur().getTelephone());
            var interdit = post("/admin/tarifs").with(user(simple)).with(csrf());
            g.forEach(interdit::param);
            mvc.perform(interdit).andExpect(status().isForbidden());
        } finally {
            g.put("seances_TLE", "2");
            g.put("heures_TLE", "3");
            tarifs.enregistrer(g);
        }
        assertEquals(new TarifService.Regle(2, 3), tarifs.regle("Tle"));

        // Activité : créneaux choisis et pointages
        Repetiteur r = tuteur();
        String telP = telAleatoire();
        Eleve e = eleve(r, "Gilles Activité", "3ème", telP);
        UtilisateurConnecte parent = principal(parents.inscrire("Parent G", telP, "secret123").getTelephone());
        int aujourdhui = LocalDate.now().getDayOfWeek().getValue();
        mvc.perform(creneaux(parent, e.getId(), new String[]{String.valueOf(aujourdhui), String.valueOf(aujourdhui % 7 + 1)}, new String[]{"09:00", "09:00"}))
                .andExpect(status().is3xxRedirection());
        Seance s = seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().atStartOfDay()).get(0);
        mvc.perform(post("/seances/{id}/pointage", s.getId()).with(user(principal(r.getTelephone()))).with(csrf()).param("presence", "ABSENT"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/admin").with(user(admin))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Créneaux choisis")))
                .andExpect(content().string(containsString("Pointage")))
                .andExpect(content().string(containsString("comptes parents")));
    }
}
