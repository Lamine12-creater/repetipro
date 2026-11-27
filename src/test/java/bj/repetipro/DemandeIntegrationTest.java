package bj.repetipro;

import bj.repetipro.model.*;
import bj.repetipro.repository.DemandeRepository;
import bj.repetipro.repository.EnfantParentRepository;
import bj.repetipro.repository.SeanceRepository;
import bj.repetipro.service.*;
import bj.repetipro.web.EleveForm;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Le parent inscrit son enfant, choisit un répétiteur et ses horaires ; le répétiteur accepte ou refuse. */
@SpringBootTest(properties = {"repetipro.paiement.simulation=false", "repetipro.fedapay.secret-key=",
        "repetipro.admin-telephones=0100000079"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemandeIntegrationTest {

    @Autowired RepetiteurService repetiteurs;
    @Autowired ParentService parents;
    @Autowired EleveService eleves;
    @Autowired PlanningService planning;
    @Autowired DemandeService demandes;
    @Autowired TarifService tarifs;
    @Autowired DemandeRepository demandeRepo;
    @Autowired EnfantParentRepository enfantRepo;
    @Autowired SeanceRepository seanceRepo;
    @Autowired MockMvc mvc;

    private static String tel() {
        return "01" + String.format("%08d", Math.abs(UUID.randomUUID().hashCode()) % 100_000_000);
    }

    private Repetiteur tuteur(String nom) { return repetiteurs.inscrire(nom, tel(), "secret123"); }
    private UtilisateurConnecte p(String telephone) { return (UtilisateurConnecte) repetiteurs.loadUserByUsername(telephone); }
    private UtilisateurConnecte parent(String nom) { return p(parents.inscrire(nom, tel(), "secret123").getTelephone()); }

    private Long enfant(UtilisateurConnecte parent, String nom, String classe) throws Exception {
        String url = mvc.perform(post("/parent/enfants").with(user(parent)).with(csrf()).param("nom", nom).param("classe", classe))
                .andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        return Long.valueOf(url.replaceAll("\\D+(\\d+)\\D+", "$1"));
    }

    private MockHttpServletRequestBuilder demande(UtilisateurConnecte parent, Long enfantId, Long rep, String[] jours, String[] heures, String... extra) {
        var b = post("/parent/enfants/{id}/demande", enfantId).with(user(parent)).with(csrf()).param("repetiteur", String.valueOf(rep));
        for (String j : jours) b.param("jour", j);
        for (String h : heures) b.param("heure", h);
        for (int i = 0; i + 1 < extra.length; i += 2) b.param(extra[i], extra[i + 1]);
        return b;
    }

    @Test
    void scenarioCompletParentDemandeAcceptationPointage() throws Exception {
        Repetiteur r = tuteur("Tuteur Demande");
        UtilisateurConnecte moi = p(r.getTelephone());
        mvc.perform(post("/parametres").with(user(moi)).with(csrf()).param("nomComplet", "Tuteur Demande")
                .param("quartier", "Akpakpa").param("matieres", "Maths, PCT")).andExpect(status().is3xxRedirection());
        UtilisateurConnecte parent = parent("Parent Demande");
        int ja = LocalDate.now().getDayOfWeek().getValue();
        int jb = ja % 7 + 1;
        String ja1 = String.valueOf(ja), jb1 = String.valueOf(jb);
        String resumeA = Jours.libelle(ja) + " · 17h00 – 19h00", resumeB = Jours.libelle(jb) + " · 17h30 – 19h30";

        // 1. Le parent crée la fiche de l'enfant : le tarif de la grille est affiché automatiquement
        mvc.perform(get("/parent/enfants/nouveau").with(user(parent))).andExpect(status().isOk())
                .andExpect(content().string(containsString("data-tarif=\"" + bj.repetipro.util.Montants.fcfa(tarifs.montant("3ème")) + "\"")));
        mvc.perform(post("/parent/enfants").with(user(parent)).with(csrf()).param("nom", " ").param("classe", "3ème"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/parent/enfants").with(user(parent)).with(csrf()).param("nom", "Awa Test").param("classe", "Inconnue"))
                .andExpect(status().isBadRequest());
        Long enfantId = enfant(parent, "Awa Test", "3ème");

        // 2. Liste des répétiteurs disponibles, avec leur profil
        mvc.perform(get("/parent/enfants/{id}/repetiteurs", enfantId).with(user(parent))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Tuteur Demande")))
                .andExpect(content().string(containsString("Akpakpa")))
                .andExpect(content().string(containsString("Maths, PCT")));
        mvc.perform(get("/parent/enfants/{id}/demande", enfantId).with(user(parent)).param("repetiteur", String.valueOf(r.getId())))
                .andExpect(status().isOk()).andExpect(content().string(containsString("Envoyer ma demande")));

        // 3. La durée ne se négocie pas, le nombre de créneaux non plus
        mvc.perform(demande(parent, enfantId, r.getId(), new String[]{ja1, jb1}, new String[]{"17:00", "17:00"}, "dureeMinutes", "60"))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("fixée à 2 h")));
        mvc.perform(demande(parent, enfantId, r.getId(), new String[]{ja1}, new String[]{"17:00"}))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("exactement 2")));
        mvc.perform(demande(parent, enfantId, r.getId(), new String[]{ja1, ja1}, new String[]{"17:00", "10:00"}))
                .andExpect(status().isBadRequest());
        mvc.perform(demande(parent, enfantId, r.getId(), new String[]{ja1, jb1}, new String[]{"21:00", "17:00"}))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("avant 22h00")));
        assertEquals(0, demandeRepo.findByEnfantIdOrderByIdDesc(enfantId).size());

        // 4. Demande valide : EN_ATTENTE, le répétiteur est notifié (badge + page Demandes)
        assertEquals(0, demandes.enAttente(r.getId()));
        mvc.perform(demande(parent, enfantId, r.getId(), new String[]{ja1, jb1}, new String[]{"17:00", "17:30"}))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/parent/enfants/" + enfantId));
        DemandeRepetiteur d = demandeRepo.findByEnfantIdOrderByIdDesc(enfantId).get(0);
        assertEquals(StatutDemande.EN_ATTENTE, d.getStatut());
        assertEquals(1, demandes.enAttente(r.getId()));
        mvc.perform(get("/tableau-de-bord").with(user(moi))).andExpect(content().string(containsString("1 demande de parent")));
        mvc.perform(get("/demandes").with(user(moi))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Awa Test")))
                .andExpect(content().string(containsString(resumeA)))
                .andExpect(content().string(containsString(resumeB)))
                .andExpect(content().string(containsString("https://wa.me/")))
                .andExpect(content().string(containsString("Accepter")));
        // Le parent voit « en attente » ; une deuxième demande est refusée
        mvc.perform(get("/parent").with(user(parent))).andExpect(content().string(containsString("en attente de sa réponse")));
        mvc.perform(demande(parent, enfantId, r.getId(), new String[]{"1", "3"}, new String[]{"10:00", "10:00"}))
                .andExpect(status().isBadRequest());
        // Pas encore d'élève chez le répétiteur
        assertTrue(eleves.actifs(r.getId()).isEmpty());

        // 5. Un autre répétiteur ne peut ni accepter ni refuser
        UtilisateurConnecte intrus = p(tuteur("Intrus").getTelephone());
        mvc.perform(post("/demandes/{id}/accepter", d.getId()).with(user(intrus)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/demandes/{id}/refuser", d.getId()).with(user(intrus)).with(csrf())).andExpect(status().isNotFound());
        // Un parent ne peut pas accepter
        mvc.perform(post("/demandes/{id}/accepter", d.getId()).with(user(parent)).with(csrf())).andExpect(status().isForbidden());

        // 6. Acceptation : l'élève est créé chez le répétiteur avec les créneaux, au tarif de la grille
        mvc.perform(post("/demandes/{id}/accepter", d.getId()).with(user(moi)).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("message"));
        assertEquals(StatutDemande.ACCEPTEE, demandeRepo.findById(d.getId()).orElseThrow().getStatut());
        List<Eleve> liste = eleves.actifs(r.getId());
        assertEquals(1, liste.size());
        Eleve e = liste.get(0);
        assertEquals("Awa Test", e.getNom());
        assertEquals("3ème", e.getClasse());
        assertEquals(tarifs.montant("3ème"), e.getTarifMensuel(), "tarif de la grille");
        assertEquals(2, planning.creneaux(e.getId()).size());
        assertTrue(planning.creneaux(e.getId()).stream().anyMatch(c -> c.getResume().equals(resumeA)));
        assertFalse(seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().atStartOfDay()).isEmpty());
        assertEquals(e.getId(), enfantRepo.findById(enfantId).orElseThrow().getEleve().getId());
        assertEquals(0, demandes.enAttente(r.getId()));
        // Déjà traitée
        mvc.perform(post("/demandes/{id}/accepter", d.getId()).with(user(moi)).with(csrf()))
                .andExpect(flash().attributeExists("erreur"));

        // 7. Le parent voit « Suivi » et accède au suivi complet (sans doublon avec l'ancien affichage)
        mvc.perform(get("/parent").with(user(parent))).andExpect(content().string(containsString("Voir le suivi")))
                .andExpect(content().string(not(containsString("Créneaux à choisir"))));
        mvc.perform(get("/parent/eleves/{id}", e.getId()).with(user(parent))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Vos créneaux")))
                .andExpect(content().string(containsString("17h00 · 2 h")));

        // 8. Le pointage fonctionne comme avant : on pointe une séance créée par l'acceptation
        Seance s = seanceRepo.findByEleveIdAndDebutGreaterThanEqualOrderByDebutAsc(e.getId(), LocalDate.now().atStartOfDay())
                .stream().filter(x -> x.getDebut().toLocalDate().equals(LocalDate.now())).findFirst().orElseThrow();
        mvc.perform(post("/seances/{id}/pointage", s.getId()).with(user(intrus)).with(csrf()).param("presence", "PRESENT"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/seances/{id}/pointage", s.getId()).with(user(moi)).with(csrf()).param("presence", "PRESENT"))
                .andExpect(status().is3xxRedirection());
        Seance pointee = seanceRepo.findById(s.getId()).orElseThrow();
        assertEquals(Presence.PRESENT, pointee.getPresence());
        assertEquals(120, pointee.getDureeMinutes());
        // Admin : la demande apparaît dans l'activité récente
        assertTrue(demandeRepo.findTop8ByOrderByDateCreationDesc().stream().anyMatch(x -> x.getId().equals(d.getId())));
    }

    @Test
    void refusAvecMotifPuisAutreRepetiteur() throws Exception {
        Repetiteur r1 = tuteur("Premier Tuteur");
        Repetiteur r2 = tuteur("Second Tuteur");
        UtilisateurConnecte t1 = p(r1.getTelephone());
        UtilisateurConnecte parent = parent("Parent Refus");
        Long enfantId = enfant(parent, "Bako Refus", "2nde");

        mvc.perform(demande(parent, enfantId, r1.getId(), new String[]{"1", "5"}, new String[]{"08:00", "08:00"}))
                .andExpect(status().is3xxRedirection());
        DemandeRepetiteur d = demandeRepo.findByEnfantIdOrderByIdDesc(enfantId).get(0);
        assertEquals(180, demandes.creneaux(d.getId()).get(0).getDureeMinutes(), "durée de la classe (3 h)");

        mvc.perform(post("/demandes/{id}/refuser", d.getId()).with(user(t1)).with(csrf()).param("motif", "Indisponible le lundi"))
                .andExpect(status().is3xxRedirection());
        DemandeRepetiteur apres = demandeRepo.findById(d.getId()).orElseThrow();
        assertEquals(StatutDemande.REFUSEE, apres.getStatut());
        assertEquals("Indisponible le lundi", apres.getMotif());
        assertTrue(eleves.actifs(r1.getId()).isEmpty(), "aucun élève créé par un refus");
        assertEquals(0, demandes.enAttente(r1.getId()));

        // Le parent voit le statut et le motif, et peut choisir un autre répétiteur
        mvc.perform(get("/parent/enfants/{id}", enfantId).with(user(parent))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Refusée")))
                .andExpect(content().string(containsString("Indisponible le lundi")))
                .andExpect(content().string(containsString("Choisir un autre répétiteur")));
        mvc.perform(get("/parent").with(user(parent))).andExpect(content().string(containsString("Indisponible le lundi")));
        mvc.perform(demande(parent, enfantId, r2.getId(), new String[]{"1", "5"}, new String[]{"08:00", "08:00"}))
                .andExpect(status().is3xxRedirection());
        DemandeRepetiteur d2 = demandeRepo.findByEnfantIdOrderByIdDesc(enfantId).get(0);
        assertEquals(r2.getId(), d2.getRepetiteur().getId());

        // Annulation par le parent (réservée au parent concerné)
        UtilisateurConnecte autreParent = parent("Autre Parent");
        mvc.perform(post("/parent/demandes/{id}/annuler", d2.getId()).with(user(autreParent)).with(csrf())).andExpect(status().isNotFound());
        mvc.perform(post("/parent/demandes/{id}/annuler", d2.getId()).with(user(parent)).with(csrf())).andExpect(status().is3xxRedirection());
        assertEquals(StatutDemande.ANNULEE, demandeRepo.findById(d2.getId()).orElseThrow().getStatut());
        assertEquals(0, demandes.enAttente(r2.getId()));
        // Une demande annulée ou refusée ne peut plus être acceptée
        mvc.perform(post("/demandes/{id}/accepter", d2.getId()).with(user(p(r2.getTelephone()))).with(csrf()))
                .andExpect(flash().attributeExists("erreur"));
        assertTrue(eleves.actifs(r2.getId()).isEmpty());
        // Un autre parent ne voit pas la fiche de cet enfant
        mvc.perform(get("/parent/enfants/{id}", enfantId).with(user(autreParent))).andExpect(status().isNotFound());
        mvc.perform(get("/parent/enfants/{id}/repetiteurs", enfantId).with(user(autreParent))).andExpect(status().isNotFound());
    }

    @Test
    void creneauDejaPrisIndisponibleEtConflitsSignales() throws Exception {
        Repetiteur r = tuteur("Tuteur Occupé");
        UtilisateurConnecte moi = p(r.getTelephone());
        // Un élève ajouté à l'ancienne par le répétiteur occupe déjà le mardi 17h00 – 19h00
        EleveForm f = new EleveForm();
        f.setNom("Secret Voisin"); f.setClasse("3ème"); f.setNomParent("Parent Voisin"); f.setWhatsappParent(tel());
        Eleve voisin = eleves.creer(r.getId(), f);
        assertNull(planning.enregistrer(voisin.getId(), List.of("2", "4"), List.of("17:00", "17:00"), null));

        UtilisateurConnecte parent = parent("Parent Conflit");
        Long enfantId = enfant(parent, "Cathy Conflit", "4ème");
        // Le formulaire montre le créneau pris, sans nom d'élève
        mvc.perform(get("/parent/enfants/{id}/demande", enfantId).with(user(parent)).param("repetiteur", String.valueOf(r.getId())))
                .andExpect(content().string(containsString("Mardi · 17h00 – 19h00")))
                .andExpect(content().string(not(containsString("Secret Voisin"))));
        // Chevauchement refusé côté serveur (18h00 se chevauche)
        mvc.perform(demande(parent, enfantId, r.getId(), new String[]{"2", "5"}, new String[]{"18:00", "10:00"}))
                .andExpect(status().isBadRequest()).andExpect(content().string(containsString("pas disponible chez ce répétiteur")))
                .andExpect(content().string(not(containsString("Secret Voisin"))));
        // Bord à bord autorisé : 19h00 commence quand l'autre finit
        mvc.perform(demande(parent, enfantId, r.getId(), new String[]{"2", "5"}, new String[]{"19:00", "10:00"}))
                .andExpect(status().is3xxRedirection());

        // Deux demandes en attente sur le même créneau : le répétiteur est averti, n'en accepte qu'une
        UtilisateurConnecte parent2 = parent("Parent Concurrent");
        Long enfant2 = enfant(parent2, "Denis Concurrent", "4ème");
        mvc.perform(demande(parent2, enfant2, r.getId(), new String[]{"2", "6"}, new String[]{"19:30", "10:00"}))
                .andExpect(status().is3xxRedirection());
        mvc.perform(get("/demandes").with(user(moi))).andExpect(content().string(containsString("Même créneau que la demande")));
        DemandeRepetiteur d1 = demandeRepo.findByEnfantIdOrderByIdDesc(enfantId).get(0);
        DemandeRepetiteur d2 = demandeRepo.findByEnfantIdOrderByIdDesc(enfant2).get(0);
        mvc.perform(post("/demandes/{id}/accepter", d1.getId()).with(user(moi)).with(csrf())).andExpect(flash().attributeExists("message"));
        mvc.perform(post("/demandes/{id}/accepter", d2.getId()).with(user(moi)).with(csrf()))
                .andExpect(flash().attributeExists("erreur"));
        assertEquals(StatutDemande.EN_ATTENTE, demandeRepo.findById(d2.getId()).orElseThrow().getStatut());
        assertEquals(2, eleves.actifs(r.getId()).size());
        // L'élève accepté ne peut pas être doublé : le parent modifie ses créneaux par l'ancienne page, conflit refusé
        Eleve accepte = eleves.actifs(r.getId()).stream().filter(x -> x.getNom().equals("Cathy Conflit")).findFirst().orElseThrow();
        assertNotNull(planning.enregistrer(accepte.getId(), List.of("2", "5"), List.of("17:00", "10:00"), null));
    }

    @Test
    void seulsLesRepetiteursActifsEtAvecPlaceSontProposes() throws Exception {
        Repetiteur actif = tuteur("Actif Visible");
        Repetiteur suspendu = tuteur("Suspendu Cache");
        repetiteurs.changerActivation(suspendu.getId(), false);
        Repetiteur admin = repetiteurs.inscrire("Admin Cache", "0100000079", "secret123");
        Repetiteur plein = tuteur("Complet Cache");
        for (int i = 0; i < EleveService.LIMITE_GRATUITE; i++) {
            EleveForm f = new EleveForm();
            f.setNom("E" + i); f.setClasse("CI"); f.setNomParent("P"); f.setWhatsappParent(tel());
            eleves.creer(plein.getId(), f);
        }
        UtilisateurConnecte parent = parent("Parent Liste");
        Long enfantId = enfant(parent, "Liste Enfant", "CE1");
        mvc.perform(get("/parent/enfants/{id}/repetiteurs", enfantId).with(user(parent))).andExpect(status().isOk())
                .andExpect(content().string(containsString("Actif Visible")))
                .andExpect(content().string(not(containsString("Suspendu Cache"))))
                .andExpect(content().string(not(containsString("Admin Cache"))))
                .andExpect(content().string(not(containsString("Complet Cache"))));
        // On ne peut pas contourner la liste en forçant l'identifiant
        mvc.perform(demande(parent, enfantId, suspendu.getId(), new String[]{"1", "3"}, new String[]{"10:00", "10:00"}))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("erreur"));
        mvc.perform(demande(parent, enfantId, admin.getId(), new String[]{"1", "3"}, new String[]{"10:00", "10:00"}))
                .andExpect(status().is3xxRedirection()).andExpect(flash().attributeExists("erreur"));
        assertTrue(demandeRepo.findByEnfantIdOrderByIdDesc(enfantId).isEmpty());
        // Pas de modification de la classe pendant une demande en cours
        mvc.perform(demande(parent, enfantId, actif.getId(), new String[]{"1", "3"}, new String[]{"10:00", "10:00"}))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/parent/enfants/{id}/infos", enfantId).with(user(parent)).with(csrf()).param("nom", "X").param("classe", "Tle"))
                .andExpect(status().isBadRequest());
        // Les pages sont réservées aux bons rôles
        mvc.perform(get("/demandes").with(user(parent))).andExpect(status().isForbidden());
        mvc.perform(get("/parent/enfants/nouveau").with(user(p(actif.getTelephone())))).andExpect(status().isForbidden());
        mvc.perform(get("/demandes")).andExpect(status().is3xxRedirection());
    }
}
