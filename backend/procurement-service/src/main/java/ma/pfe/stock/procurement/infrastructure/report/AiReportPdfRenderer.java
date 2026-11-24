package ma.pfe.stock.procurement.infrastructure.report;

import com.lowagie.text.Chunk;
import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.ColumnText;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfPageEventHelper;
import com.lowagie.text.pdf.PdfWriter;
import ma.pfe.stock.procurement.application.dto.AiReport;
import ma.pfe.stock.procurement.application.port.AiReportRenderer;
import org.springframework.stereotype.Component;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Rapport PDF imprimable « apport de l'agent IA achats » (OpenPDF, A4 portrait) : indicateurs, recommandations,
 * meilleures offres, prédictions de prix, économies par fournisseur et méthode.
 */
@Component
public class AiReportPdfRenderer implements AiReportRenderer {

    private static final Color PRIMARY = new Color(0x1F, 0x4E, 0x79);
    private static final Color ACCENT = new Color(0x25, 0x63, 0xEB);
    private static final Color LIGHT = new Color(0xF3, 0xF6, 0xF9);
    private static final Color BORDER = new Color(0xDD, 0xE3, 0xEA);
    private static final Color GREEN = new Color(0x15, 0x80, 0x3D);
    private static final Color RED = new Color(0xB9, 0x1C, 0x1C);
    private static final Color TRACK = new Color(0xE8, 0xEE, 0xF6);

    private static final Font TITLE = new Font(Font.HELVETICA, 18, Font.BOLD, Color.WHITE);
    private static final Font BAND = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.WHITE);
    private static final Font H2 = new Font(Font.HELVETICA, 12, Font.BOLD, PRIMARY);
    private static final Font TEXT = new Font(Font.HELVETICA, 9, Font.NORMAL, Color.DARK_GRAY);
    private static final Font NOTE = new Font(Font.HELVETICA, 8, Font.ITALIC, Color.GRAY);
    private static final Font KPI_VALUE = new Font(Font.HELVETICA, 14, Font.BOLD, PRIMARY);
    private static final Font KPI_LABEL = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.DARK_GRAY);
    private static final Font TH = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.WHITE);
    private static final Font TD = new Font(Font.HELVETICA, 7.5f, Font.NORMAL, Color.BLACK);
    private static final Font TD_BOLD = new Font(Font.HELVETICA, 7.5f, Font.BOLD, Color.BLACK);
    private static final Font TD_GREEN = new Font(Font.HELVETICA, 7.5f, Font.BOLD, GREEN);
    private static final Font TD_RED = new Font(Font.HELVETICA, 7.5f, Font.BOLD, RED);
    private static final Font FOOTER = new Font(Font.HELVETICA, 7, Font.NORMAL, Color.GRAY);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ZoneId zone;

    public AiReportPdfRenderer(ZoneId zone) {
        this.zone = zone;
    }

    @Override
    public byte[] render(AiReport report) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(PageSize.A4, 32, 32, 30, 40);
        try {
            PdfWriter writer = PdfWriter.getInstance(doc, out);
            writer.setPageEvent(new Footer(DATE_TIME.format(report.generatedAt().atZone(zone))));
            doc.addTitle("Rapport de l'agent IA achats");
            doc.addCreator("Makzeo - procurement-service");
            doc.open();

            header(doc, report);
            kpis(doc, report);
            highlights(doc, writer, report);
            recommendations(doc, writer, report);
            comparisons(doc, writer, report);
            predictions(doc, writer, report);
            savingsBySupplier(doc, writer, report);
            method(doc, writer, report);
            doc.close();
        } catch (DocumentException e) {
            throw new IllegalStateException("Génération du rapport PDF impossible", e);
        }
        return out.toByteArray();
    }

    // ------------------------------------------------------------------------------------------
    private void header(Document doc, AiReport r) throws DocumentException {
        PdfPTable band = new PdfPTable(1);
        band.setWidthPercentage(100);
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(PRIMARY);
        cell.setBorder(Rectangle.NO_BORDER);
        cell.setPadding(14);
        cell.addElement(new Paragraph("Rapport de l'agent IA achats", TITLE));
        Paragraph sub = new Paragraph(String.format(Locale.FRANCE,
                "Veille fournisseurs, comparaison des offres, prédiction des prix et achats anticipés - marché : %s%n"
                        + "Généré le %s par %s - montants en %s hors taxes - moteur IA : %s",
                r.marketName(), DATE_TIME.format(r.generatedAt().atZone(zone)), r.generatedBy(), r.currency(),
                r.aiEnabled() ? "API Claude activée" : "non configuré (calculs statistiques seuls)"), BAND);
        sub.setSpacingBefore(4);
        cell.addElement(sub);
        band.addCell(cell);
        band.setSpacingAfter(12);
        doc.add(band);
    }

    private void kpis(Document doc, AiReport r) throws DocumentException {
        AiReport.Kpis k = r.kpis();
        String cur = " " + r.currency();
        PdfPTable grid = new PdfPTable(4);
        grid.setWidthPercentage(100);
        kpi(grid, money(k.potentialSavings()) + cur, "Économies potentielles (opportunités ouvertes)");
        kpi(grid, money(k.realizedSavings()) + cur, "Économies réalisées (recommandations acceptées)");
        kpi(grid, k.openOpportunities() + " / " + k.acceptedOpportunities(), "Opportunités ouvertes / acceptées");
        kpi(grid, String.valueOf(k.comparedProducts()), "Articles comparés sur le marché");
        kpi(grid, String.valueOf(k.trackedProducts()), "Articles dont le prix est prédit");
        kpi(grid, k.risingPrices() + " / " + k.fallingPrices(), "Hausses / baisses significatives prévues");
        kpi(grid, k.validatedSuppliers() + " / " + k.candidateSuppliers(), "Fournisseurs validés / à valider");
        kpi(grid, k.sentRfqs() + " / " + k.draftRfqs(), "Demandes de prix envoyées / en brouillon");
        grid.setSpacingAfter(10);
        doc.add(grid);
    }

    private void kpi(PdfPTable grid, String value, String label) {
        PdfPCell cell = new PdfPCell();
        cell.setBackgroundColor(LIGHT);
        cell.setBorderColor(Color.WHITE);
        cell.setBorderWidth(3);
        cell.setPadding(8);
        cell.addElement(new Paragraph(value, KPI_VALUE));
        cell.addElement(new Paragraph(label, KPI_LABEL));
        grid.addCell(cell);
    }

    private void highlights(Document doc, PdfWriter w, AiReport r) throws DocumentException {
        section(doc, w, "L'apport de l'agent en bref");
        AiReport.Kpis k = r.kpis();
        String cur = " " + r.currency();
        bullet(doc, String.format(Locale.FRANCE,
                "%d recommandation(s) d'achat produite(s) : %s%s d'économies au total (réalisées et potentielles), "
                        + "dont %s%s par changement de fournisseur et %s%s par achat anticipé.",
                r.recommendations().size(), money(k.potentialSavings().add(k.realizedSavings())), cur,
                money(k.supplierSwitchGains()), cur, money(k.anticipatedPurchaseGains()), cur));
        long cheaper = r.comparisons().stream()
                .filter(c -> c.savingPct() != null && c.savingPct().signum() > 0).count();
        bullet(doc, String.format(Locale.FRANCE,
                "%d article(s) comparé(s) sur le marché ; %d disposent d'une offre moins chère que le dernier prix payé.",
                r.comparisons().size(), cheaper));
        bullet(doc, String.format(Locale.FRANCE,
                "%d prédiction(s) de prix, dont %d à confiance élevée ; %d hausse(s) significative(s) à anticiper.",
                k.trackedProducts(), k.highConfidencePredictions(), k.risingPrices()));
        if (r.lastRun() != null) {
            bullet(doc, String.format(Locale.FRANCE,
                    "Dernière veille (%s) : %d article(s) analysé(s), %d fournisseur(s) découvert(s), %d offre(s) "
                            + "repérée(s), %d demande(s) de prix préparée(s).",
                    r.lastRun().period(), r.lastRun().productsScanned(), r.lastRun().suppliersDiscovered(),
                    r.lastRun().leadsFound(), r.lastRun().rfqsDrafted()));
        }
        bullet(doc, "Aucune décision n'est automatique : chaque fournisseur, demande de prix et achat est validé "
                + "par le responsable des achats.");
    }

    private void recommendations(Document doc, PdfWriter w, AiReport r) throws DocumentException {
        section(doc, w, "Recommandations d'achat");
        if (r.recommendations().isEmpty()) {
            doc.add(new Paragraph("Aucune recommandation sur la période.", NOTE));
            return;
        }
        PdfPTable t = table(new float[]{2.2f, 4.2f, 2.4f, 1.1f, 1.7f, 1.7f, 1.9f, 1.5f},
                "Type", "Article", "Fournisseur", "Qté", "Prix payé", "Prix conseillé", "Gain net", "Statut");
        int row = 0;
        for (AiReport.RecommendationLine l : r.recommendations()) {
            Color bg = row++ % 2 == 0 ? null : LIGHT;
            td(t, l.type(), TD, bg, Element.ALIGN_LEFT);
            td(t, l.productName() + "\n" + l.productReference(), TD, bg, Element.ALIGN_LEFT);
            td(t, orDash(l.supplierName()), TD, bg, Element.ALIGN_LEFT);
            td(t, String.valueOf(l.quantity()), TD, bg, Element.ALIGN_RIGHT);
            td(t, money(l.referencePrice()), TD, bg, Element.ALIGN_RIGHT);
            td(t, money(l.recommendedPrice()), TD, bg, Element.ALIGN_RIGHT);
            td(t, money(l.netSavings()), TD_GREEN, bg, Element.ALIGN_RIGHT);
            td(t, l.status() + (l.buyBefore() != null ? "\navant le " + DATE.format(l.buyBefore()) : ""), TD, bg,
                    Element.ALIGN_LEFT);
        }
        doc.add(t);
    }

    private void comparisons(Document doc, PdfWriter w, AiReport r) throws DocumentException {
        section(doc, w, "Meilleures offres du marché face au dernier prix payé");
        if (r.comparisons().isEmpty()) {
            doc.add(new Paragraph("Aucune offre enregistrée.", NOTE));
            return;
        }
        PdfPTable t = table(new float[]{4.2f, 1f, 2.6f, 1.7f, 1.7f, 1.3f, 1.9f},
                "Article", "Offres", "Meilleure offre", "Prix HT", "Prix payé", "Écart", "Gain annuel");
        int row = 0;
        for (AiReport.ComparisonLine c : r.comparisons()) {
            Color bg = row++ % 2 == 0 ? null : LIGHT;
            td(t, c.productName() + "\n" + c.productReference(), TD, bg, Element.ALIGN_LEFT);
            td(t, String.valueOf(c.offers()), TD, bg, Element.ALIGN_RIGHT);
            td(t, c.bestSupplier() + (c.equivalent() ? "\n(article équivalent)" : ""), TD_BOLD, bg, Element.ALIGN_LEFT);
            td(t, money(c.bestPrice()), TD, bg, Element.ALIGN_RIGHT);
            td(t, money(c.referencePrice()), TD, bg, Element.ALIGN_RIGHT);
            boolean saving = c.savingPct() != null && c.savingPct().signum() > 0;
            td(t, c.savingPct() == null ? "-" : (saving ? "-" : "+") + pct(c.savingPct().abs()),
                    saving ? TD_GREEN : TD, bg, Element.ALIGN_RIGHT);
            td(t, money(c.annualSaving()), saving ? TD_GREEN : TD, bg, Element.ALIGN_RIGHT);
        }
        doc.add(t);
        doc.add(new Paragraph("Classement : 60 % prix effectif (minimum de commande compris), 20 % délai, 20 % qualité ; "
                + "un article équivalent perd 5 % de score.", NOTE));
    }

    private void predictions(Document doc, PdfWriter w, AiReport r) throws DocumentException {
        section(doc, w, "Prédictions de prix (variations les plus fortes)");
        if (r.predictions().isEmpty()) {
            doc.add(new Paragraph("Aucune prédiction : importer l'historique d'achats ou lancer la veille.", NOTE));
            return;
        }
        PdfPTable t = table(new float[]{4.6f, 1.8f, 1.8f, 1.4f, 1.2f, 2.2f, 1.2f},
                "Article", "Prix actuel", "Prix prévu", "Variation", "Délai", "Confiance", "Hist.");
        int row = 0;
        for (AiReport.PredictionLine p : r.predictions()) {
            Color bg = row++ % 2 == 0 ? null : LIGHT;
            td(t, p.productName() + "\n" + p.productReference(), TD, bg, Element.ALIGN_LEFT);
            td(t, money(p.currentPrice()), TD, bg, Element.ALIGN_RIGHT);
            td(t, money(p.predictedPrice()), TD, bg, Element.ALIGN_RIGHT);
            boolean up = p.changePct().signum() > 0;
            td(t, (up ? "+" : "") + pct(p.changePct()), up ? TD_RED : TD_GREEN, bg, Element.ALIGN_RIGHT);
            td(t, p.horizonDays() + " j", TD, bg, Element.ALIGN_RIGHT);
            td(t, p.confidenceLevel() + " (" + Math.round(p.confidence() * 100) + " %)", TD, bg, Element.ALIGN_LEFT);
            td(t, p.sampleSize() + " mois", TD, bg, Element.ALIGN_RIGHT);
        }
        doc.add(t);
    }

    private void savingsBySupplier(Document doc, PdfWriter w, AiReport r) throws DocumentException {
        List<AiReport.AmountLine> lines = r.savingsBySupplier();
        if (lines.isEmpty()) {
            return;
        }
        section(doc, w, "Économies par fournisseur recommandé");
        double max = lines.stream().mapToDouble(l -> l.realized().add(l.potential()).doubleValue()).max().orElse(1);
        PdfPTable t = table(new float[]{3f, 5f, 1.8f, 1.8f, 1f}, "Fournisseur", "", "Réalisé", "Potentiel", "Nb");
        for (AiReport.AmountLine l : lines) {
            td(t, l.label(), TD_BOLD, null, Element.ALIGN_LEFT);
            PdfPCell barCell = new PdfPCell(bar(l.realized().add(l.potential()).doubleValue() / Math.max(1, max)));
            barCell.setBorderColor(BORDER);
            barCell.setPadding(4);
            barCell.setVerticalAlignment(Element.ALIGN_MIDDLE);
            t.addCell(barCell);
            td(t, money(l.realized()), TD_GREEN, null, Element.ALIGN_RIGHT);
            td(t, money(l.potential()), TD, null, Element.ALIGN_RIGHT);
            td(t, String.valueOf(l.count()), TD, null, Element.ALIGN_RIGHT);
        }
        doc.add(t);
    }

    private PdfPTable bar(double ratio) {
        double r = Math.max(0.02, Math.min(1, ratio));
        PdfPTable bar = new PdfPTable(r >= 0.999 ? new float[]{1f} : new float[]{(float) r, (float) (1 - r)});
        bar.setWidthPercentage(100);
        PdfPCell fill = new PdfPCell(new Phrase(" ", TD));
        fill.setBackgroundColor(ACCENT);
        fill.setBorder(Rectangle.NO_BORDER);
        fill.setFixedHeight(8);
        bar.addCell(fill);
        if (r < 0.999) {
            PdfPCell rest = new PdfPCell(new Phrase(" ", TD));
            rest.setBackgroundColor(TRACK);
            rest.setBorder(Rectangle.NO_BORDER);
            rest.setFixedHeight(8);
            bar.addCell(rest);
        }
        return bar;
    }

    private void method(Document doc, PdfWriter w, AiReport r) throws DocumentException {
        section(doc, w, "Méthode et limites");
        String[] lines = {
            "Veille : l'API Claude recherche sur le web (marché " + r.marketName() + ") les fournisseurs proposant "
                    + "le même article ou un équivalent ; tout nouveau fournisseur reste « candidat » jusqu'à validation.",
            "Prix : un prix n'entre dans les calculs que s'il provient d'un achat réel, d'un devis reçu, d'une page web "
                    + "citée ou du catalogue ; l'IA ne fixe aucun prix.",
            "Prédiction : régression log-linéaire sur les prix mensuels payés, saisonnalité (24 mois et plus), "
                    + "inflation de référence et signal du marché ; la confiance dépend de l'ajustement et de la "
                    + "profondeur de l'historique.",
            "Achat anticipé : quantité optimale calculée mois par mois en comparant la hausse évitée au coût de "
                    + "stockage, dans la limite du budget ; aucune recommandation si stocker n'est pas rentable.",
            "Économies : réalisées = recommandations acceptées ; potentielles = recommandations ouvertes."
        };
        for (String line : lines) {
            bullet(doc, line);
        }
    }

    // ------------------------------------------------------------------------------------------
    private static void section(Document doc, PdfWriter w, String title) throws DocumentException {
        // évite un titre de section isolé en bas de page
        if (w.getVerticalPosition(true) - doc.bottom() < 110) {
            doc.newPage();
        }
        Paragraph p = new Paragraph(title, H2);
        p.setSpacingBefore(12);
        p.setSpacingAfter(6);
        doc.add(p);
    }

    private static void bullet(Document doc, String text) throws DocumentException {
        Paragraph p = new Paragraph();
        p.add(new Chunk("•  ", TD_BOLD));
        p.add(new Chunk(text, TEXT));
        p.setIndentationLeft(8);
        p.setSpacingAfter(3);
        doc.add(p);
    }

    private static PdfPTable table(float[] widths, String... headers) {
        PdfPTable t = new PdfPTable(widths);
        t.setWidthPercentage(100);
        t.setHeaderRows(1);
        for (String h : headers) {
            PdfPCell c = new PdfPCell(new Phrase(h, TH));
            c.setBackgroundColor(PRIMARY);
            c.setBorderColor(PRIMARY);
            c.setPadding(5);
            t.addCell(c);
        }
        return t;
    }

    private static void td(PdfPTable t, String text, Font font, Color bg, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text == null ? "-" : text, font));
        c.setHorizontalAlignment(align);
        c.setBorderColor(BORDER);
        c.setPadding(4);
        if (bg != null) {
            c.setBackgroundColor(bg);
        }
        t.addCell(c);
    }

    private static String orDash(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    static String money(BigDecimal value) {
        if (value == null) {
            return "-";
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        symbols.setGroupingSeparator(' ');
        return new DecimalFormat("#,##0.000", symbols).format(value);
    }

    private static String pct(BigDecimal value) {
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.FRANCE);
        return new DecimalFormat("0.0", symbols).format(value) + " %";
    }

    /** Pied de page : application, date de génération et numéro de page. */
    private static final class Footer extends PdfPageEventHelper {

        private final String generated;

        Footer(String generated) {
            this.generated = generated;
        }

        @Override
        public void onEndPage(PdfWriter writer, Document document) {
            Phrase left = new Phrase("Makzeo - Rapport de l'agent IA achats - " + generated, FOOTER);
            Phrase right = new Phrase("Page " + writer.getPageNumber(), FOOTER);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_LEFT, left,
                    document.left(), document.bottom() - 18, 0);
            ColumnText.showTextAligned(writer.getDirectContent(), Element.ALIGN_RIGHT, right,
                    document.right(), document.bottom() - 18, 0);
        }
    }
}
