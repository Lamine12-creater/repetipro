package bj.repetipro.config;

import bj.repetipro.model.*;
import bj.repetipro.repository.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;

/**
 * Données de démonstration, UNIQUEMENT en profil "dev" (jamais en production).
 * Tous les noms et numéros sont fictifs.
 * Compte démo : 01 00 00 00 00 / demo1234
 */
@Component
@Profile("dev")
public class DonneesDemo implements CommandLineRunner {

    private final RepetiteurRepository repetiteurs;
    private final EleveRepository eleves;
    private final SeanceRepository seances;
    private final FactureRepository factures;
    private final PasswordEncoder encodeur;

    public DonneesDemo(RepetiteurRepository repetiteurs, EleveRepository eleves, SeanceRepository seances,
                       FactureRepository factures, PasswordEncoder encodeur) {
        this.repetiteurs = repetiteurs;
        this.eleves = eleves;
        this.seances = seances;
        this.factures = factures;
        this.encodeur = encodeur;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (repetiteurs.existsByTelephone("+2290100000000")) return; // déjà créées

        Repetiteur r = new Repetiteur();
        r.setNomComplet("Rodrigue Démo");
        r.setTelephone("+2290100000000");
        r.setMotDePasseHash(encodeur.encode("demo1234"));
        r.setPlan(Plan.SOLO);
        r.setNumeroMomo("+2290100000000");
        r.setNomMomo("RODRIGUE DEMO");
        repetiteurs.save(r);

        Eleve fifame = eleve(r, "Fifamè Exemple", "3e", "Maths", "Mme Exemple", "+2290100000001", 25000);
        Eleve josue = eleve(r, "Josué Fictif", "Tle D", "PCT", "M. Fictif", "+2290100000002", 20000);
        Eleve sena = eleve(r, "Sèna Modèle", "CM2", "Français", "Mme Modèle", "+2290100000003", 16000);
        eleve(r, "Nafissatou Test", "1re C", "Maths", "M. Test", "+2290100000004", 9000);

        LocalDate aujourdhui = LocalDate.now();
        seance(r, fifame, aujourdhui.atTime(16, 0), Presence.NON_POINTEE, null, null);
        seance(r, josue, aujourdhui.atTime(17, 30), Presence.NON_POINTEE, null, null);
        seance(r, sena, aujourdhui.atTime(19, 0), Presence.NON_POINTEE, null, null);
        seance(r, fifame, aujourdhui.minusDays(2).atTime(16, 0), Presence.PRESENT, "Théorème de Thalès",
                "Fifamè a bien compris le théorème de Thalès. Devoir : exercice 7 page 112.");
        seance(r, josue, aujourdhui.plusDays(1).atTime(LocalTime.of(17, 30)), Presence.NON_POINTEE, null, null);

        // Une facture impayée du mois précédent (pour voir "En retard" / "En attente")
        YearMonth moisPasse = YearMonth.now().minusMonths(1);
        Facture f = new Facture();
        f.setRepetiteur(r);
        f.setEleve(josue);
        f.setAnnee(moisPasse.getYear());
        f.setMois(moisPasse.getMonthValue());
        f.setMontant(20000);
        f.setNombreSeances(8);
        f.setDateEcheance(moisPasse.plusMonths(1).atDay(10));
        factures.save(f);
    }

    private Eleve eleve(Repetiteur r, String nom, String classe, String matieres, String parent, String tel, int tarif) {
        Eleve e = new Eleve();
        e.setRepetiteur(r);
        e.setNom(nom);
        e.setClasse(classe);
        e.setMatieres(matieres);
        e.setNomParent(parent);
        e.setWhatsappParent(tel);
        e.setTarifMensuel(tarif);
        return eleves.save(e);
    }

    private void seance(Repetiteur r, Eleve e, LocalDateTime debut, Presence p, String notions, String cr) {
        Seance s = new Seance();
        s.setRepetiteur(r);
        s.setEleve(e);
        s.setDebut(debut);
        s.setDureeMinutes(120);
        s.setPresence(p);
        s.setNotions(notions);
        s.setCompteRendu(cr);
        seances.save(s);
    }
}
