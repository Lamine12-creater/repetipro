package bj.repetipro.web;

import bj.repetipro.util.Montants;
import bj.repetipro.util.Telephones;
import org.springframework.stereotype.Component;

/**
 * Petites fonctions de mise en forme utilisables dans les gabarits Thymeleaf :
 *   ${@fmt.fcfa(25000)}  ->  "25 000 FCFA"
 *   ${@fmt.tel(eleve.whatsappParent)} -> "01 97 00 00 00"
 */
@Component("fmt")
public class Formats {
    public String fcfa(long montant) { return Montants.fcfa(montant); }
    /** Montant sans le mot FCFA (pour les petites cases). */
    public String nombre(long montant) { return Montants.fcfa(montant).replace(" FCFA", ""); }
    /** "octobre 2026" */
    public String mois(java.time.YearMonth m) { return bj.repetipro.service.FactureService.libelleMois(m); }
    /** "d'octobre 2026" */
    public String deMois(java.time.YearMonth m) { return bj.repetipro.service.FactureService.deMois(m); }
    public String tel(String normalise) { return normalise == null ? "" : Telephones.afficher(normalise); }
}
