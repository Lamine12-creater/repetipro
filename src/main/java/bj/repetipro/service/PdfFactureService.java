package bj.repetipro.service;

import bj.repetipro.model.Eleve;
import bj.repetipro.model.Facture;
import bj.repetipro.model.Repetiteur;
import bj.repetipro.model.StatutFacture;
import bj.repetipro.util.Montants;
import bj.repetipro.util.Telephones;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Fabrique le PDF d'une facture avec OpenPDF (bibliothèque gratuite). */
@Service
public class PdfFactureService {

    private static final Color VERT = new Color(15, 118, 110);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    public byte[] generer(Facture f) {
        Repetiteur r = f.getRepetiteur();
        Eleve e = f.getEleve();
        ByteArrayOutputStream sortie = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A5, 36, 36, 36, 36); // A5 : se lit bien sur téléphone
        PdfWriter.getInstance(doc, sortie);
        doc.open();

        Font titre = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, VERT);
        Font gras = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        Font normal = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font petit = FontFactory.getFont(FontFactory.HELVETICA, 8, Color.GRAY);

        doc.add(new Paragraph("RépétiPro", titre));
        doc.add(new Paragraph("Facture " + f.getNumero(), gras));
        doc.add(new Paragraph("Émise le " + java.time.LocalDate.now().format(DATE), petit));
        doc.add(Chunk.NEWLINE);

        // Bloc répétiteur / parent
        PdfPTable entete = new PdfPTable(2);
        entete.setWidthPercentage(100);
        entete.addCell(cellule("Répétiteur\n" + r.getNomComplet() + "\nTél. " + Telephones.afficher(r.getTelephone()), normal));
        entete.addCell(cellule("Parent / élève\n" + (e.getNomParent() == null ? "" : e.getNomParent() + "\n")
                + e.getNom() + " (" + e.getClasse() + ")", normal));
        doc.add(entete);
        doc.add(Chunk.NEWLINE);

        // Détail
        PdfPTable detail = new PdfPTable(new float[]{3, 1.4f});
        detail.setWidthPercentage(100);
        detail.addCell(enTete("Désignation"));
        detail.addCell(enTete("Montant"));
        String libelle = "Cours à domicile — " + FactureService.libelleMois(f.getPeriode())
                + (e.getMatieres() == null || e.getMatieres().isBlank() ? "" : "\nMatières : " + e.getMatieres())
                + "\nSéances réalisées : " + f.getNombreSeances();
        detail.addCell(cellule(libelle, normal));
        detail.addCell(cellule(Montants.fcfa(f.getMontant()), gras));
        PdfPCell total = cellule("TOTAL À PAYER", gras);
        total.setBackgroundColor(new Color(204, 251, 241));
        detail.addCell(total);
        PdfPCell totalMontant = cellule(Montants.fcfa(f.getMontant()), gras);
        totalMontant.setBackgroundColor(new Color(204, 251, 241));
        detail.addCell(totalMontant);
        doc.add(detail);
        doc.add(Chunk.NEWLINE);

        // Statut et paiement
        if (f.getStatut() == StatutFacture.PAYE) {
            doc.add(new Paragraph("PAYÉE le " + f.getDatePaiement().toLocalDate().format(DATE)
                    + (f.getReferenceMomo() == null || f.getReferenceMomo().isBlank() ? "" : " — Réf. MoMo : " + f.getReferenceMomo()),
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11, new Color(22, 163, 74))));
        } else {
            doc.add(new Paragraph("À régler avant le " + f.getDateEcheance().format(DATE), gras));
            if (r.getNumeroMomo() != null) {
                doc.add(new Paragraph("Paiement Mobile Money (MTN MoMo, Moov Money ou Celtiis Cash) au "
                        + Telephones.afficher(r.getNumeroMomo())
                        + (r.getNomMomo() == null || r.getNomMomo().isBlank() ? "" : " — " + r.getNomMomo()), normal));
                doc.add(new Paragraph("Motif : " + e.getPrenom() + " " + FactureService.libelleMois(f.getPeriode()), normal));
            }
        }
        doc.add(Chunk.NEWLINE);
        doc.add(new Paragraph("Document généré avec RépétiPro. Merci de votre confiance.", petit));
        doc.close();
        return sortie.toByteArray();
    }

    private PdfPCell cellule(String texte, Font police) {
        PdfPCell c = new PdfPCell(new Phrase(texte, police));
        c.setPadding(6);
        c.setBorderColor(new Color(229, 231, 235));
        return c;
    }

    private PdfPCell enTete(String texte) {
        PdfPCell c = cellule(texte, FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, Color.WHITE));
        c.setBackgroundColor(VERT);
        return c;
    }
}
